package net.dandare21.fracturedutils.screeneffect.packet;

import io.netty.buffer.Unpooled;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectInstance;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectRegistry;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectType;
import net.dandare21.fracturedutils.screeneffect.client.ClientScreenEffectHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CPlayScreenEffectPacket {
    private final ResourceLocation typeId;
    private final byte[] payload;

    public S2CPlayScreenEffectPacket(ScreenEffectInstance instance) {
        this.typeId = instance.getType().getId();
        FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());
        instance.toNetwork(tempBuf);
        this.payload = new byte[tempBuf.readableBytes()];
        tempBuf.readBytes(this.payload);
        tempBuf.release();
    }

    public S2CPlayScreenEffectPacket(FriendlyByteBuf buf) {
        this.typeId = buf.readResourceLocation();
        int len = buf.readVarInt();
        this.payload = new byte[len];
        buf.readBytes(this.payload);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.typeId);
        buf.writeVarInt(this.payload.length);
        buf.writeBytes(this.payload);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ScreenEffectType<?> type = ScreenEffectRegistry.get(typeId);
            if (type != null) {
                FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
                try {
                    ScreenEffectInstance instance = type.fromNetwork(tempBuf);
                    ClientScreenEffectHandler.playEffect(instance);
                } catch (Exception e) {
                    FracturedUtils.LOGGER.error("[S2CPlayScreenEffectPacket] Failed to decode screen effect: {}", typeId, e);
                } finally {
                    tempBuf.release();
                }
            } else {
                FracturedUtils.LOGGER.warn("[S2CPlayScreenEffectPacket] Unknown screen effect type received: {}", typeId);
            }
        }));
        ctx.setPacketHandled(true);
    }
}
