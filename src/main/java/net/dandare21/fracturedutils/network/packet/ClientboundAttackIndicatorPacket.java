package net.dandare21.fracturedutils.network.packet;

import net.dandare21.fracturedutils.puppet.client.ClientAttackIndicatorManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C Packet for broadcasting ground attack indicators (such as circular attack decals) to clients.
 */
public class ClientboundAttackIndicatorPacket {
    private final int id;
    private final double x;
    private final double y;
    private final double z;
    private final float radius;
    private final int durationTicks;
    private final int color;
    private final boolean remove;

    public ClientboundAttackIndicatorPacket(int id, double x, double y, double z, float radius, int durationTicks, int color, boolean remove) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.durationTicks = durationTicks;
        this.color = color;
        this.remove = remove;
    }

    public ClientboundAttackIndicatorPacket(FriendlyByteBuf buf) {
        this.id = buf.readVarInt();
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.radius = buf.readFloat();
        this.durationTicks = buf.readVarInt();
        this.color = buf.readInt();
        this.remove = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.radius);
        buf.writeVarInt(this.durationTicks);
        buf.writeInt(this.color);
        buf.writeBoolean(this.remove);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    ClientAttackIndicatorManager.handlePacket(this.id, this.x, this.y, this.z, this.radius, this.durationTicks, this.color, this.remove)
            );
        });
        ctx.setPacketHandled(true);
    }

    public int getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getRadius() { return radius; }
    public int getDurationTicks() { return durationTicks; }
    public int getColor() { return color; }
    public boolean isRemove() { return remove; }
}
