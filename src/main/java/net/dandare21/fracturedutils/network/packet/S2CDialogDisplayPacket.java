package net.dandare21.fracturedutils.network.packet;

import net.dandare21.fracturedutils.client.ClientDialogHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CDialogDisplayPacket {
    private final String speaker;
    private final String text;
    private final int delayTicks;
    private final int charSpeedTicks;
    private final String letterSound;
    private final float letterSoundPitchMin;
    private final float letterSoundPitchMax;
    private final boolean waitForInput;

    // Custom camera & dynamic tracking
    private final boolean useCamera;
    private final double cameraX;
    private final double cameraY;
    private final double cameraZ;
    private final float cameraYaw;
    private final float cameraPitch;
    private final double cameraFov;
    private final int targetEntityId;
    private final String anchor;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final boolean lookAtTarget;
    private final int interpolationTicks;
    private final String easing;
    private final String styleId;

    public S2CDialogDisplayPacket(
            String speaker, String text, int delayTicks, int charSpeedTicks,
            String letterSound, float letterSoundPitchMin, float letterSoundPitchMax,
            boolean waitForInput, boolean useCamera, double cameraX, double cameraY, double cameraZ,
            float cameraYaw, float cameraPitch, double cameraFov, int targetEntityId,
            String anchor, double offsetX, double offsetY, double offsetZ, boolean lookAtTarget,
            int interpolationTicks, String easing, String styleId
    ) {
        this.speaker = speaker != null ? speaker : "";
        this.text = text != null ? text : "";
        this.delayTicks = Math.max(1, delayTicks);
        this.charSpeedTicks = Math.max(0, charSpeedTicks);
        this.letterSound = letterSound != null ? letterSound : "";
        this.letterSoundPitchMin = letterSoundPitchMin > 0 ? letterSoundPitchMin : 0.8f;
        this.letterSoundPitchMax = letterSoundPitchMax > 0 ? letterSoundPitchMax : 1.2f;
        this.waitForInput = waitForInput;
        this.useCamera = useCamera;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.cameraYaw = cameraYaw;
        this.cameraPitch = cameraPitch;
        this.cameraFov = cameraFov > 0 ? cameraFov : 70.0;
        this.targetEntityId = targetEntityId;
        this.anchor = anchor != null ? anchor : "eyes";
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.lookAtTarget = lookAtTarget;
        this.interpolationTicks = Math.max(0, interpolationTicks);
        this.easing = easing != null ? easing : "EASE_IN_OUT_CUBIC";
        this.styleId = styleId != null ? styleId : "fractured_utils:cyberpunk";
    }

    public S2CDialogDisplayPacket(
            String speaker, String text, int delayTicks, int charSpeedTicks,
            String letterSound, float letterSoundPitchMin, float letterSoundPitchMax,
            boolean waitForInput, boolean useCamera, double cameraX, double cameraY, double cameraZ,
            float cameraYaw, float cameraPitch, double cameraFov
    ) {
        this(speaker, text, delayTicks, charSpeedTicks, letterSound, letterSoundPitchMin, letterSoundPitchMax,
                waitForInput, useCamera, cameraX, cameraY, cameraZ, cameraYaw, cameraPitch, cameraFov,
                -1, "eyes", 0.0, 0.0, 0.0, true, 20, "EASE_IN_OUT_CUBIC", "fractured_utils:cyberpunk");
    }

    public S2CDialogDisplayPacket(FriendlyByteBuf buf) {
        this.speaker = buf.readUtf(32767);
        this.text = buf.readUtf(32767);
        this.delayTicks = buf.readVarInt();
        this.charSpeedTicks = buf.readVarInt();
        this.letterSound = buf.readUtf(32767);
        this.letterSoundPitchMin = buf.readFloat();
        this.letterSoundPitchMax = buf.readFloat();
        this.waitForInput = buf.readBoolean();
        this.useCamera = buf.readBoolean();
        this.cameraX = buf.readDouble();
        this.cameraY = buf.readDouble();
        this.cameraZ = buf.readDouble();
        this.cameraYaw = buf.readFloat();
        this.cameraPitch = buf.readFloat();
        this.cameraFov = buf.readDouble();
        this.targetEntityId = buf.readVarInt();
        this.anchor = buf.readUtf(64);
        this.offsetX = buf.readDouble();
        this.offsetY = buf.readDouble();
        this.offsetZ = buf.readDouble();
        this.lookAtTarget = buf.readBoolean();
        this.interpolationTicks = buf.readVarInt();
        this.easing = buf.readUtf(64);
        this.styleId = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(speaker, 32767);
        buf.writeUtf(text, 32767);
        buf.writeVarInt(delayTicks);
        buf.writeVarInt(charSpeedTicks);
        buf.writeUtf(letterSound, 32767);
        buf.writeFloat(letterSoundPitchMin);
        buf.writeFloat(letterSoundPitchMax);
        buf.writeBoolean(waitForInput);
        buf.writeBoolean(useCamera);
        buf.writeDouble(cameraX);
        buf.writeDouble(cameraY);
        buf.writeDouble(cameraZ);
        buf.writeFloat(cameraYaw);
        buf.writeFloat(cameraPitch);
        buf.writeDouble(cameraFov);
        buf.writeVarInt(targetEntityId);
        buf.writeUtf(anchor, 64);
        buf.writeDouble(offsetX);
        buf.writeDouble(offsetY);
        buf.writeDouble(offsetZ);
        buf.writeBoolean(lookAtTarget);
        buf.writeVarInt(interpolationTicks);
        buf.writeUtf(easing, 64);
        buf.writeUtf(styleId, 256);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientDialogHandler.displayDialog(
                    speaker, text, delayTicks, charSpeedTicks, letterSound, letterSoundPitchMin, letterSoundPitchMax,
                    waitForInput, useCamera, cameraX, cameraY, cameraZ, cameraYaw, cameraPitch, cameraFov,
                    targetEntityId, anchor, offsetX, offsetY, offsetZ, lookAtTarget, interpolationTicks, easing, styleId
            ));
        });
        ctx.setPacketHandled(true);
    }

    public String getSpeaker() { return speaker; }
    public String getText() { return text; }
    public int getDelayTicks() { return delayTicks; }
    public int getCharSpeedTicks() { return charSpeedTicks; }
    public String getLetterSound() { return letterSound; }
    public float getLetterSoundPitchMin() { return letterSoundPitchMin; }
    public float getLetterSoundPitchMax() { return letterSoundPitchMax; }
    public boolean isWaitForInput() { return waitForInput; }
    public boolean isUseCamera() { return useCamera; }
    public double getCameraX() { return cameraX; }
    public double getCameraY() { return cameraY; }
    public double getCameraZ() { return cameraZ; }
    public float getCameraYaw() { return cameraYaw; }
    public float getCameraPitch() { return cameraPitch; }
    public double getCameraFov() { return cameraFov; }
    public int getTargetEntityId() { return targetEntityId; }
    public String getAnchor() { return anchor; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }
    public boolean isLookAtTarget() { return lookAtTarget; }
    public int getInterpolationTicks() { return interpolationTicks; }
    public String getEasing() { return easing; }
    public String getStyleId() { return styleId; }
}
