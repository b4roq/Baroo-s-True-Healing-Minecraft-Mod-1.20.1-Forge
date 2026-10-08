package com.baroo.truehealing;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TrueHealing.MODID)
public final class ServerEvents {
    private static final Map<UUID, Integer> PANIC = new HashMap<>();
    /** Whether the player had absorption hearts right before the current hit. */
    private static final Map<UUID, Boolean> HAD_ABSORPTION = new HashMap<>();

    private ServerEvents() {}

    // ---------------- where did a projectile actually hit? ----------------

    private static BodyPart partFromHeight(double rel, RandomSource rnd) {
        if (rel > 0.82) return BodyPart.HEAD;
        if (rel > 0.45) {
            int r = rnd.nextInt(10);
            return r < 6 ? BodyPart.TORSO : (r < 8 ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM);
        }
        return rnd.nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
    }

    /**
     * Arrows are still at the START of their flight step when they damage you, so their own height says
     * nothing about the impact point. Instead we trace the arrow's path into the player's hitbox and use
     * the entry point. Projectiles we can't trace spread their hits over the whole body.
     */
    private static BodyPart partFromProjectile(ServerPlayer p, Entity proj, RandomSource rnd) {
        AABB box = p.getBoundingBox().inflate(0.3);
        Vec3 from = proj.position();
        Vec3 point = null;
        if (box.contains(from)) {
            point = from; // bullets that report their impact position
        } else {
            Optional<Vec3> hit = box.clip(from, from.add(proj.getDeltaMovement()));
            if (hit.isPresent()) point = hit.get();
        }
        if (point != null) {
            double rel = Mth.clamp((point.y - p.getY()) / Math.max(0.1, p.getBbHeight()), 0.0, 1.0);
            return partFromHeight(rel, rnd);
        }
        int r = rnd.nextInt(100);
        if (r < 10) return BodyPart.HEAD;
        if (r < 50) return BodyPart.TORSO;
        if (r < 63) return BodyPart.LEFT_ARM;
        if (r < 76) return BodyPart.RIGHT_ARM;
        if (r < 88) return BodyPart.LEFT_LEG;
        return BodyPart.RIGHT_LEG;
    }

    /** Runs before absorption is subtracted, so we know if the player was shielded by absorption hearts. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            HAD_ABSORPTION.put(p.getUUID(), p.getAbsorptionAmount() > 0f);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent e) {
        if (!TrueHealingConfig.ENABLED.get()) return;
        if (InjuryManager.applyingBleed) return;
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (p.isCreative() || p.isSpectator()) return;

        Boolean hadAbsorption = HAD_ABSORPTION.remove(p.getUUID());

        float amount = e.getAmount();
        if (amount < 1.0f) return;

        InjuryManager.cancelAction(p, "Interrupted!");

        // absorption hearts soak up injuries too, until they are used up (or the effect ends)
        if (hadAbsorption != null && hadAbsorption) return;

        DamageSource src = e.getSource();
        Entity attacker = src.getEntity();
        Entity direct = src.getDirectEntity();
        boolean explosion = src.is(DamageTypeTags.IS_EXPLOSION);
        if (attacker == null && direct == null && !explosion) return; // environment: ignored for now

        RandomSource rnd = p.getRandom();
        boolean projectile = direct != null && direct != attacker;
        boolean skeletonArrow = projectile && attacker instanceof AbstractSkeleton;

        double chance = Mth.clamp(TrueHealingConfig.WOUND_CHANCE_BASE.get()
                + amount * TrueHealingConfig.WOUND_CHANCE_PER_DAMAGE.get(), 0.0, 1.0);
        if (projectile || explosion) chance = Math.min(1.0, chance + 0.3);
        if (skeletonArrow) chance *= TrueHealingConfig.SKELETON_ARROW_MULT.get();
        if (rnd.nextDouble() > chance) return;

        // body part
        BodyPart part;
        if (projectile) {
            part = partFromProjectile(p, direct, rnd);
        } else if (explosion) {
            part = rnd.nextBoolean() ? BodyPart.TORSO : (rnd.nextBoolean() ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM);
        } else {
            int r = rnd.nextInt(100);
            if (r < 8) part = BodyPart.HEAD;
            else if (r < 42) part = BodyPart.TORSO;
            else if (r < 57) part = BodyPart.LEFT_ARM;
            else if (r < 72) part = BodyPart.RIGHT_ARM;
            else if (r < 86) part = BodyPart.LEFT_LEG;
            else part = BodyPart.RIGHT_LEG;
        }

        // armor on that body part can deflect the injury (the hit itself still hurts)
        ArmorProtection.Kind kind = explosion ? ArmorProtection.Kind.EXPLOSION
                : projectile ? ArmorProtection.Kind.PROJECTILE : ArmorProtection.Kind.GENERIC;
        double deflect = ArmorProtection.forPart(p, part, kind);
        if (deflect > 0 && rnd.nextDouble() < deflect) return;

        // wound type: scratch < laceration < deep wound
        WoundType type;
        if (explosion) type = WoundType.DEEP_WOUND;
        else if (skeletonArrow) type = rnd.nextFloat() < 0.85f ? WoundType.LACERATION : WoundType.DEEP_WOUND;
        else if (projectile) type = rnd.nextFloat() < 0.65f ? WoundType.DEEP_WOUND : WoundType.LACERATION;
        else type = rnd.nextFloat() < 0.40f ? WoundType.LACERATION : WoundType.SCRATCH;
        if (amount >= 8f && type == WoundType.SCRATCH) type = WoundType.LACERATION;

        InjuryManager.addWound(p, part, type);
    }

    /** Long falls can break legs: 10% per leg at 7 blocks, up to 90% per leg at 20 blocks. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!TrueHealingConfig.ENABLED.get() || !TrueHealingConfig.FRACTURES_ENABLED.get()) return;
        if (!(e.getEntity() instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return;
        double minF = TrueHealingConfig.FRACTURE_MIN_FALL.get(), maxF = TrueHealingConfig.FRACTURE_MAX_FALL.get();
        double dist = e.getDistance() * e.getDamageMultiplier();
        if (dist < minF) return;
        double t = Mth.clamp((dist - minF) / Math.max(0.01, maxF - minF), 0.0, 1.0);
        double lo = TrueHealingConfig.FRACTURE_MIN_CHANCE.get(), hi = TrueHealingConfig.FRACTURE_MAX_CHANCE.get();
        double chance = lo + t * (hi - lo);
        RandomSource r = p.getRandom();
        if (r.nextDouble() < chance) InjuryManager.addFracture(p, BodyPart.LEFT_LEG);
        if (r.nextDouble() < chance) InjuryManager.addFracture(p, BodyPart.RIGHT_LEG);
    }

    /** Bleeding beats natural regeneration (small heals only, so healing potions still work). */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getAmount() <= 1.0f && InjuryManager.isBleeding(p)) {
            e.setCanceled(true);
        }
    }

    /** Project Zomboid rule: in major pain (stage 3) or agony (stage 4) you can't sleep. */
    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent e) {
        if (!TrueHealingConfig.ENABLED.get() || !TrueHealingConfig.BLOCK_SLEEP_IN_PAIN.get()) return;
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (p.getMaxHealth() > 0 && p.getHealth() / p.getMaxHealth() < 0.55f) {
            e.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            p.displayClientMessage(Component.literal("You are in too much pain to sleep."), true);
        }
    }

    // ---------------- panic (for the panic moodle) ----------------

    /** 0 = calm. Panic starts when MORE than 3 hostile mobs are hunting the player. */
    private static int panicLevel(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator() || !p.isAlive()) return 0;
        AABB box = p.getBoundingBox().inflate(24.0);
        int n = p.level().getEntitiesOfClass(Mob.class, box,
                m -> m instanceof Enemy && m.isAlive() && m.getTarget() == p).size();
        if (n <= 3) return 0;
        if (n <= 5) return 1;
        if (n <= 8) return 2;
        if (n <= 12) return 3;
        return 4;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer p)) return;
        if (!TrueHealingConfig.ENABLED.get()) return;
        InjuryManager.tickAction(p);
        if (p.tickCount % 20 == 0) {
            InjuryManager.tick(p);
            int level = panicLevel(p);
            Integer old = PANIC.put(p.getUUID(), level);
            if (old == null || old != level) TrueHealingNetwork.sendPanic(p, level);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) InjuryManager.sync(p);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer viewer) {
            InjuryData d = InjuryManager.peek(target);
            if (d != null && !d.isEmpty()) {
                TrueHealingNetwork.sendVisualTo(viewer, target.getId(), Visuals.encode(d));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            InjuryManager.unload(p);
            PANIC.remove(p.getUUID());
            HAD_ABSORPTION.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) InjuryManager.clear(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            InjuryManager.clear(p);
            PANIC.put(p.getUUID(), 0);
            TrueHealingNetwork.sendPanic(p, 0);
        }
    }

    // ---------------- commands: /truehealing ----------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommand(String name) {
        return Commands.literal(name)
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("wound")
                        .then(Commands.argument("part", StringArgumentType.word())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            try {
                                                BodyPart part = BodyPart.valueOf(StringArgumentType.getString(ctx, "part").toUpperCase());
                                                WoundType type = WoundType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase());
                                                InjuryManager.addWound(p, part, type);
                                                return 1;
                                            } catch (IllegalArgumentException ex) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "Parts: head, torso, left_arm, right_arm, left_leg, right_leg. "
                                                                + "Types: scratch, laceration, deep_wound, fracture"));
                                                return 0;
                                            }
                                        }))))
                .then(Commands.literal("infect").executes(ctx -> {
                    InjuryManager.infectAll(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    InjuryManager.clear(ctx.getSource().getPlayerOrException());
                    return 1;
                }));
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(buildCommand("truehealing"));
    }
}
