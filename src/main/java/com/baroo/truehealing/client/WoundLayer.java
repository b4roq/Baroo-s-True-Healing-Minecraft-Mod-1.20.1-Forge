package com.baroo.truehealing.client;

import com.baroo.truehealing.BodyPart;
import com.baroo.truehealing.ClientVisuals;
import com.baroo.truehealing.TrueHealing;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws bandage wraps, wound marks and splints on player models. */
public class WoundLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEX = new ResourceLocation(TrueHealing.MODID, "textures/entity/solid.png");

    // shaded palettes: bandage = scattered whites, rag = beige, dirty = brownish
    private static final int[][] RAG = {{176, 160, 128}, {160, 144, 112}, {190, 174, 142}, {148, 132, 102}};
    private static final int[][] DIRTY_RAG = {{128, 100, 60}, {112, 86, 50}, {140, 112, 70}, {98, 74, 42}};
    private static final int[][] BANDAGE = {{255, 255, 255}, {238, 238, 240}, {222, 222, 226}, {206, 206, 212}, {246, 246, 248}};
    private static final int[][] DIRTY_BANDAGE = {{196, 182, 150}, {176, 160, 128}, {206, 194, 166}, {160, 144, 112}};

    private static final int[][] BLOOD = {{110, 8, 8}, {92, 6, 6}, {128, 12, 12}};
    // pixel clusters (dx, dy) for wound marks
    private static final int[][][] CLUSTERS = {
            {{0, 0}, {1, 0}, {2, 0}, {1, 1}, {2, 1}},
            {{0, 0}, {0, 1}, {1, 1}, {1, 2}, {1, 3}},
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}, {2, 2}}
    };
    private static final float[] FX = {0.30f, 0.70f, 0.45f};
    private static final float[] FY = {0.20f, 0.50f, 0.75f};

    private static final int[] WOOD = {122, 78, 40};
    private static final int[] WOOD_DARK = {88, 54, 28};
    private static final int[] WOOD_LIGHT = {146, 96, 52};
    private static final int[] WIRE = {112, 112, 118};

    private final boolean slim;

    public WoundLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
        super(parent);
        this.slim = slim; // Alex (slim) arms are 3px wide, so wraps and marks are fitted to them
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        byte[] v = ClientVisuals.get(player.getId());
        if (v == null) return;

        PlayerModel<AbstractClientPlayer> m = getParentModel();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        part(ps, vc, light, m.head, BodyPart.HEAD, v);
        part(ps, vc, light, m.body, BodyPart.TORSO, v);
        part(ps, vc, light, m.rightArm, BodyPart.RIGHT_ARM, v);
        part(ps, vc, light, m.leftArm, BodyPart.LEFT_ARM, v);
        part(ps, vc, light, m.rightLeg, BodyPart.RIGHT_LEG, v);
        part(ps, vc, light, m.leftLeg, BodyPart.LEFT_LEG, v);
    }

    /** x0,y0,z0,x1,y1,z1 of the cube in model pixels, relative to the part's pivot (arms use the 4px width). */
    private float[] bounds(BodyPart bp) {
        return switch (bp) {
            case HEAD -> new float[]{-4, -8, -4, 4, 0, 4};
            case TORSO -> new float[]{-4, 0, -2, 4, 12, 2};
            case RIGHT_ARM -> slim ? new float[]{-2, -2, -2, 1, 10, 2} : new float[]{-3, -2, -2, 1, 10, 2};
            case LEFT_ARM -> slim ? new float[]{-1, -2, -2, 2, 10, 2} : new float[]{-1, -2, -2, 3, 10, 2};
            case RIGHT_LEG, LEFT_LEG -> new float[]{-2, 0, -2, 2, 12, 2};
        };
    }

    private static float[] bandY(BodyPart bp) {
        return switch (bp) {
            case HEAD -> new float[]{-7.5f, -5.5f};   // 2px tall sweatband
            case TORSO -> new float[]{7.0f, 10.0f};   // 3px tall, lower torso
            case LEFT_ARM, RIGHT_ARM -> new float[]{-1.0f, 3.0f}; // 4px tall, 1px below the limb's top
            case LEFT_LEG, RIGHT_LEG -> new float[]{1.0f, 5.0f};  // same offset from the limb's top as arms
        };
    }

    private static int[][] palette(int dress) {
        return switch (dress) {
            case 1 -> RAG;
            case 2 -> DIRTY_RAG;
            case 3 -> BANDAGE;
            default -> DIRTY_BANDAGE;
        };
    }

    private void part(PoseStack ps, VertexConsumer vc, int light, ModelPart mp, BodyPart bp, byte[] v) {
        int o = bp.ordinal() * 4;
        int dress = v[o], open = v[o + 1], splint = v[o + 2], aids = v[o + 3];
        if (dress == 0 && open == 0 && splint == 0 && aids == 0) return;

        ps.pushPose();
        mp.translateAndRotate(ps);
        PoseStack.Pose pose = ps.last();
        float[] b = bounds(bp);

        if (dress != 0) band(pose, vc, light, bp, b, dress);
        for (int i = 0; i < open; i++) cluster(pose, vc, light, bp, b, i);
        for (int i = 0; i < aids; i++) aid(pose, vc, light, bp, b, i);
        if (splint != 0 && (bp == BodyPart.LEFT_LEG || bp == BodyPart.RIGHT_LEG)) splint(pose, vc, light, bp, b);
        ps.popPose();
    }

    // ---------------- bandage ----------------

    private static void band(PoseStack.Pose pose, VertexConsumer vc, int light, BodyPart bp, float[] b, int dress) {
        boolean leg = bp == BodyPart.LEFT_LEG || bp == BodyPart.RIGHT_LEG;
        float e = leg ? 0.4f : 0.6f;
        float[] y = bandY(bp);
        float x0 = b[0] - e, x1 = b[3] + e, z0 = b[2] - e, z1 = b[5] + e;
        int[][] pal = palette(dress);
        int seed = bp.ordinal() * 101 + dress * 7;
        cellsZ(pose, vc, light, pal, seed + 1, -1f, x0, x1, y[0], y[1], z0);
        cellsZ(pose, vc, light, pal, seed + 2, 1f, x0, x1, y[0], y[1], z1);
        cellsX(pose, vc, light, pal, seed + 3, -1f, z0, z1, y[0], y[1], x0);
        cellsX(pose, vc, light, pal, seed + 4, 1f, z0, z1, y[0], y[1], x1);
    }

    private static int hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /** Plane at fixed z, 1px cells over x/y, each cell a random shade from the palette. */
    private static void cellsZ(PoseStack.Pose pose, VertexConsumer vc, int light, int[][] pal, int seed, float nz,
                               float xa, float xb, float ya, float yb, float z) {
        int nx = Math.max(1, Math.round(xb - xa)), ny = Math.max(1, Math.round(yb - ya));
        float cw = (xb - xa) / nx, ch = (yb - ya) / ny;
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                float x0 = xa + i * cw, x1 = x0 + cw, y0 = ya + j * ch, y1 = y0 + ch;
                int[] c = pal[hash(seed, i, j) % pal.length];
                quad(pose, vc, light, c, 0, 0, nz, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z);
            }
        }
    }

    /** Plane at fixed x, 1px cells over z/y. */
    private static void cellsX(PoseStack.Pose pose, VertexConsumer vc, int light, int[][] pal, int seed, float nx,
                               float za, float zb, float ya, float yb, float x) {
        int nz = Math.max(1, Math.round(zb - za)), ny = Math.max(1, Math.round(yb - ya));
        float cw = (zb - za) / nz, ch = (yb - ya) / ny;
        for (int i = 0; i < nz; i++) {
            for (int j = 0; j < ny; j++) {
                float z0 = za + i * cw, z1 = z0 + cw, y0 = ya + j * ch, y1 = y0 + ch;
                int[] c = pal[hash(seed, i, j) % pal.length];
                quad(pose, vc, light, c, nx, 0, 0, x, y0, z0, x, y0, z1, x, y1, z1, x, y1, z0);
            }
        }
    }

    // ---------------- wound marks ----------------

    private static void cluster(PoseStack.Pose pose, VertexConsumer vc, int light, BodyPart bp, float[] b, int i) {
        int[][] t = CLUSTERS[(bp.ordinal() + i) % CLUSTERS.length];
        int tw = 0, th = 0;
        for (int[] c : t) { tw = Math.max(tw, c[0] + 1); th = Math.max(th, c[1] + 1); }
        float off = 0.3f;
        boolean right = bp == BodyPart.RIGHT_ARM || bp == BodyPart.RIGHT_LEG;
        int ay = (int) Math.floor(b[1] + FY[i] * Math.max(0f, (b[4] - b[1]) - th));

        for (int k = 0; k < t.length; k++) {
            int[] blood = BLOOD[hash(bp.ordinal(), i, k) % BLOOD.length];
            float v0 = ay + t[k][1], v1 = v0 + 1;
            if (i == 1) { // outer side
                int az = (int) Math.floor(b[2] + FX[i] * Math.max(0f, (b[5] - b[2]) - tw));
                float u0 = az + t[k][0], u1 = u0 + 1;
                float xs = right ? b[0] - off : b[3] + off;
                quad(pose, vc, light, blood, right ? -1 : 1, 0, 0, xs, v0, u0, xs, v0, u1, xs, v1, u1, xs, v1, u0);
            } else {
                int ax = (int) Math.floor(b[0] + FX[i] * Math.max(0f, (b[3] - b[0]) - tw));
                float u0 = ax + t[k][0], u1 = u0 + 1;
                if (i == 0) { // front
                    float z = b[2] - off;
                    quad(pose, vc, light, blood, 0, 0, -1, u0, v0, z, u1, v0, z, u1, v1, z, u0, v1, z);
                } else { // back
                    float z = b[5] + off;
                    quad(pose, vc, light, blood, 0, 0, 1, u1, v0, z, u0, v0, z, u0, v1, z, u1, v1, z);
                }
            }
        }
    }

    // ---------------- bandaid patch ----------------

    private static final int[] AID = {232, 190, 140};
    private static final int[] AID_PAD = {244, 238, 230};
    private static final float[] PFX = {0.55f, 0.20f, 0.65f};
    private static final float[] PFY = {0.40f, 0.65f, 0.25f};

    /** A small 3x2 pixel patch: tan ends with a lighter pad in the middle. */
    private static void aid(PoseStack.Pose pose, VertexConsumer vc, int light, BodyPart bp, float[] b, int i) {
        float off = 0.35f;
        boolean right = bp == BodyPart.RIGHT_ARM || bp == BodyPart.RIGHT_LEG;
        int ay = (int) Math.floor(b[1] + PFY[i] * Math.max(0f, (b[4] - b[1]) - 2));
        for (int u = 0; u < 3; u++) {
            for (int v = 0; v < 2; v++) {
                int[] c = (u == 1) ? AID_PAD : AID;
                float v0 = ay + v, v1 = v0 + 1;
                if (i == 1) { // outer side
                    int az = (int) Math.floor(b[2] + PFX[i] * Math.max(0f, (b[5] - b[2]) - 3));
                    float u0 = az + u, u1 = u0 + 1;
                    float xs = right ? b[0] - off : b[3] + off;
                    quad(pose, vc, light, c, right ? -1 : 1, 0, 0, xs, v0, u0, xs, v0, u1, xs, v1, u1, xs, v1, u0);
                } else {
                    int ax = (int) Math.floor(b[0] + PFX[i] * Math.max(0f, (b[3] - b[0]) - 3));
                    float u0 = ax + u, u1 = u0 + 1;
                    if (i == 0) { // front
                        float z = b[2] - off;
                        quad(pose, vc, light, c, 0, 0, -1, u0, v0, z, u1, v0, z, u1, v1, z, u0, v1, z);
                    } else { // back
                        float z = b[5] + off;
                        quad(pose, vc, light, c, 0, 0, 1, u1, v0, z, u0, v0, z, u0, v1, z, u1, v1, z);
                    }
                }
            }
        }
    }

    // ---------------- splint ----------------

    private static void splint(PoseStack.Pose pose, VertexConsumer vc, int light, BodyPart bp, float[] b) {
        boolean right = bp == BodyPart.RIGHT_LEG;
        float sgn = right ? -1f : 1f;
        float base = right ? b[0] : b[3];
        float xa = base + sgn * 0.35f, xb = base + sgn * 1.0f;
        float x0 = Math.min(xa, xb), x1 = Math.max(xa, xb);

        box(pose, vc, light, WOOD_DARK, x0, 1.5f, -1.5f, x1, 2.5f, 1.5f);
        box(pose, vc, light, WOOD, x0, 2.5f, -1.5f, x1, 9.5f, 1.5f);
        box(pose, vc, light, WOOD_DARK, x0, 9.5f, -1.5f, x1, 10.5f, 1.5f);
        float xo = base + sgn * 1.02f;
        quad(pose, vc, light, WOOD_LIGHT, sgn, 0, 0,
                xo, 3.5f, -0.5f, xo, 3.5f, 0.5f, xo, 8.5f, 0.5f, xo, 8.5f, -0.5f);

        // metal wires wrapping all the way around the leg
        float wx0 = right ? b[0] - 1.15f : b[0] - 0.45f;
        float wx1 = right ? b[3] + 0.45f : b[3] + 1.15f;
        float wz0 = b[2] - 0.45f, wz1 = b[5] + 0.45f;
        tube(pose, vc, light, WIRE, wx0, wx1, wz0, wz1, 3.0f, 4.0f);
        tube(pose, vc, light, WIRE, wx0, wx1, wz0, wz1, 8.0f, 9.0f);
    }

    private static void tube(PoseStack.Pose pose, VertexConsumer vc, int light, int[] c,
                             float x0, float x1, float z0, float z1, float y0, float y1) {
        quad(pose, vc, light, c, 0, 0, -1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(pose, vc, light, c, 0, 0, 1, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
        quad(pose, vc, light, c, -1, 0, 0, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
        quad(pose, vc, light, c, 1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer vc, int light, int[] c,
                            float x0, float y0, float z0, float x1, float y1, float z1) {
        tube(pose, vc, light, c, x0, x1, z0, z1, y0, y1);
        quad(pose, vc, light, c, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, vc, light, c, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
    }

    // ---------------- vertex helpers ----------------

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int light, int[] c,
                             float nx, float ny, float nz,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vert(pose, vc, light, c, nx, ny, nz, x1, y1, z1);
        vert(pose, vc, light, c, nx, ny, nz, x2, y2, z2);
        vert(pose, vc, light, c, nx, ny, nz, x3, y3, z3);
        vert(pose, vc, light, c, nx, ny, nz, x4, y4, z4);
    }

    private static void vert(PoseStack.Pose pose, VertexConsumer vc, int light, int[] c,
                             float nx, float ny, float nz, float x, float y, float z) {
        vc.vertex(pose.pose(), x / 16f, y / 16f, z / 16f)
                .color(c[0], c[1], c[2], 255)
                .uv(0.5f, 0.5f)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
    }
}
