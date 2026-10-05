package net.dandare21.fracturedutils.sound.sequence;

import net.dandare21.fracturedutils.dialog.DialogLine;

public class MusicSequenceEntry {
    private long timestampMs;
    private String actionType;
    private String channelId;
    private String command;
    private String description;

    private int windupMs;
    private int jumpMs;
    private int durationMs;
    private int recoveryMs;
    private String subAction;
    private DialogLine dialog;

    // Camera Settings
    private boolean useCamera;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private float cameraYaw;
    private float cameraPitch;
    private float cameraRoll;
    private double cameraFov = 70.0;
    private String cameraMode = "STATIC"; // STATIC, FOLLOW, OVER_THE_SHOULDER, CLEAR
    private String cameraTarget = "@p";
    private double cameraHeightOffset = 5.5;
    private double cameraBackDistance = 2.4;
    private double cameraShoulderOffset = 0.55;
    private boolean cameraInterpolate = true;

    // Advanced Spline & Easing Settings
    private String splineMode = "CATMULL_ROM"; // CATMULL_ROM, BEZIER, LINEAR, STEP
    private String cameraEasing = "EASE_IN_OUT_CUBIC";
    private double inHandleX = 0.0;
    private double inHandleY = 0.0;
    private double inHandleZ = 0.0;
    private double outHandleX = 0.0;
    private double outHandleY = 0.0;
    private double outHandleZ = 0.0;

    // Decoupled Look-At Target (Inverse Kinematics)
    private boolean lookAtEnabled = false;
    private String lookAtMode = "COORDINATE"; // COORDINATE, ENTITY
    private double lookAtX = 0.0;
    private double lookAtY = 0.0;
    private double lookAtZ = 0.0;
    private String lookAtTarget = "@p";
    private double lookAtWeight = 1.0;

    // Screen Effect Settings
    private String screenEffectId = "fractured_utils:screen_shake";
    private float screenEffectIntensity = 2.0f;
    private float screenEffectFrequency = 20.0f;
    private boolean screenEffectDecay = true;
    private int screenEffectColor = 0xFFFFFFFF;
    private boolean screenEffectSmooth = false;
    private float screenEffectMaxAlpha = 0.85f;
    private boolean screenEffectPulse = false;
    private int screenEffectSecondaryColor = 0xFF000000;
    private boolean screenEffectContinuous = true;
    private float screenEffectAngle = 0.0f;
    private String screenEffectStyle = "MONOCHROME_CUT";

    // Custom Puppet Action Parameters
    private java.util.Map<String, String> puppetParams = new java.util.LinkedHashMap<>();

    // Puppet Spawn Settings
    private float spawnYaw = 0.0f;
    private float spawnPitch = 0.0f;
    private boolean showNametag = false;

    public MusicSequenceEntry() {
        this.timestampMs = 0L;
        this.actionType = "COMMAND";
        this.channelId = "";
        this.command = "";
        this.description = "";
        this.windupMs = 0;
        this.jumpMs = 0;
        this.durationMs = 0;
        this.recoveryMs = 0;
        this.subAction = "";
        this.cameraFov = 70.0;
        this.cameraMode = "STATIC";
        this.cameraTarget = "@p";
        this.cameraHeightOffset = 5.5;
        this.cameraBackDistance = 2.4;
        this.cameraShoulderOffset = 0.55;
        this.cameraInterpolate = true;
    }

    public MusicSequenceEntry(long timestampMs, String actionType, String command, String description) {
        this(timestampMs, actionType, "", command, description);
    }

    public MusicSequenceEntry(long timestampMs, String actionType, String channelId, String command, String description) {
        this.timestampMs = Math.max(0L, timestampMs);
        this.actionType = actionType != null ? actionType : "COMMAND";
        this.channelId = channelId != null ? channelId : "";
        this.command = command != null ? command : "";
        this.description = description != null ? description : "";
        this.windupMs = 0;
        this.jumpMs = 0;
        this.durationMs = 0;
        this.recoveryMs = 0;
        this.subAction = "";
        this.cameraFov = 70.0;
        this.cameraMode = "STATIC";
        this.cameraTarget = "@p";
        this.cameraHeightOffset = 5.5;
        this.cameraBackDistance = 2.4;
        this.cameraShoulderOffset = 0.55;
        this.cameraInterpolate = true;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public void setTimestampMs(long timestampMs) {
        this.timestampMs = Math.max(0L, timestampMs);
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType != null ? actionType : "COMMAND";
    }

    public String getChannelId() {
        return channelId != null ? channelId : "";
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId != null ? channelId : "";
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command != null ? command : "";
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public int getWindupMs() {
        return windupMs;
    }

    public void setWindupMs(int windupMs) {
        this.windupMs = Math.max(0, windupMs);
    }

    public int getJumpMs() {
        return jumpMs;
    }

    public void setJumpMs(int jumpMs) {
        this.jumpMs = Math.max(0, jumpMs);
    }

    public int getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(int durationMs) {
        this.durationMs = Math.max(0, durationMs);
    }

    public int getRecoveryMs() {
        return recoveryMs;
    }

    public void setRecoveryMs(int recoveryMs) {
        this.recoveryMs = Math.max(0, recoveryMs);
    }

    public int getTotalDurationMs() {
        return windupMs + jumpMs + durationMs + recoveryMs;
    }

    public int getExecutionTimeOffsetMs() {
        return windupMs + jumpMs;
    }

    public int getPhaseDurationMs(int phaseIndex) {
        return switch (phaseIndex) {
            case 0 -> windupMs;
            case 1 -> jumpMs;
            case 2 -> durationMs;
            case 3 -> recoveryMs;
            default -> 0;
        };
    }

    public String getSubAction() {
        return subAction != null ? subAction : "";
    }

    public void setSubAction(String subAction) {
        this.subAction = subAction != null ? subAction : "";
    }

    public DialogLine getDialog() {
        return dialog;
    }

    public void setDialog(DialogLine dialog) {
        this.dialog = dialog;
    }

    public DialogLine getOrCreateDialog() {
        if (this.dialog == null) {
            this.dialog = new DialogLine();
            this.dialog.setWaitForInput(false);
            this.dialog.setUseCamera(false);
        }
        return this.dialog;
    }

    public boolean isUseCamera() {
        return useCamera;
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

    public float getCameraRoll() {
        return cameraRoll;
    }

    public void setCameraRoll(float cameraRoll) {
        this.cameraRoll = cameraRoll;
    }

    public double getCameraFov() {
        return cameraFov > 0.0 ? cameraFov : 70.0;
    }

    public void setCameraFov(double cameraFov) {
        this.cameraFov = cameraFov > 0.0 ? cameraFov : 70.0;
    }

    public String getCameraMode() {
        return (cameraMode != null && !cameraMode.isBlank()) ? cameraMode : "STATIC";
    }

    public void setCameraMode(String cameraMode) {
        this.cameraMode = (cameraMode != null && !cameraMode.isBlank()) ? cameraMode : "STATIC";
    }

    public String getCameraTarget() {
        return cameraTarget != null ? cameraTarget : "@p";
    }

    public void setCameraTarget(String cameraTarget) {
        this.cameraTarget = cameraTarget != null ? cameraTarget : "@p";
    }

    public double getCameraHeightOffset() {
        return cameraHeightOffset;
    }

    public void setCameraHeightOffset(double cameraHeightOffset) {
        this.cameraHeightOffset = cameraHeightOffset;
    }

    public double getCameraBackDistance() {
        return cameraBackDistance;
    }

    public void setCameraBackDistance(double cameraBackDistance) {
        this.cameraBackDistance = cameraBackDistance;
    }

    public double getCameraShoulderOffset() {
        return cameraShoulderOffset;
    }

    public void setCameraShoulderOffset(double cameraShoulderOffset) {
        this.cameraShoulderOffset = cameraShoulderOffset;
    }

    public boolean isCameraInterpolate() {
        return cameraInterpolate;
    }

    public void setCameraInterpolate(boolean cameraInterpolate) {
        this.cameraInterpolate = cameraInterpolate;
    }

    public String getSplineMode() {
        return (splineMode != null && !splineMode.isBlank()) ? splineMode : "CATMULL_ROM";
    }

    public void setSplineMode(String splineMode) {
        this.splineMode = (splineMode != null && !splineMode.isBlank()) ? splineMode : "CATMULL_ROM";
    }

    public String getCameraEasing() {
        return (cameraEasing != null && !cameraEasing.isBlank()) ? cameraEasing : "EASE_IN_OUT_CUBIC";
    }

    public void setCameraEasing(String cameraEasing) {
        this.cameraEasing = (cameraEasing != null && !cameraEasing.isBlank()) ? cameraEasing : "EASE_IN_OUT_CUBIC";
    }

    public double getInHandleX() {
        return inHandleX;
    }

    public void setInHandleX(double inHandleX) {
        this.inHandleX = inHandleX;
    }

    public double getInHandleY() {
        return inHandleY;
    }

    public void setInHandleY(double inHandleY) {
        this.inHandleY = inHandleY;
    }

    public double getInHandleZ() {
        return inHandleZ;
    }

    public void setInHandleZ(double inHandleZ) {
        this.inHandleZ = inHandleZ;
    }

    public double getOutHandleX() {
        return outHandleX;
    }

    public void setOutHandleX(double outHandleX) {
        this.outHandleX = outHandleX;
    }

    public double getOutHandleY() {
        return outHandleY;
    }

    public void setOutHandleY(double outHandleY) {
        this.outHandleY = outHandleY;
    }

    public double getOutHandleZ() {
        return outHandleZ;
    }

    public void setOutHandleZ(double outHandleZ) {
        this.outHandleZ = outHandleZ;
    }

    public boolean isLookAtEnabled() {
        return lookAtEnabled;
    }

    public void setLookAtEnabled(boolean lookAtEnabled) {
        this.lookAtEnabled = lookAtEnabled;
    }

    public String getLookAtMode() {
        return (lookAtMode != null && !lookAtMode.isBlank()) ? lookAtMode : "COORDINATE";
    }

    public void setLookAtMode(String lookAtMode) {
        this.lookAtMode = (lookAtMode != null && !lookAtMode.isBlank()) ? lookAtMode : "COORDINATE";
    }

    public double getLookAtX() {
        return lookAtX;
    }

    public void setLookAtX(double lookAtX) {
        this.lookAtX = lookAtX;
    }

    public double getLookAtY() {
        return lookAtY;
    }

    public void setLookAtY(double lookAtY) {
        this.lookAtY = lookAtY;
    }

    public double getLookAtZ() {
        return lookAtZ;
    }

    public void setLookAtZ(double lookAtZ) {
        this.lookAtZ = lookAtZ;
    }

    public String getLookAtTarget() {
        return (lookAtTarget != null && !lookAtTarget.isBlank()) ? lookAtTarget : "@p";
    }

    public void setLookAtTarget(String lookAtTarget) {
        this.lookAtTarget = (lookAtTarget != null && !lookAtTarget.isBlank()) ? lookAtTarget : "@p";
    }

    public double getLookAtWeight() {
        return lookAtWeight;
    }

    public void setLookAtWeight(double lookAtWeight) {
        this.lookAtWeight = Math.max(0.0, Math.min(1.0, lookAtWeight));
    }

    public String getScreenEffectId() {
        return (screenEffectId != null && !screenEffectId.isBlank()) ? screenEffectId : "fractured_utils:screen_shake";
    }

    public void setScreenEffectId(String screenEffectId) {
        this.screenEffectId = (screenEffectId != null && !screenEffectId.isBlank()) ? screenEffectId : "fractured_utils:screen_shake";
    }

    public float getScreenEffectIntensity() {
        return screenEffectIntensity;
    }

    public void setScreenEffectIntensity(float screenEffectIntensity) {
        this.screenEffectIntensity = screenEffectIntensity;
    }

    public float getScreenEffectFrequency() {
        return screenEffectFrequency;
    }

    public void setScreenEffectFrequency(float screenEffectFrequency) {
        this.screenEffectFrequency = screenEffectFrequency;
    }

    public boolean isScreenEffectDecay() {
        return screenEffectDecay;
    }

    public void setScreenEffectDecay(boolean screenEffectDecay) {
        this.screenEffectDecay = screenEffectDecay;
    }

    public int getScreenEffectColor() {
        return screenEffectColor;
    }

    public void setScreenEffectColor(int screenEffectColor) {
        this.screenEffectColor = screenEffectColor;
    }

    public boolean isScreenEffectSmooth() {
        return screenEffectSmooth;
    }

    public void setScreenEffectSmooth(boolean screenEffectSmooth) {
        this.screenEffectSmooth = screenEffectSmooth;
    }

    public float getScreenEffectMaxAlpha() {
        return screenEffectMaxAlpha;
    }

    public void setScreenEffectMaxAlpha(float screenEffectMaxAlpha) {
        this.screenEffectMaxAlpha = screenEffectMaxAlpha;
    }

    public boolean isScreenEffectPulse() {
        return screenEffectPulse;
    }

    public void setScreenEffectPulse(boolean screenEffectPulse) {
        this.screenEffectPulse = screenEffectPulse;
    }

    public int getScreenEffectSecondaryColor() {
        return screenEffectSecondaryColor;
    }

    public void setScreenEffectSecondaryColor(int screenEffectSecondaryColor) {
        this.screenEffectSecondaryColor = screenEffectSecondaryColor;
    }

    public boolean isScreenEffectContinuous() {
        return screenEffectContinuous;
    }

    public void setScreenEffectContinuous(boolean screenEffectContinuous) {
        this.screenEffectContinuous = screenEffectContinuous;
    }

    public float getScreenEffectAngle() {
        return screenEffectAngle;
    }

    public void setScreenEffectAngle(float screenEffectAngle) {
        this.screenEffectAngle = screenEffectAngle;
    }

    public String getScreenEffectStyle() {
        return (screenEffectStyle != null && !screenEffectStyle.isBlank()) ? screenEffectStyle : "MONOCHROME_CUT";
    }

    public void setScreenEffectStyle(String screenEffectStyle) {
        this.screenEffectStyle = (screenEffectStyle != null && !screenEffectStyle.isBlank()) ? screenEffectStyle : "MONOCHROME_CUT";
    }

    public java.util.Map<String, String> getPuppetParams() {
        if (this.puppetParams == null) {
            this.puppetParams = new java.util.LinkedHashMap<>();
        }
        return this.puppetParams;
    }

    public void setPuppetParams(java.util.Map<String, String> puppetParams) {
        this.puppetParams = puppetParams != null ? new java.util.LinkedHashMap<>(puppetParams) : new java.util.LinkedHashMap<>();
    }

    public String getPuppetParam(String key, String fallback) {
        if (this.puppetParams == null || key == null) return fallback;
        return this.puppetParams.getOrDefault(key, fallback);
    }

    public void putPuppetParam(String key, String value) {
        if (this.puppetParams == null) {
            this.puppetParams = new java.util.LinkedHashMap<>();
        }
        if (key != null && !key.isBlank()) {
            if (value != null) {
                this.puppetParams.put(key, value);
            } else {
                this.puppetParams.remove(key);
            }
        }
    }

    public MusicSequenceEntry copy() {
        MusicSequenceEntry entry = new MusicSequenceEntry(this.timestampMs, this.actionType, this.channelId, this.command, this.description);
        entry.setWindupMs(this.windupMs);
        entry.setJumpMs(this.jumpMs);
        entry.setDurationMs(this.durationMs);
        entry.setRecoveryMs(this.recoveryMs);
        entry.setSubAction(this.subAction);
        if (this.dialog != null) {
            entry.setDialog(this.dialog.copy());
        }
        entry.setUseCamera(this.useCamera);
        entry.setCameraX(this.cameraX);
        entry.setCameraY(this.cameraY);
        entry.setCameraZ(this.cameraZ);
        entry.setCameraYaw(this.cameraYaw);
        entry.setCameraPitch(this.cameraPitch);
        entry.setCameraRoll(this.cameraRoll);
        entry.setCameraFov(this.cameraFov);
        entry.setCameraMode(this.cameraMode);
        entry.setCameraTarget(this.cameraTarget);
        entry.setCameraHeightOffset(this.cameraHeightOffset);
        entry.setCameraBackDistance(this.cameraBackDistance);
        entry.setCameraShoulderOffset(this.cameraShoulderOffset);
        entry.setCameraInterpolate(this.cameraInterpolate);
        entry.setSplineMode(this.splineMode);
        entry.setCameraEasing(this.cameraEasing);
        entry.setInHandleX(this.inHandleX);
        entry.setInHandleY(this.inHandleY);
        entry.setInHandleZ(this.inHandleZ);
        entry.setOutHandleX(this.outHandleX);
        entry.setOutHandleY(this.outHandleY);
        entry.setOutHandleZ(this.outHandleZ);
        entry.setLookAtEnabled(this.lookAtEnabled);
        entry.setLookAtMode(this.lookAtMode);
        entry.setLookAtX(this.lookAtX);
        entry.setLookAtY(this.lookAtY);
        entry.setLookAtZ(this.lookAtZ);
        entry.setLookAtTarget(this.lookAtTarget);
        entry.setLookAtWeight(this.lookAtWeight);
        entry.setScreenEffectId(this.screenEffectId);
        entry.setScreenEffectIntensity(this.screenEffectIntensity);
        entry.setScreenEffectFrequency(this.screenEffectFrequency);
        entry.setScreenEffectDecay(this.screenEffectDecay);
        entry.setScreenEffectColor(this.screenEffectColor);
        entry.setScreenEffectSmooth(this.screenEffectSmooth);
        entry.setScreenEffectMaxAlpha(this.screenEffectMaxAlpha);
        entry.setScreenEffectPulse(this.screenEffectPulse);
        entry.setScreenEffectSecondaryColor(this.screenEffectSecondaryColor);
        entry.setScreenEffectContinuous(this.screenEffectContinuous);
        entry.setScreenEffectAngle(this.screenEffectAngle);
        entry.setScreenEffectStyle(this.screenEffectStyle);
        if (this.puppetParams != null) {
            entry.setPuppetParams(new java.util.LinkedHashMap<>(this.puppetParams));
        }
        entry.setSpawnYaw(this.spawnYaw);
        entry.setSpawnPitch(this.spawnPitch);
        entry.setShowNametag(this.showNametag);
        return entry;
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public void setSpawnYaw(float spawnYaw) {
        this.spawnYaw = spawnYaw;
    }

    public float getSpawnPitch() {
        return spawnPitch;
    }

    public void setSpawnPitch(float spawnPitch) {
        this.spawnPitch = spawnPitch;
    }

    public boolean isShowNametag() {
        return showNametag;
    }

    public void setShowNametag(boolean showNametag) {
        this.showNametag = showNametag;
    }
}
