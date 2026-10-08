package com.baroo.truehealing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class InjuryData {
    private final EnumMap<BodyPart, List<Wound>> parts = new EnumMap<>(BodyPart.class);
    /** Remaining ticks of antibiotic effect (slows all infections). */
    public int antibioticTicks;
    /** Lingering sickness (0..100) left over from healed infections; fades slowly. */
    public float sickness;

    public InjuryData() {
        for (BodyPart p : BodyPart.values()) parts.put(p, new ArrayList<>());
    }

    public List<Wound> get(BodyPart part) { return parts.get(part); }

    public boolean isEmpty() {
        for (List<Wound> l : parts.values()) if (!l.isEmpty()) return false;
        return true;
    }

    /** Walking speed lost to broken legs (0 = none). Splinted legs count half. */
    public double fractureSlow() {
        double units = 0;
        for (BodyPart leg : new BodyPart[]{BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG}) {
            for (Wound w : get(leg)) if (w.type == WoundType.FRACTURE) units += w.splinted ? 0.5 : 1.0;
        }
        if (units <= 0) return 0;
        double one = TrueHealingConfig.FRACTURE_SLOW_ONE.get(), both = TrueHealingConfig.FRACTURE_SLOW_BOTH.get();
        return units <= 1.0 ? one * units : one + (both - one) * (units - 1.0);
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        for (BodyPart p : BodyPart.values()) {
            ListTag list = new ListTag();
            for (Wound w : parts.get(p)) list.add(w.save());
            root.put(p.name(), list);
        }
        root.putInt("abx", antibioticTicks);
        root.putFloat("sick", sickness);
        return root;
    }

    public static InjuryData load(CompoundTag root) {
        InjuryData d = new InjuryData();
        if (root == null) return d;
        d.antibioticTicks = root.getInt("abx");
        d.sickness = root.getFloat("sick");
        for (BodyPart p : BodyPart.values()) {
            ListTag list = root.getList(p.name(), Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) d.parts.get(p).add(Wound.load(list.getCompound(i)));
        }
        return d;
    }
}
