package net.dandare21.fracturedutils.network.packet;

import net.dandare21.fracturedutils.client.camera.ClientCameraHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CCameraOverridePacket {
    private final boolean active;
    private final String mode;
    private final double cameraX;
    private final double cameraY;
    private final double cameraZ;
    private final float cameraYaw;
    private final float cameraPitch;
    private final float cameraRoll;
    private final double cameraFov;
    private final int durationMs;
    private final boolean interpolate;
    private final int targetEntityId;
    private final double heightOffset;
    private final double backDistance;
    private final double shoulderOffset;

    public S2CCameraOverridePacket(boolean active, String mode, double cameraX, double cameraY, double cameraZ,
                                   float cameraYaw, float cameraPitch, float cameraRoll, double cameraFov,
                                   int durationMs, boolean interpolate, int targetEntityId,
                                   double heightOffset, double backDistance, double shoulderOffset) {
        this.active = active;
        this.mode = mode != null ? mode : "STATIC";
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.cameraYaw = cameraYaw;
        this.cameraPitch = cameraPitch;
        this.cameraRoll = cameraRoll;
        this.cameraFov = cameraFov > 0.0 ? cameraFov : 70.0;
        this.durationMs = durationMs;
        this.interpolate = interpolate;
        this.targetEntityId = targetEntityId;
        this.heightOffset = heightOffset;
        this.backDistance = backDistance;
        this.shoulderOffset = shoulderOffset;
    }

    public S2CCameraOverridePacket(FriendlyByteBuf buf) {
        this.active = buf.readBoolean();
        this.mode = buf.readUtf(64);
        this.cameraX = buf.readDouble();
        this.cameraY = buf.readDouble();
        this.cameraZ = buf.readDouble();
        this.cameraYaw = buf.readFloat();
        this.cameraPitch = buf.readFloat();
        this.cameraRoll = buf.readFloat();
        this.cameraFov = buf.readDouble();
        this.durationMs = buf.readVarInt();
        this.interpolate = buf.readBoolean();
        this.targetEntityId = buf.readVarInt();
        this.heightOffset = buf.readDouble();
        this.backDistance = buf.readDouble();
        this.shoulderOffset = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.active);
        buf.writeUtf(this.mode, 64);
        buf.writeDouble(this.cameraX);
        buf.writeDouble(this.cameraY);
        buf.writeDouble(this.cameraZ);
        buf.writeFloat(this.cameraYaw);
        buf.writeFloat(this.cameraPitch);
        buf.writeFloat(this.cameraRoll);
        buf.writeDouble(this.cameraFov);
        buf.writeVarInt(this.durationMs);
        buf.writeBoolean(this.interpolate);
        buf.writeVarInt(this.targetEntityId);
        buf.writeDouble(this.heightOffset);
        buf.writeDouble(this.backDistance);
        buf.writeDouble(this.shoulderOffset);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCameraHandler.handleCameraOverride(
                active, mode, cameraX, cameraY, cameraZ, cameraYaw, cameraPitch, cameraRoll, cameraFov,
                durationMs, interpolate, targetEntityId, heightOffset, backDistance, shoulderOffset
        )));
        ctx.setPacketHandled(true);
    }

    public boolean isActive() { return active; }
    public String getMode() { return mode; }
    public double getCameraX() { return cameraX; }
    public double getCameraY() { return cameraY; }
    public double getCameraZ() { return cameraZ; }
    public float getCameraYaw() { return cameraYaw; }
    public float getCameraPitch() { return cameraPitch; }
    public float getCameraRoll() { return cameraRoll; }
    public double getCameraFov() { return cameraFov; }
    public int getDurationMs() { return durationMs; }
    public boolean isInterpolate() { return interpolate; }
    public int getTargetEntityId() { return targetEntityId; }
    public double getHeightOffset() { return heightOffset; }
    public double getBackDistance() { return backDistance; }
    public double getShoulderOffset() { return shoulderOffset; }
}
