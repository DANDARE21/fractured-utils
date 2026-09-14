package net.dandare21.fracturedutils.screeneffect.packet;

import net.dandare21.fracturedutils.screeneffect.client.ClientScreenEffectHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class S2CStopScreenEffectPacket {
    @Nullable
    private final ResourceLocation typeId;

    public S2CStopScreenEffectPacket() {
        this.typeId = null;
    }

    public S2CStopScreenEffectPacket(@Nullable ResourceLocation typeId) {
        this.typeId = typeId;
    }

    public S2CStopScreenEffectPacket(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            this.typeId = buf.readResourceLocation();
        } else {
            this.typeId = null;
        }
    }

    public void encode(FriendlyByteBuf buf) {
        if (this.typeId != null) {
            buf.writeBoolean(true);
            buf.writeResourceLocation(this.typeId);
        } else {
            buf.writeBoolean(false);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (typeId != null) {
                ClientScreenEffectHandler.stopEffect(typeId);
            } else {
                ClientScreenEffectHandler.clearAllEffects();
            }
        }));
        ctx.setPacketHandled(true);
    }
}
