package com.baroo.truehealing;

import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class TrueHealingNetwork {
    private static final String PROTOCOL = "4";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TrueHealing.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private TrueHealingNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncPacket.class, SyncPacket::encode, SyncPacket::decode, SyncPacket::handle);
        CHANNEL.registerMessage(id++, TreatPacket.class, TreatPacket::encode, TreatPacket::decode, TreatPacket::handle);
        CHANNEL.registerMessage(id++, VisualPacket.class, VisualPacket::encode, VisualPacket::decode, VisualPacket::handle);
        CHANNEL.registerMessage(id++, MessagePacket.class, MessagePacket::encode, MessagePacket::decode, MessagePacket::handle);
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
        CHANNEL.registerMessage(id++, PanicPacket.class, PanicPacket::encode, PanicPacket::decode, PanicPacket::handle);
    }

    public static void sendSync(ServerPlayer player, CompoundTag tag) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncPacket(tag));
    }

    /** Visual state of `target`, sent to everyone tracking it (and itself). */
    public static void sendVisual(ServerPlayer target, byte[] data) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new VisualPacket(target.getId(), data));
    }

    /** Visual state of `entityId`, sent to one viewer. */
    public static void sendVisualTo(ServerPlayer viewer, int entityId, byte[] data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new VisualPacket(entityId, data));
    }

    public static void sendPanic(ServerPlayer player, int level) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PanicPacket(level));
    }

    /** Tells the client an action started (ticks > 0) or ended (ticks == 0). */
    public static void sendAction(ServerPlayer player, int ticks, String label) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ActionPacket(ticks, label));
    }

    public static void sendMessage(ServerPlayer player, String text) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MessagePacket(text));
    }

    /** Server -> client: the player's full injury data. */
    public static class SyncPacket {
        private final CompoundTag tag;

        public SyncPacket(CompoundTag tag) { this.tag = tag; }
        public static void encode(SyncPacket m, FriendlyByteBuf buf) { buf.writeNbt(m.tag); }
        public static SyncPacket decode(FriendlyByteBuf buf) { return new SyncPacket(buf.readNbt()); }

        public static void handle(SyncPacket m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientData.set(m.tag));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: apply a treatment to a body part. */
    public static class TreatPacket {
        private final int part;
        private final int action;

        public TreatPacket(int part, int action) { this.part = part; this.action = action; }

        public static void encode(TreatPacket m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.part);
            buf.writeVarInt(m.action);
        }

        public static TreatPacket decode(FriendlyByteBuf buf) {
            return new TreatPacket(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(TreatPacket m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    if (m.action < 0 || m.action >= TreatAction.values().length) {
                        InjuryManager.cancelAction(player, null); // client closed the screen
                    } else {
                        InjuryManager.requestAction(player, BodyPart.byIndex(m.part), TreatAction.byIndex(m.action));
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: what to draw on a player model (bandages, open wounds). */
    public static class VisualPacket {
        private final int entityId;
        private final byte[] data;

        public VisualPacket(int entityId, byte[] data) { this.entityId = entityId; this.data = data; }

        public static void encode(VisualPacket m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
            buf.writeByteArray(m.data);
        }

        public static VisualPacket decode(FriendlyByteBuf buf) {
            return new VisualPacket(buf.readVarInt(), buf.readByteArray());
        }

        public static void handle(VisualPacket m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientVisuals.set(m.entityId, m.data));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: how many hostile mobs are hunting the player (0..4). */
    public static class PanicPacket {
        private final int level;

        public PanicPacket(int level) { this.level = level; }
        public static void encode(PanicPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.level); }
        public static PanicPacket decode(FriendlyByteBuf buf) { return new PanicPacket(buf.readVarInt()); }

        public static void handle(PanicPacket m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientData.panicLevel = m.level);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: progress bar state for a timed treatment. */
    public static class ActionPacket {
        private final int ticks;
        private final String label;

        public ActionPacket(int ticks, String label) { this.ticks = ticks; this.label = label; }

        public static void encode(ActionPacket m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.ticks);
            buf.writeUtf(m.label);
        }

        public static ActionPacket decode(FriendlyByteBuf buf) {
            return new ActionPacket(buf.readVarInt(), buf.readUtf());
        }

        public static void handle(ActionPacket m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientData.setAction(m.label, m.ticks));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: feedback text shown inside the medical screen. */
    public static class MessagePacket {
        private final String text;

        public MessagePacket(String text) { this.text = text; }
        public static void encode(MessagePacket m, FriendlyByteBuf buf) { buf.writeUtf(m.text); }
        public static MessagePacket decode(FriendlyByteBuf buf) { return new MessagePacket(buf.readUtf()); }

        public static void handle(MessagePacket m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientData.setMessage(m.text));
            ctx.get().setPacketHandled(true);
        }
    }
}
