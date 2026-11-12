package net.dandare21.fracturedutils.dialog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.dandare21.fracturedutils.dialog.camera.CameraTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class DialogLine {
    private String speaker;
    private String text;
    private int delayTicks;
    private String sound;
    private float volume;
    private float pitch;

    // Undertale-like typewriter settings
    private int charSpeedTicks;
    private String letterSound;
    private float letterSoundPitchMin;
    private float letterSoundPitchMax;
    private boolean waitForInput;

    // Custom camera settings
    private boolean useCamera;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private float cameraYaw;
    private float cameraPitch;
    private double cameraFov;

    // Dynamic camera target & ergonomics
    private CameraTarget cameraTarget;
    private int interpolationTicks;
    private String easing;
    private ResourceLocation styleId;
    private String speakerSelector;

    private record ContentData(
            String speaker, String text, int delayTicks, String sound, float volume, float pitch,
            int charSpeedTicks, String letterSound, float letterSoundPitchMin, float letterSoundPitchMax,
            boolean waitForInput, Optional<ResourceLocation> style
    ) {}

    private record CameraData(
            boolean useCamera, double cameraX, double cameraY, double cameraZ,
            float cameraYaw, float cameraPitch, double cameraFov,
            Optional<CameraTarget> cameraTarget, int interpolationTicks, String easing,
            Optional<String> speakerSelector
    ) {}

    private static final MapCodec<ContentData> CONTENT_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("speaker", "").forGetter(ContentData::speaker),
            Codec.STRING.optionalFieldOf("text", "").forGetter(ContentData::text),
            Codec.INT.optionalFieldOf("delayTicks", 40).forGetter(ContentData::delayTicks),
            Codec.STRING.optionalFieldOf("sound", "").forGetter(ContentData::sound),
            Codec.FLOAT.optionalFieldOf("volume", 1.0f).forGetter(ContentData::volume),
            Codec.FLOAT.optionalFieldOf("pitch", 1.0f).forGetter(ContentData::pitch),
            Codec.INT.optionalFieldOf("charSpeedTicks", 1).forGetter(ContentData::charSpeedTicks),
            Codec.STRING.optionalFieldOf("letterSound", "").forGetter(ContentData::letterSound),
            Codec.FLOAT.optionalFieldOf("letterSoundPitchMin", 0.8f).forGetter(ContentData::letterSoundPitchMin),
            Codec.FLOAT.optionalFieldOf("letterSoundPitchMax", 1.2f).forGetter(ContentData::letterSoundPitchMax),
            Codec.BOOL.optionalFieldOf("waitForInput", true).forGetter(ContentData::waitForInput),
            ResourceLocation.CODEC.optionalFieldOf("style").forGetter(ContentData::style)
    ).apply(instance, ContentData::new));

    private static final MapCodec<CameraData> CAMERA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("useCamera", false).forGetter(CameraData::useCamera),
            Codec.DOUBLE.optionalFieldOf("cameraX", 0.0).forGetter(CameraData::cameraX),
            Codec.DOUBLE.optionalFieldOf("cameraY", 0.0).forGetter(CameraData::cameraY),
            Codec.DOUBLE.optionalFieldOf("cameraZ", 0.0).forGetter(CameraData::cameraZ),
            Codec.FLOAT.optionalFieldOf("cameraYaw", 0.0f).forGetter(CameraData::cameraYaw),
            Codec.FLOAT.optionalFieldOf("cameraPitch", 0.0f).forGetter(CameraData::cameraPitch),
            Codec.DOUBLE.optionalFieldOf("cameraFov", 70.0).forGetter(CameraData::cameraFov),
            CameraTarget.CODEC.optionalFieldOf("cameraTarget").forGetter(CameraData::cameraTarget),
            Codec.INT.optionalFieldOf("interpolationTicks", 20).forGetter(CameraData::interpolationTicks),
            Codec.STRING.optionalFieldOf("easing", "EASE_IN_OUT_CUBIC").forGetter(CameraData::easing),
            Codec.STRING.optionalFieldOf("speakerSelector").forGetter(CameraData::speakerSelector)
    ).apply(instance, CameraData::new));

    public static final Codec<DialogLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CONTENT_CODEC.forGetter(DialogLine::toContentData),
            CAMERA_CODEC.forGetter(DialogLine::toCameraData)
    ).apply(instance, DialogLine::fromData));

    private ContentData toContentData() {
        return new ContentData(
                this.speaker, this.text, this.delayTicks, this.sound, this.volume, this.pitch,
                this.charSpeedTicks, this.letterSound, this.letterSoundPitchMin, this.letterSoundPitchMax,
                this.waitForInput, getStyleIdOptional()
        );
    }

    private CameraData toCameraData() {
        return new CameraData(
                this.useCamera, this.cameraX, this.cameraY, this.cameraZ,
                this.cameraYaw, this.cameraPitch, this.cameraFov,
                getCameraTargetOptional(), this.interpolationTicks, this.easing,
                getSpeakerSelectorOptional()
        );
    }

    private static DialogLine fromData(ContentData content, CameraData camera) {
        DialogLine line = new DialogLine(
                content.speaker(), content.text(), content.delayTicks(), content.sound(), content.volume(), content.pitch(),
                content.charSpeedTicks(), content.letterSound(), content.letterSoundPitchMin(), content.letterSoundPitchMax(),
                content.waitForInput(), camera.useCamera(), camera.cameraX(), camera.cameraY(), camera.cameraZ(),
                camera.cameraYaw(), camera.cameraPitch(), camera.cameraFov()
        );
        camera.cameraTarget().ifPresent(target -> {
            line.setCameraTarget(target);
            line.setUseCamera(true);
        });
        line.setInterpolationTicks(camera.interpolationTicks());
        line.setEasing(camera.easing() != null ? camera.easing() : "EASE_IN_OUT_CUBIC");
        content.style().ifPresent(line::setStyleId);
        camera.speakerSelector().ifPresent(line::setSpeakerSelector);

        // If speakerSelector is specified without cameraTarget, auto-generate selector camera target
        if (line.getCameraTarget() == null && line.getSpeakerSelector() != null
                && !line.getSpeakerSelector().isBlank()) {
            line.setCameraTarget(CameraTarget.fromSelector(line.getSpeakerSelector(), CameraTarget.AnchorPoint.EYES,
                    new Vec3(0, 0, 2.0)));
            line.setUseCamera(true);
        }

        return line;
    }

    public DialogLine() {
        this.speaker = "";
        this.text = "";
        this.delayTicks = 40;
        this.sound = "";
        this.volume = 1.0f;
        this.pitch = 1.0f;
        this.charSpeedTicks = 1;
        this.letterSound = "";
        this.letterSoundPitchMin = 0.8f;
        this.letterSoundPitchMax = 1.2f;
        this.waitForInput = true;
        this.useCamera = false;
        this.cameraX = 0.0;
        this.cameraY = 0.0;
        this.cameraZ = 0.0;
        this.cameraYaw = 0.0f;
        this.cameraPitch = 0.0f;
        this.cameraFov = 70.0;
        this.cameraTarget = null;
        this.interpolationTicks = 20;
        this.easing = "EASE_IN_OUT_CUBIC";
        this.styleId = null;
        this.speakerSelector = null;
    }

    public DialogLine(String speaker, String text, int delayTicks, String sound, float volume, float pitch,
            int charSpeedTicks, String letterSound, float letterSoundPitchMin, float letterSoundPitchMax,
            boolean waitForInput, boolean useCamera, double cameraX, double cameraY, double cameraZ, float cameraYaw,
            float cameraPitch, double cameraFov) {
        this();
        this.speaker = speaker != null ? speaker : "";
        this.text = text != null ? text : "";
        this.delayTicks = Math.max(1, delayTicks);
        this.sound = sound != null ? sound : "";
        this.volume = volume > 0 ? volume : 1.0f;
        this.pitch = pitch > 0 ? pitch : 1.0f;
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
    }

    public String getSpeaker() {
        return speaker;
    }

    public void setSpeaker(String speaker) {
        this.speaker = speaker != null ? speaker : "";
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public int getDelayTicks() {
        return delayTicks;
    }

    public void setDelayTicks(int delayTicks) {
        this.delayTicks = Math.max(1, delayTicks);
    }

    public String getSound() {
        return sound;
    }

    public void setSound(String sound) {
        this.sound = sound != null ? sound : "";
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public int getCharSpeedTicks() {
        return charSpeedTicks;
    }

    public void setCharSpeedTicks(int charSpeedTicks) {
        this.charSpeedTicks = Math.max(0, charSpeedTicks);
    }

    public String getLetterSound() {
        return letterSound;
    }

    public void setLetterSound(String letterSound) {
        this.letterSound = letterSound != null ? letterSound : "";
    }

    public float getLetterSoundPitchMin() {
        return letterSoundPitchMin;
    }

    public void setLetterSoundPitchMin(float letterSoundPitchMin) {
        this.letterSoundPitchMin = letterSoundPitchMin;
    }

    public float getLetterSoundPitchMax() {
        return letterSoundPitchMax;
    }

    public void setLetterSoundPitchMax(float letterSoundPitchMax) {
        this.letterSoundPitchMax = letterSoundPitchMax;
    }

    public boolean isWaitForInput() {
        return waitForInput;
    }

    public void setWaitForInput(boolean waitForInput) {
        this.waitForInput = waitForInput;
    }

    public boolean isUseCamera() {
        return useCamera || cameraTarget != null;
    }

    public void setUseCamera(boolean useCamera) {
        this.useCamera = useCamera;
    }

    public double getCameraX() {
        return cameraX;
    }

    public void setCameraX(double cameraX) {
        this.cameraX = cameraX;
    }

    public double getCameraY() {
        return cameraY;
    }

    public void setCameraY(double cameraY) {
        this.cameraY = cameraY;
    }

    public double getCameraZ() {
        return cameraZ;
    }

    public void setCameraZ(double cameraZ) {
        this.cameraZ = cameraZ;
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public void setCameraYaw(float cameraYaw) {
        this.cameraYaw = cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }

    public void setCameraPitch(float cameraPitch) {
        this.cameraPitch = cameraPitch;
    }

    public double getCameraFov() {
        return cameraFov > 0 ? cameraFov : 70.0;
    }

    public void setCameraFov(double cameraFov) {
        this.cameraFov = Math.max(10.0, Math.min(140.0, cameraFov));
    }

    public CameraTarget getCameraTarget() {
        return cameraTarget;
    }

    public Optional<CameraTarget> getCameraTargetOptional() {
        return Optional.ofNullable(cameraTarget);
    }

    public void setCameraTarget(CameraTarget cameraTarget) {
        this.cameraTarget = cameraTarget;
        if (cameraTarget != null) {
            this.useCamera = true;
        }
    }

    public int getInterpolationTicks() {
        return interpolationTicks;
    }

    public void setInterpolationTicks(int interpolationTicks) {
        this.interpolationTicks = Math.max(0, interpolationTicks);
    }

    public String getEasing() {
        return easing != null ? easing : "EASE_IN_OUT_CUBIC";
    }

    public void setEasing(String easing) {
        this.easing = easing != null ? easing : "EASE_IN_OUT_CUBIC";
    }

    public ResourceLocation getStyleId() {
        return styleId;
    }

    public Optional<ResourceLocation> getStyleIdOptional() {
        return Optional.ofNullable(styleId);
    }

    public void setStyleId(ResourceLocation styleId) {
        this.styleId = styleId;
    }

    public String getSpeakerSelector() {
        return speakerSelector;
    }

    public Optional<String> getSpeakerSelectorOptional() {
        return Optional.ofNullable(speakerSelector);
    }

    public void setSpeakerSelector(String speakerSelector) {
        this.speakerSelector = speakerSelector;
    }

    public DialogLine copy() {
        DialogLine copy = new DialogLine(this.speaker, this.text, this.delayTicks, this.sound, this.volume, this.pitch,
                this.charSpeedTicks, this.letterSound, this.letterSoundPitchMin, this.letterSoundPitchMax,
                this.waitForInput, this.useCamera, this.cameraX, this.cameraY, this.cameraZ, this.cameraYaw,
                this.cameraPitch, this.cameraFov);
        copy.setCameraTarget(this.cameraTarget);
        copy.setInterpolationTicks(this.interpolationTicks);
        copy.setEasing(this.easing);
        copy.setStyleId(this.styleId);
        copy.setSpeakerSelector(this.speakerSelector);
        return copy;
    }
}
