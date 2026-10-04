package com.baroo.truehealing;

public final class Visuals {
    private Visuals() {}

    /**
     * Four bytes per body part (in BodyPart order):
     *  [0] dressing: 0 none, 1 rag clean, 2 rag dirty, 3 bandage clean, 4 bandage dirty
     *  [1] number of open (undressed, unstitched) wounds, 0..3
     *  [2] 1 if a splint is worn
     *  [3] number of bandaids, 0..3
     */
    public static byte[] encode(InjuryData d) {
        byte[] out = new byte[BodyPart.values().length * 4];
        for (BodyPart bp : BodyPart.values()) {
            int rag = 0, band = 0, open = 0, splint = 0, aid = 0; // rag/band: 0 none, 1 dirty, 2 clean
            for (Wound w : d.get(bp)) {
                if (w.type == WoundType.FRACTURE) {
                    if (w.splinted) splint = 1;
                    continue;
                }
                int level = w.isDressingDirty() ? 1 : 2;
                if (w.dressing == DressingType.RAG) rag = Math.max(rag, level);
                else if (w.dressing == DressingType.BANDAGE) band = Math.max(band, level);
                else if (w.dressing == DressingType.BANDAID) aid++;
                else if (w.dressing == DressingType.NONE && !w.stitched) open++;
            }
            int dress = band > 0 ? (band == 2 ? 3 : 4) : (rag > 0 ? (rag == 2 ? 1 : 2) : 0);
            int o = bp.ordinal() * 4;
            out[o] = (byte) dress;
            out[o + 1] = (byte) Math.min(3, open);
            out[o + 2] = (byte) splint;
            out[o + 3] = (byte) Math.min(3, aid);
        }
        return out;
    }
}
