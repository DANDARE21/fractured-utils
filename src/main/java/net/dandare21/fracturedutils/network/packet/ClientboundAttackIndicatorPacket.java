package net.dandare21.fracturedutils.network.packet;

import net.dandare21.fracturedutils.puppet.client.ClientAttackIndicatorManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C Packet for broadcasting ground attack indicators (such as circular or linear decals) to clients.
 */
public class ClientboundAttackIndicatorPacket {

    public enum IndicatorShape {
        CIRCLE,
        LINE
    }

    private final IndicatorShape shape;
    private final int id;
    private final double x;
    private final double y;
    private final double z;
    private final double endX;
    private final double endY;
    private final double endZ;
    private final float widthOrRadius;
    private final int durationTicks;
    private final int color;
    private final boolean remove;

    /**
     * Backwards-compatible constructor for circular attack indicators.
     */
    public ClientboundAttackIndicatorPacket(int id, double x, double y, double z, float radius, int durationTicks, int color, boolean remove) {
        this(IndicatorShape.CIRCLE, id, x, y, z, x, y, z, radius, durationTicks, color, remove);
    }

    /**
     * Full constructor supporting both circular and linear attack indicators.
     */
    public ClientboundAttackIndicatorPacket(IndicatorShape shape, int id, double x, double y, double z,
                                           double endX, double endY, double endZ,
                                           float widthOrRadius, int durationTicks, int color, boolean remove) {
        this.shape = shape != null ? shape : IndicatorShape.CIRCLE;
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;
        this.widthOrRadius = widthOrRadius;
        this.durationTicks = durationTicks;
        this.color = color;
        this.remove = remove;
    }

    public ClientboundAttackIndicatorPacket(FriendlyByteBuf buf) {
        this.shape = buf.readEnum(IndicatorShape.class);
        this.id = buf.readVarInt();
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.endX = buf.readDouble();
        this.endY = buf.readDouble();
        this.endZ = buf.readDouble();
        this.widthOrRadius = buf.readFloat();
        this.durationTicks = buf.readVarInt();
        this.color = buf.readInt();
        this.remove = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(this.shape);
        buf.writeVarInt(this.id);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeDouble(this.endX);
        buf.writeDouble(this.endY);
        buf.writeDouble(this.endZ);
        buf.writeFloat(this.widthOrRadius);
        buf.writeVarInt(this.durationTicks);
        buf.writeInt(this.color);
        buf.writeBoolean(this.remove);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    ClientAttackIndicatorManager.handlePacket(
                            this.shape, this.id, this.x, this.y, this.z,
                            this.endX, this.endY, this.endZ,
                            this.widthOrRadius, this.durationTicks, this.color, this.remove
                    )
            );
        });
        ctx.setPacketHandled(true);
    }

    public IndicatorShape getShape() { return shape; }
    public int getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getEndX() { return endX; }
    public double getEndY() { return endY; }
    public double getEndZ() { return endZ; }
    public float getRadius() { return widthOrRadius; }
    public float getWidth() { return widthOrRadius; }
    public float getWidthOrRadius() { return widthOrRadius; }
    public int getDurationTicks() { return durationTicks; }
    public int getColor() { return color; }
    public boolean isRemove() { return remove; }
}
