package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.dialog.DialogFormatUtil;
import net.dandare21.fracturedutils.dialog.DialogLine;
import net.dandare21.fracturedutils.orchestrator.action.CommandAction;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionType;
import net.dandare21.fracturedutils.puppet.registry.ModPuppetActions;
import net.dandare21.fracturedutils.sound.DialogSoundRegistry;
import net.dandare21.fracturedutils.sound.ModSounds;
import net.dandare21.fracturedutils.sound.sequence.MusicSequenceChannel;
import net.dandare21.fracturedutils.sound.sequence.MusicSequenceEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class EditMusicEntryModalScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BRIGHT = 0xFF33F0FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int PANEL_BG = 0xFA070E16;
    private static final int HEADER_BG = 0xEE091622;
    private static final int BORDER_CYAN = 0xFF00E5FF;
    private static final int BORDER_MUTED = 0xFF172D3D;
    private static final int TEXT_MUTED = 0xFF658A9F;
    private static final int TEXT_LABEL = 0xFFA0C2D4;
    private static final int RED_CANCEL = 0xFFFF3355;
    private static final int GREEN_VALID = 0xFF00FF88;
    private static final int CARD_BORDER = 0x5500E5FF;

    // Phase Colors for segmented duration
    private static final int COLOR_WINDUP = 0xFFFF9900;   // Amber / Orange
    private static final int COLOR_JUMP = 0xFF4A69BD;     // Indigo / Deep Sky Blue
    private static final int COLOR_ACTIVE = 0xFFAA55FF;   // Purple / Magenta
    private static final int COLOR_RECOVERY = 0xFF00E5FF; // Teal / Cyan

    public enum PuppetSubAction {
        SPAWN,
        DESPAWN,
        TOGGLE_AI,
        EXECUTE_ACTION
    }

    private final Screen parentScreen;
    private final MusicSequenceEntry entry;
    private final MusicSequenceChannel channel;
    private final Consumer<MusicSequenceEntry> onSave;
    private final Runnable onDelete;
    private final boolean isPuppet;
    private boolean isDialog;
    private final boolean isCamera;
    private final boolean isScreenEffect;

    // Screen Effect state & inputs
    private String activeScreenEffectMode = "SCREEN_SHAKE";
    private EditBox screenEffectDurationBox;
    private EditBox screenEffectIntensityBox;
    private EditBox screenEffectFrequencyBox;
    private boolean screenEffectDecay = true;
    private EditBox screenEffectColorBox;
    private boolean screenEffectSmooth = false;
    private boolean screenEffectPulse = false;
    private int screenEffectSecondaryColor = 0xFF000000;
    private boolean screenEffectContinuous = true;
    private float screenEffectAngle = 0.0f;
    private String screenEffectStyle = "MONOCHROME_CUT";
    private CyberpunkColorPicker screenEffectColorPicker;

    // Camera state & inputs
    private String activeCameraMode = "STATIC";
    private EditBox cameraPosXBox;
    private EditBox cameraPosYBox;
    private EditBox cameraPosZBox;
    private EditBox cameraYawBox;
    private EditBox cameraPitchBox;
    private EditBox cameraRollBox;
    private EditBox cameraFovBox;
    private EditBox cameraTargetBox;
    private EditBox cameraHeightOffsetBox;
    private EditBox cameraBackDistanceBox;
    private EditBox cameraShoulderOffsetBox;
    private EditBox cameraDurationBox;
    private boolean cameraInterpolate = true;

    // Dialog state & inputs
    private DialogLine dialogLine;
    private EditBox dialogSpeakerBox;
    private CyberpunkMultilineEditBox dialogTextBox;
    private EditBox dialogDelayBox;
    private EditBox dialogSpeedBox;
    private EditBox dialogSoundBox;
    private CyberpunkDropdown<String> dialogLetterSoundDropdown;
    private EditBox dialogLetterSoundBox;
    private EditBox dialogLetterPitchMinBox;
    private EditBox dialogLetterPitchMaxBox;
    private EditBox dialogTimestampBox;

    // Real-Time Typing Animation Test State
    private boolean isTypingTestActive = false;
    private int typingRevealedChars = 0;
    private int typingTotalChars = 0;
    private int typingTickTimer = 0;
    private long lastTypingTickTime = 0;

    // Active sub-action mode for puppet channels
    private PuppetSubAction activePuppetMode = PuppetSubAction.EXECUTE_ACTION;

    // Common Inputs
    private EditBox timestampBox;
    private EditBox descriptionBox;

    // Standard Channel Inputs
    private CyberpunkDropdown<String> typeDropdown;
    private EditBox commandBox;
    private CommandSuggestions commandSuggestions;
    private boolean isCommandInvalid = false;

    // Puppet Spawn Inputs
    private EditBox spawnXBox;
    private EditBox spawnYBox;
    private EditBox spawnZBox;
    private boolean spawnDisableAi = false;

    // Puppet Toggle AI Inputs
    private boolean isSuppressAi = true;
    private boolean suppressFullAi = true;
    private boolean suppressNav = true;
    private boolean suppressTgt = true;
    private boolean suppressLook = true;
    private boolean suppressActions = true;

    // Puppet Execute Action Inputs
    private CyberpunkDropdown<String> puppetActionDropdown;
    private EditBox customActionIdBox;
    private EditBox targetSelectorBox;
    private EditBox windupBox;
    private EditBox jumpBox;
    private EditBox durationBox;
    private EditBox recoveryBox;
    private String selectedActionId = "fractured_utils:leap_slam";

    // Internal timing values (ms)
    private int windupMs = 500;
    private int jumpMs = 1500;
    private int durationMs = 800;
    private int recoveryMs = 600;
    private String combatTarget = "@p";

    public EditMusicEntryModalScreen(Screen parentScreen, MusicSequenceEntry entry, Consumer<MusicSequenceEntry> onSave) {
        this(parentScreen, entry, null, onSave, null);
    }

    public EditMusicEntryModalScreen(Screen parentScreen, MusicSequenceEntry entry, MusicSequenceChannel channel,
                                     Consumer<MusicSequenceEntry> onSave, Runnable onDelete) {
        super(Component.literal("Edit Sequence Entry"));
        this.parentScreen = parentScreen;
        this.entry = entry != null ? entry.copy() : new MusicSequenceEntry();
        this.channel = channel;
        this.onSave = onSave;
        this.onDelete = onDelete;

        boolean channelIsPuppet = channel != null && channel.getType().equalsIgnoreCase(MusicSequenceChannel.TYPE_PUPPET);
        boolean entryIsPuppet = "PUPPET".equalsIgnoreCase(this.entry.getActionType());
        this.isPuppet = channelIsPuppet || entryIsPuppet;

        boolean channelIsDialog = channel != null && channel.getType().equalsIgnoreCase(MusicSequenceChannel.TYPE_DIALOG);
        boolean entryIsDialog = "DIALOG".equalsIgnoreCase(this.entry.getActionType());
        this.isDialog = !this.isPuppet && (channelIsDialog || entryIsDialog);

        boolean channelIsCamera = channel != null && channel.getType().equalsIgnoreCase(MusicSequenceChannel.TYPE_CAMERA);
        boolean entryIsCamera = "CAMERA".equalsIgnoreCase(this.entry.getActionType());
        this.isCamera = !this.isPuppet && !this.isDialog && (channelIsCamera || entryIsCamera);

        boolean channelIsScreenEffect = channel != null && (channel.getType().equalsIgnoreCase(MusicSequenceChannel.TYPE_SCREEN_EFFECT) || channel.getType().equalsIgnoreCase(MusicSequenceChannel.TYPE_OBJECTIVE));
        boolean entryIsScreenEffect = "SCREEN_EFFECT".equalsIgnoreCase(this.entry.getActionType()) || "SCREEN_EFFECTS".equalsIgnoreCase(this.entry.getActionType()) || "OBJECTIVE".equalsIgnoreCase(this.entry.getActionType());
        this.isScreenEffect = !this.isPuppet && !this.isDialog && !this.isCamera && (channelIsScreenEffect || entryIsScreenEffect);

        initEntryState();
    }

    private void initScreenEffectState() {
        String eff = entry.getScreenEffectId();
        if (eff != null && eff.contains("invert")) {
            this.activeScreenEffectMode = "INVERT_COLORS";
        } else if (eff != null && eff.contains("strobe")) {
            this.activeScreenEffectMode = "STROBE";
        } else if (eff != null && (eff.contains("hue") || eff.contains("rainbow"))) {
            this.activeScreenEffectMode = "HUE_SHIFT";
        } else if (eff != null && (eff.contains("impact") || eff.contains("flash"))) {
            this.activeScreenEffectMode = "IMPACT_FRAME";
        } else {
            this.activeScreenEffectMode = "SCREEN_SHAKE";
        }
        if (entry.getSubAction() != null && !entry.getSubAction().isBlank()) {
            String sub = entry.getSubAction().toUpperCase(Locale.ROOT);
            if (sub.equals("INVERT_COLORS") || sub.equals("STROBE") || sub.equals("SCREEN_SHAKE") || sub.equals("HUE_SHIFT") || sub.equals("IMPACT_FRAME")) {
                this.activeScreenEffectMode = sub;
            }
        }
        this.screenEffectDecay = entry.isScreenEffectDecay();
        this.screenEffectSmooth = entry.isScreenEffectSmooth();
        this.screenEffectPulse = entry.isScreenEffectPulse();
        this.screenEffectSecondaryColor = entry.getScreenEffectSecondaryColor();
        this.screenEffectContinuous = entry.isScreenEffectContinuous();
        this.screenEffectAngle = entry.getScreenEffectAngle();
        this.screenEffectStyle = entry.getScreenEffectStyle();
        if (entry.getDurationMs() <= 0 && entry.getTotalDurationMs() <= 0) {
            entry.setDurationMs(1000);
        }
    }

    private void initDialogState() {
        if (entry.getDialog() != null) {
            this.dialogLine = entry.getDialog().copy();
        } else {
            this.dialogLine = new DialogLine();
            if (entry.getCommand() != null && !entry.getCommand().isBlank() && !entry.getCommand().startsWith("/")) {
                this.dialogLine.setText(entry.getCommand());
            }
            if (entry.getDescription() != null && !entry.getDescription().isBlank()) {
                if (entry.getDescription().contains(": ")) {
                    String[] parts = entry.getDescription().split(": ", 2);
                    this.dialogLine.setSpeaker(parts[0]);
                    if (this.dialogLine.getText().isEmpty()) {
                        this.dialogLine.setText(parts[1]);
                    }
                } else {
                    this.dialogLine.setSpeaker(entry.getDescription());
                }
            }
        }
        this.dialogLine.setWaitForInput(false);
        this.dialogLine.setUseCamera(false);
    }

    private void initCameraState() {
        this.activeCameraMode = entry.getCameraMode();
        if (this.activeCameraMode == null || this.activeCameraMode.isBlank()) {
            this.activeCameraMode = "STATIC";
        }
        this.cameraInterpolate = entry.isCameraInterpolate();
        if (entry.getDurationMs() <= 0 && entry.getTotalDurationMs() <= 0) {
            entry.setDurationMs(3000);
        }
    }

    private void initEntryState() {
        if (isDialog) {
            initDialogState();
            return;
        }
        if (isCamera) {
            initCameraState();
            return;
        }
        if (isScreenEffect) {
            initScreenEffectState();
            return;
        }
        if (!isPuppet) return;

        String sub = entry.getSubAction();
        if ("SPAWN".equalsIgnoreCase(sub)) {
            activePuppetMode = PuppetSubAction.SPAWN;
        } else if ("DESPAWN".equalsIgnoreCase(sub)) {
            activePuppetMode = PuppetSubAction.DESPAWN;
        } else if ("TOGGLE_AI".equalsIgnoreCase(sub)) {
            activePuppetMode = PuppetSubAction.TOGGLE_AI;
            if (entry.getCommand().contains("puppet_restore")) {
                this.isSuppressAi = false;
            } else {
                this.isSuppressAi = true;
                this.suppressFullAi = entry.getCommand().contains("ai:true");
                this.suppressNav = entry.getCommand().contains("nav:true");
                this.suppressTgt = entry.getCommand().contains("tgt:true");
                this.suppressLook = entry.getCommand().contains("look:true");
                this.suppressActions = entry.getCommand().contains("actions:true");
            }
        } else if (sub != null && !sub.isBlank()) {
            activePuppetMode = PuppetSubAction.EXECUTE_ACTION;
        } else {
            // If newly registered actor or summon command, default to SPAWN
            boolean isSpawnCmd = entry.getCommand().startsWith("summon");
            if (isSpawnCmd || (channel != null && channel.isActorRegisteredOnly())) {
                activePuppetMode = PuppetSubAction.SPAWN;
            } else {
                activePuppetMode = PuppetSubAction.EXECUTE_ACTION;
            }
        }

        // Parse spawn
        if (entry.getCommand().contains("NoAI:1b")) {
            this.spawnDisableAi = true;
        }

        // Parse timings
        this.windupMs = entry.getWindupMs();
        this.jumpMs = entry.getJumpMs();
        this.durationMs = entry.getDurationMs();
        this.recoveryMs = entry.getRecoveryMs();

        // Parse action id, combat target, and embedded timings from command string
        String cmd = entry.getCommand().trim();
        if (cmd.startsWith("puppet_action")) {
            if (cmd.contains("action:")) {
                for (String part : cmd.split("\\s+")) {
                    if (part.startsWith("action:")) {
                        this.selectedActionId = part.substring(7).trim();
                    } else if (part.startsWith("target:")) {
                        this.combatTarget = part.substring(7).trim();
                    } else if (part.startsWith("windup:")) {
                        try { this.windupMs = Math.max(0, Integer.parseInt(part.substring(7).trim())); } catch (Exception ignored) {}
                    } else if (part.startsWith("jump:")) {
                        try { this.jumpMs = Math.max(0, Integer.parseInt(part.substring(5).trim())); } catch (Exception ignored) {}
                    } else if (part.startsWith("duration:")) {
                        try { this.durationMs = Math.max(0, Integer.parseInt(part.substring(9).trim())); } catch (Exception ignored) {}
                    } else if (part.startsWith("recovery:")) {
                        try { this.recoveryMs = Math.max(0, Integer.parseInt(part.substring(9).trim())); } catch (Exception ignored) {}
                    }
                }
            } else {
                String[] parts = cmd.split("\\s+");
                if (parts.length > 1) {
                    this.selectedActionId = parts[1].trim();
                }
                if (parts.length > 2) {
                    this.combatTarget = parts[2].trim();
                }
            }
        } else if (!cmd.isBlank() && !cmd.startsWith("summon") && !cmd.startsWith("kill") && !cmd.startsWith("puppet_")) {
            this.selectedActionId = cmd;
        }

        if (this.windupMs == 0 && this.jumpMs == 0 && this.durationMs == 0 && this.recoveryMs == 0) {
            this.windupMs = 500;
            this.jumpMs = 1500;
            this.durationMs = 800;
            this.recoveryMs = 600;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int getPanelWidth() {
        if (isDialog) return 520;
        if (isCamera) return 490;
        if (isScreenEffect) return 490;
        if (isPuppet) return 476;
        return 430;
    }

    private int getPanelHeight() {
        if (isDialog) return 450;
        if (isCamera) return 258;
        if (isScreenEffect) return 260;
        if (isPuppet) return 320;
        return 202;
    }

    private double getLayoutScale() {
        int targetW = getPanelWidth() + 20;
        int targetH = getPanelHeight() + 20;
        if (this.width <= 0 || this.height <= 0) return 1.0;
        double scaleX = (double) this.width / targetW;
        double scaleY = (double) this.height / targetH;
        return Math.min(1.0, Math.min(scaleX, scaleY));
    }

    @Override
    protected void init() {
        this.clearWidgets();

        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();

        int panelLeft = (effWidth - panelWidth) / 2;
        int panelTop = (effHeight - panelHeight) / 2;

        if (isDialog) {
            initDialogWidgets(panelLeft, panelTop, panelWidth, panelHeight);
        } else if (isCamera) {
            initCameraWidgets(panelLeft, panelTop, panelWidth, panelHeight);
        } else if (isScreenEffect) {
            initScreenEffectWidgets(panelLeft, panelTop, panelWidth, panelHeight);
        } else if (isPuppet) {
            initPuppetWidgets(panelLeft, panelTop, panelWidth, panelHeight);
        } else {
            initStandardWidgets(panelLeft, panelTop, panelWidth, panelHeight);
        }
    }

    private void switchScreenEffectMode(String mode) {
        this.activeScreenEffectMode = mode;
        this.init();
    }

    private void initScreenEffectWidgets(int panelLeft, int panelTop, int panelWidth, int panelHeight) {
        int contentX = panelLeft + 14;
        int contentW = panelWidth - 28;
        int currentY = panelTop + 30;

        // 1. Five Mode Tabs
        int tabW = (contentW - 12) / 5;
        CyberpunkButton shakeTab = new CyberpunkButton(contentX, currentY, tabW, 20, Component.literal("⚡ SHAKE"),
                b -> switchScreenEffectMode("SCREEN_SHAKE"),
                MusicSequenceChannel.COLOR_SCREEN_EFFECT, "SCREEN_SHAKE".equalsIgnoreCase(activeScreenEffectMode), Component.literal("Camera shake & rotational tremor"));

        CyberpunkButton invertTab = new CyberpunkButton(contentX + tabW + 3, currentY, tabW, 20, Component.literal("🔄 INVERT"),
                b -> switchScreenEffectMode("INVERT_COLORS"),
                0xFF00FFCC, "INVERT_COLORS".equalsIgnoreCase(activeScreenEffectMode), Component.literal("Invert screen colors (solid or pulse)"));

        CyberpunkButton strobeTab = new CyberpunkButton(contentX + (tabW + 3) * 2, currentY, tabW, 20, Component.literal("✨ STROBE"),
                b -> switchScreenEffectMode("STROBE"),
                0xFFFFD700, "STROBE".equalsIgnoreCase(activeScreenEffectMode), Component.literal("High-intensity flashing or smooth pulsating strobe"));

        CyberpunkButton hueTab = new CyberpunkButton(contentX + (tabW + 3) * 3, currentY, tabW, 20, Component.literal("🌈 HUE"),
                b -> switchScreenEffectMode("HUE_SHIFT"),
                0xFF33CCFF, "HUE_SHIFT".equalsIgnoreCase(activeScreenEffectMode), Component.literal("Dynamic color grading & continuous/static hue shift"));

        CyberpunkButton impactTab = new CyberpunkButton(contentX + (tabW + 3) * 4, currentY, tabW, 20, Component.literal("💥 IMPACT"),
                b -> switchScreenEffectMode("IMPACT_FRAME"),
                0xFFFF2255, "IMPACT_FRAME".equalsIgnoreCase(activeScreenEffectMode), Component.literal("Action cuts, shock frames & silhouettes"));

        this.addRenderableWidget(shakeTab);
        this.addRenderableWidget(invertTab);
        this.addRenderableWidget(strobeTab);
        this.addRenderableWidget(hueTab);
        this.addRenderableWidget(impactTab);

        // 2. Mode-Specific Content Area Card (Height = 68)
        int card1Y = panelTop + 54;
        if ("INVERT_COLORS".equalsIgnoreCase(activeScreenEffectMode)) {
            initInvertControls(contentX, card1Y, contentW);
        } else if ("STROBE".equalsIgnoreCase(activeScreenEffectMode)) {
            initStrobeControls(contentX, card1Y, contentW);
        } else if ("HUE_SHIFT".equalsIgnoreCase(activeScreenEffectMode)) {
            initHueShiftControls(contentX, card1Y, contentW);
        } else if ("IMPACT_FRAME".equalsIgnoreCase(activeScreenEffectMode)) {
            initImpactFrameControls(contentX, card1Y, contentW);
        } else {
            initShakeControls(contentX, card1Y, contentW);
        }

        // 3. Timing Section Card (Height = 32)
        int card2Y = card1Y + 74;
        this.timestampBox = new EditBox(this.font, contentX + 66, card2Y + 5, 64, 18, Component.literal("Timestamp (ms)"));
        this.timestampBox.setMaxLength(10);
        this.timestampBox.setValue(String.valueOf(entry.getTimestampMs()));
        this.addRenderableWidget(this.timestampBox);

        this.screenEffectDurationBox = new EditBox(this.font, contentX + 192, card2Y + 5, 56, 18, Component.literal("Duration (ms)"));
        this.screenEffectDurationBox.setMaxLength(10);
        this.screenEffectDurationBox.setValue(String.valueOf(entry.getDurationMs() > 0 ? entry.getDurationMs() : 1000));
        this.addRenderableWidget(this.screenEffectDurationBox);

        int qx = contentX + 260;
        int btnW = 32;
        this.addRenderableWidget(new CyberpunkButton(qx, card2Y + 5, btnW, 18, Component.literal("+1s"), b -> adjustTimestamp(1000)));
        this.addRenderableWidget(new CyberpunkButton(qx + btnW + 2, card2Y + 5, btnW, 18, Component.literal("+2s"), b -> adjustTimestamp(2000)));
        this.addRenderableWidget(new CyberpunkButton(qx + (btnW + 2) * 2, card2Y + 5, btnW, 18, Component.literal("+5s"), b -> adjustTimestamp(5000)));

        // 4. Description Box
        int descY = card2Y + 38;
        this.descriptionBox = new EditBox(this.font, contentX, descY + 12, contentW, 18, Component.literal("Description / Note"));
        this.descriptionBox.setMaxLength(128);
        this.descriptionBox.setValue(entry.getDescription());
        this.descriptionBox.setHint(Component.literal("Optional note or label for timeline"));
        this.addRenderableWidget(this.descriptionBox);

        // 5. Footer Buttons
        int footerY = panelTop + 230;
        CyberpunkButton saveBtn = new CyberpunkButton(panelLeft + panelWidth - 14 - 120, footerY, 120, 20,
                Component.literal("✓ Save Effect"), b -> saveScreenEffectEntry(), MusicSequenceChannel.COLOR_SCREEN_EFFECT, false);
        this.addRenderableWidget(saveBtn);

        CyberpunkButton cancelBtn = new CyberpunkButton(panelLeft + 14, footerY, 80, 20,
                Component.literal("✕ Cancel"), b -> this.onClose(), RED_CANCEL, false);
        this.addRenderableWidget(cancelBtn);

        if (onDelete != null) {
            CyberpunkButton deleteBtn = new CyberpunkButton(panelLeft + 98, footerY, 80, 20,
                    Component.literal("🗑 Delete"), b -> {
                onDelete.run();
                this.onClose();
            }, RED_CANCEL, false);
            this.addRenderableWidget(deleteBtn);
        }
    }

    private void initShakeControls(int contentX, int card1Y, int contentW) {
        int y1 = card1Y + 8;
        this.screenEffectIntensityBox = new EditBox(this.font, contentX + 68, y1, 46, 18, Component.literal("Intensity"));
        this.screenEffectIntensityBox.setValue(String.format(Locale.US, "%.1f", entry.getScreenEffectIntensity() > 0 ? entry.getScreenEffectIntensity() : 2.0f));
        this.addRenderableWidget(this.screenEffectIntensityBox);

        this.screenEffectFrequencyBox = new EditBox(this.font, contentX + 186, y1, 46, 18, Component.literal("Frequency"));
        this.screenEffectFrequencyBox.setValue(String.format(Locale.US, "%.0f", entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 20.0f));
        this.addRenderableWidget(this.screenEffectFrequencyBox);

        CyberpunkButton decayBtn = new CyberpunkButton(contentX + 246, y1, contentW - 252, 18,
                Component.literal(screenEffectDecay ? "DECAY: SMOOTH" : "DECAY: CONSTANT"),
                b -> {
                    screenEffectDecay = !screenEffectDecay;
                    b.setMessage(Component.literal(screenEffectDecay ? "DECAY: SMOOTH" : "DECAY: CONSTANT"));
                    if (b instanceof CyberpunkButton cb) {
                        cb.setAccentColor(screenEffectDecay ? 0xFF00FF88 : 0xFF8899AA);
                    }
                },
                screenEffectDecay ? 0xFF00FF88 : 0xFF8899AA, false, Component.literal("Taper shake amplitude towards end of duration"));
        this.addRenderableWidget(decayBtn);
    }

    private void initInvertControls(int contentX, int card1Y, int contentW) {
        int y1 = card1Y + 8;
        CyberpunkButton pulseBtn = new CyberpunkButton(contentX + 10, y1, 140, 18,
                Component.literal(screenEffectPulse ? "MODE: PULSATING" : "MODE: SOLID INVERT"),
                b -> {
                    screenEffectPulse = !screenEffectPulse;
                    b.setMessage(Component.literal(screenEffectPulse ? "MODE: PULSATING" : "MODE: SOLID INVERT"));
                    if (b instanceof CyberpunkButton cb) {
                        cb.setAccentColor(screenEffectPulse ? 0xFF00FFCC : 0xFF8899AA);
                    }
                },
                screenEffectPulse ? 0xFF00FFCC : 0xFF8899AA, false, Component.literal("Toggle continuous inversion vs rhythmic pulses"));
        this.addRenderableWidget(pulseBtn);

        this.screenEffectFrequencyBox = new EditBox(this.font, contentX + 235, y1, 50, 18, Component.literal("Pulse Freq"));
        this.screenEffectFrequencyBox.setValue(String.format(Locale.US, "%.1f", entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 2.0f));
        this.addRenderableWidget(this.screenEffectFrequencyBox);
    }

    private void initStrobeControls(int contentX, int card1Y, int contentW) {
        int y1 = card1Y + 8;
        int y2 = card1Y + 36;
        this.screenEffectFrequencyBox = new EditBox(this.font, contentX + 54, y1, 46, 18, Component.literal("Speed"));
        this.screenEffectFrequencyBox.setValue(String.format(Locale.US, "%.1f", entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 10.0f));
        this.addRenderableWidget(this.screenEffectFrequencyBox);

        CyberpunkButton smoothBtn = new CyberpunkButton(contentX + 108, y1, 136, 18,
                Component.literal(screenEffectSmooth ? "WAVE: SMOOTH" : "WAVE: HARD FLASH"),
                b -> {
                    screenEffectSmooth = !screenEffectSmooth;
                    b.setMessage(Component.literal(screenEffectSmooth ? "WAVE: SMOOTH" : "WAVE: HARD FLASH"));
                    if (b instanceof CyberpunkButton cb) {
                        cb.setAccentColor(screenEffectSmooth ? 0xFFFFD700 : 0xFF8899AA);
                    }
                },
                screenEffectSmooth ? 0xFFFFD700 : 0xFF8899AA, false, Component.literal("Smooth sinusoidal pulse vs abrupt square flash"));
        this.addRenderableWidget(smoothBtn);

        this.screenEffectColorBox = new EditBox(this.font, contentX + 44, y2, 60, 18, Component.literal("Hex"));
        this.screenEffectColorBox.setValue(String.format("#%06X", entry.getScreenEffectColor() & 0x00FFFFFF));
        this.addRenderableWidget(this.screenEffectColorBox);

        this.screenEffectIntensityBox = new EditBox(this.font, contentX + 180, y2, 46, 18, Component.literal("Opacity"));
        float curAlpha = entry.getScreenEffectMaxAlpha() > 0 ? entry.getScreenEffectMaxAlpha() : 0.85f;
        this.screenEffectIntensityBox.setValue(String.format(Locale.US, "%.2f", curAlpha));
        this.addRenderableWidget(this.screenEffectIntensityBox);

        this.screenEffectColorPicker = new CyberpunkColorPicker(contentX + 254, card1Y + 6, entry.getScreenEffectColor(), c -> {
            entry.setScreenEffectColor(c);
        });
        this.screenEffectColorPicker.bindHexBox(this.screenEffectColorBox);
        this.addRenderableWidget(this.screenEffectColorPicker);
    }

    private void initHueShiftControls(int contentX, int card1Y, int contentW) {
        int y1 = card1Y + 8;
        int y2 = card1Y + 36;

        CyberpunkButton contBtn = new CyberpunkButton(contentX + 10, y1, 140, 18,
                Component.literal(screenEffectContinuous ? "MODE: CYCLE SPECTRUM" : "MODE: FIXED ANGLE"),
                b -> {
                    screenEffectContinuous = !screenEffectContinuous;
                    b.setMessage(Component.literal(screenEffectContinuous ? "MODE: CYCLE SPECTRUM" : "MODE: FIXED ANGLE"));
                    if (b instanceof CyberpunkButton cb) {
                        cb.setAccentColor(screenEffectContinuous ? 0xFF00E5FF : 0xFFFFD700);
                    }
                    this.init();
                },
                screenEffectContinuous ? 0xFF00E5FF : 0xFFFFD700, false, Component.literal("Toggle continuous spectrum rotation vs fixed hue angle"));
        this.addRenderableWidget(contBtn);

        if (screenEffectContinuous) {
            this.screenEffectFrequencyBox = new EditBox(this.font, contentX + 252, y1, 46, 18, Component.literal("Speed"));
            this.screenEffectFrequencyBox.setValue(String.format(Locale.US, "%.1f", entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 0.5f));
            this.addRenderableWidget(this.screenEffectFrequencyBox);
        } else {
            this.screenEffectFrequencyBox = new EditBox(this.font, contentX + 232, y1, 46, 18, Component.literal("Angle"));
            this.screenEffectFrequencyBox.setValue(String.format(Locale.US, "%.0f", entry.getScreenEffectAngle() > 0 ? entry.getScreenEffectAngle() : 180.0f));
            this.addRenderableWidget(this.screenEffectFrequencyBox);

            CyberpunkButton b90 = new CyberpunkButton(contentX + 284, y1, 38, 18, Component.literal("90°"),
                    b -> { if (screenEffectFrequencyBox != null) screenEffectFrequencyBox.setValue("90"); },
                    0xFF00E5FF, false, Component.literal("Rotate hue by 90° (Warm / Alien shift)"));
            CyberpunkButton b180 = new CyberpunkButton(contentX + 326, y1, 42, 18, Component.literal("180°"),
                    b -> { if (screenEffectFrequencyBox != null) screenEffectFrequencyBox.setValue("180"); },
                    0xFFFF00CC, false, Component.literal("Invert hue angle (180° complementary colors)"));
            CyberpunkButton b270 = new CyberpunkButton(contentX + 372, y1, 42, 18, Component.literal("270°"),
                    b -> { if (screenEffectFrequencyBox != null) screenEffectFrequencyBox.setValue("270"); },
                    0xFFFFD700, false, Component.literal("Rotate hue by 270° (Cool shift)"));
            this.addRenderableWidget(b90);
            this.addRenderableWidget(b180);
            this.addRenderableWidget(b270);
        }

        this.screenEffectIntensityBox = new EditBox(this.font, contentX + 74, y2, 46, 18, Component.literal("Intensity"));
        this.screenEffectIntensityBox.setValue(String.format(Locale.US, "%.2f", entry.getScreenEffectIntensity() > 0 ? entry.getScreenEffectIntensity() : 1.0f));
        this.addRenderableWidget(this.screenEffectIntensityBox);
    }

    private void initImpactFrameControls(int contentX, int card1Y, int contentW) {
        int y1 = card1Y + 8;
        int y2 = card1Y + 36;

        CyberpunkButton styleBtn = new CyberpunkButton(contentX + 10, y1, 130, 18,
                Component.literal("STYLE: " + (screenEffectStyle != null ? screenEffectStyle : "MONOCHROME_CUT")),
                b -> {
                    if ("MONOCHROME_CUT".equalsIgnoreCase(screenEffectStyle)) {
                        screenEffectStyle = "MANGA_OUTLINE";
                    } else if ("MANGA_OUTLINE".equalsIgnoreCase(screenEffectStyle)) {
                        screenEffectStyle = "RADIAL_SHOCK";
                    } else {
                        screenEffectStyle = "MONOCHROME_CUT";
                    }
                    b.setMessage(Component.literal("STYLE: " + screenEffectStyle));
                },
                0xFFFF2255, false, Component.literal("Cycle impact style: Monochrome Cut (B&W keyframe), Manga Outline (ink edges), or Radial Shock"));
        this.addRenderableWidget(styleBtn);

        CyberpunkButton invertBtn = new CyberpunkButton(contentX + 144, y1, 100, 18,
                Component.literal(screenEffectPulse ? "INVERT: YES" : "INVERT: NO"),
                b -> {
                    screenEffectPulse = !screenEffectPulse;
                    b.setMessage(Component.literal(screenEffectPulse ? "INVERT: YES" : "INVERT: NO"));
                    if (b instanceof CyberpunkButton cb) {
                        cb.setAccentColor(screenEffectPulse ? 0xFF00FFCC : 0xFF8899AA);
                    }
                },
                screenEffectPulse ? 0xFF00FFCC : 0xFF8899AA, false, Component.literal("Toggle negative/inverted cut flashes in monochrome sequence"));
        this.addRenderableWidget(invertBtn);

        this.screenEffectFrequencyBox = new EditBox(this.font, contentX + 60, y2, 46, 18, Component.literal("Cut FPS"));
        float initialHz = entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 28.0f;
        this.screenEffectFrequencyBox.setValue(String.format(Locale.US, "%.0f", initialHz));
        this.addRenderableWidget(this.screenEffectFrequencyBox);

        this.screenEffectColorBox = new EditBox(this.font, contentX + 160, y2, 60, 18, Component.literal("Hex"));
        this.screenEffectColorBox.setValue(String.format("#%06X", entry.getScreenEffectColor() & 0x00FFFFFF));
        this.addRenderableWidget(this.screenEffectColorBox);

        this.screenEffectColorPicker = new CyberpunkColorPicker(contentX + 254, card1Y + 6, entry.getScreenEffectColor(), c -> {
            entry.setScreenEffectColor(c);
        });
        this.screenEffectColorPicker.bindHexBox(this.screenEffectColorBox);
        this.addRenderableWidget(this.screenEffectColorPicker);
    }

    private void saveScreenEffectEntry() {
        long ts = 0L;
        try {
            ts = Math.max(0L, Long.parseLong(this.timestampBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}
        entry.setTimestampMs(ts);
        entry.setActionType(MusicSequenceChannel.TYPE_SCREEN_EFFECT);
        if (channel != null) {
            entry.setChannelId(channel.getId());
        }

        int duration = 1000;
        try {
            if (screenEffectDurationBox != null && !screenEffectDurationBox.getValue().trim().isEmpty()) {
                duration = Math.max(0, Integer.parseInt(screenEffectDurationBox.getValue().trim()));
            }
        } catch (Exception ignored) {}
        entry.setDurationMs(duration);
        entry.setWindupMs(0);
        entry.setJumpMs(0);
        entry.setRecoveryMs(0);

        entry.setSubAction(activeScreenEffectMode);

        if ("INVERT_COLORS".equalsIgnoreCase(activeScreenEffectMode)) {
            entry.setScreenEffectId("fractured_utils:invert_colors");
            entry.setScreenEffectPulse(screenEffectPulse);
            float freq = parseFloat(screenEffectFrequencyBox, 2.0f);
            entry.setScreenEffectFrequency(freq);
            entry.setCommand("screeneffect invert " + duration + (screenEffectPulse ? " pulse " + freq : ""));
            if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                entry.setDescription(descriptionBox.getValue().trim());
            } else {
                entry.setDescription("Invert Colors (" + duration + "ms" + (screenEffectPulse ? ", pulse" : "") + ")");
            }
        } else if ("STROBE".equalsIgnoreCase(activeScreenEffectMode)) {
            entry.setScreenEffectId("fractured_utils:strobe");
            float freq = parseFloat(screenEffectFrequencyBox, 10.0f);
            entry.setScreenEffectFrequency(freq);
            entry.setScreenEffectSmooth(screenEffectSmooth);
            float opacity = parseOpacity(screenEffectIntensityBox, 0.85f);
            entry.setScreenEffectMaxAlpha(opacity);
            int clr = 0xFFFFFFFF;
            if (screenEffectColorPicker != null) {
                clr = screenEffectColorPicker.getColor();
            } else if (screenEffectColorBox != null) {
                String cStr = screenEffectColorBox.getValue().trim().replace("#", "");
                try {
                    clr = (int) Long.parseLong(cStr, 16);
                    if (cStr.length() <= 6) {
                        clr = 0xFF000000 | clr;
                    }
                } catch (Exception ignored) {}
            }
            entry.setScreenEffectColor(clr);
            entry.setCommand("screeneffect strobe " + duration + " " + freq);
            if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                entry.setDescription(descriptionBox.getValue().trim());
            } else {
                entry.setDescription("Strobe Flash (" + freq + "Hz, " + (int)(entry.getScreenEffectMaxAlpha() * 100) + "% op, " + duration + "ms)");
            }
        } else if ("HUE_SHIFT".equalsIgnoreCase(activeScreenEffectMode)) {
            entry.setScreenEffectId("fractured_utils:hue_shift");
            entry.setScreenEffectContinuous(screenEffectContinuous);
            if (screenEffectContinuous) {
                float speed = parseFloat(screenEffectFrequencyBox, 0.5f);
                entry.setScreenEffectFrequency(speed);
            } else {
                float angle = parseFloat(screenEffectFrequencyBox, 180.0f);
                entry.setScreenEffectAngle(angle);
            }
            float intensity = parseFloat(screenEffectIntensityBox, 1.0f);
            entry.setScreenEffectIntensity(Math.max(0.05f, Math.min(1.0f, intensity)));
            entry.setCommand("screeneffect hue_shift " + duration + (screenEffectContinuous ? " cycle " + entry.getScreenEffectFrequency() : " angle " + entry.getScreenEffectAngle()));
            if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                entry.setDescription(descriptionBox.getValue().trim());
            } else {
                entry.setDescription("Hue Shift (" + (screenEffectContinuous ? entry.getScreenEffectFrequency() + " rot/s" : (int)entry.getScreenEffectAngle() + "°") + ", " + duration + "ms)");
            }
        } else if ("IMPACT_FRAME".equalsIgnoreCase(activeScreenEffectMode)) {
            entry.setScreenEffectId("fractured_utils:impact_frame");
            float speed = parseFloat(screenEffectFrequencyBox, 28.0f);
            entry.setScreenEffectFrequency(speed);
            entry.setScreenEffectPulse(screenEffectPulse);
            entry.setScreenEffectStyle(screenEffectStyle);
            int clr = 0xFFFFFFFF;
            if (screenEffectColorPicker != null) {
                clr = screenEffectColorPicker.getColor();
            } else if (screenEffectColorBox != null) {
                String cStr = screenEffectColorBox.getValue().trim().replace("#", "");
                try {
                    clr = 0xFF000000 | (int) Long.parseLong(cStr, 16);
                } catch (Exception ignored) {}
            }
            entry.setScreenEffectColor(clr);
            entry.setScreenEffectSecondaryColor(screenEffectSecondaryColor);
            entry.setCommand("screeneffect impact_frame " + duration + " " + screenEffectStyle);
            if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                entry.setDescription(descriptionBox.getValue().trim());
            } else {
                entry.setDescription("Impact Frame (" + screenEffectStyle + ", " + duration + "ms)");
            }
        } else {
            // SCREEN_SHAKE
            entry.setScreenEffectId("fractured_utils:screen_shake");
            float intensity = parseFloat(screenEffectIntensityBox, 2.0f);
            float freq = parseFloat(screenEffectFrequencyBox, 20.0f);
            entry.setScreenEffectIntensity(intensity);
            entry.setScreenEffectFrequency(freq);
            entry.setScreenEffectDecay(screenEffectDecay);
            entry.setCommand("screeneffect shake " + duration + " " + intensity);
            if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                entry.setDescription(descriptionBox.getValue().trim());
            } else {
                entry.setDescription("Screen Shake (" + intensity + " amp, " + duration + "ms)");
            }
        }

        if (onSave != null) {
            onSave.accept(entry);
        }
        this.onClose();
    }

    private void initCameraWidgets(int panelLeft, int panelTop, int panelWidth, int panelHeight) {
        int contentX = panelLeft + 14;
        int contentW = panelWidth - 28;
        int currentY = panelTop + 30;

        // 1. Four Camera Mode Tabs
        int tabW = (contentW - 9) / 4;
        CyberpunkButton staticTab = new CyberpunkButton(contentX, currentY, tabW, 20, Component.literal("📌 STATIC"),
                b -> switchCameraMode("STATIC"),
                MusicSequenceChannel.COLOR_CAMERA, "STATIC".equalsIgnoreCase(activeCameraMode), Component.literal("Fixed coordinates, angles and flight setup"));

        CyberpunkButton followTab = new CyberpunkButton(contentX + tabW + 3, currentY, tabW, 20, Component.literal("👤 FOLLOW"),
                b -> switchCameraMode("FOLLOW"),
                0xFF00E5FF, "FOLLOW".equalsIgnoreCase(activeCameraMode), Component.literal("Track entity or player with height offset"));

        CyberpunkButton otsTab = new CyberpunkButton(contentX + (tabW + 3) * 2, currentY, tabW, 20, Component.literal("🎥 3RD PERSON"),
                b -> switchCameraMode("OVER_THE_SHOULDER"),
                0xFFAA55FF, "OVER_THE_SHOULDER".equalsIgnoreCase(activeCameraMode), Component.literal("Over-the-shoulder dynamic camera tracking"));

        CyberpunkButton clearTab = new CyberpunkButton(contentX + (tabW + 3) * 3, currentY, tabW, 20, Component.literal("🔄 CLEAR"),
                b -> switchCameraMode("CLEAR"),
                0xFFFF3355, "CLEAR".equalsIgnoreCase(activeCameraMode), Component.literal("Restore normal player camera controls"));

        this.addRenderableWidget(staticTab);
        this.addRenderableWidget(followTab);
        this.addRenderableWidget(otsTab);
        this.addRenderableWidget(clearTab);

        // 2. Mode-Specific Content Area Card (Height = 58)
        int card1Y = panelTop + 56;
        if ("STATIC".equalsIgnoreCase(activeCameraMode)) {
            initStaticCameraControls(contentX, card1Y, contentW);
        } else if ("FOLLOW".equalsIgnoreCase(activeCameraMode)) {
            initFollowCameraControls(contentX, card1Y, contentW);
        } else if ("OVER_THE_SHOULDER".equalsIgnoreCase(activeCameraMode)) {
            initOtsCameraControls(contentX, card1Y, contentW);
        } else if ("CLEAR".equalsIgnoreCase(activeCameraMode)) {
            initClearCameraControls(contentX, card1Y, contentW);
        }

        // 3. Timing & Interpolation Section Card (Height = 50)
        int card2Y = panelTop + 120;
        this.timestampBox = new EditBox(this.font, contentX + 66, card2Y + 5, 64, 18, Component.literal("Timestamp (ms)"));
        this.timestampBox.setMaxLength(10);
        this.timestampBox.setValue(String.valueOf(entry.getTimestampMs()));
        this.addRenderableWidget(this.timestampBox);

        this.cameraDurationBox = new EditBox(this.font, contentX + 192, card2Y + 5, 56, 18, Component.literal("Duration (ms)"));
        this.cameraDurationBox.setMaxLength(10);
        this.cameraDurationBox.setValue(String.valueOf(entry.getDurationMs() > 0 ? entry.getDurationMs() : 3000));
        this.addRenderableWidget(this.cameraDurationBox);

        CyberpunkButton smoothToggle = new CyberpunkButton(contentX + 256, card2Y + 5, 94, 18,
                Component.literal(cameraInterpolate ? "SMOOTH: ON" : "SMOOTH: OFF"),
                b -> {
                    cameraInterpolate = !cameraInterpolate;
                    b.setMessage(Component.literal(cameraInterpolate ? "SMOOTH: ON" : "SMOOTH: OFF"));
                    if (b instanceof CyberpunkButton cb) {
                        cb.setAccentColor(cameraInterpolate ? 0xFF00FF88 : 0xFF8899AA);
                    }
                },
                cameraInterpolate ? 0xFF00FF88 : 0xFF8899AA, false, Component.literal("Smooth camera interpolation between shots"));
        this.addRenderableWidget(smoothToggle);

        int qx = contentX + 356;
        int btnW = 32;
        this.addRenderableWidget(new CyberpunkButton(qx, card2Y + 5, btnW, 18, Component.literal("+1s"), b -> adjustTimestamp(1000)));
        this.addRenderableWidget(new CyberpunkButton(qx + btnW + 2, card2Y + 5, btnW, 18, Component.literal("+3s"), b -> adjustTimestamp(3000)));
        this.addRenderableWidget(new CyberpunkButton(qx + (btnW + 2) * 2, card2Y + 5, btnW, 18, Component.literal("+5s"), b -> adjustTimestamp(5000)));

        // 4. Description / Note Box
        int descLabelY = panelTop + 176;
        this.descriptionBox = new EditBox(this.font, contentX, descLabelY + 13, contentW, 18, Component.literal("Description / Note"));
        this.descriptionBox.setMaxLength(128);
        this.descriptionBox.setValue(entry.getDescription());
        this.descriptionBox.setHint(Component.literal("Optional label or cinematic note for timeline"));
        this.addRenderableWidget(this.descriptionBox);

        // 5. Footer: Save, Cancel, Delete
        int footerY = panelTop + 224;
        CyberpunkButton saveBtn = new CyberpunkButton(panelLeft + panelWidth - 14 - 110, footerY, 110, 20,
                Component.literal("✓ Save Camera"), b -> saveCameraEntry(), MusicSequenceChannel.COLOR_CAMERA, false);
        this.addRenderableWidget(saveBtn);

        CyberpunkButton cancelBtn = new CyberpunkButton(panelLeft + 14, footerY, 80, 20,
                Component.literal("✕ Cancel"), b -> this.onClose(), RED_CANCEL, false);
        this.addRenderableWidget(cancelBtn);

        if (onDelete != null) {
            CyberpunkButton deleteBtn = new CyberpunkButton(panelLeft + 98, footerY, 80, 20,
                    Component.literal("🗑 Delete"), b -> {
                onDelete.run();
                this.onClose();
            }, RED_CANCEL, false);
            this.addRenderableWidget(deleteBtn);
        }
    }

    private void initStaticCameraControls(int contentX, int card1Y, int contentW) {
        int y1 = card1Y + 5;
        this.cameraPosXBox = new EditBox(this.font, contentX + 20, y1, 44, 18, Component.literal("X"));
        this.cameraPosXBox.setValue(String.format(Locale.US, "%.1f", entry.getCameraX()));
        this.addRenderableWidget(this.cameraPosXBox);

        this.cameraPosYBox = new EditBox(this.font, contentX + 84, y1, 44, 18, Component.literal("Y"));
        this.cameraPosYBox.setValue(String.format(Locale.US, "%.1f", entry.getCameraY()));
        this.addRenderableWidget(this.cameraPosYBox);

        this.cameraPosZBox = new EditBox(this.font, contentX + 148, y1, 44, 18, Component.literal("Z"));
        this.cameraPosZBox.setValue(String.format(Locale.US, "%.1f", entry.getCameraZ()));
        this.addRenderableWidget(this.cameraPosZBox);

        CyberpunkButton snapEyeBtn = new CyberpunkButton(contentX + 202, y1, 114, 18,
                Component.literal("📍 SNAP EYE POS"), b -> capturePlayerEyePosition(), 0xFF00FF88, false, Component.literal("Capture player eye coordinates and angles"));
        this.addRenderableWidget(snapEyeBtn);

        CyberpunkButton flyBtn = new CyberpunkButton(contentX + 322, y1, contentW - 322 - 4, 18,
                Component.literal("✈ FLY & AIM (3D)"), b -> openCameraSetupScreen(), CYAN_MAIN, false, Component.literal("Launch 3D in-world camera flight & aim setup"));
        flyBtn.setSolidPrimary(true);
        this.addRenderableWidget(flyBtn);

        int y2 = card1Y + 31;
        this.cameraYawBox = new EditBox(this.font, contentX + 32, y2, 40, 18, Component.literal("Yaw"));
        this.cameraYawBox.setValue(String.format(Locale.US, "%.0f", entry.getCameraYaw()));
        this.addRenderableWidget(this.cameraYawBox);

        this.cameraPitchBox = new EditBox(this.font, contentX + 110, y2, 40, 18, Component.literal("Pitch"));
        this.cameraPitchBox.setValue(String.format(Locale.US, "%.0f", entry.getCameraPitch()));
        this.addRenderableWidget(this.cameraPitchBox);

        this.cameraRollBox = new EditBox(this.font, contentX + 184, y2, 38, 18, Component.literal("Roll"));
        this.cameraRollBox.setValue(String.format(Locale.US, "%.0f", entry.getCameraRoll()));
        this.addRenderableWidget(this.cameraRollBox);

        this.cameraFovBox = new EditBox(this.font, contentX + 254, y2, 38, 18, Component.literal("FOV"));
        this.cameraFovBox.setValue(String.format(Locale.US, "%.0f", entry.getCameraFov()));
        this.addRenderableWidget(this.cameraFovBox);
    }

    private void initFollowCameraControls(int contentX, int card1Y, int contentW) {
        int y1 = card1Y + 5;
        this.cameraTargetBox = new EditBox(this.font, contentX + 52, y1, 150, 18, Component.literal("Target"));
        this.cameraTargetBox.setValue(entry.getCameraTarget().isEmpty() ? "@p" : entry.getCameraTarget());
        this.addRenderableWidget(this.cameraTargetBox);

        this.cameraHeightOffsetBox = new EditBox(this.font, contentX + 254, y1, 46, 18, Component.literal("Height Offset"));
        this.cameraHeightOffsetBox.setValue(String.format(Locale.US, "%.1f", entry.getCameraHeightOffset() > 0 ? entry.getCameraHeightOffset() : 5.5));
        this.addRenderableWidget(this.cameraHeightOffsetBox);

        this.cameraFovBox = new EditBox(this.font, contentX + 338, y1, 42, 18, Component.literal("FOV"));
        this.cameraFovBox.setValue(String.format(Locale.US, "%.0f", entry.getCameraFov()));
        this.addRenderableWidget(this.cameraFovBox);

        int y2 = card1Y + 31;
        this.cameraPitchBox = new EditBox(this.font, contentX + 52, y2, 46, 18, Component.literal("Pitch"));
        this.cameraPitchBox.setValue(String.format(Locale.US, "%.0f", entry.getCameraPitch()));
        this.addRenderableWidget(this.cameraPitchBox);
    }

    private void initOtsCameraControls(int contentX, int card1Y, int contentW) {
        int y1 = card1Y + 5;
        this.cameraTargetBox = new EditBox(this.font, contentX + 52, y1, 110, 18, Component.literal("Target"));
        this.cameraTargetBox.setValue(entry.getCameraTarget().isEmpty() ? "@p" : entry.getCameraTarget());
        this.addRenderableWidget(this.cameraTargetBox);

        this.cameraBackDistanceBox = new EditBox(this.font, contentX + 202, y1, 46, 18, Component.literal("Back Dist"));
        this.cameraBackDistanceBox.setValue(String.format(Locale.US, "%.1f", entry.getCameraBackDistance() > 0 ? entry.getCameraBackDistance() : 2.4));
        this.addRenderableWidget(this.cameraBackDistanceBox);

        this.cameraShoulderOffsetBox = new EditBox(this.font, contentX + 286, y1, 46, 18, Component.literal("Shoulder"));
        this.cameraShoulderOffsetBox.setValue(String.format(Locale.US, "%.2f", entry.getCameraShoulderOffset() > 0 ? entry.getCameraShoulderOffset() : 0.55));
        this.addRenderableWidget(this.cameraShoulderOffsetBox);

        this.cameraFovBox = new EditBox(this.font, contentX + 368, y1, 42, 18, Component.literal("FOV"));
        this.cameraFovBox.setValue(String.format(Locale.US, "%.0f", entry.getCameraFov()));
        this.addRenderableWidget(this.cameraFovBox);

        int y2 = card1Y + 31;
        this.cameraHeightOffsetBox = new EditBox(this.font, contentX + 52, y2, 46, 18, Component.literal("Height Offset"));
        this.cameraHeightOffsetBox.setValue(String.format(Locale.US, "%.2f", entry.getCameraHeightOffset()));
        this.addRenderableWidget(this.cameraHeightOffsetBox);

        this.cameraPitchBox = new EditBox(this.font, contentX + 140, y2, 46, 18, Component.literal("Pitch"));
        this.cameraPitchBox.setValue(String.format(Locale.US, "%.0f", entry.getCameraPitch() != 0 ? entry.getCameraPitch() : 15.0f));
        this.addRenderableWidget(this.cameraPitchBox);
    }

    private void initClearCameraControls(int contentX, int card1Y, int contentW) {
    }

    private void switchCameraMode(String newMode) {
        this.activeCameraMode = newMode;
        this.init();
    }

    private void capturePlayerEyePosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Vec3 eye = mc.player.getEyePosition();
            if (cameraPosXBox != null) cameraPosXBox.setValue(String.format(Locale.US, "%.1f", eye.x));
            if (cameraPosYBox != null) cameraPosYBox.setValue(String.format(Locale.US, "%.1f", eye.y));
            if (cameraPosZBox != null) cameraPosZBox.setValue(String.format(Locale.US, "%.1f", eye.z));
            if (cameraYawBox != null) cameraYawBox.setValue(String.format(Locale.US, "%.0f", mc.player.getYRot()));
            if (cameraPitchBox != null) cameraPitchBox.setValue(String.format(Locale.US, "%.0f", mc.player.getXRot()));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.2f));
        }
    }

    private void openCameraSetupScreen() {
        double x = parseDouble(cameraPosXBox, entry.getCameraX());
        double y = parseDouble(cameraPosYBox, entry.getCameraY());
        double z = parseDouble(cameraPosZBox, entry.getCameraZ());
        float yaw = parseFloat(cameraYawBox, entry.getCameraYaw());
        float pitch = parseFloat(cameraPitchBox, entry.getCameraPitch());
        double fov = parseDouble(cameraFovBox, entry.getCameraFov());

        this.minecraft.setScreen(new CameraSetupScreen(this, x, y, z, yaw, pitch, fov, true, res -> {
            if (cameraPosXBox != null) cameraPosXBox.setValue(String.format(Locale.US, "%.1f", res.x()));
            if (cameraPosYBox != null) cameraPosYBox.setValue(String.format(Locale.US, "%.1f", res.y()));
            if (cameraPosZBox != null) cameraPosZBox.setValue(String.format(Locale.US, "%.1f", res.z()));
            if (cameraYawBox != null) cameraYawBox.setValue(String.format(Locale.US, "%.0f", res.yaw()));
            if (cameraPitchBox != null) cameraPitchBox.setValue(String.format(Locale.US, "%.0f", res.pitch()));
            if (cameraFovBox != null) cameraFovBox.setValue(String.format(Locale.US, "%.0f", res.fov()));
        }));
    }

    private double parseDouble(EditBox box, double fallback) {
        if (box == null || box.getValue().trim().isEmpty()) return fallback;
        try {
            return Double.parseDouble(box.getValue().trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private float parseFloat(EditBox box, float fallback) {
        if (box == null || box.getValue().trim().isEmpty()) return fallback;
        try {
            return Float.parseFloat(box.getValue().trim().replace(",", "."));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private float parseOpacity(EditBox box, float fallback) {
        if (box == null || box.getValue().trim().isEmpty()) return fallback;
        try {
            String valStr = box.getValue().trim().replace("%", "").replace(",", ".");
            float val = Float.parseFloat(valStr);
            if (val > 1.0f && val <= 100.0f) {
                val = val / 100.0f;
            }
            return Math.max(0.01f, Math.min(1.0f, val));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void initPuppetWidgets(int panelLeft, int panelTop, int panelWidth, int panelHeight) {
        int contentX = panelLeft + 16;
        int contentW = panelWidth - 32;
        int currentY = panelTop + 38;

        // 1. Four Action Selection Cards / Tabs
        int tabW = (contentW - 9) / 4;
        CyberpunkButton spawnTab = new CyberpunkButton(contentX, currentY, tabW, 20, Component.literal("➕ SPAWN"),
                b -> switchPuppetMode(PuppetSubAction.SPAWN),
                0xFF00FF88, activePuppetMode == PuppetSubAction.SPAWN, Component.literal("Summon actor into the world"));

        CyberpunkButton despawnTab = new CyberpunkButton(contentX + tabW + 3, currentY, tabW, 20, Component.literal("✕ DESPAWN"),
                b -> switchPuppetMode(PuppetSubAction.DESPAWN),
                0xFFFF3355, activePuppetMode == PuppetSubAction.DESPAWN, Component.literal("Discard actor from the world"));

        CyberpunkButton aiTab = new CyberpunkButton(contentX + (tabW + 3) * 2, currentY, tabW, 20, Component.literal("🧠 TOGGLE AI"),
                b -> switchPuppetMode(PuppetSubAction.TOGGLE_AI),
                0xFFFFB300, activePuppetMode == PuppetSubAction.TOGGLE_AI, Component.literal("Freeze, suppress, or restore actor AI"));

        CyberpunkButton actionTab = new CyberpunkButton(contentX + (tabW + 3) * 3, currentY, tabW, 20, Component.literal("⚔ ACTION"),
                b -> switchPuppetMode(PuppetSubAction.EXECUTE_ACTION),
                0xFFAA55FF, activePuppetMode == PuppetSubAction.EXECUTE_ACTION, Component.literal("Execute scripted puppet boss action"));

        this.addRenderableWidget(spawnTab);
        this.addRenderableWidget(despawnTab);
        this.addRenderableWidget(aiTab);
        this.addRenderableWidget(actionTab);
        currentY += 28;

        // 2. Mode-Specific Content Area
        if (activePuppetMode == PuppetSubAction.SPAWN) {
            initSpawnControls(contentX, currentY, contentW);
            currentY += 66;
        } else if (activePuppetMode == PuppetSubAction.DESPAWN) {
            initDespawnControls(contentX, currentY, contentW);
            currentY += 66;
        } else if (activePuppetMode == PuppetSubAction.TOGGLE_AI) {
            initToggleAiControls(contentX, currentY, contentW);
            currentY += 66;
        } else if (activePuppetMode == PuppetSubAction.EXECUTE_ACTION) {
            initExecuteActionControls(contentX, currentY, contentW);
            currentY += 92;
        }

        // 3. Timestamp (Ms) Input Row
        this.timestampBox = new EditBox(this.font, contentX, currentY, 110, 18, Component.literal("Timestamp (ms)"));
        this.timestampBox.setMaxLength(10);
        this.timestampBox.setValue(String.valueOf(entry.getTimestampMs()));
        this.addRenderableWidget(this.timestampBox);

        int qx = contentX + 116;
        int btnW = 48;
        this.addRenderableWidget(new CyberpunkButton(qx, currentY, btnW, 18, Component.literal("+500ms"), b -> adjustTimestamp(500)));
        this.addRenderableWidget(new CyberpunkButton(qx + btnW + 4, currentY, btnW, 18, Component.literal("+1s"), b -> adjustTimestamp(1000)));
        this.addRenderableWidget(new CyberpunkButton(qx + (btnW + 4) * 2, currentY, btnW, 18, Component.literal("+5s"), b -> adjustTimestamp(5000)));
        this.addRenderableWidget(new CyberpunkButton(qx + (btnW + 4) * 3, currentY, btnW, 18, Component.literal("-1s"), b -> adjustTimestamp(-1000)));
        currentY += 24;

        // 4. Description / Note Box
        this.descriptionBox = new EditBox(this.font, contentX, currentY, contentW, 18, Component.literal("Description / Note"));
        this.descriptionBox.setMaxLength(128);
        this.descriptionBox.setValue(entry.getDescription());
        this.descriptionBox.setHint(Component.literal("Optional label for timeline marker"));
        this.addRenderableWidget(this.descriptionBox);

        // 5. Footer Buttons: [DELETE ACTION] [CANCEL] [SAVE ENTRY]
        int footerY = panelTop + panelHeight - 28;
        if (onDelete != null) {
            CyberpunkButton deleteBtn = new CyberpunkButton(contentX, footerY, 110, 20, Component.literal("🗑 DELETE ACTION"),
                    b -> {
                        onDelete.run();
                        this.onClose();
                    }, RED_CANCEL, false, Component.literal("Delete this action from the timeline"));
            this.addRenderableWidget(deleteBtn);
        }

        CyberpunkButton cancelBtn = new CyberpunkButton(panelLeft + panelWidth - 210, footerY, 95, 20, Component.literal("CANCEL"),
                b -> this.onClose(), 0xFF778899, false);
        CyberpunkButton saveBtn = new CyberpunkButton(panelLeft + panelWidth - 105, footerY, 95, 20, Component.literal("SAVE ENTRY"),
                b -> saveAndClose(), CYAN_MAIN, false);

        this.addRenderableWidget(cancelBtn);
        this.addRenderableWidget(saveBtn);
    }

    private void initSpawnControls(int contentX, int currentY, int contentW) {
        // Parse previous coords or default
        String prevX = "~", prevY = "~", prevZ = "~";
        String cmd = entry.getCommand().trim();
        if (cmd.startsWith("summon")) {
            String[] parts = cmd.split("\\s+");
            if (parts.length >= 5) {
                prevX = parts[2];
                prevY = parts[3];
                prevZ = parts[4];
            }
        }
        if (prevX.equals("~") && prevY.equals("~") && prevZ.equals("~") && this.minecraft != null && this.minecraft.player != null) {
            prevX = String.format(Locale.ROOT, "%.2f", this.minecraft.player.getX());
            prevY = String.format(Locale.ROOT, "%.2f", this.minecraft.player.getY());
            prevZ = String.format(Locale.ROOT, "%.2f", this.minecraft.player.getZ());
        }

        // Coordinate text boxes
        int coordW = 56;
        this.spawnXBox = new EditBox(this.font, contentX + 16, currentY + 14, coordW, 18, Component.literal("X"));
        this.spawnXBox.setValue(prevX);
        this.spawnXBox.setMaxLength(16);

        this.spawnYBox = new EditBox(this.font, contentX + 16 + coordW + 18, currentY + 14, coordW, 18, Component.literal("Y"));
        this.spawnYBox.setValue(prevY);
        this.spawnYBox.setMaxLength(16);

        this.spawnZBox = new EditBox(this.font, contentX + 16 + (coordW + 18) * 2, currentY + 14, coordW, 18, Component.literal("Z"));
        this.spawnZBox.setValue(prevZ);
        this.spawnZBox.setMaxLength(16);

        this.addRenderableWidget(this.spawnXBox);
        this.addRenderableWidget(this.spawnYBox);
        this.addRenderableWidget(this.spawnZBox);

        // Helper Buttons: Player Pos and Look Pos
        int btnX = contentX + 16 + (coordW + 18) * 3 + 6;
        CyberpunkButton playerPosBtn = new CyberpunkButton(btnX, currentY + 14, 94, 18, Component.literal("📍 PLAYER POS"), b -> {
            if (minecraft != null && minecraft.player != null) {
                spawnXBox.setValue(String.format(Locale.ROOT, "%.2f", minecraft.player.getX()));
                spawnYBox.setValue(String.format(Locale.ROOT, "%.2f", minecraft.player.getY()));
                spawnZBox.setValue(String.format(Locale.ROOT, "%.2f", minecraft.player.getZ()));
            }
        }, 0xFF00FF88, false, Component.literal("Set coordinates to player current position"));

        CyberpunkButton lookPosBtn = new CyberpunkButton(btnX + 100, currentY + 14, 88, 18, Component.literal("🎯 LOOK POS"), b -> {
            if (minecraft != null && minecraft.player != null) {
                HitResult hit = minecraft.player.pick(50.0D, 0.0F, false);
                if (hit != null && hit.getType() != HitResult.Type.MISS) {
                    Vec3 pos = hit.getLocation();
                    spawnXBox.setValue(String.format(Locale.ROOT, "%.2f", pos.x));
                    spawnYBox.setValue(String.format(Locale.ROOT, "%.2f", pos.y));
                    spawnZBox.setValue(String.format(Locale.ROOT, "%.2f", pos.z));
                }
            }
        }, 0xFF00FFCC, false, Component.literal("Set coordinates to crosshair block position"));

        this.addRenderableWidget(playerPosBtn);
        this.addRenderableWidget(lookPosBtn);

        // AI Disable Toggle
        Component aiLabel = Component.literal(spawnDisableAi ? "[✓] DISABLE AI ON SPAWN: YES" : "[ ] DISABLE AI ON SPAWN: NO");
        CyberpunkButton toggleAiOnSpawnBtn = new CyberpunkButton(contentX, currentY + 38, contentW, 18, aiLabel, b -> {
            spawnDisableAi = !spawnDisableAi;
            b.setMessage(Component.literal(spawnDisableAi ? "[✓] DISABLE AI ON SPAWN: YES" : "[ ] DISABLE AI ON SPAWN: NO"));
            ((CyberpunkButton) b).setAccentColor(spawnDisableAi ? 0xFFFF9900 : 0xFF778899);
        }, spawnDisableAi ? 0xFFFF9900 : 0xFF778899, false, Component.literal("When enabled, adds NoAI:1b so entity stays frozen upon summon until instructed"));
        this.addRenderableWidget(toggleAiOnSpawnBtn);
    }

    private void initDespawnControls(int contentX, int currentY, int contentW) {
        // Despawn is an instant command that removes the actor. We display informative status.
    }

    private void initToggleAiControls(int contentX, int currentY, int contentW) {
        // Mode: SUPPRESS AI vs RESTORE AI
        Component modeComp = Component.literal(isSuppressAi ? "STATE: [🛑 SUPPRESS / FREEZE AI]" : "STATE: [▶ RESTORE / RESUME AI]");
        CyberpunkButton modeBtn = new CyberpunkButton(contentX, currentY + 4, contentW, 20, modeComp, b -> {
            isSuppressAi = !isSuppressAi;
            this.init();
        }, isSuppressAi ? 0xFFFF3355 : 0xFF00FF88, false);
        this.addRenderableWidget(modeBtn);

        if (isSuppressAi) {
            int aspectW = (contentW - 16) / 5;
            int flagY = currentY + 28;

            CyberpunkButton fullAiBtn = new CyberpunkButton(contentX, flagY, aspectW, 18, Component.literal(suppressFullAi ? "AI: ON" : "AI: OFF"), b -> {
                suppressFullAi = !suppressFullAi;
                b.setMessage(Component.literal(suppressFullAi ? "AI: ON" : "AI: OFF"));
                ((CyberpunkButton) b).setAccentColor(suppressFullAi ? 0xFFFF9900 : 0xFF556677);
            }, suppressFullAi ? 0xFFFF9900 : 0xFF556677, false, Component.literal("Suppress full mob brain & goal selectors"));

            CyberpunkButton navBtn = new CyberpunkButton(contentX + aspectW + 4, flagY, aspectW, 18, Component.literal(suppressNav ? "NAV: ON" : "NAV: OFF"), b -> {
                suppressNav = !suppressNav;
                b.setMessage(Component.literal(suppressNav ? "NAV: ON" : "NAV: OFF"));
                ((CyberpunkButton) b).setAccentColor(suppressNav ? 0xFFFF9900 : 0xFF556677);
            }, suppressNav ? 0xFFFF9900 : 0xFF556677, false, Component.literal("Suppress pathfinding navigation"));

            CyberpunkButton tgtBtn = new CyberpunkButton(contentX + (aspectW + 4) * 2, flagY, aspectW, 18, Component.literal(suppressTgt ? "TGT: ON" : "TGT: OFF"), b -> {
                suppressTgt = !suppressTgt;
                b.setMessage(Component.literal(suppressTgt ? "TGT: ON" : "TGT: OFF"));
                ((CyberpunkButton) b).setAccentColor(suppressTgt ? 0xFFFF9900 : 0xFF556677);
            }, suppressTgt ? 0xFFFF9900 : 0xFF556677, false, Component.literal("Suppress attack targeting"));

            CyberpunkButton lookBtn = new CyberpunkButton(contentX + (aspectW + 4) * 3, flagY, aspectW, 18, Component.literal(suppressLook ? "LOOK: ON" : "LOOK: OFF"), b -> {
                suppressLook = !suppressLook;
                b.setMessage(Component.literal(suppressLook ? "LOOK: ON" : "LOOK: OFF"));
                ((CyberpunkButton) b).setAccentColor(suppressLook ? 0xFFFF9900 : 0xFF556677);
            }, suppressLook ? 0xFFFF9900 : 0xFF556677, false, Component.literal("Suppress head look controller"));

            CyberpunkButton actionsBtn = new CyberpunkButton(contentX + (aspectW + 4) * 4, flagY, aspectW, 18, Component.literal(suppressActions ? "ACT: ON" : "ACT: OFF"), b -> {
                suppressActions = !suppressActions;
                b.setMessage(Component.literal(suppressActions ? "ACT: ON" : "ACT: OFF"));
                ((CyberpunkButton) b).setAccentColor(suppressActions ? 0xFFFF9900 : 0xFF556677);
            }, suppressActions ? 0xFFFF9900 : 0xFF556677, false, Component.literal("Suppress active puppet actions"));

            this.addRenderableWidget(fullAiBtn);
            this.addRenderableWidget(navBtn);
            this.addRenderableWidget(tgtBtn);
            this.addRenderableWidget(lookBtn);
            this.addRenderableWidget(actionsBtn);
        }
    }

    private void initExecuteActionControls(int contentX, int currentY, int contentW) {
        // 1. Registered Actions Dropdown + Target Selector
        List<CyberpunkDropdown.DropdownEntry<String>> actionEntries = new ArrayList<>();
        for (PuppetActionType<?> action : ModPuppetActions.getAll()) {
            String idStr = action.getId().toString();
            String simpleName = action.getId().getPath().replace("_", " ").toUpperCase(Locale.ROOT);
            actionEntries.add(new CyberpunkDropdown.DropdownEntry<>(idStr, Component.literal("⚡ " + simpleName), Component.literal(idStr)));
        }
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("custom", Component.literal("⚙ CUSTOM ACTION ID"), Component.literal("Enter custom puppet action")));

        int dropdownW = 260;
        this.puppetActionDropdown = new CyberpunkDropdown<>(contentX, currentY + 12, dropdownW, 18, Component.literal("Puppet Action"));
        this.puppetActionDropdown.setOptions(actionEntries);
        this.puppetActionDropdown.selectByValue(selectedActionId);
        this.puppetActionDropdown.setOnSelect(entry -> {
            this.selectedActionId = entry.getValue();
            if ("custom".equalsIgnoreCase(entry.getValue())) {
                if (customActionIdBox != null) customActionIdBox.setVisible(true);
            } else {
                if (customActionIdBox != null) customActionIdBox.setVisible(false);
            }
        });
        this.addRenderableWidget(this.puppetActionDropdown);

        int targetX = contentX + dropdownW + 10;
        int targetW = contentW - dropdownW - 10;
        this.targetSelectorBox = new EditBox(this.font, targetX, currentY + 12, targetW, 18, Component.literal("Target"));
        this.targetSelectorBox.setMaxLength(64);
        this.targetSelectorBox.setValue(this.combatTarget != null && !this.combatTarget.isBlank() ? this.combatTarget : "@p");
        this.targetSelectorBox.setHint(Component.literal("@p"));
        this.addRenderableWidget(this.targetSelectorBox);

        // 2. Timings: Indication (ms), Jump (ms), Slam (ms), Recovery (ms)
        int timingY = currentY + 36;
        int colW = (contentW - 24) / 4;

        // 1. Indication / Ground Charge Column (Amber)
        this.windupBox = new EditBox(this.font, contentX + 46, timingY, colW - 46, 16, Component.literal("Indicator"));
        this.windupBox.setMaxLength(6);
        this.windupBox.setValue(String.valueOf(windupMs));
        this.windupBox.setResponder(val -> {
            try { this.windupMs = Math.max(0, Integer.parseInt(val.trim())); } catch (Exception ignored) {}
        });
        this.addRenderableWidget(this.windupBox);

        // 2. Jump Column (Indigo)
        this.jumpBox = new EditBox(this.font, contentX + colW + 8 + 32, timingY, colW - 32, 16, Component.literal("Jump"));
        this.jumpBox.setMaxLength(6);
        this.jumpBox.setValue(String.valueOf(jumpMs));
        this.jumpBox.setResponder(val -> {
            try { this.jumpMs = Math.max(0, Integer.parseInt(val.trim())); } catch (Exception ignored) {}
        });
        this.addRenderableWidget(this.jumpBox);

        // 3. Active Slam Column (Purple)
        this.durationBox = new EditBox(this.font, contentX + (colW + 8) * 2 + 32, timingY, colW - 32, 16, Component.literal("Slam"));
        this.durationBox.setMaxLength(6);
        this.durationBox.setValue(String.valueOf(durationMs));
        this.durationBox.setResponder(val -> {
            try { this.durationMs = Math.max(0, Integer.parseInt(val.trim())); } catch (Exception ignored) {}
        });
        this.addRenderableWidget(this.durationBox);

        // 4. Recovery Column (Cyan)
        this.recoveryBox = new EditBox(this.font, contentX + (colW + 8) * 3 + 48, timingY, colW - 48, 16, Component.literal("Recovery"));
        this.recoveryBox.setMaxLength(6);
        this.recoveryBox.setValue(String.valueOf(recoveryMs));
        this.recoveryBox.setResponder(val -> {
            try { this.recoveryMs = Math.max(0, Integer.parseInt(val.trim())); } catch (Exception ignored) {}
        });
        this.addRenderableWidget(this.recoveryBox);
    }

    private void initStandardWidgets(int panelLeft, int panelTop, int panelWidth, int panelHeight) {
        int contentX = panelLeft + 16;
        int contentW = panelWidth - 32;

        String prevTimestamp = (this.timestampBox != null) ? this.timestampBox.getValue() : String.valueOf(entry.getTimestampMs());
        String prevCommand = (this.commandBox != null) ? this.commandBox.getValue() : entry.getCommand();
        String prevDescription = (this.descriptionBox != null) ? this.descriptionBox.getValue() : entry.getDescription();

        // 1. Action Type Dropdown (Top Row)
        List<CyberpunkDropdown.DropdownEntry<String>> typeEntries = new ArrayList<>();
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("COMMAND", Component.literal("COMMAND"), Component.literal("Execute console command")));
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("DIALOG", Component.literal("DIALOG"), Component.literal("Trigger dialog sequence")));
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>(MusicSequenceChannel.TYPE_SCREEN_EFFECT, Component.literal("SCREEN_EFFECT"), Component.literal("Display screen effect (shake/invert/strobe)")));
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("CAMERA", Component.literal("CAMERA"), Component.literal("Set camera position")));
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("CHECKPOINT", Component.literal("CHECKPOINT"), Component.literal("Trigger checkpoint")));
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("PUPPET", Component.literal("PUPPET"), Component.literal("Trigger puppet actor action")));
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("SOUND_EFFECT", Component.literal("SOUND_EFFECT"), Component.literal("Play sound effect")));
        typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("OPERATOR_RESUME", Component.literal("OPERATOR_RESUME"), Component.literal("Resume sequence")));

        int row1Y = panelTop + 30;
        this.typeDropdown = new CyberpunkDropdown<>(contentX + 34, row1Y, 100, 18, Component.literal("Action Type"));
        this.typeDropdown.setOptions(typeEntries);
        this.typeDropdown.selectByValue(entry.getActionType());
        this.typeDropdown.setOnSelect(selected -> {
            entry.setActionType(selected.getValue());
            if ("DIALOG".equalsIgnoreCase(selected.getValue())) {
                this.isDialog = true;
                initDialogState();
            }
            this.init();
        });
        this.addRenderableWidget(this.typeDropdown);

        // 2. Timestamp input
        this.timestampBox = new EditBox(this.font, contentX + 208, row1Y, 70, 18, Component.literal("Timestamp"));
        this.timestampBox.setMaxLength(10);
        this.timestampBox.setValue(prevTimestamp);
        this.addRenderableWidget(this.timestampBox);

        // 3. Command Box (with Brigadier suggestions & validation, anchorToBottom = false)
        int cmdY = panelTop + 68;
        this.commandBox = new EditBox(this.font, contentX, cmdY, contentW, 20, Component.literal("Command Payload"));
        this.commandBox.setMaxLength(512);
        this.commandBox.setValue(prevCommand);
        this.commandBox.setHint(Component.literal("e.g. /say Hello %player%"));
        this.addRenderableWidget(this.commandBox);

        if (isCommandAction()) {
            this.commandSuggestions = new CommandSuggestions(
                    this.minecraft, this, this.commandBox, this.font,
                    true, true, 0, 7, false, 0xEE081622
            );
            this.commandSuggestions.setAllowSuggestions(true);
            this.commandSuggestions.updateCommandInfo();
            updateCommandValidation();

            this.commandBox.setResponder(text -> {
                if (this.commandSuggestions != null) {
                    this.commandSuggestions.updateCommandInfo();
                }
                updateCommandValidation();
            });
        } else {
            this.commandSuggestions = null;
        }

        // 4. Description / Timeline Label
        int descY = panelTop + 120;
        this.descriptionBox = new EditBox(this.font, contentX, descY, contentW, 20, Component.literal("Description"));
        this.descriptionBox.setMaxLength(128);
        this.descriptionBox.setValue(prevDescription);
        this.descriptionBox.setHint(Component.literal("Optional timeline marker label"));
        this.addRenderableWidget(this.descriptionBox);

        // 5. Footer Buttons: [DELETE] [CANCEL] [SAVE COMMAND]
        int footerY = panelTop + panelHeight - 26;
        if (onDelete != null) {
            CyberpunkButton deleteBtn = new CyberpunkButton(contentX, footerY, 90, 20, Component.literal("🗑 DELETE"),
                    b -> {
                        onDelete.run();
                        this.onClose();
                    }, RED_CANCEL, false, Component.literal("Delete this action from the timeline"));
            this.addRenderableWidget(deleteBtn);
        }

        CyberpunkButton cancelBtn = new CyberpunkButton(panelLeft + panelWidth - 190, footerY, 80, 20, Component.literal("CANCEL"),
                b -> this.onClose(), 0xFF778899, false);
        CyberpunkButton saveBtn = new CyberpunkButton(panelLeft + panelWidth - 105, footerY, 90, 20,
                Component.literal(isCommandAction() ? "SAVE COMMAND" : "SAVE ENTRY"),
                b -> saveAndClose(), CYAN_MAIN, false);

        this.addRenderableWidget(cancelBtn);
        this.addRenderableWidget(saveBtn);
    }

    private void initDialogWidgets(int panelLeft, int panelTop, int panelWidth, int panelHeight) {
        int fullW = panelWidth - 32;

        // Top Row: Action Type Dropdown (if generic channel) & Timestamp Box + Playhead Snap
        int topRowY = panelTop + 7;
        if (channel == null || !channel.getType().equalsIgnoreCase(MusicSequenceChannel.TYPE_DIALOG)) {
            List<CyberpunkDropdown.DropdownEntry<String>> typeEntries = new ArrayList<>();
            typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("COMMAND", Component.literal("COMMAND"), Component.literal("Execute console command")));
            typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("DIALOG", Component.literal("DIALOG"), Component.literal("Trigger dialog action")));
            typeEntries.add(new CyberpunkDropdown.DropdownEntry<>(MusicSequenceChannel.TYPE_SCREEN_EFFECT, Component.literal("SCREEN_EFFECT"), Component.literal("Display screen effect (shake/invert/strobe)")));
            typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("CAMERA", Component.literal("CAMERA"), Component.literal("Set camera position")));
            typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("CHECKPOINT", Component.literal("CHECKPOINT"), Component.literal("Trigger checkpoint")));
            typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("PUPPET", Component.literal("PUPPET"), Component.literal("Trigger puppet actor action")));
            typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("SOUND_EFFECT", Component.literal("SOUND_EFFECT"), Component.literal("Play sound effect")));
            typeEntries.add(new CyberpunkDropdown.DropdownEntry<>("OPERATOR_RESUME", Component.literal("OPERATOR_RESUME"), Component.literal("Resume sequence")));

            this.typeDropdown = new CyberpunkDropdown<>(panelLeft + 16, topRowY, 95, 18, Component.literal("Action Type"));
            this.typeDropdown.setOptions(typeEntries);
            this.typeDropdown.selectByValue("DIALOG");
            this.typeDropdown.setOnSelect(selected -> {
                entry.setActionType(selected.getValue());
                if (!"DIALOG".equalsIgnoreCase(selected.getValue())) {
                    this.isDialog = false;
                }
                this.init();
            });
            this.addRenderableWidget(this.typeDropdown);
        }

        // Timestamp input on top-right
        int tsX = panelLeft + panelWidth - 16 - 130;
        this.dialogTimestampBox = new EditBox(this.font, tsX, topRowY, 68, 18, Component.literal("Timestamp"));
        this.dialogTimestampBox.setMaxLength(10);
        this.dialogTimestampBox.setValue(String.valueOf(entry.getTimestampMs()));
        this.addRenderableWidget(this.dialogTimestampBox);

        CyberpunkButton snapBtn = new CyberpunkButton(tsX + 72, topRowY, 58, 18, Component.literal("⏱ Snap"), b -> snapDialogToPlayhead(), 0xFF00E5FF, false, Component.literal("Snap timestamp to current timeline playhead position"));
        this.addRenderableWidget(snapBtn);

        // --- PRIMARY FOCUS SECTION: FULL WIDTH SPEAKER TAG & MULTILINE DIALOG TEXT ---
        int formY = panelTop + 96;

        // 1. Speaker Tag EditBox (Full Width)
        this.dialogSpeakerBox = new EditBox(this.font, panelLeft + 16, formY + 10, fullW, 18, Component.literal("Speaker"));
        this.dialogSpeakerBox.setMaxLength(64);
        this.dialogSpeakerBox.setValue(dialogLine.getSpeaker());
        this.addRenderableWidget(this.dialogSpeakerBox);
        formY += 32;

        // 2. Multiline Dialog Text EditBox (Full Width & Wrapped Height)
        this.dialogTextBox = new CyberpunkMultilineEditBox(panelLeft + 16, formY + 10, fullW, 54, Component.literal("Dialog Text"));
        this.dialogTextBox.setMaxLength(512);
        this.dialogTextBox.setValue(dialogLine.getText());
        this.addRenderableWidget(this.dialogTextBox);
        formY += 68;

        // 3. Complete Formatting Palette Bar (22 Standard Codes + 6 Custom Animated FX)
        int btnW = 20;
        int btnH = 15;
        int gapX = 4;

        String[] row1Codes = {"&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7", "&8", "&9", "&a"};
        String[] row1Labels = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a"};
        String[] row1Names = {"Black (&0)", "Dark Blue (&1)", "Dark Green (&2)", "Dark Aqua (&3)", "Dark Red (&4)", "Dark Purple (&5)", "Gold (&6)", "Gray (&7)", "Dark Gray (&8)", "Blue (&9)", "Green (&a)"};

        String[] row2Codes = {"&b", "&c", "&d", "&e", "&f", "&k", "&l", "&m", "&n", "&o", "&r"};
        String[] row2Labels = {"b", "c", "d", "e", "f", "?", "L", "S", "U", "I", "R"};
        String[] row2Names = {"Aqua (&b)", "Red (&c)", "Light Purple (&d)", "Yellow (&e)", "White (&f)", "Obfuscated (&k)", "Bold (&l)", "Strikethrough (&m)", "Underline (&n)", "Italic (&o)", "Reset (&r)"};

        int paletteY = formY + 10;

        for (int i = 0; i < row1Codes.length; i++) {
            final String code = row1Codes[i];
            String label = row1Labels[i];
            String name = row1Names[i];
            CyberpunkButton codeBtn = new CyberpunkButton(panelLeft + 16 + i * (btnW + gapX), paletteY, btnW, btnH,
                    DialogFormatUtil.formatText(code + label),
                    b -> insertDialogFormattingCode(code));
            codeBtn.setTooltip(Tooltip.create(Component.literal(name)));
            this.addRenderableWidget(codeBtn);
        }

        for (int i = 0; i < row2Codes.length; i++) {
            final String code = row2Codes[i];
            String label = row2Labels[i];
            String name = row2Names[i];
            CyberpunkButton codeBtn = new CyberpunkButton(panelLeft + 16 + i * (btnW + gapX), paletteY + btnH + 2, btnW, btnH,
                    DialogFormatUtil.formatText(code + label),
                    b -> insertDialogFormattingCode(code));
            codeBtn.setTooltip(Tooltip.create(Component.literal(name)));
            this.addRenderableWidget(codeBtn);
        }

        int fxBtnW = 76;
        int fxBtnH = 16;
        int fxGapX = 6;

        String[] fxCodes = {"&~s", "&~w", "&~r", "&~g", "&~p", "&~x"};
        String[] fxPreviewTexts = {"&~s~s Shake", "&~w~w Wave", "&~r~r Rainbow", "&~g~g Glitch", "&~p~p Pulse", "&~x~x Reset"};
        String[] fxNames = {
            "Letter Shake Effect (&~s) - Jitters letters in place",
            "Sine Wave Effect (&~w) - Bobs letters up & down",
            "Rainbow Spectrum Effect (&~r) - Rotating HSL colors",
            "Cyberpunk Glitch Effect (&~g) - RGB flicker & twitch",
            "Glow Pulse Effect (&~p) - Brightness pulsing glow",
            "Clear Custom FX (&~x) - Resets animation effect"
        };
        int[] fxAccents = {0xFFFF5555, 0xFF55FFFF, 0xFFFF55FF, 0xFF00FFCC, 0xFFFFFF55, 0xFFAAAAAA};

        int fxY = paletteY + (btnH * 2) + 5;
        for (int i = 0; i < fxCodes.length; i++) {
            final String code = fxCodes[i];
            final String previewText = fxPreviewTexts[i];
            String name = fxNames[i];
            int accent = fxAccents[i];

            EditDialogLineModalScreen.CyberpunkAnimatedFxButton fxBtn = new EditDialogLineModalScreen.CyberpunkAnimatedFxButton(
                    panelLeft + 16 + i * (fxBtnW + fxGapX), fxY, fxBtnW, fxBtnH,
                    previewText,
                    b -> insertDialogFormattingCode(code), accent);
            fxBtn.setTooltip(Tooltip.create(Component.literal(name)));
            this.addRenderableWidget(fxBtn);
        }

        formY += 66;

        // --- SECONDARY CONTROLS SECTION: 2-COLUMN BOTTOM GRID ---
        int col1X = panelLeft + 16;
        int col2X = panelLeft + 266;
        int colW = 238;

        int gridY = formY + 10;

        // Column 1: Voice Sound Preset & Custom Sound ID & Pitch Min/Max
        List<CyberpunkDropdown.DropdownEntry<String>> soundEntries = DialogSoundRegistry.getAvailableSoundEntries(dialogLine.getLetterSound());
        this.dialogLetterSoundDropdown = new CyberpunkDropdown<>(col1X, gridY, colW, 18, Component.literal("Voice Sound Preset"));
        this.dialogLetterSoundDropdown.setOptions(soundEntries);
        this.dialogLetterSoundDropdown.setMaxVisibleItems(5);
        this.dialogLetterSoundDropdown.selectByValue(dialogLine.getLetterSound());
        this.dialogLetterSoundDropdown.setOnSelect(sEntry -> {
            if (dialogLetterSoundBox != null) {
                dialogLetterSoundBox.setValue(sEntry.getValue());
            }
        });

        int c1Y = gridY + 32;
        this.dialogLetterSoundBox = new EditBox(this.font, col1X, c1Y, colW - 34, 18, Component.literal("Letter Sound ID"));
        this.dialogLetterSoundBox.setMaxLength(128);
        this.dialogLetterSoundBox.setValue(dialogLine.getLetterSound());
        this.dialogLetterSoundBox.setResponder(val -> {
            if (dialogLetterSoundDropdown != null) {
                dialogLetterSoundDropdown.selectByValue(val.trim());
            }
        });
        this.addRenderableWidget(this.dialogLetterSoundBox);

        CyberpunkButton testLetterSoundBtn = new CyberpunkButton(col1X + colW - 30, c1Y, 30, 18, Component.literal("🔊"), b -> playTestVoiceSound());
        testLetterSoundBtn.setTooltip(Tooltip.create(Component.literal("Preview voice typewriter blip sound")));
        this.addRenderableWidget(testLetterSoundBtn);

        c1Y += 32;
        this.dialogLetterPitchMinBox = new EditBox(this.font, col1X, c1Y, 112, 18, Component.literal("Min Pitch"));
        this.dialogLetterPitchMinBox.setMaxLength(6);
        this.dialogLetterPitchMinBox.setValue(String.valueOf(dialogLine.getLetterSoundPitchMin()));
        this.addRenderableWidget(this.dialogLetterPitchMinBox);

        this.dialogLetterPitchMaxBox = new EditBox(this.font, col1X + 126, c1Y, 112, 18, Component.literal("Max Pitch"));
        this.dialogLetterPitchMaxBox.setMaxLength(6);
        this.dialogLetterPitchMaxBox.setValue(String.valueOf(dialogLine.getLetterSoundPitchMax()));
        this.addRenderableWidget(this.dialogLetterPitchMaxBox);

        // Column 2: Char Speed & Line Delay & Line Sound ID
        int c2Y = gridY;
        this.dialogSpeedBox = new EditBox(this.font, col2X, c2Y, 112, 18, Component.literal("Char Speed"));
        this.dialogSpeedBox.setMaxLength(4);
        this.dialogSpeedBox.setValue(String.valueOf(dialogLine.getCharSpeedTicks()));
        this.addRenderableWidget(this.dialogSpeedBox);

        this.dialogDelayBox = new EditBox(this.font, col2X + 126, c2Y, 112, 18, Component.literal("Line Delay"));
        this.dialogDelayBox.setMaxLength(6);
        this.dialogDelayBox.setValue(String.valueOf(dialogLine.getDelayTicks()));
        this.addRenderableWidget(this.dialogDelayBox);

        c2Y += 58;
        this.dialogSoundBox = new EditBox(this.font, col2X, c2Y, colW - 34, 18, Component.literal("Line Sound ID"));
        this.dialogSoundBox.setMaxLength(128);
        this.dialogSoundBox.setValue(dialogLine.getSound());
        this.addRenderableWidget(this.dialogSoundBox);

        CyberpunkButton testLineSoundBtn = new CyberpunkButton(col2X + colW - 30, c2Y, 30, 18, Component.literal("🔊"), b -> playTestLineSound());
        testLineSoundBtn.setTooltip(Tooltip.create(Component.literal("Preview line start sound event")));
        this.addRenderableWidget(testLineSoundBtn);

        // Dropdowns added at end so their popup overlays render cleanly
        this.addRenderableWidget(this.dialogLetterSoundDropdown);

        // --- FOOTER ACTION BAR: TEST TYPING, CANCEL & SAVE DIALOG ---
        int footerY = panelTop + panelHeight - 26;

        if (onDelete != null) {
            CyberpunkButton deleteBtn = new CyberpunkButton(col1X, footerY, 85, 20, Component.literal("🗑 DELETE"), b -> {
                onDelete.run();
                this.onClose();
            }, RED_CANCEL, false, Component.literal("Delete this dialog action from timeline"));
            this.addRenderableWidget(deleteBtn);
        }

        CyberpunkButton testTypingBtn = new CyberpunkButton(col1X + (onDelete != null ? 92 : 0), footerY, 100, 20, Component.literal("▶ Test Typing"), b -> startTypingTest(), 0xFF00FFCC, false);
        this.addRenderableWidget(testTypingBtn);

        CyberpunkButton cancelBtn = new CyberpunkButton(panelLeft + panelWidth - 190, footerY, 80, 20, Component.literal("✕ Cancel"), b -> this.onClose(), 0xFF778899, false);
        this.addRenderableWidget(cancelBtn);

        CyberpunkButton saveBtn = new CyberpunkButton(panelLeft + panelWidth - 105, footerY, 95, 20, Component.literal("✓ Save Dialog"), b -> saveDialogEntry(), 0xFFFFD700, false);
        this.addRenderableWidget(saveBtn);
    }

    private void snapDialogToPlayhead() {
        if (parentScreen instanceof MusicSequenceScreen mss) {
            long ph = (long) Math.max(0, mss.getPlayheadMs());
            if (this.dialogTimestampBox != null) {
                this.dialogTimestampBox.setValue(String.valueOf(ph));
            }
        }
    }

    private void insertDialogFormattingCode(String code) {
        if (dialogTextBox != null && dialogTextBox.isFocused()) {
            dialogTextBox.insertText(code);
        } else if (dialogSpeakerBox != null && dialogSpeakerBox.isFocused()) {
            dialogSpeakerBox.insertText(code);
        } else if (dialogTextBox != null) {
            dialogTextBox.insertText(code);
        }
    }

    private void startTypingTest() {
        if (dialogTextBox == null) return;
        this.typingTotalChars = DialogFormatUtil.getVisibleCharCount(dialogTextBox.getValue());
        this.typingRevealedChars = 0;
        this.typingTickTimer = 0;
        this.lastTypingTickTime = System.currentTimeMillis();
        this.isTypingTestActive = true;
    }

    private void playTestVoiceSound() {
        if (dialogLetterSoundBox == null) return;
        String snd = dialogLetterSoundBox.getValue().trim();
        if (!snd.isEmpty()) {
            try {
                float minP = 0.8f;
                float maxP = 1.2f;
                try { minP = Float.parseFloat(dialogLetterPitchMinBox.getValue().trim()); } catch (Exception ignored) {}
                try { maxP = Float.parseFloat(dialogLetterPitchMaxBox.getValue().trim()); } catch (Exception ignored) {}
                float pitch = minP + (float) Math.random() * (Math.max(minP, maxP) - Math.min(minP, maxP));
                pitch = Math.max(0.1f, Math.min(2.0f, pitch));
                SoundEvent soundEvent = ModSounds.resolveSound(snd);
                if (soundEvent != null) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, pitch));
                }
            } catch (Exception ignored) {}
        }
    }

    private void playTestLineSound() {
        if (dialogSoundBox == null) return;
        String snd = dialogSoundBox.getValue().trim();
        if (!snd.isEmpty()) {
            try {
                SoundEvent soundEvent = ModSounds.resolveSound(snd);
                if (soundEvent != null) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1.0f));
                }
            } catch (Exception ignored) {}
        }
    }

    private void saveDialogEntry() {
        long ts = 0L;
        try {
            ts = Math.max(0L, Long.parseLong(this.dialogTimestampBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}
        entry.setTimestampMs(ts);
        entry.setActionType(MusicSequenceChannel.TYPE_DIALOG);
        if (channel != null) {
            entry.setChannelId(channel.getId());
        }

        if (dialogLine == null) {
            dialogLine = new DialogLine();
        }
        dialogLine.setSpeaker(dialogSpeakerBox.getValue().trim());
        dialogLine.setText(dialogTextBox.getValue());
        dialogLine.setWaitForInput(false);
        dialogLine.setUseCamera(false);

        int charSpeed = 1;
        try { charSpeed = Math.max(0, Integer.parseInt(dialogSpeedBox.getValue().trim())); } catch (Exception ignored) {}
        dialogLine.setCharSpeedTicks(charSpeed);

        int delay = 40;
        try { delay = Math.max(1, Integer.parseInt(dialogDelayBox.getValue().trim())); } catch (Exception ignored) {}
        dialogLine.setDelayTicks(delay);

        dialogLine.setSound(dialogSoundBox.getValue().trim());
        dialogLine.setLetterSound(dialogLetterSoundBox.getValue().trim());

        float minPitch = 0.8f;
        try { minPitch = Math.max(0.1f, Float.parseFloat(dialogLetterPitchMinBox.getValue().trim())); } catch (Exception ignored) {}
        dialogLine.setLetterSoundPitchMin(minPitch);

        float maxPitch = 1.2f;
        try { maxPitch = Math.max(0.1f, Float.parseFloat(dialogLetterPitchMaxBox.getValue().trim())); } catch (Exception ignored) {}
        dialogLine.setLetterSoundPitchMax(maxPitch);

        int visibleChars = DialogFormatUtil.getVisibleCharCount(dialogLine.getText());
        int animTicks = charSpeed * visibleChars;
        int totalTicks = animTicks + delay;
        int totalMs = totalTicks * 50;

        entry.setWindupMs(0);
        entry.setJumpMs(0);
        entry.setDurationMs(totalMs);
        entry.setRecoveryMs(0);
        entry.setSubAction("SINGLE_DIALOG");

        entry.setDialog(dialogLine);
        entry.setCommand(dialogLine.getText());
        String speaker = dialogLine.getSpeaker();
        entry.setDescription(speaker.isEmpty() ? dialogLine.getText() : speaker + ": " + dialogLine.getText());

        if (onSave != null) {
            onSave.accept(entry);
        }
        this.onClose();
    }

    private void saveCameraEntry() {
        long ts = 0L;
        try {
            ts = Math.max(0L, Long.parseLong(this.timestampBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}
        entry.setTimestampMs(ts);
        entry.setActionType(MusicSequenceChannel.TYPE_CAMERA);
        if (channel != null) {
            entry.setChannelId(channel.getId());
        }

        int duration = 3000;
        try {
            if (cameraDurationBox != null && !cameraDurationBox.getValue().trim().isEmpty()) {
                duration = Math.max(0, Integer.parseInt(cameraDurationBox.getValue().trim()));
            }
        } catch (Exception ignored) {}
        entry.setDurationMs(duration);
        entry.setWindupMs(0);
        entry.setJumpMs(0);
        entry.setRecoveryMs(0);

        entry.setSubAction(activeCameraMode);
        entry.setCameraMode(activeCameraMode);
        entry.setCameraInterpolate(cameraInterpolate);

        if ("CLEAR".equalsIgnoreCase(activeCameraMode)) {
            entry.setUseCamera(false);
            entry.setCommand("camera clear");
            if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                entry.setDescription(descriptionBox.getValue().trim());
            } else {
                entry.setDescription("Reset Camera");
            }
        } else if ("FOLLOW".equalsIgnoreCase(activeCameraMode)) {
            entry.setUseCamera(true);
            String tgt = cameraTargetBox != null ? cameraTargetBox.getValue().trim() : "@p";
            if (tgt.isEmpty()) tgt = "@p";
            entry.setCameraTarget(tgt);
            entry.setCameraHeightOffset(parseDouble(cameraHeightOffsetBox, 5.5));
            entry.setCameraPitch(parseFloat(cameraPitchBox, 45.0f));
            entry.setCameraFov(parseDouble(cameraFovBox, 70.0));
            entry.setCommand("camera follow " + tgt);
            if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                entry.setDescription(descriptionBox.getValue().trim());
            } else {
                entry.setDescription("Follow: " + tgt);
            }
        } else if ("OVER_THE_SHOULDER".equalsIgnoreCase(activeCameraMode)) {
            entry.setUseCamera(true);
            String tgt = cameraTargetBox != null ? cameraTargetBox.getValue().trim() : "@p";
            if (tgt.isEmpty()) tgt = "@p";
            entry.setCameraTarget(tgt);
            entry.setCameraBackDistance(parseDouble(cameraBackDistanceBox, 2.4));
            entry.setCameraShoulderOffset(parseDouble(cameraShoulderOffsetBox, 0.55));
            entry.setCameraHeightOffset(parseDouble(cameraHeightOffsetBox, 0.1));
            entry.setCameraPitch(parseFloat(cameraPitchBox, 15.0f));
            entry.setCameraFov(parseDouble(cameraFovBox, 70.0));
            entry.setCommand("camera ots " + tgt);
            if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                entry.setDescription(descriptionBox.getValue().trim());
            } else {
                entry.setDescription("OTS: " + tgt);
            }
        } else {
            // STATIC
            entry.setUseCamera(true);
            entry.setCameraX(parseDouble(cameraPosXBox, entry.getCameraX()));
            entry.setCameraY(parseDouble(cameraPosYBox, entry.getCameraY()));
            entry.setCameraZ(parseDouble(cameraPosZBox, entry.getCameraZ()));
            entry.setCameraYaw(parseFloat(cameraYawBox, entry.getCameraYaw()));
            entry.setCameraPitch(parseFloat(cameraPitchBox, entry.getCameraPitch()));
            entry.setCameraRoll(parseFloat(cameraRollBox, entry.getCameraRoll()));
            entry.setCameraFov(parseDouble(cameraFovBox, entry.getCameraFov()));
            entry.setCommand(String.format(Locale.US, "camera static %.1f %.1f %.1f %.0f %.0f %.0f",
                    entry.getCameraX(), entry.getCameraY(), entry.getCameraZ(),
                    entry.getCameraYaw(), entry.getCameraPitch(), entry.getCameraFov()));
            if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                entry.setDescription(descriptionBox.getValue().trim());
            } else {
                entry.setDescription(String.format(Locale.US, "Camera (%.1f, %.1f, %.1f)",
                        entry.getCameraX(), entry.getCameraY(), entry.getCameraZ()));
            }
        }

        if (onSave != null) {
            onSave.accept(entry);
        }
        this.onClose();
    }

    private void switchPuppetMode(PuppetSubAction newMode) {
        this.activePuppetMode = newMode;
        this.init();
    }

    private void updateCommandHint() {
        if (commandBox == null) return;
        String type = (typeDropdown != null && typeDropdown.getSelectedValue() != null) ? typeDropdown.getSelectedValue() : entry.getActionType();
        if (type == null) type = "COMMAND";
        switch (type.toUpperCase(Locale.ROOT)) {
            case "PUPPET" -> commandBox.setHint(Component.literal("Action ID (e.g. leap_slam) - Target directed to channel actor"));
            case "CHECKPOINT" -> commandBox.setHint(Component.literal("Checkpoint label / coordinates or command"));
            case "DIALOG" -> commandBox.setHint(Component.literal("Dialog file name (e.g. intro.json) or /dialog run <name>"));
            case "SCREEN_EFFECT", "SCREEN_EFFECTS", "OBJECTIVE" -> commandBox.setHint(Component.literal("Screen effect ID or parameters"));
            case "CAMERA" -> commandBox.setHint(Component.literal("Camera parameters or command"));
            default -> commandBox.setHint(Component.literal("Minecraft command to execute (e.g. /say Hello)"));
        }
    }

    private void adjustTimestamp(long deltaMs) {
        try {
            long current = Long.parseLong(this.timestampBox.getValue().trim());
            long updated = Math.max(0L, current + deltaMs);
            this.timestampBox.setValue(String.valueOf(updated));
        } catch (NumberFormatException ignored) {
            this.timestampBox.setValue("0");
        }
    }

    private void saveAndClose() {
        long ts = 0L;
        try {
            ts = Math.max(0L, Long.parseLong(this.timestampBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}
        entry.setTimestampMs(ts);

        if (isPuppet) {
            entry.setActionType(MusicSequenceChannel.TYPE_PUPPET);
            entry.setSubAction(activePuppetMode.name());
            if (channel != null) {
                entry.setChannelId(channel.getId());
            }

            String actorTag = (channel != null && !channel.getActorTag().isBlank()) ? channel.getActorTag() : (channel != null ? channel.getName() : "actor");
            String actorName = (channel != null) ? channel.getName() : "Puppet";
            String entityType = (channel != null && !channel.getEntityTypeId().isBlank()) ? channel.getEntityTypeId() : "fractured_utils:void_herald";
            if (entityType.equalsIgnoreCase("void_herald") || entityType.equalsIgnoreCase("fracturedutils:void_herald")) {
                entityType = "fractured_utils:void_herald";
            }

            if (activePuppetMode == PuppetSubAction.SPAWN) {
                entry.setWindupMs(0);
                entry.setJumpMs(0);
                entry.setDurationMs(0);
                entry.setRecoveryMs(0);

                String x = spawnXBox != null ? spawnXBox.getValue().trim() : "~";
                String y = spawnYBox != null ? spawnYBox.getValue().trim() : "~";
                String z = spawnZBox != null ? spawnZBox.getValue().trim() : "~";
                if (x.isEmpty()) x = "~";
                if (y.isEmpty()) y = "~";
                if (z.isEmpty()) z = "~";

                String cmd = String.format(Locale.ROOT, "summon %s %s %s %s {Tags:[\"%s\",\"puppet_actor\"],CustomName:'{\"text\":\"%s\"}',NoAI:%sb,PersistenceRequired:1b}",
                        entityType, x, y, z, actorTag, actorName, spawnDisableAi ? "1" : "0");
                entry.setCommand(cmd);

                if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                    entry.setDescription(descriptionBox.getValue().trim());
                } else {
                    entry.setDescription("Spawn " + actorName + " @ " + x + " " + y + " " + z);
                }
            } else if (activePuppetMode == PuppetSubAction.DESPAWN) {
                entry.setWindupMs(0);
                entry.setJumpMs(0);
                entry.setDurationMs(0);
                entry.setRecoveryMs(0);

                entry.setCommand(String.format(Locale.ROOT, "kill @e[tag=%s]", actorTag));
                if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                    entry.setDescription(descriptionBox.getValue().trim());
                } else {
                    entry.setDescription("Despawn " + actorName + " (#" + actorTag + ")");
                }
            } else if (activePuppetMode == PuppetSubAction.TOGGLE_AI) {
                entry.setWindupMs(0);
                entry.setJumpMs(0);
                entry.setDurationMs(0);
                entry.setRecoveryMs(0);

                if (isSuppressAi) {
                    entry.setCommand(String.format(Locale.ROOT, "puppet_suppress tag:%s ai:%b nav:%b tgt:%b look:%b actions:%b",
                            actorTag, suppressFullAi, suppressNav, suppressTgt, suppressLook, suppressActions));
                    if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                        entry.setDescription(descriptionBox.getValue().trim());
                    } else {
                        entry.setDescription("Suppress AI: #" + actorTag);
                    }
                } else {
                    entry.setCommand(String.format(Locale.ROOT, "puppet_restore tag:%s", actorTag));
                    if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                        entry.setDescription(descriptionBox.getValue().trim());
                    } else {
                        entry.setDescription("Restore AI: #" + actorTag);
                    }
                }
            } else if (activePuppetMode == PuppetSubAction.EXECUTE_ACTION) {
                entry.setWindupMs(windupMs);
                entry.setJumpMs(jumpMs);
                entry.setDurationMs(durationMs);
                entry.setRecoveryMs(recoveryMs);

                String actionId = (selectedActionId != null && !selectedActionId.isBlank()) ? selectedActionId : "fractured_utils:leap_slam";
                String tgt = (targetSelectorBox != null && !targetSelectorBox.getValue().trim().isEmpty()) ? targetSelectorBox.getValue().trim() : "@p";
                entry.setCommand(String.format(Locale.ROOT, "puppet_action tag:%s action:%s target:%s windup:%d jump:%d duration:%d recovery:%d",
                        actorTag, actionId, tgt, windupMs, jumpMs, durationMs, recoveryMs));

                if (descriptionBox != null && !descriptionBox.getValue().trim().isEmpty()) {
                    entry.setDescription(descriptionBox.getValue().trim());
                } else {
                    entry.setDescription("Action: " + actionId);
                }
            }
        } else {
            // Standard / Command entry
            if (typeDropdown != null && typeDropdown.getSelectedValue() != null) {
                entry.setActionType(typeDropdown.getSelectedValue());
            }
            if (channel != null) {
                entry.setChannelId(channel.getId());
            }
            String cmd = this.commandBox != null ? this.commandBox.getValue().trim() : "";
            entry.setCommand(cmd);

            String desc = this.descriptionBox != null ? this.descriptionBox.getValue().trim() : "";
            if (desc.isEmpty()) {
                if (cmd.startsWith("playsound") || cmd.startsWith("/playsound")) {
                    desc = "Sound Effect";
                } else if (cmd.startsWith("particle") || cmd.startsWith("/particle")) {
                    desc = "Particle FX";
                } else if (cmd.startsWith("title") || cmd.startsWith("/title")) {
                    desc = "Screen Title";
                } else if (cmd.startsWith("summon") || cmd.startsWith("/summon")) {
                    desc = "Summon Entity";
                } else if (cmd.startsWith("effect") || cmd.startsWith("/effect")) {
                    desc = "Potion Effect";
                } else if (!cmd.isEmpty()) {
                    int spaceIdx = cmd.indexOf(' ');
                    desc = (spaceIdx > 0 ? cmd.substring(0, spaceIdx) : cmd).replace("/", "").toUpperCase(Locale.ROOT);
                } else {
                    desc = entry.getActionType() + " Event";
                }
            }
            entry.setDescription(desc);
        }

        if (onSave != null) {
            onSave.accept(entry);
        }
        this.onClose();
    }

    private boolean isCommandAction() {
        if (isPuppet) return false;
        String type = (typeDropdown != null && typeDropdown.getSelectedValue() != null)
                ? typeDropdown.getSelectedValue()
                : entry.getActionType();
        return type == null || type.isBlank() || "COMMAND".equalsIgnoreCase(type);
    }

    private void updateCommandValidation() {
        if (commandBox == null) return;
        String val = commandBox.getValue();
        this.isCommandInvalid = !CommandAction.isValidCommand(this.minecraft, val);
        this.commandBox.setTextColor(this.isCommandInvalid ? 0xFFFF5555 : 0xFFFFFFFF);
    }

    private void insertToken(String tok) {
        if (commandBox == null) return;
        String current = commandBox.getValue();
        if (!current.isEmpty() && !current.endsWith(" ")) {
            commandBox.setValue(current + " " + tok);
        } else {
            commandBox.setValue(current + tok);
        }
        commandBox.moveCursorToEnd();
        if (this.commandSuggestions != null) {
            this.commandSuggestions.updateCommandInfo();
        }
        updateCommandValidation();
    }

    private void applyPreset(String cmd, String defaultDesc) {
        if (commandBox == null) return;
        commandBox.setValue(cmd);
        commandBox.moveCursorToEnd();
        if (descriptionBox != null && (descriptionBox.getValue().trim().isEmpty() || descriptionBox.getValue().equals(entry.getDescription()))) {
            descriptionBox.setValue(defaultDesc);
        }
        if (this.commandSuggestions != null) {
            this.commandSuggestions.updateCommandInfo();
        }
        updateCommandValidation();
    }

    private String formatTimestamp(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = ms % 1000;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }

    private void snapToPlayhead() {
        if (parentScreen instanceof MusicSequenceScreen mss) {
            long ph = (long) Math.max(0, mss.getPlayheadMs());
            if (this.timestampBox != null) {
                this.timestampBox.setValue(String.valueOf(ph));
            }
        }
    }

    private void drawInputFrame(GuiGraphics graphics, int x, int y, int w, int h, boolean focused, boolean invalid) {
        int fill = 0xEE060F17;
        int border = invalid ? 0xFFFF3355 : (focused ? 0xFFFFFFFF : BORDER_MUTED);
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y + 1, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
    }

    private void drawPillBadge(GuiGraphics graphics, int x, int y, String text, int borderColor, int textColor) {
        int tw = this.font.width(text);
        int bw = tw + 8;
        int bh = 11;
        graphics.fill(x, y, x + bw, y + bh, 0xEE06121C);
        graphics.fill(x, y, x + bw, y + 1, borderColor);
        graphics.fill(x, y + bh - 1, x + bw, y + bh, borderColor);
        graphics.fill(x, y + 1, x + 1, y + bh, borderColor);
        graphics.fill(x + bw - 1, y, x + bw, y + bh, borderColor);
        graphics.drawString(this.font, text, x + 4, y + 2, textColor, false);
    }

    private boolean isDropdownOpen() {
        return (puppetActionDropdown != null && puppetActionDropdown.isOpen())
                || (typeDropdown != null && typeDropdown.isOpen())
                || (dialogLetterSoundDropdown != null && dialogLetterSoundDropdown.isOpen());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isDropdownOpen() && this.commandSuggestions != null && this.commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }

        if (dialogLetterSoundDropdown != null && dialogLetterSoundDropdown.isOpen()) {
            if (dialogLetterSoundDropdown.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (puppetActionDropdown != null && puppetActionDropdown.isOpen()) {
            if (puppetActionDropdown.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (typeDropdown != null && typeDropdown.isOpen()) {
            if (typeDropdown.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (this.commandSuggestions != null) {
            if (this.commandSuggestions.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }
        if (dialogLetterSoundDropdown != null && dialogLetterSoundDropdown.isOpen()) {
            if (dialogLetterSoundDropdown.mouseScrolled(mouseX, mouseY, amount)) {
                return true;
            }
        }
        if (this.commandSuggestions != null && this.commandSuggestions.mouseScrolled(amount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
            dragX /= scale;
            dragY /= scale;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        double scale = getLayoutScale();
        guiGraphics.pose().pushPose();
        int scaledMouseX = mouseX;
        int scaledMouseY = mouseY;
        if (scale < 1.0) {
            guiGraphics.pose().scale((float) scale, (float) scale, 1.0f);
            scaledMouseX = (int) (mouseX / scale);
            scaledMouseY = (int) (mouseY / scale);
        }

        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        guiGraphics.fill(0, 0, effWidth, effHeight, 0xBB000000);

        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        int panelLeft = (effWidth - panelWidth) / 2;
        int panelTop = (effHeight - panelHeight) / 2;

        int borderClr = isDialog ? 0xFFFFD700 : (isCamera ? MusicSequenceChannel.COLOR_CAMERA : (isScreenEffect ? MusicSequenceChannel.COLOR_SCREEN_EFFECT : CYAN_MAIN));

        // Modal Background & Borders
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, CYAN_BG);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 1, borderClr);
        guiGraphics.fill(panelLeft, panelTop + panelHeight - 1, panelLeft + panelWidth, panelTop + panelHeight, borderClr);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 1, panelTop + panelHeight, borderClr);
        guiGraphics.fill(panelLeft + panelWidth - 1, panelTop, panelLeft + panelWidth, panelTop + panelHeight, borderClr);

        if (isDialog) {
            renderDialogScreen(guiGraphics, panelLeft, panelTop, panelWidth, panelHeight, scaledMouseX, scaledMouseY, partialTick);
        } else if (isCamera) {
            renderCameraScreen(guiGraphics, panelLeft, panelTop, panelWidth, panelHeight, scaledMouseX, scaledMouseY, partialTick);
        } else if (isScreenEffect) {
            renderScreenEffectScreen(guiGraphics, panelLeft, panelTop, panelWidth, panelHeight, scaledMouseX, scaledMouseY, partialTick);
        } else if (isPuppet) {
            renderPuppetScreen(guiGraphics, panelLeft, panelTop, panelWidth, panelHeight, scaledMouseX, scaledMouseY, partialTick);
        } else {
            renderStandardScreen(guiGraphics, panelLeft, panelTop, panelWidth, panelHeight, scaledMouseX, scaledMouseY, partialTick);
        }

        guiGraphics.pose().popPose();
    }

    private void renderScreenEffectScreen(GuiGraphics guiGraphics, int panelLeft, int panelTop, int panelWidth, int panelHeight,
                                          int mouseX, int mouseY, float partialTick) {
        int contentX = panelLeft + 14;
        int contentW = panelWidth - 28;

        // 1. Header Bar
        guiGraphics.fill(panelLeft + 1, panelTop + 1, panelLeft + panelWidth - 1, panelTop + 24, HEADER_BG);
        guiGraphics.fill(panelLeft + 1, panelTop + 23, panelLeft + panelWidth - 1, panelTop + 24, MusicSequenceChannel.COLOR_SCREEN_EFFECT);
        guiGraphics.drawString(this.font, "⚡ SCREEN EFFECT STUDIO", contentX, panelTop + 8, MusicSequenceChannel.COLOR_SCREEN_EFFECT, false);

        String modeBadge = "FX: " + activeScreenEffectMode;
        int badgeW = this.font.width(modeBadge);
        drawPillBadge(guiGraphics, panelLeft + panelWidth - 14 - badgeW - 8, panelTop + 6, modeBadge, MusicSequenceChannel.COLOR_SCREEN_EFFECT, MusicSequenceChannel.COLOR_SCREEN_EFFECT);

        // 2. Mode Parameters Card (Height = 68)
        int card1Y = panelTop + 54;
        int card1H = 68;
        drawInputFrame(guiGraphics, contentX, card1Y, contentW, card1H, false, false);

        if ("INVERT_COLORS".equalsIgnoreCase(activeScreenEffectMode)) {
            guiGraphics.drawString(this.font, "PULSE HZ:", contentX + 175, card1Y + 12, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Inverts world & screen luminance. Supports solid invert or sinusoidal pulse.", contentX + 10, card1Y + 42, TEXT_MUTED, false);
        } else if ("STROBE".equalsIgnoreCase(activeScreenEffectMode)) {
            guiGraphics.drawString(this.font, "SPEED:", contentX + 10, card1Y + 12, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "HEX:", contentX + 10, card1Y + 40, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "OPACITY:", contentX + 116, card1Y + 40, TEXT_LABEL, false);
        } else if ("HUE_SHIFT".equalsIgnoreCase(activeScreenEffectMode)) {
            if (screenEffectContinuous) {
                guiGraphics.drawString(this.font, "SPEED (rot/s):", contentX + 160, card1Y + 12, TEXT_LABEL, false);
            } else {
                guiGraphics.drawString(this.font, "ANGLE (°):", contentX + 164, card1Y + 12, TEXT_LABEL, false);
            }
            guiGraphics.drawString(this.font, "INTENSITY:", contentX + 10, card1Y + 40, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Full RGB spectrum shader rotation without solid color overlay.", contentX + 128, card1Y + 40, TEXT_MUTED, false);
        } else if ("IMPACT_FRAME".equalsIgnoreCase(activeScreenEffectMode)) {
            guiGraphics.drawString(this.font, "CUT FPS:", contentX + 10, card1Y + 40, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "ACCENT:", contentX + 112, card1Y + 40, TEXT_LABEL, false);
        } else {
            // SCREEN_SHAKE
            guiGraphics.drawString(this.font, "INTENSITY:", contentX + 6, card1Y + 12, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "FREQ:", contentX + 120, card1Y + 12, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Viewport camera shake with rotational pitch, roll & yaw jitter.", contentX + 10, card1Y + 42, TEXT_MUTED, false);
        }

        // 3. Timing Card
        int card2Y = card1Y + 74;
        int card2H = 32;
        drawInputFrame(guiGraphics, contentX, card2Y, contentW, card2H, false, false);
        guiGraphics.drawString(this.font, "START AT:", contentX + 6, card2Y + 10, TEXT_LABEL, false);
        guiGraphics.drawString(this.font, "DURATION:", contentX + 135, card2Y + 10, TEXT_LABEL, false);

        // 4. Description label
        int descY = card2Y + 38;
        guiGraphics.drawString(this.font, "TIMELINE LABEL / NOTE:", contentX, descY + 2, TEXT_LABEL, false);

        // 5. Render All Widgets
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderCameraScreen(GuiGraphics guiGraphics, int panelLeft, int panelTop, int panelWidth, int panelHeight,
                                    int mouseX, int mouseY, float partialTick) {
        int contentX = panelLeft + 14;
        int contentW = panelWidth - 28;

        // 1. Header Bar
        guiGraphics.fill(panelLeft + 1, panelTop + 1, panelLeft + panelWidth - 1, panelTop + 24, HEADER_BG);
        guiGraphics.fill(panelLeft + 1, panelTop + 23, panelLeft + panelWidth - 1, panelTop + 24, MusicSequenceChannel.COLOR_CAMERA);
        guiGraphics.drawString(this.font, "🎥 CAMERA CHANNEL STUDIO", contentX, panelTop + 8, MusicSequenceChannel.COLOR_CAMERA, false);

        String modeBadge = "MODE: " + activeCameraMode;
        int badgeW = this.font.width(modeBadge);
        drawPillBadge(guiGraphics, panelLeft + panelWidth - 14 - badgeW - 8, panelTop + 6, modeBadge, MusicSequenceChannel.COLOR_CAMERA, MusicSequenceChannel.COLOR_CAMERA);

        // 2. Mode-Specific Content Card (Height = 58)
        int card1Y = panelTop + 56;
        int card1H = 58;
        drawInputFrame(guiGraphics, contentX, card1Y, contentW, card1H, false, false);

        if ("STATIC".equalsIgnoreCase(activeCameraMode)) {
            int y1 = card1Y + 5;
            guiGraphics.drawString(this.font, "X:", contentX + 6, y1 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Y:", contentX + 70, y1 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Z:", contentX + 134, y1 + 5, TEXT_LABEL, false);

            int y2 = card1Y + 31;
            guiGraphics.drawString(this.font, "Yaw:", contentX + 6, y2 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Pitch:", contentX + 76, y2 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Roll:", contentX + 154, y2 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "FOV:", contentX + 228, y2 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Roll 0° • FOV 70° (Normal)", contentX + 298, y2 + 5, TEXT_MUTED, false);
        } else if ("FOLLOW".equalsIgnoreCase(activeCameraMode)) {
            int y1 = card1Y + 5;
            guiGraphics.drawString(this.font, "Target:", contentX + 6, y1 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Height:", contentX + 210, y1 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "FOV:", contentX + 310, y1 + 5, TEXT_LABEL, false);

            int y2 = card1Y + 31;
            guiGraphics.drawString(this.font, "Pitch:", contentX + 6, y2 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Tracks player/entity from above (Height offset: 5.5)", contentX + 106, y2 + 5, TEXT_MUTED, false);
        } else if ("OVER_THE_SHOULDER".equalsIgnoreCase(activeCameraMode)) {
            int y1 = card1Y + 5;
            guiGraphics.drawString(this.font, "Target:", contentX + 6, y1 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Back:", contentX + 170, y1 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Side:", contentX + 256, y1 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "FOV:", contentX + 340, y1 + 5, TEXT_LABEL, false);

            int y2 = card1Y + 31;
            guiGraphics.drawString(this.font, "Height:", contentX + 6, y2 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Pitch:", contentX + 104, y2 + 5, TEXT_LABEL, false);
            guiGraphics.drawString(this.font, "Cinematic 3rd-person shoulder tracking", contentX + 196, y2 + 5, TEXT_MUTED, false);
        } else if ("CLEAR".equalsIgnoreCase(activeCameraMode)) {
            guiGraphics.drawCenteredString(this.font, "🔄 CLEAR CAMERA OVERRIDE", contentX + (contentW / 2), card1Y + 14, MusicSequenceChannel.COLOR_CAMERA);
            guiGraphics.drawCenteredString(this.font, "Restores first-person player controls and camera perspective at this timestamp.", contentX + (contentW / 2), card1Y + 32, 0xFFAABBCC);
        }

        // 3. Timing & Interpolation Section Card (Height = 50)
        int card2Y = panelTop + 120;
        int card2H = 50;
        drawInputFrame(guiGraphics, contentX, card2Y, contentW, card2H, false, false);

        int ty1 = card2Y + 5;
        guiGraphics.drawString(this.font, "Time (ms):", contentX + 6, ty1 + 5, TEXT_LABEL, false);
        guiGraphics.drawString(this.font, "Duration:", contentX + 138, ty1 + 5, TEXT_LABEL, false);

        long curTs = entry.getTimestampMs();
        if (this.timestampBox != null) {
            try { curTs = Math.max(0L, Long.parseLong(this.timestampBox.getValue().trim())); } catch (Exception ignored) {}
        }
        long durMs = 3000L;
        if (this.cameraDurationBox != null) {
            try { durMs = Math.max(50L, Long.parseLong(this.cameraDurationBox.getValue().trim())); } catch (Exception ignored) {}
        }

        int ty2 = card2Y + 31;
        String timingStr = String.format(Locale.ROOT, "Start: %s  |  Duration: %dms  |  End: %s",
                formatTimestamp(curTs), durMs, formatTimestamp(curTs + durMs));
        guiGraphics.drawString(this.font, timingStr, contentX + 8, ty2 + 4, 0xFF00FFCC, false);

        // 4. Description Label
        int descLabelY = panelTop + 176;
        guiGraphics.drawString(this.font, "Timeline Label / Note (Optional):", contentX, descLabelY, TEXT_LABEL, false);

        // 5. Render All Widgets, Buttons, and Inputs
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderPuppetScreen(GuiGraphics guiGraphics, int panelLeft, int panelTop, int panelWidth, int panelHeight,
                                    int mouseX, int mouseY, float partialTick) {
        // 1. Header & Actor Info Banner
        guiGraphics.drawString(this.font, "PUPPET ACTOR ACTION EDITOR", panelLeft + 16, panelTop + 10, CYAN_MAIN, false);

        String actorTag = (channel != null && !channel.getActorTag().isBlank()) ? channel.getActorTag() : (channel != null ? channel.getName() : "actor");
        String actorName = (channel != null) ? channel.getName() : "Puppet Actor";
        String entityType = (channel != null && !channel.getEntityTypeId().isBlank()) ? channel.getEntityTypeId() : "fracturedutils:void_herald";

        String actorBanner = "🎭 " + actorName + "  |  #" + actorTag + "  |  " + entityType;
        if (this.font.width(actorBanner) > panelWidth - 40) {
            actorBanner = this.font.plainSubstrByWidth(actorBanner, panelWidth - 46) + "..";
        }
        guiGraphics.drawString(this.font, actorBanner, panelLeft + 16, panelTop + 24, 0xFFAA55FF, false);

        int contentX = panelLeft + 16;
        int contentW = panelWidth - 32;

        // 2. Mode Area Header & Labels
        if (activePuppetMode == PuppetSubAction.SPAWN) {
            int spawnY = panelTop + 66;
            guiGraphics.drawString(this.font, "X:", contentX + 4, spawnY + 18, 0xFFAABBCC, false);
            guiGraphics.drawString(this.font, "Y:", contentX + 16 + 56 + 6, spawnY + 18, 0xFFAABBCC, false);
            guiGraphics.drawString(this.font, "Z:", contentX + 16 + (56 + 18) * 2 - 12, spawnY + 18, 0xFFAABBCC, false);
        } else if (activePuppetMode == PuppetSubAction.DESPAWN) {
            int despawnY = panelTop + 76;
            guiGraphics.fill(contentX, despawnY, contentX + contentW, despawnY + 48, 0x33FF3355);
            guiGraphics.fill(contentX, despawnY, contentX + contentW, despawnY + 1, 0x88FF3355);
            guiGraphics.fill(contentX, despawnY + 47, contentX + contentW, despawnY + 48, 0x88FF3355);
            guiGraphics.drawCenteredString(this.font, "✕ DESPAWN PUPPET ACTOR", contentX + (contentW / 2), despawnY + 8, 0xFFFF3355);
            guiGraphics.drawCenteredString(this.font, "Target: @e[tag=" + actorTag + ",limit=1] (Command: /kill)", contentX + (contentW / 2), despawnY + 22, 0xFFAABBCC);
            guiGraphics.drawCenteredString(this.font, "Removes and discards the spawned actor entity from the world", contentX + (contentW / 2), despawnY + 34, 0xFF8899AA);
        } else if (activePuppetMode == PuppetSubAction.EXECUTE_ACTION) {
            int actionY = panelTop + 66;
            guiGraphics.drawString(this.font, "Puppet Action:", contentX, actionY, 0xFFAABBCC, false);
            guiGraphics.drawString(this.font, "Combat Target:", contentX + 270, actionY, 0xFFAABBCC, false);

            int timingY = actionY + 39;
            int colW = (contentW - 24) / 4;
            guiGraphics.drawString(this.font, "Indicator:", contentX, timingY + 4, COLOR_WINDUP, false);
            guiGraphics.drawString(this.font, "Jump:", contentX + colW + 8, timingY + 4, COLOR_JUMP, false);
            guiGraphics.drawString(this.font, "Slam:", contentX + (colW + 8) * 2, timingY + 4, COLOR_ACTIVE, false);
            guiGraphics.drawString(this.font, "Recovery:", contentX + (colW + 8) * 3, timingY + 4, COLOR_RECOVERY, false);

            // Live 5-Part Segmented Duration Preview Bar
            int barY = timingY + 24;
            int barH = 18;
            renderLiveDurationBar(guiGraphics, contentX, barY, contentW, barH);
        }

        // 3. Timestamp & Description Labels
        int tsLabelY = panelTop + (activePuppetMode == PuppetSubAction.EXECUTE_ACTION ? 210 : 184);
        guiGraphics.drawString(this.font, "Timestamp (ms):", contentX, tsLabelY, 0xFFAABBCC, false);
        guiGraphics.drawString(this.font, "Description / Note:", contentX, tsLabelY + 24, 0xFFAABBCC, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (puppetActionDropdown != null) {
            puppetActionDropdown.renderOverlay(guiGraphics, mouseX, mouseY);
        }
    }

    private void renderLiveDurationBar(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int totalMs = windupMs + jumpMs + durationMs + recoveryMs;

        // Container Background & Outline
        guiGraphics.fill(x, y, x + width, y + height, 0xEE060C12);
        guiGraphics.fill(x, y, x + width, y + 1, CARD_BORDER);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, CARD_BORDER);
        guiGraphics.fill(x, y, x + 1, y + height, CARD_BORDER);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, CARD_BORDER);

        if (totalMs <= 0) {
            guiGraphics.drawCenteredString(this.font, "Instant Action (0ms)", x + (width / 2), y + 5, 0xFF8899AA);
            return;
        }

        int usableW = width - 4;
        int windupW = (int) Math.round(((double) windupMs / totalMs) * usableW);
        int jumpW = (int) Math.round(((double) jumpMs / totalMs) * usableW);
        int durationW = (int) Math.round(((double) durationMs / totalMs) * usableW);
        int recoveryW = usableW - windupW - jumpW - durationW;

        int curX = x + 2;

        // 1. Indication / Ground Charge Bar
        if (windupW > 0) {
            guiGraphics.fill(curX, y + 2, curX + windupW, y + height - 2, COLOR_WINDUP);
            if (windupW >= 48) {
                String label = "INDICATOR (" + windupMs + "ms)";
                guiGraphics.drawCenteredString(this.font, label, curX + (windupW / 2), y + 5, 0xFF000000);
            } else if (windupW >= 24) {
                guiGraphics.drawCenteredString(this.font, windupMs + "ms", curX + (windupW / 2), y + 5, 0xFF000000);
            }
            curX += windupW;
        }

        // 2. Jump Bar (Airborne Ascent & Descent)
        if (jumpW > 0) {
            guiGraphics.fill(curX, y + 2, curX + jumpW, y + height - 2, COLOR_JUMP);
            if (jumpW >= 40) {
                String label = "JUMP (" + jumpMs + "ms)";
                guiGraphics.drawCenteredString(this.font, label, curX + (jumpW / 2), y + 5, 0xFFFFFFFF);
            } else if (jumpW >= 20) {
                guiGraphics.drawCenteredString(this.font, jumpMs + "ms", curX + (jumpW / 2), y + 5, 0xFFFFFFFF);
            }
            curX += jumpW;
        }

        // 3. Execution Point (Touchdown Slam Impact)
        int execX = curX;

        // 4. Active Slam Attack Duration Bar
        if (durationW > 0) {
            guiGraphics.fill(curX, y + 2, curX + durationW, y + height - 2, COLOR_ACTIVE);
            if (durationW >= 44) {
                String label = "ATTACK (" + durationMs + "ms)";
                guiGraphics.drawCenteredString(this.font, label, curX + (durationW / 2), y + 5, 0xFFFFFFFF);
            } else if (durationW >= 20) {
                guiGraphics.drawCenteredString(this.font, durationMs + "ms", curX + (durationW / 2), y + 5, 0xFFFFFFFF);
            }
            curX += durationW;
        }

        int impactX = curX;
        if (durationW > 0 && recoveryW > 0) {
            guiGraphics.fill(impactX - 1, y + 1, impactX + 1, y + height - 1, 0xFFFF3355);
        }

        // 5. Recovery Bar (Stun / Recovery state)
        if (recoveryW > 0) {
            guiGraphics.fill(curX, y + 2, curX + recoveryW, y + height - 2, COLOR_RECOVERY);
            if (recoveryW >= 48) {
                String label = "RECOVERY (" + recoveryMs + "ms)";
                guiGraphics.drawCenteredString(this.font, label, curX + (recoveryW / 2), y + 5, 0xFF000000);
            } else if (recoveryW >= 24) {
                guiGraphics.drawCenteredString(this.font, recoveryMs + "ms", curX + (recoveryW / 2), y + 5, 0xFF000000);
            }
        }

        // 6. Attack Execution Square (Touchdown ground impact)
        int centerY = y + (height / 2);
        guiGraphics.fill(execX - 1, y - 2, execX + 1, y + height + 2, 0xFFFFFFFF);
        guiGraphics.fill(execX - 4, centerY - 4, execX + 4, centerY + 4, 0xFF000000);
        guiGraphics.fill(execX - 3, centerY - 3, execX + 3, centerY + 3, 0xFFFF0055);
        guiGraphics.fill(execX - 1, centerY - 1, execX + 1, centerY + 1, 0xFFFFFFFF);

        // Total & Timing badge text
        long startMs = entry.getTimestampMs();
        if (this.timestampBox != null) {
            try { startMs = Math.max(0L, Long.parseLong(this.timestampBox.getValue().trim())); } catch (Exception ignored) {}
        }
        long slamMs = startMs + windupMs + jumpMs;
        String timingDetail = String.format(Locale.ROOT, "Start: %dms  |  💥 SLAM: %dms  |  Total: %dms", startMs, slamMs, totalMs);
        int textW = this.font.width(timingDetail);
        guiGraphics.fill(x, y - 12, x + textW + 8, y - 1, 0xEE060C12);
        guiGraphics.drawString(this.font, timingDetail, x + 4, y - 10, CYAN_MAIN, false);
    }

    private void renderStandardScreen(GuiGraphics guiGraphics, int panelLeft, int panelTop, int panelWidth, int panelHeight,
                                      int mouseX, int mouseY, float partialTick) {
        int contentX = panelLeft + 16;
        int contentW = panelWidth - 32;

        // 1. Header Bar
        guiGraphics.fill(panelLeft + 1, panelTop + 1, panelLeft + panelWidth - 1, panelTop + 24, HEADER_BG);
        guiGraphics.fill(panelLeft + 1, panelTop + 23, panelLeft + panelWidth - 1, panelTop + 24, BORDER_CYAN);

        boolean isCmd = isCommandAction();
        String title = isCmd ? "COMMAND ACTION" : "TIMELINE ACTION";
        guiGraphics.drawString(this.font, title, contentX, panelTop + 8, CYAN_MAIN, false);

        // Right side of header: Channel Tag Badge or Console category
        if (channel != null) {
            String chTag = "TRACK: " + channel.getName().toUpperCase(Locale.ROOT);
            int chTagW = this.font.width(chTag);
            drawPillBadge(guiGraphics, panelLeft + panelWidth - 16 - chTagW - 8, panelTop + 6, chTag, channel.getColor(), channel.getColor());
        } else {
            String catBadge = isCmd ? "SYS::CONSOLE" : ("SYS::" + (entry.getActionType() != null ? entry.getActionType().toUpperCase(Locale.ROOT) : "ACTION"));
            int cbW = this.font.width(catBadge);
            guiGraphics.drawString(this.font, catBadge, panelLeft + panelWidth - 16 - cbW, panelTop + 8, TEXT_MUTED, false);
        }

        // Row 1 Labels: "Type:" and "Time (ms):"
        int row1Y = panelTop + 30;
        guiGraphics.drawString(this.font, "Type:", contentX, row1Y + 5, TEXT_LABEL, false);
        guiGraphics.drawString(this.font, "Time (ms):", contentX + 145, row1Y + 5, TEXT_LABEL, false);

        long curTs = 0L;
        if (this.timestampBox != null) {
            try { curTs = Math.max(0L, Long.parseLong(this.timestampBox.getValue().trim())); } catch (Exception ignored) {}
        }
        guiGraphics.drawString(this.font, "(" + formatTimestamp(curTs) + ")", contentX + 284, row1Y + 5, TEXT_MUTED, false);

        // Row 2: Command Label & Validation Status
        int cmdY = panelTop + 56;
        guiGraphics.drawString(this.font, isCmd ? "Console Command:" : "Action Payload:", contentX, cmdY, CYAN_BRIGHT, false);

        if (isCmd) {
            String statusText = isCommandInvalid ? "⚠ UNKNOWN COMMAND" : "• SYNTAX VALID";
            int statusColor = isCommandInvalid ? RED_CANCEL : GREEN_VALID;
            int stW = this.font.width(statusText);
            drawPillBadge(guiGraphics, contentX + contentW - stW - 8, cmdY - 2, statusText, statusColor, statusColor);

            // Substitutions helper note directly under commandBox
            guiGraphics.drawString(this.font, "Supports Minecraft commands & selectors. Variables: %player%, %uuid%", contentX, panelTop + 92, TEXT_MUTED, false);
        }

        // Row 3: Description Label
        int descY = panelTop + 108;
        guiGraphics.drawString(this.font, "Timeline Label (Optional):", contentX, descY, TEXT_LABEL, false);

        // Operator permissions note
        guiGraphics.drawString(this.font, "ⓘ Runs as server console with operator permissions.", contentX, panelTop + 148, TEXT_MUTED, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (this.commandSuggestions != null) {
            this.commandSuggestions.render(guiGraphics, mouseX, mouseY);
        }
        if (typeDropdown != null) {
            typeDropdown.renderOverlay(guiGraphics, mouseX, mouseY);
        }
    }

    private void renderDialogScreen(GuiGraphics guiGraphics, int panelLeft, int panelTop, int panelWidth, int panelHeight,
                                    int mouseX, int mouseY, float partialTick) {
        // Update real-time typing animation test
        if (isTypingTestActive) {
            long now = System.currentTimeMillis();
            if (now - lastTypingTickTime >= 50) {
                lastTypingTickTime = now;
                int speed = 1;
                if (dialogSpeedBox != null) {
                    try { speed = Math.max(0, Integer.parseInt(dialogSpeedBox.getValue().trim())); } catch (Exception ignored) {}
                }

                if (speed == 0) {
                    typingRevealedChars = typingTotalChars;
                    isTypingTestActive = false;
                } else {
                    typingTickTimer++;
                    if (typingTickTimer >= speed) {
                        typingTickTimer = 0;
                        if (typingRevealedChars < typingTotalChars) {
                            typingRevealedChars++;
                            playTestVoiceSound();
                        } else {
                            isTypingTestActive = false;
                        }
                    }
                }
            }
        }

        // Header Title
        if (typeDropdown != null) {
            guiGraphics.drawString(this.font, Component.literal("DIALOG ACTION STUDIO").withStyle(ChatFormatting.BOLD), panelLeft + 118, panelTop + 11, 0xFFFFD700);
        } else {
            String chTitle = (channel != null && !channel.getName().isBlank()) ? ("💬 DIALOG STUDIO: " + channel.getName().toUpperCase(Locale.ROOT)) : "💬 DIALOG ACTION STUDIO EDITOR";
            guiGraphics.drawString(this.font, Component.literal(chTitle).withStyle(ChatFormatting.BOLD), panelLeft + 16, panelTop + 11, 0xFFFFD700);
        }

        int tsX = panelLeft + panelWidth - 16 - 130;
        guiGraphics.drawString(this.font, Component.literal("Time:"), tsX - 32, panelTop + 11, 0xFFAABBCC);

        // --- TOP SECTION: 1:1 EXACT LIVE RPG DIALOG HUD PREVIEW ---
        int boxW = 330;
        int boxH = 58;
        int boxX = panelLeft + (panelWidth - boxW) / 2;
        int boxY = panelTop + 34;

        int alphaBits = 0xF5000000;

        // Dark RPG Frame Background Fill
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, alphaBits | 0x05090C);

        // Double Cyberpunk Border (Gold for Dialog)
        int borderColor = alphaBits | 0xFFD700;
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 1, borderColor);
        guiGraphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, borderColor);
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, borderColor);
        guiGraphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, borderColor);

        // Inner Border
        guiGraphics.fill(boxX + 2, boxY + 2, boxX + boxW - 2, boxY + 3, alphaBits | 0x44FFD700);
        guiGraphics.fill(boxX + 2, boxY + boxH - 3, boxX + boxW - 2, boxY + boxH - 2, alphaBits | 0x44FFD700);
        guiGraphics.fill(boxX + 2, boxY + 2, boxX + 3, boxY + boxH - 2, alphaBits | 0x44FFD700);
        guiGraphics.fill(boxX + boxW - 3, boxY + 2, boxX + boxW - 2, boxY + boxH - 2, alphaBits | 0x44FFD700);

        // Corner Accent Notches
        guiGraphics.fill(boxX + 3, boxY + 3, boxX + 8, boxY + 5, borderColor);
        guiGraphics.fill(boxX + 3, boxY + 3, boxX + 5, boxY + 8, borderColor);
        guiGraphics.fill(boxX + boxW - 8, boxY + 3, boxX + boxW - 3, boxY + 5, borderColor);
        guiGraphics.fill(boxX + boxW - 5, boxY + 3, boxX + boxW - 3, boxY + 8, borderColor);
        guiGraphics.fill(boxX + 3, boxY + boxH - 5, boxX + 8, boxY + boxH - 3, borderColor);
        guiGraphics.fill(boxX + 3, boxY + boxH - 8, boxX + 5, boxY + boxH - 3, borderColor);
        guiGraphics.fill(boxX + boxW - 8, boxY + boxH - 5, boxX + boxW - 3, boxY + boxH - 3, borderColor);
        guiGraphics.fill(boxX + boxW - 5, boxY + boxH - 8, boxX + boxW - 3, boxY + boxH - 3, borderColor);

        int contentX = boxX + 16;
        int currentTextY = boxY + 8;
        int maxTextWidth = boxW - 32;

        // Speaker Badge with Custom Animated Effect Rendering
        String speakerVal = dialogSpeakerBox != null ? dialogSpeakerBox.getValue() : "";
        if (speakerVal != null && !speakerVal.trim().isEmpty()) {
            Component speakerComp = DialogFormatUtil.formatText(speakerVal);
            int speakerWidth = this.font.width(speakerComp);

            int badgeX = boxX + 12;
            int badgeY = boxY - 12;
            int badgeW = speakerWidth + 12;
            int badgeH = 14;

            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, alphaBits | 0x1A1408);
            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, borderColor);
            guiGraphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, borderColor);
            guiGraphics.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, borderColor);

            DialogFormatUtil.renderAnimatedText(guiGraphics, this.font, speakerVal, badgeX + 6, badgeY + 3, badgeW, 0xFFFFFFFF);
            currentTextY += 2;
        }

        // Formatted Wrapped Dialog Text Lines with Custom Animated Effects
        String rawText = dialogTextBox != null ? dialogTextBox.getValue() : "";
        String displayText = rawText;
        if (isTypingTestActive) {
            displayText = DialogFormatUtil.getRevealedText(rawText, typingRevealedChars);
        }

        DialogFormatUtil.renderAnimatedText(guiGraphics, this.font, displayText, contentX, currentTextY, maxTextWidth, 0xFFFFFFFF);

        // State Badge in bottom-right corner of preview
        int dTicks = 40;
        if (dialogDelayBox != null) {
            try { dTicks = Math.max(1, Integer.parseInt(dialogDelayBox.getValue().trim())); } catch (Exception ignored) {}
        }
        String durPreview = String.format(Locale.ROOT, "⏱ %dt (%.1fs)", dTicks, dTicks * 0.05f);
        guiGraphics.drawString(this.font, Component.literal(durPreview), boxX + boxW - this.font.width(durPreview) - 8, boxY + boxH - 14, 0xFFFFD700, false);

        // Form labels
        int formY = panelTop + 96;
        guiGraphics.drawString(this.font, Component.literal("Speaker Tag (supports color & custom FX):"), panelLeft + 16, formY, 0xAAAAAA);
        formY += 32;

        guiGraphics.drawString(this.font, Component.literal("Dialog Text (supports color & custom FX):"), panelLeft + 16, formY, 0xAAAAAA);

        // Overflow & char count
        Component textComp = DialogFormatUtil.formatText(rawText);
        List<FormattedCharSequence> rpgLines = this.font.split(textComp, 298);
        int visibleChars = DialogFormatUtil.getVisibleCharCount(rawText);

        boolean isOverflow = rpgLines.size() > 4;
        String countStr;
        int countColor;
        if (isOverflow) {
            countStr = "⚠ OVERFLOW: " + visibleChars + " chars (" + rpgLines.size() + "/4 Lines - text will cut off in-game!)";
            countColor = 0xFFFF3355;
        } else {
            countStr = "Chars: " + visibleChars + " (Line " + Math.max(1, rpgLines.size()) + "/4)";
            countColor = 0xFF00FF55;
        }
        guiGraphics.drawString(this.font, Component.literal(countStr).withStyle(isOverflow ? ChatFormatting.BOLD : ChatFormatting.RESET), panelLeft + panelWidth - 16 - this.font.width(countStr), formY, countColor, false);
        formY += 68;

        guiGraphics.drawString(this.font, Component.literal("Standard Formatting & Custom Animated FX Palette:"), panelLeft + 16, formY, 0xFFFFD700);
        formY += 66;

        // Secondary controls labels
        int col1X = panelLeft + 16;
        int col2X = panelLeft + 266;
        int gridY = formY + 10;

        guiGraphics.drawString(this.font, Component.literal("Voice Sound Preset:"), col1X, gridY - 10, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("Custom Voice Sound ID:"), col1X, gridY + 22, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("Min Pitch (0.8):"), col1X, gridY + 54, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("Max Pitch (1.2):"), col1X + 126, gridY + 54, 0xAAAAAA);

        guiGraphics.drawString(this.font, Component.literal("Char Speed (ticks):"), col2X, gridY - 10, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("Line Delay (ticks):"), col2X + 126, gridY - 10, 0xAAAAAA);

        int speed = 1;
        if (dialogSpeedBox != null) {
            try { speed = Math.max(0, Integer.parseInt(dialogSpeedBox.getValue().trim())); } catch (Exception ignored) {}
        }
        int totTicks = visibleChars * speed + dTicks;
        String totDurationStr = String.format(Locale.ROOT, "Total Duration: %.2fs (%dt) - auto advances", totTicks * 0.05f, totTicks);
        guiGraphics.drawString(this.font, Component.literal(totDurationStr), col2X, gridY + 22, 0xFF00FFCC, false);

        guiGraphics.drawString(this.font, Component.literal("Line Sound ID (triggers on start):"), col2X, gridY + 46, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (dialogLetterSoundDropdown != null) {
            dialogLetterSoundDropdown.renderOverlay(guiGraphics, mouseX, mouseY);
        }
        if (typeDropdown != null) {
            typeDropdown.renderOverlay(guiGraphics, mouseX, mouseY);
        }
    }
}
