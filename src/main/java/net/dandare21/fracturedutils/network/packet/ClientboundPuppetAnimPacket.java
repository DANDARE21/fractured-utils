package net.dandare21.fracturedutils.network.packet;

import net.dandare21.fracturedutils.puppet.client.ClientPuppetAnimHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C Packet that commands tracking clients to trigger a GeckoLib animation on a puppet entity.
 */
public class ClientboundPuppetAnimPacket {
    private final int entityId;
    private final String controllerName;
    private final String animName;

    public ClientboundPuppetAnimPacket(int entityId, String controllerName, String animName) {
        this.entityId = entityId;
        this.controllerName = controllerName != null ? controllerName : "";
        this.animName = animName != null ? animName : "";
    }

    public ClientboundPuppetAnimPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.controllerName = buf.readUtf(256);
        this.animName = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeUtf(this.controllerName, 256);
        buf.writeUtf(this.animName, 256);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    ClientPuppetAnimHandler.handleAnim(this.entityId, this.controllerName, this.animName)
            );
        });
        ctx.setPacketHandled(true);
    }

    public int getEntityId() {
        return entityId;
    }

    public String getControllerName() {
        return controllerName;
    }

    public String getAnimName() {
        return animName;
    }
}
