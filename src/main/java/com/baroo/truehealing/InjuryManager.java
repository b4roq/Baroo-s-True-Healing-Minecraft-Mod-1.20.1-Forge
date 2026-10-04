package com.baroo.truehealing;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Server-side injury logic: storage, wound creation, ticking, timed treatments. */
public final class InjuryManager {
    private static final String NBT_KEY = "truehealing_injuries";
    private static final int DISINFECT_TICKS = 6000; // infection progress pauses for 5 min after a wipe
    private static final Map<UUID, InjuryData> CACHE = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    /** True while TrueHealing itself is dealing bleed damage, so it doesn't create new wounds. */
    public static boolean applyingBleed = false;

    private static final class Pending {
        final BodyPart part;
        final TreatAction action;
        int remaining;

        Pending(BodyPart part, TreatAction action, int ticks) {
            this.part = part;
            this.action = action;
            this.remaining = ticks;
        }
    }

    private record Check(String error, int ticks) {}

    private InjuryManager() {}

    // ---------------- storage ----------------

    public static InjuryData get(ServerPlayer p) {
        return CACHE.computeIfAbsent(p.getUUID(), id -> {
            CompoundTag pd = p.getPersistentData();
            return pd.contains(NBT_KEY) ? InjuryData.load(pd.getCompound(NBT_KEY)) : new InjuryData();
        });
    }

    public static InjuryData peek(ServerPlayer p) {
        return CACHE.get(p.getUUID());
    }

    public static void save(ServerPlayer p) {
        InjuryData d = CACHE.get(p.getUUID());
        if (d != null) p.getPersistentData().put(NBT_KEY, d.save());
    }

    public static void unload(ServerPlayer p) {
        PENDING.remove(p.getUUID());
        save(p);
        CACHE.remove(p.getUUID());
    }

    public static void clear(ServerPlayer p) {
        cancelAction(p, null);
        removeSlow(p);
        CACHE.put(p.getUUID(), new InjuryData());
        p.getPersistentData().remove(NBT_KEY);
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        InjuryData d = get(p);
        TrueHealingNetwork.sendSync(p, d.save());
        TrueHealingNetwork.sendVisual(p, Visuals.encode(d));
    }

    public static boolean isBleeding(ServerPlayer p) {
        InjuryData d = CACHE.get(p.getUUID());
        if (d == null) return false;
        for (BodyPart part : BodyPart.values())
            for (Wound w : d.get(part)) if (w.isBleeding()) return true;
        return false;
    }

    private static void msg(ServerPlayer p, String text) {
        if (TrueHealingConfig.SHOW_MESSAGES.get()) p.displayClientMessage(Component.literal(text), true);
        TrueHealingNetwork.sendMessage(p, text); // also shown inside the medical screen
    }

    // ---------------- wound creation (one wound per limb) ----------------

    /** Dressing items go back to the player when a worse wound replaces the old one. */
    private static void giveBackDressing(ServerPlayer p, Wound w) {
        boolean dirty = w.isDressingDirty();
        switch (w.dressing) {
            case RAG -> giveBack(p, new ItemStack(dirty ? TrueHealing.DIRTY_RAG.get() : TrueHealing.RAG.get()));
            case BANDAGE -> giveBack(p, new ItemStack(dirty ? TrueHealing.DIRTY_BANDAGE.get() : TrueHealing.BANDAGE.get()));
            default -> { } // bandaids are lost
        }
    }

    /**
     * A limb can only carry one wound (plus a fracture on legs). A new wound is ignored unless it is
     * worse than the existing one, in which case it replaces it.
     */
    public static void addWound(ServerPlayer p, BodyPart part, WoundType type) {
        if (type == WoundType.FRACTURE) {
            addFracture(p, part);
            return;
        }
        InjuryData d = get(p);
        List<Wound> list = d.get(part);
        Wound existing = null;
        for (Wound w : list) if (w.type != WoundType.FRACTURE) existing = w;
        if (existing != null) {
            if (existing.type.severity >= type.severity) return;
            giveBackDressing(p, existing);
            list.remove(existing);
        }
        list.add(new Wound(type));
        msg(p, "You got a " + type.label.toLowerCase() + " on your " + part.label.toLowerCase()
                + ". Open the medical screen to treat it.");
        sync(p);
    }

    public static void infectAll(ServerPlayer p) {
        InjuryData d = get(p);
        for (BodyPart part : BodyPart.values())
            for (Wound w : d.get(part)) if (w.type != WoundType.FRACTURE) w.infection = Math.max(w.infection, 30f);
        sync(p);
    }

    public static void takeAntibiotics(ServerPlayer p) {
        InjuryData d = get(p);
        d.antibioticTicks = Math.min(d.antibioticTicks + TrueHealingConfig.ANTIBIOTIC_SECONDS.get() * 20, 72000);
        msg(p, "You took antibiotics. Infections spread more slowly for a while.");
        save(p);
        sync(p);
    }

    // ---------------- fractures ----------------

    private static final UUID SLOW_UUID = UUID.fromString("7b1c3d54-2f0e-4a76-9a3f-5d1e8c0b9a11");

    public static void addFracture(ServerPlayer p, BodyPart part) {
        InjuryData d = get(p);
        List<Wound> list = d.get(part);
        for (Wound w : list) if (w.type == WoundType.FRACTURE) return;
        list.add(new Wound(WoundType.FRACTURE));
        msg(p, "You broke your " + part.label.toLowerCase() + "! Apply a splint.");
        updateSlow(p, d);
        sync(p);
    }

    private static void updateSlow(ServerPlayer p, InjuryData d) {
        AttributeInstance inst = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        double slow = (p.isCreative() || p.isSpectator()) ? 0 : d.fractureSlow();
        AttributeModifier cur = inst.getModifier(SLOW_UUID);
        if (slow <= 0) {
            if (cur != null) inst.removeModifier(SLOW_UUID);
            return;
        }
        if (cur != null && Math.abs(cur.getAmount() + slow) < 1.0E-6) return;
        if (cur != null) inst.removeModifier(SLOW_UUID);
        inst.addTransientModifier(new AttributeModifier(SLOW_UUID, "truehealing_fracture", -slow,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void removeSlow(ServerPlayer p) {
        AttributeInstance inst = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null && inst.getModifier(SLOW_UUID) != null) inst.removeModifier(SLOW_UUID);
    }

    // ---------------- ticking (called once per second) ----------------

    private static double infectionRiskMult(Wound w) {
        double m;
        if (w.dressing == DressingType.NONE) m = 1.0;
        else m = w.isDressingDirty() ? w.dressing.dirtyRiskMult : w.dressing.cleanRiskMult;
        if (w.stitched) m *= 0.3;
        if (w.disinfectTicks > 0) m *= 0.05;
        return m;
    }

    public static void tick(ServerPlayer p) {
        InjuryData d = get(p);
        updateSlow(p, d);
        if (d.antibioticTicks > 0) d.antibioticTicks = Math.max(0, d.antibioticTicks - 20);
        if (d.isEmpty()) return;
        if (p.isCreative() || p.isSpectator()) return;

        boolean changed = false;
        double drain = 0;
        float worst = 0f;

        for (BodyPart part : BodyPart.values()) {
            Iterator<Wound> it = d.get(part).iterator();
            while (it.hasNext()) {
                Wound w = it.next();
                w.age += 20;
                if (w.disinfectTicks > 0) w.disinfectTicks = Math.max(0, w.disinfectTicks - 20);

                boolean wasDirty = w.isDressingDirty();
                if (w.dressing != DressingType.NONE) w.dressingAge += 20;
                if (!wasDirty && w.isDressingDirty()) {
                    changed = true;
                    msg(p, "The dressing on your " + part.label.toLowerCase() + " has become dirty.");
                }

                // bleeding
                drain += w.type.bleedPer10s * w.bleedFactor() * TrueHealingConfig.BLEED_MULT.get() / 10.0;

                // infection
                if (!w.isInfected()) {
                    double chance = w.type.infectionPerMin / 60.0
                            * TrueHealingConfig.INFECTION_CHANCE_MULT.get() * infectionRiskMult(w);
                    if (chance > 0 && p.getRandom().nextDouble() < chance) {
                        w.infection = 1f;
                        changed = true;
                        msg(p, "Your " + part.label.toLowerCase() + " wound looks infected!");
                    }
                } else {
                    double rate = TrueHealingConfig.INFECTION_SPEED.get()
                            * (d.antibioticTicks > 0 ? TrueHealingConfig.ANTIBIOTIC_FACTOR.get() : 1.0);
                    if (w.disinfectTicks > 0) rate = 0;
                    else {
                        if (w.dressing != DressingType.NONE && !w.isDressingDirty()) rate *= 0.6;
                        if (w.stitched) rate *= 0.5;
                    }
                    w.infection = (float) Math.min(100.0, w.infection + rate);
                    worst = Math.max(worst, w.infection);
                }

                // healing: scratch 5 min, laceration 15 min (10 once stitched), deep wound 15 min regardless
                boolean canHeal;
                if (w.type == WoundType.FRACTURE) canHeal = w.splinted;
                else if (w.isInfected()) canHeal = false;
                else if (w.type == WoundType.DEEP_WOUND) canHeal = true;
                else canHeal = w.stitched || w.dressing != DressingType.NONE || w.type == WoundType.SCRATCH;
                if (canHeal) {
                    double hrate = 1.0;
                    if (w.type == WoundType.LACERATION && w.stitched) hrate = 1.5;
                    w.healProgress += (int) (20 * hrate * TrueHealingConfig.HEAL_SPEED_MULT.get());
                    if (w.healProgress >= w.type.healTicks) {
                        it.remove();
                        changed = true;
                        msg(p, "Your " + part.label.toLowerCase() + " wound has healed.");
                    }
                }
            }
        }

        // infection effects
        if (worst >= 25f) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, false, true));
        if (worst >= 50f) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0, false, false, true));
        if (worst >= 75f) {
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false, true));
            drain += 0.03;
        }
        if (worst >= 100f) drain += 0.07;

        if (drain > 0) {
            applyDrain(p, (float) drain);
            if (isBleeding(p)) {
                p.serverLevel().sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                        p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.25, 0.4, 0.25, 0.02);
            }
        }

        if (changed || p.tickCount % 100 == 0) {
            save(p);
            sync(p);
        }
    }

    private static void applyDrain(ServerPlayer p, float amount) {
        float hp = p.getHealth() - amount;
        if (hp <= 0.0f) {
            applyingBleed = true;
            try {
                p.hurt(p.damageSources().generic(), 1000.0f);
            } finally {
                applyingBleed = false;
            }
        } else {
            p.setHealth(hp);
        }
    }

    // ---------------- helpers ----------------

    private static float score(Wound w) {
        return w.infection * 10f + w.type.severity * 3f + (w.isBleeding() ? 1f : 0f);
    }

    private static Wound pick(List<Wound> list, Predicate<Wound> filter) {
        Wound best = null;
        for (Wound w : list) {
            if (!filter.test(w)) continue;
            if (best == null || score(w) > score(best)) best = w;
        }
        return best;
    }

    private static boolean hasItem(ServerPlayer p, Item item) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return true;
        return false;
    }

    private static boolean consume(ServerPlayer p, Item item) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean useSuture(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(TrueHealing.SUTURE_KIT.get())) {
                s.hurtAndBreak(1, p, pl -> {});
                return true;
            }
        }
        return false;
    }

    private static void sound(ServerPlayer p, SoundEvent ev) {
        p.level().playSound(null, p.blockPosition(), ev, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    private static void giveBack(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    // ---------------- timed actions ----------------

    private static int duration(TreatAction a, Wound target) {
        int base = switch (a) {
            case RAG, BANDAGE -> 30;            // 1.5 s
            case BANDAID -> 20;                 // 1 s
            case DISINFECT -> 30;               // 1.5 s
            case SPLINT -> 60;                  // 3 s
            case REMOVE -> 20;                  // 1 s
            case REMOVE_SPLINT -> 30;           // 1.5 s
            case STITCH -> target != null && target.type == WoundType.DEEP_WOUND ? 80 : 50; // 4 s / 2.5 s
        };
        return Math.max(1, (int) Math.round(base * TrueHealingConfig.ACTION_TIME_MULT.get()));
    }

    private static String label(TreatAction a) {
        return switch (a) {
            case RAG -> "Applying rag...";
            case BANDAGE -> "Applying bandage...";
            case BANDAID -> "Applying bandaid...";
            case DISINFECT -> "Disinfecting...";
            case STITCH -> "Stitching...";
            case SPLINT -> "Applying splint...";
            case REMOVE -> "Removing dressing...";
            case REMOVE_SPLINT -> "Removing splint...";
        };
    }

    /** Dry-run: can this action start right now, and how long would it take? */
    private static Check check(ServerPlayer p, BodyPart part, TreatAction a) {
        List<Wound> wounds = get(p).get(part);
        if (wounds.isEmpty()) return new Check("No wounds on your " + part.label.toLowerCase() + ".", 0);

        switch (a) {
            case DISINFECT -> {
                Wound w = pick(wounds, x -> x.type != WoundType.FRACTURE && x.infection > 0f && x.dressing == DressingType.NONE);
                if (w == null) {
                    boolean dressed = false;
                    for (Wound x : wounds) if (x.infection > 0f && x.dressing != DressingType.NONE) dressed = true;
                    return new Check(dressed ? "Remove the dressing first." : "No infection to disinfect there.", 0);
                }
                if (!hasItem(p, TrueHealing.WIPE.get())) return new Check("You need alcohol wipes.", 0);
                return new Check(null, duration(a, w));
            }
            case RAG, BANDAGE, BANDAID -> {
                DressingType dt = a == TreatAction.RAG ? DressingType.RAG
                        : a == TreatAction.BANDAGE ? DressingType.BANDAGE : DressingType.BANDAID;
                Item item = dt == DressingType.RAG ? TrueHealing.RAG.get()
                        : dt == DressingType.BANDAGE ? TrueHealing.BANDAGE.get() : TrueHealing.BANDAID.get();
                Wound w = pick(wounds, x -> x.dressing == DressingType.NONE && dt.canCover(x.type));
                if (w == null) return new Check("Nothing there that a " + dt.label.toLowerCase() + " can cover.", 0);
                if (!hasItem(p, item)) return new Check("You don't have a " + dt.label.toLowerCase() + ".", 0);
                return new Check(null, duration(a, w));
            }
            case STITCH -> {
                Wound w = pick(wounds, x -> !x.stitched && x.type != WoundType.SCRATCH
                        && x.type != WoundType.FRACTURE && x.dressing == DressingType.NONE);
                if (w == null) {
                    boolean covered = false;
                    for (Wound x : wounds) {
                        if (!x.stitched && x.type != WoundType.SCRATCH && x.type != WoundType.FRACTURE
                                && x.dressing != DressingType.NONE) covered = true;
                    }
                    return new Check(covered ? "Remove the dressing first." : "Nothing to stitch there.", 0);
                }
                if (!hasItem(p, TrueHealing.SUTURE_KIT.get())) return new Check("You need a suture needle.", 0);
                return new Check(null, duration(a, w));
            }
            case SPLINT -> {
                Wound w = pick(wounds, x -> x.type == WoundType.FRACTURE && !x.splinted);
                if (w == null) return new Check("Nothing to splint there.", 0);
                if (!hasItem(p, TrueHealing.SPLINT.get())) return new Check("You need a splint.", 0);
                return new Check(null, duration(a, w));
            }
            case REMOVE -> {
                Wound w = pick(wounds, x -> x.dressing != DressingType.NONE);
                if (w == null) return new Check("No dressing to remove.", 0);
                return new Check(null, duration(a, w));
            }
            case REMOVE_SPLINT -> {
                Wound w = pick(wounds, x -> x.type == WoundType.FRACTURE && x.splinted);
                if (w == null) return new Check("No splint to remove.", 0);
                return new Check(null, duration(a, w));
            }
        }
        return new Check("Nothing to do.", 0);
    }

    public static void requestAction(ServerPlayer p, BodyPart part, TreatAction a) {
        if (!p.isAlive()) return;
        if (PENDING.containsKey(p.getUUID())) {
            msg(p, "Finish what you're doing first.");
            return;
        }
        Check c = check(p, part, a);
        if (c.error() != null) {
            msg(p, c.error());
            return;
        }
        PENDING.put(p.getUUID(), new Pending(part, a, c.ticks()));
        TrueHealingNetwork.sendAction(p, c.ticks(), label(a));
    }

    public static void cancelAction(ServerPlayer p, String reason) {
        if (PENDING.remove(p.getUUID()) != null) {
            TrueHealingNetwork.sendAction(p, 0, "");
            if (reason != null) msg(p, reason);
        }
    }

    /** Called every tick. */
    public static void tickAction(ServerPlayer p) {
        Pending pa = PENDING.get(p.getUUID());
        if (pa == null) return;
        if (!p.isAlive()) {
            PENDING.remove(p.getUUID());
            return;
        }
        if (--pa.remaining > 0) return;
        PENDING.remove(p.getUUID());
        TrueHealingNetwork.sendAction(p, 0, "");
        treat(p, pa.part, pa.action);
    }

    // ---------------- treatment (runs when the timer finishes) ----------------

    private static void treat(ServerPlayer p, BodyPart part, TreatAction action) {
        Check c = check(p, part, action);
        if (c.error() != null) {
            msg(p, c.error());
            return;
        }
        InjuryData d = get(p);
        List<Wound> wounds = d.get(part);
        String where = part.label.toLowerCase();

        switch (action) {
            case DISINFECT -> {
                Wound w = pick(wounds, x -> x.type != WoundType.FRACTURE && x.infection > 0f && x.dressing == DressingType.NONE);
                if (w == null || !consume(p, TrueHealing.WIPE.get())) return;
                w.disinfectTicks = DISINFECT_TICKS;
                w.infection = Math.max(0f, w.infection - 35f);
                sound(p, SoundEvents.BOTTLE_FILL);
                msg(p, "Disinfected your " + where + ".");
            }
            case RAG, BANDAGE, BANDAID -> {
                DressingType dt = action == TreatAction.RAG ? DressingType.RAG
                        : action == TreatAction.BANDAGE ? DressingType.BANDAGE : DressingType.BANDAID;
                Item item = dt == DressingType.RAG ? TrueHealing.RAG.get()
                        : dt == DressingType.BANDAGE ? TrueHealing.BANDAGE.get() : TrueHealing.BANDAID.get();
                Wound w = pick(wounds, x -> x.dressing == DressingType.NONE && dt.canCover(x.type));
                if (w == null || !consume(p, item)) return;
                w.dressing = dt;
                w.dressingAge = 0;
                sound(p, SoundEvents.WOOL_PLACE);
                msg(p, "Applied a " + dt.label.toLowerCase() + " to your " + where + ".");
            }
            case STITCH -> {
                Wound w = pick(wounds, x -> !x.stitched && x.type != WoundType.SCRATCH
                        && x.type != WoundType.FRACTURE && x.dressing == DressingType.NONE);
                if (w == null || !useSuture(p)) return;
                w.stitched = true;
                sound(p, SoundEvents.LEASH_KNOT_PLACE);
                msg(p, "Stitched your " + where + ".");
            }
            case SPLINT -> {
                Wound w = pick(wounds, x -> x.type == WoundType.FRACTURE && !x.splinted);
                if (w == null || !consume(p, TrueHealing.SPLINT.get())) return;
                w.splinted = true;
                sound(p, SoundEvents.CHAIN_PLACE);
                msg(p, "Splinted your " + where + ".");
                updateSlow(p, d);
            }
            case REMOVE -> {
                int removed = 0;
                for (Wound x : wounds) {
                    if (x.dressing == DressingType.NONE) continue;
                    giveBackDressing(p, x);
                    x.dressing = DressingType.NONE;
                    x.dressingAge = 0;
                    removed++;
                }
                if (removed == 0) return;
                sound(p, SoundEvents.WOOL_BREAK);
                msg(p, "Removed the dressing from your " + where + ".");
            }
            case REMOVE_SPLINT -> {
                Wound f = pick(wounds, x -> x.type == WoundType.FRACTURE && x.splinted);
                if (f == null) return;
                f.splinted = false;
                giveBack(p, new ItemStack(TrueHealing.SPLINT.get()));
                sound(p, SoundEvents.CHAIN_PLACE);
                msg(p, "Took the splint off your " + where + ".");
                updateSlow(p, d);
            }
        }
        save(p);
        sync(p);
    }
}
