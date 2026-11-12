package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.client.camera.CameraEasing;
import net.dandare21.fracturedutils.dialog.DialogFormatUtil;
import net.dandare21.fracturedutils.dialog.DialogLine;
import net.dandare21.fracturedutils.dialog.camera.CameraTarget;
import net.dandare21.fracturedutils.dialog.style.DialogStyle;
import net.dandare21.fracturedutils.dialog.style.DialogStyleRegistry;
import net.dandare21.fracturedutils.dialog.text.DialogTextParser;
import net.dandare21.fracturedutils.dialog.text.DialogTextToken;
import net.dandare21.fracturedutils.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public class EditDialogLineModalScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int RED_CANCEL = 0xFFFF3355;
    private static final int GREEN_ACCENT = 0xFF00FF88;
    private static final int AMBER_ACCENT = 0xFFFFB300;

    public enum CameraMode {
        DEFAULT("Default (Player Eye)", "Normal first-person player eye view", 0xFF888888),
        ENTITY_TAG("Entity Tag Target", "Track entity tagged with scoreboard tag", 0xFF00E5FF),
        SPEAKER_SELECTOR("Speaker Target Selector", "Track entity via @e[...] selector query", 0xFF00FF88),
        STATIC_3D("Static 3D Shot", "Fixed world coordinates & custom angles", 0xFFFFB300);

        private final String label;
        private final String subtitle;
        private final int accent;

        CameraMode(String label, String subtitle, int accent) {
            this.label = label;
            this.subtitle = subtitle;
            this.accent = accent;
        }

        public String getLabel() {
            return label;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public int getAccent() {
            return accent;
        }
    }

    private final Screen parentScreen;
    private final DialogLine line;
    private final Consumer<DialogLine> onSave;

    // Active tab: 0 = Voice & Dialogue Timing, 1 = Camera & Staging
    private int activeTab = 0;
    private CameraMode activeCameraMode = CameraMode.DEFAULT;

    // Header & Content Controls
    private EditBox speakerBox;
    private CyberpunkMultilineEditBox textBox;

    // Tab Navigation Buttons
    private CyberpunkButton tabVoiceBtn;
    private CyberpunkButton tabCamBtn;

    // Tab 0 Controls (Voice & Timing)
    private EditBox delayBox;
    private EditBox soundBox;
    private EditBox speedBox;
    private EditBox letterSoundBox;
    private EditBox letterPitchMinBox;
    private EditBox letterPitchMaxBox;
    private CyberpunkDropdown<String> letterSoundDropdown;
    private CyberpunkDropdown<ResourceLocation> lineStyleDropdown;
    private CyberpunkCheckbox waitForInputCheckbox;

    // Tab 1 Controls (Camera & Staging)
    private CyberpunkDropdown<CameraMode> cameraModeDropdown;
    private EditBox tagBox;
    private EditBox selectorBox;
    private CyberpunkDropdown<CameraTarget.AnchorPoint> anchorDropdown;
    private EditBox offsetXBox;
    private EditBox offsetYBox;
    private EditBox offsetZBox;
    private CyberpunkCheckbox lookAtCheckbox;
    private EditBox interpTicksBox;
    private CyberpunkDropdown<CameraEasing> easingDropdown;
    private CyberpunkButton launch3DSetupBtn;

    // Real-Time Typing Animation Test State
    private boolean isTypingTestActive = false;
    private List<DialogTextToken.Token> testTokens = new ArrayList<>();
    private int testTokenIndex = 0;
    private int testPauseRemaining = 0;
    private int testRevealedChars = 0;
    private int testTotalChars = 0;
    private int testCharTimer = 0;
    private long lastTypingTickTime = 0;

    public EditDialogLineModalScreen(Screen parentScreen, DialogLine line, Consumer<DialogLine> onSave) {
        super(Component.literal("Edit Dialog Line"));
        this.parentScreen = parentScreen;
        this.line = line != null ? line.copy() : new DialogLine();
        this.onSave = onSave;

        // Determine initial camera mode from line state
        if (this.line.isUseCamera()) {
            CameraTarget target = this.line.getCameraTarget();
            if (target != null) {
                if (target.type() == CameraTarget.TargetType.ENTITY_TAG) {
                    this.activeCameraMode = CameraMode.ENTITY_TAG;
                } else if (target.type() == CameraTarget.TargetType.SELECTOR) {
                    this.activeCameraMode = CameraMode.SPEAKER_SELECTOR;
                } else {
                    this.activeCameraMode = CameraMode.STATIC_3D;
                }
            } else if (this.line.getSpeakerSelector() != null && !this.line.getSpeakerSelector().isBlank()) {
                this.activeCameraMode = CameraMode.SPEAKER_SELECTOR;
            } else {
                this.activeCameraMode = CameraMode.STATIC_3D;
            }
        } else {
            this.activeCameraMode = CameraMode.DEFAULT;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public double getLayoutScale() {
        int targetW = 540;
        int targetH = 470;
        if (this.width <= 0 || this.height <= 0)
            return 1.0;
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

        int panelWidth = 520;
        int panelHeight = 450;
        int panelLeft = (effWidth - panelWidth) / 2;
        int panelTop = (effHeight - panelHeight) / 2;
        int fullW = panelWidth - 32;

        // 1. Speaker Tag EditBox (Left Half) & Dialog Box Style Selector (Right Half)
        int halfW = (fullW - 12) / 2;
        this.speakerBox = new EditBox(this.font, panelLeft + 16, panelTop + 82, halfW, 16,
                Component.literal("Speaker"));
        this.speakerBox.setMaxLength(64);
        this.speakerBox.setValue(line.getSpeaker());
        this.addRenderableWidget(this.speakerBox);

        this.lineStyleDropdown = new CyberpunkDropdown<>(panelLeft + 16 + halfW + 12, panelTop + 82, halfW, 16,
                Component.literal("Dialog Box Style"));
        List<CyberpunkDropdown.DropdownEntry<ResourceLocation>> styleEntries = new ArrayList<>();
        styleEntries.add(new CyberpunkDropdown.DropdownEntry<>(null, Component.literal("Default (Sequence Style)"),
                Component.literal("Inherits sequence style"), 0xFF888888));
        styleEntries.add(new CyberpunkDropdown.DropdownEntry<>(DialogStyleRegistry.CYBERPUNK_ID,
                Component.literal("Cyberpunk"), Component.literal("Neon Cyan 9-Slice & Badges"), 0xFF00E5FF));
        styleEntries.add(new CyberpunkDropdown.DropdownEntry<>(DialogStyleRegistry.RETRO_UNDERTALE_ID,
                Component.literal("Retro Undertale"), Component.literal("Monochrome Pixel RPG Frame"), 0xFFFFFFFF));
        styleEntries.add(new CyberpunkDropdown.DropdownEntry<>(DialogStyleRegistry.FANTASY_PARCHMENT_ID,
                Component.literal("Fantasy Parchment"), Component.literal("Aged Gold & Warm Wood"), 0xFFFFD700));
        styleEntries.add(new CyberpunkDropdown.DropdownEntry<>(DialogStyleRegistry.MINIMAL_DARK_ID,
                Component.literal("Minimal Dark"), Component.literal("Sleek Modern Charcoal Frame"), 0xFF888888));
        this.lineStyleDropdown.setOptions(styleEntries);
        this.lineStyleDropdown.selectByValue(line.getStyleId());
        this.lineStyleDropdown.setOnSelect(entry -> line.setStyleId(entry.getValue()));
        this.addRenderableWidget(this.lineStyleDropdown);

        // 2. Multiline Dialog Text EditBox
        this.textBox = new CyberpunkMultilineEditBox(panelLeft + 16, panelTop + 112, fullW, 40,
                Component.literal("Dialog Text"));
        this.textBox.setMaxLength(512);
        this.textBox.setValue(line.getText());
        this.addRenderableWidget(this.textBox);

        // 3. Rich In-Line Tags Assistant Bar (Row 1)
        int tagBtnH = 14;
        int tagY = panelTop + 155;
        int tagBtnW = 44;
        int tagGap = 4;
        int tagStartX = panelLeft + 16;

        record TagDef(String label, String code, boolean isWrapper, int color, String name, String tip) {
        }
        List<TagDef> tags = List.of(
                new TagDef("{pause}", "{pause:15}", false, AMBER_ACCENT, "Mid-Sentence Pause",
                        "Mid-Sentence Pause ({pause:ticks})\nPauses typewriter for N ticks (20 ticks = 1 second).\n\nParameters:\n• ticks: Delay amount (e.g. 15, 20, 40)\n\nExample:\nHold fast! {pause:20} Do not listen to its deceits!"),
                new TagDef("{shake}", "shake", true, 0xFFFF5555, "Letter Shake Effect",
                        "Letter Shake Effect ({shake}...{/shake})\nFrantically jitters letters in-place.\n\nExample:\nBeware of its {shake}unholy power{/shake}!"),
                new TagDef("{wave}", "wave", true, 0xFF55FFFF, "Sine Wave Effect",
                        "Sine Wave Effect ({wave}...{/wave})\nSmoothly bobs letters up and down like ocean waves.\n\nExample:\n{wave}Listen to the whispering wind...{/wave}"),
                new TagDef("{rainbow}", "rainbow", true, 0xFFFF55FF, "Rainbow Cycle Effect",
                        "Rainbow Cycle Effect ({rainbow}...{/rainbow})\nCycles letters through continuous chromatic rainbow colors.\n\nExample:\n{rainbow}Pure prismatic arcane energy!{/rainbow}"),
                new TagDef("{glitch}", "glitch", true, 0xFF00FFCC, "Glitch Effect",
                        "Cyberpunk Glitch Effect ({glitch}...{/glitch})\nProduces RGB twitch and flickering distortion.\n\nExample:\n{glitch}WARNING: SIGNAL CORRUPTED{/glitch}"),
                new TagDef("{pulse}", "pulse", true, 0xFFFFFF55, "Luminosity Pulse Effect",
                        "Luminosity Pulse Effect ({pulse}...{/pulse})\nSmooth breathing glow and brightness pulse.\n\nExample:\n{pulse}The core is overloading!{/pulse}"),
                new TagDef("{color}", "{color:#00E5FF}text{/color}", false, 0xFFFFAA00, "Custom Color Tag",
                        "Custom Color Tag ({color:#HEX}...{/color})\nApplies custom 24-bit hex or named color to text.\n\nParameters:\n• #HEX code (e.g. #FF5500, #00E5FF, #AA00FF)\n\nExample:\nAlert: {color:#FF0055}Critical malfunction detected{/color}!"),
                new TagDef("{shake_screen}", "{shake_screen:3.0:20}", false, 0xFFFF3333, "Screen Shake Trigger",
                        "Screen Shake Trigger ({shake_screen:intensity:duration})\nTriggers full camera viewport screen shake exactly on this word.\n\nParameters:\n• intensity (1.0 - 5.0)\n• duration in ticks (20 ticks = 1 sec)\n\nExample:\nLook out! {shake_screen:3.5:25} An explosion rocks the ground!"),
                new TagDef("{sound}", "{sound:minecraft:entity.experience_orb.pickup:1.0:1.0}", false, GREEN_ACCENT,
                        "Mid-Text Sound Trigger",
                        "Mid-Text Sound Trigger ({sound:id:volume:pitch})\nPlays a specific sound event exactly when this word prints.\n\nParameters:\n• id: Sound ResourceLocation\n• volume: (0.5 - 2.0)\n• pitch: (0.5 - 2.0)\n\nExample:\nYou acquired the relic! {sound:minecraft:ui.toast.challenge_complete:1.0:1.0}"));

        for (int i = 0; i < tags.size(); i++) {
            TagDef t = tags.get(i);
            CyberpunkButton btn = new CyberpunkButton(tagStartX + i * (tagBtnW + tagGap), tagY, tagBtnW, tagBtnH,
                    Component.literal(t.label),
                    b -> insertRichTag(t.code, t.isWrapper), t.color, false);
            btn.setTooltip(Tooltip.create(Component.literal(t.tip)));
            this.addRenderableWidget(btn);
        }

        // Natural Pacing Info Button
        int pacingInfoX = tagStartX + tags.size() * (tagBtnW + tagGap);
        int pacingInfoW = fullW - (tags.size() * (tagBtnW + tagGap));
        CyberpunkButton pacingBtn = new CyberpunkButton(pacingInfoX, tagY, pacingInfoW, tagBtnH,
                Component.literal("ℹ Pacing"),
                b -> {
                }, 0xFFAAAAAA, false);
        pacingBtn.setTooltip(Tooltip.create(Component.literal(
                "Natural Punctuation Pacing:\nDuring dialogue playback, the typewriter automatically adds subtle, natural pauses:\n• Comma (,) & Semicolon (;) pause for +4 ticks (0.2s)\n• Period (.), Exclamation (!), & Question mark (?) pause for +10 ticks (0.5s)\n\nNo manual tags needed for standard punctuation!")));
        this.addRenderableWidget(pacingBtn);

        // 4. Standard Format Palette Row (Row 2)
        int colorY = tagY + tagBtnH + 2;
        int colorBtnW = 18;
        int colorBtnH = 13;
        int colorGap = 3;
        String[] colorCodes = { "&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7", "&8", "&9", "&a", "&b", "&c", "&d",
                "&e", "&f", "&l", "&o", "&n", "&m", "&k", "&r" };
        String[] colorLabels = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f", "B",
                "I", "U", "S", "?", "R" };

        for (int i = 0; i < colorCodes.length; i++) {
            final String code = colorCodes[i];
            String lbl = colorLabels[i];
            CyberpunkButton cBtn = new CyberpunkButton(panelLeft + 16 + i * (colorBtnW + colorGap), colorY, colorBtnW,
                    colorBtnH,
                    DialogFormatUtil.formatText(code + lbl),
                    b -> insertFormattingCode(code));
            cBtn.setTooltip(Tooltip.create(Component.literal("Format Code: " + code)));
            this.addRenderableWidget(cBtn);
        }

        // 5. Custom Animated FX Palette Buttons (Row 3, with Live In-Button Animations!)
        int fxY = colorY + colorBtnH + 3;
        int fxBtnW = 76;
        int fxBtnH = 14;
        int fxGap = 6;
        String[] fxCodes = { "&~s", "&~w", "&~r", "&~g", "&~p", "&~x" };
        String[] fxPreviewTexts = { "&~sShake", "&~wWave", "&~rRainbow", "&~gGlitch", "&~pPulse", "&~xStop FX" };
        String[] fxNames = { "Letter Shake Effect (&~s)", "Letter Sine Wave (&~w)", "Letter Rainbow Cycle (&~r)",
                "Cyberpunk Glitch (&~g)", "Luminosity Pulse (&~p)", "Stop Animated Effects (&~x)" };
        int[] fxAccents = { 0xFFFF5555, 0xFF55FFFF, 0xFFFF55FF, 0xFF00FFCC, 0xFFFFFF55, 0xFFAAAAAA };

        for (int i = 0; i < fxCodes.length; i++) {
            final String code = fxCodes[i];
            final String previewText = fxPreviewTexts[i];
            String name = fxNames[i];
            int accent = fxAccents[i];

            CyberpunkAnimatedFxButton fxBtn = new CyberpunkAnimatedFxButton(
                    panelLeft + 16 + i * (fxBtnW + fxGap), fxY, fxBtnW, fxBtnH,
                    previewText,
                    b -> insertFormattingCode(code), accent);
            fxBtn.setTooltip(Tooltip.create(Component.literal(name)));
            this.addRenderableWidget(fxBtn);
        }

        // 6. Tab Navigation Bar
        int tabY = fxY + fxBtnH + 4;
        int tabW = (fullW - 8) / 2;

        this.tabVoiceBtn = new CyberpunkButton(panelLeft + 16, tabY, tabW, 18,
                Component.literal("🎙 Voice & Timing"),
                b -> {
                    applyValues();
                    activeTab = 0;
                    this.rebuildWidgets();
                }, activeTab == 0 ? CYAN_MAIN : 0xFF556677, activeTab == 0,
                Component.literal("Configure character voice sounds, typewriter speeds, and box style override"));
        this.addRenderableWidget(this.tabVoiceBtn);

        boolean hasCameraActive = line.isUseCamera() || activeCameraMode != CameraMode.DEFAULT;
        String camTabLabel = hasCameraActive ? "🎬 Camera & Staging [ACTIVE]" : "🎬 Camera & Staging";
        int camTabColor = activeTab == 1 ? (hasCameraActive ? GREEN_ACCENT : CYAN_MAIN)
                : (hasCameraActive ? AMBER_ACCENT : 0xFF556677);

        this.tabCamBtn = new CyberpunkButton(panelLeft + 16 + tabW + 8, tabY, tabW, 18,
                Component.literal(camTabLabel),
                b -> {
                    applyValues();
                    activeTab = 1;
                    this.rebuildWidgets();
                }, camTabColor, activeTab == 1,
                Component.literal(
                        "Configure camera targeting (Entity Tag, Speaker Selector, 3D Shot), easing curves and duration"));
        this.addRenderableWidget(this.tabCamBtn);

        // 5. Tab Content Construction
        int col1X = panelLeft + 16;
        int col2X = panelLeft + 266;
        int colW = 238;
        int contentY = tabY + 24;

        if (activeTab == 0) {
            initVoiceTab(col1X, col2X, colW, contentY);
        } else {
            initCameraTab(col1X, col2X, colW, contentY);
        }

        // 6. Footer Action Bar
        int footerY = panelTop + panelHeight - 24;

        CyberpunkButton testTypingBtn = new CyberpunkButton(panelLeft + 16, footerY, 110, 18,
                Component.literal("▶ Test Typing"),
                b -> startTypingTest(), 0xFF00FFCC, false,
                Component.literal(
                        "Preview live typewriter typing with mid-sentence pauses, sound, and punctuation delays"));
        this.addRenderableWidget(testTypingBtn);

        CyberpunkButton cancelBtn = new CyberpunkButton(panelLeft + panelWidth - 176, footerY, 80, 18,
                Component.literal("✕ Cancel"),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(parentScreen);
                    }
                }, RED_CANCEL, false);
        this.addRenderableWidget(cancelBtn);

        CyberpunkButton saveBtn = new CyberpunkButton(panelLeft + panelWidth - 92, footerY, 80, 18,
                Component.literal("✓ Save"),
                b -> {
                    applyValues();
                    if (onSave != null) {
                        onSave.accept(line);
                    }
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(parentScreen);
                    }
                }, CYAN_MAIN, false);
        saveBtn.setSolidPrimary(true);
        this.addRenderableWidget(saveBtn);

        // Ensure all dropdown overlay event listeners are configured to prevent
        // overlapping
        setupDropdownMutualExclusion();
    }

    private void initVoiceTab(int col1X, int col2X, int colW, int startY) {
        // Row 1: Voice Preset Dropdown & Typing Speed / Line Delay
        List<CyberpunkDropdown.DropdownEntry<String>> soundEntries = net.dandare21.fracturedutils.sound.DialogSoundRegistry
                .getAvailableSoundEntries(line.getLetterSound());
        this.letterSoundDropdown = new CyberpunkDropdown<>(col1X, startY, colW, 18, Component.literal("Voice Preset"));
        this.letterSoundDropdown.setOptions(soundEntries);
        this.letterSoundDropdown.setMaxVisibleItems(5);
        this.letterSoundDropdown.selectByValue(line.getLetterSound());
        this.letterSoundDropdown.setOnSelect(entry -> {
            if (letterSoundBox != null) {
                letterSoundBox.setValue(entry.getValue());
            }
        });
        this.addRenderableWidget(this.letterSoundDropdown);

        this.speedBox = new EditBox(this.font, col2X, startY, 112, 18, Component.literal("Char Speed"));
        this.speedBox.setMaxLength(4);
        this.speedBox.setValue(String.valueOf(line.getCharSpeedTicks()));
        this.speedBox.setTooltip(Tooltip.create(Component.literal(
                "Typewriter Speed (ticks/char):\n• 0 = Instant reveal\n• 1 = Fast normal\n• 2-4 = Slow & dramatic")));
        this.addRenderableWidget(this.speedBox);

        this.delayBox = new EditBox(this.font, col2X + 126, startY, 112, 18, Component.literal("Line Delay"));
        this.delayBox.setMaxLength(6);
        this.delayBox.setValue(String.valueOf(line.getDelayTicks()));
        this.delayBox.setTooltip(Tooltip.create(Component.literal(
                "Post-line delay in ticks (20 ticks = 1 sec) before advancing automatically (used when Wait for Input is off)")));
        this.delayBox.visible = !line.isWaitForInput();
        this.addRenderableWidget(this.delayBox);

        // Row 2: Custom Voice Sound ID & Wait for Input Checkbox
        int r2Y = startY + 34;
        this.letterSoundBox = new EditBox(this.font, col1X, r2Y, colW - 34, 18, Component.literal("Voice Sound ID"));
        this.letterSoundBox.setMaxLength(128);
        this.letterSoundBox.setValue(line.getLetterSound());
        this.letterSoundBox.setResponder(val -> {
            if (letterSoundDropdown != null) {
                letterSoundDropdown.selectByValue(val.trim());
            }
        });
        this.addRenderableWidget(this.letterSoundBox);

        CyberpunkButton testVoiceBtn = new CyberpunkButton(col1X + colW - 30, r2Y, 30, 18, Component.literal("🔊"),
                b -> playTestVoiceSound());
        testVoiceBtn.setTooltip(Tooltip.create(Component.literal("Test Voice Sound")));
        this.addRenderableWidget(testVoiceBtn);

        this.waitForInputCheckbox = new CyberpunkCheckbox(col2X, r2Y, colW, 18,
                Component.literal("Wait for User Input to Advance"),
                line.isWaitForInput(),
                checked -> {
                    if (delayBox != null) {
                        delayBox.visible = !checked;
                    }
                });
        this.waitForInputCheckbox.setTooltip(Tooltip.create(
                Component.literal("When checked, dialogue halts until the player presses the interaction key ('E')")));
        this.addRenderableWidget(this.waitForInputCheckbox);

        // Row 3: Min Pitch, Max Pitch & Line Trigger Sound
        int r3Y = r2Y + 34;
        this.letterPitchMinBox = new EditBox(this.font, col1X, r3Y, 112, 18, Component.literal("Min Pitch"));
        this.letterPitchMinBox.setMaxLength(6);
        this.letterPitchMinBox.setValue(String.valueOf(line.getLetterSoundPitchMin()));
        this.letterPitchMinBox
                .setTooltip(Tooltip.create(Component.literal("Minimum random pitch for voice sound (default: 0.8)")));
        this.addRenderableWidget(this.letterPitchMinBox);

        this.letterPitchMaxBox = new EditBox(this.font, col1X + 126, r3Y, 112, 18, Component.literal("Max Pitch"));
        this.letterPitchMaxBox.setMaxLength(6);
        this.letterPitchMaxBox.setValue(String.valueOf(line.getLetterSoundPitchMax()));
        this.letterPitchMaxBox
                .setTooltip(Tooltip.create(Component.literal("Maximum random pitch for voice sound (default: 1.2)")));
        this.addRenderableWidget(this.letterPitchMaxBox);

        this.soundBox = new EditBox(this.font, col2X, r3Y, colW - 34, 18, Component.literal("Line Sound ID"));
        this.soundBox.setMaxLength(128);
        this.soundBox.setValue(line.getSound());
        this.soundBox.setTooltip(
                Tooltip.create(Component.literal("Sound event triggered once when this line starts displaying")));
        this.addRenderableWidget(this.soundBox);

        CyberpunkButton testLineSoundBtn = new CyberpunkButton(col2X + colW - 30, r3Y, 30, 18, Component.literal("🔊"),
                b -> playTestLineSound());
        testLineSoundBtn.setTooltip(Tooltip.create(Component.literal("Test Line Sound")));
        this.addRenderableWidget(testLineSoundBtn);
    }

    private void initCameraTab(int col1X, int col2X, int colW, int startY) {
        // Row 1: Camera Mode Dropdown & Interpolation Ticks / Camera Easing
        this.cameraModeDropdown = new CyberpunkDropdown<>(col1X, startY, colW, 18, Component.literal("Camera Mode"));
        List<CyberpunkDropdown.DropdownEntry<CameraMode>> modeEntries = List.of(
                new CyberpunkDropdown.DropdownEntry<>(CameraMode.DEFAULT,
                        Component.literal(CameraMode.DEFAULT.getLabel()),
                        Component.literal(CameraMode.DEFAULT.getSubtitle()), CameraMode.DEFAULT.getAccent()),
                new CyberpunkDropdown.DropdownEntry<>(CameraMode.ENTITY_TAG,
                        Component.literal(CameraMode.ENTITY_TAG.getLabel()),
                        Component.literal(CameraMode.ENTITY_TAG.getSubtitle()), CameraMode.ENTITY_TAG.getAccent()),
                new CyberpunkDropdown.DropdownEntry<>(CameraMode.SPEAKER_SELECTOR,
                        Component.literal(CameraMode.SPEAKER_SELECTOR.getLabel()),
                        Component.literal(CameraMode.SPEAKER_SELECTOR.getSubtitle()),
                        CameraMode.SPEAKER_SELECTOR.getAccent()),
                new CyberpunkDropdown.DropdownEntry<>(CameraMode.STATIC_3D,
                        Component.literal(CameraMode.STATIC_3D.getLabel()),
                        Component.literal(CameraMode.STATIC_3D.getSubtitle()), CameraMode.STATIC_3D.getAccent()));
        this.cameraModeDropdown.setOptions(modeEntries);
        this.cameraModeDropdown.selectByValue(this.activeCameraMode);
        this.cameraModeDropdown.setOnSelect(entry -> {
            applyValues();
            this.activeCameraMode = entry.getValue();
            this.rebuildWidgets();
        });
        this.addRenderableWidget(this.cameraModeDropdown);

        boolean camEnabled = activeCameraMode != CameraMode.DEFAULT;

        this.interpTicksBox = new EditBox(this.font, col2X, startY, 100, 18, Component.literal("Interpolation Ticks"));
        this.interpTicksBox.setMaxLength(5);
        this.interpTicksBox.setValue(String.valueOf(line.getInterpolationTicks()));
        this.interpTicksBox.setTooltip(Tooltip.create(Component.literal(
                "Camera Glide Duration:\n• 20 ticks = 1.0 second smooth glide\n• 0 ticks = instant cut / teleport\n• 40 ticks = 2.0s slow cinematic pan")));
        this.interpTicksBox.visible = camEnabled;
        this.addRenderableWidget(this.interpTicksBox);

        this.easingDropdown = new CyberpunkDropdown<>(col2X + 106, startY, 132, 18, Component.literal("Easing Curve"));
        List<CyberpunkDropdown.DropdownEntry<CameraEasing>> easingEntries = List.of(
                new CyberpunkDropdown.DropdownEntry<>(CameraEasing.EASE_IN_OUT_CUBIC,
                        Component.literal("Ease In-Out (Cubic)"),
                        Component.literal("Smooth acceleration & stop (Default)"), 0xFF00E5FF),
                new CyberpunkDropdown.DropdownEntry<>(CameraEasing.EASE_IN_OUT_QUAD,
                        Component.literal("Ease In-Out (Quad)"), Component.literal("Gentle ease in and out"),
                        0xFF55FFFF),
                new CyberpunkDropdown.DropdownEntry<>(CameraEasing.LINEAR, Component.literal("Linear Pan"),
                        Component.literal("Constant mechanical panning speed"), 0xFFFFB300),
                new CyberpunkDropdown.DropdownEntry<>(CameraEasing.EASE_OUT_SINE, Component.literal("Ease Out (Sine)"),
                        Component.literal("Gradually decelerates to a stop"), 0xFF00FF88),
                new CyberpunkDropdown.DropdownEntry<>(CameraEasing.EASE_IN_EXPO, Component.literal("Ease In (Expo)"),
                        Component.literal("Sudden dramatic snap into place"), 0xFFFF5555),
                new CyberpunkDropdown.DropdownEntry<>(CameraEasing.INSTANT, Component.literal("Instant Cut"),
                        Component.literal("Zero interpolation hard cut"), 0xFFAAAAAA));
        this.easingDropdown.setOptions(easingEntries);
        this.easingDropdown.selectByValue(CameraEasing.fromString(line.getEasing()));
        this.easingDropdown.setOnSelect(entry -> line.setEasing(entry.getValue().name()));
        this.easingDropdown.visible = camEnabled;
        this.addRenderableWidget(this.easingDropdown);

        // Row 2 & Row 3 depending on mode
        int r2Y = startY + 34;
        int r3Y = r2Y + 34;

        CameraTarget currentTarget = line.getCameraTarget();

        if (activeCameraMode == CameraMode.ENTITY_TAG) {
            String initialTag = currentTarget != null && currentTarget.tag().isPresent() ? currentTarget.tag().get()
                    : "";
            this.tagBox = new EditBox(this.font, col1X, r2Y, colW, 18, Component.literal("Scoreboard Tag"));
            this.tagBox.setMaxLength(64);
            this.tagBox.setValue(initialTag);
            this.tagBox.setHint(Component.literal("e.g. herald_boss, guide_npc"));
            this.tagBox.setTooltip(Tooltip.create(Component.literal(
                    "Entity Scoreboard Tag:\nThe camera will find and track the nearest entity bearing this scoreboard tag.")));
            this.addRenderableWidget(this.tagBox);

            initAnchorAndOffsetControls(col1X, col2X, colW, r2Y, r3Y, currentTarget);
        } else if (activeCameraMode == CameraMode.SPEAKER_SELECTOR) {
            String initialSelector = "";
            if (currentTarget != null && currentTarget.selector().isPresent()) {
                initialSelector = currentTarget.selector().get();
            } else if (line.getSpeakerSelector() != null) {
                initialSelector = line.getSpeakerSelector();
            }
            this.selectorBox = new EditBox(this.font, col1X, r2Y, colW, 18, Component.literal("Target Selector"));
            this.selectorBox.setMaxLength(128);
            this.selectorBox.setValue(initialSelector);
            this.selectorBox.setHint(Component.literal("e.g. @e[type=villager,limit=1]"));
            this.selectorBox.setTooltip(Tooltip.create(Component.literal(
                    "Target Selector Query:\nVanilla Minecraft entity selector to track (e.g. @e[type=iron_golem,limit=1]).")));
            this.addRenderableWidget(this.selectorBox);

            initAnchorAndOffsetControls(col1X, col2X, colW, r2Y, r3Y, currentTarget);
        } else if (activeCameraMode == CameraMode.STATIC_3D) {
            this.launch3DSetupBtn = new CyberpunkButton(col1X, r2Y, colW, 20,
                    Component.literal("📷 Launch 3D Camera Flight Setup..."),
                    b -> openCameraSetupScreen(), CYAN_MAIN, false,
                    Component.literal(
                            "Opens the full interactive 3D spectator flight camera viewport in-world to position the camera angles and FOV"));
            this.addRenderableWidget(this.launch3DSetupBtn);
        }
    }

    private void initAnchorAndOffsetControls(int col1X, int col2X, int colW, int r2Y, int r3Y,
            CameraTarget currentTarget) {
        // Anchor Point Dropdown in Col 2 Row 2
        this.anchorDropdown = new CyberpunkDropdown<>(col2X, r2Y, colW, 18, Component.literal("Anchor Point"));
        List<CyberpunkDropdown.DropdownEntry<CameraTarget.AnchorPoint>> anchorEntries = List.of(
                new CyberpunkDropdown.DropdownEntry<>(CameraTarget.AnchorPoint.EYES,
                        Component.literal("Eyes (Head Level)"), Component.literal("Entity eye height"), 0xFF00E5FF),
                new CyberpunkDropdown.DropdownEntry<>(CameraTarget.AnchorPoint.HEAD,
                        Component.literal("Head (Top of Model)"), Component.literal("Top of bounding box"), 0xFF55FFFF),
                new CyberpunkDropdown.DropdownEntry<>(CameraTarget.AnchorPoint.CHEST,
                        Component.literal("Chest (Torso Center)"), Component.literal("Center of entity body"),
                        0xFFFFB300),
                new CyberpunkDropdown.DropdownEntry<>(CameraTarget.AnchorPoint.FEET,
                        Component.literal("Feet (Base Ground)"), Component.literal("Ground coordinate"), 0xFFAAAAAA));
        this.anchorDropdown.setOptions(anchorEntries);
        CameraTarget.AnchorPoint curAnchor = currentTarget != null ? currentTarget.anchor()
                : CameraTarget.AnchorPoint.EYES;
        this.anchorDropdown.selectByValue(curAnchor);
        this.addRenderableWidget(this.anchorDropdown);

        // 3D Offset Edit Boxes in Col 1 Row 3
        Vec3 curOffset = currentTarget != null ? currentTarget.offset() : new Vec3(0.0, 0.5, 2.0);
        int offW = 74;
        this.offsetXBox = new EditBox(this.font, col1X, r3Y, offW, 18, Component.literal("Offset X"));
        this.offsetXBox.setMaxLength(6);
        this.offsetXBox.setValue(String.format(Locale.US, "%.1f", curOffset.x));
        this.offsetXBox.setTooltip(Tooltip.create(Component.literal("Horizontal X Offset (relative to entity)")));
        this.addRenderableWidget(this.offsetXBox);

        this.offsetYBox = new EditBox(this.font, col1X + offW + 8, r3Y, offW, 18, Component.literal("Offset Y"));
        this.offsetYBox.setMaxLength(6);
        this.offsetYBox.setValue(String.format(Locale.US, "%.1f", curOffset.y));
        this.offsetYBox
                .setTooltip(Tooltip.create(Component.literal("Vertical Y Offset (e.g. 0.5 = chest/chin height)")));
        this.addRenderableWidget(this.offsetYBox);

        this.offsetZBox = new EditBox(this.font, col1X + (offW + 8) * 2, r3Y, offW, 18, Component.literal("Offset Z"));
        this.offsetZBox.setMaxLength(6);
        this.offsetZBox.setValue(String.format(Locale.US, "%.1f", curOffset.z));
        this.offsetZBox.setTooltip(
                Tooltip.create(Component.literal("Depth Z Offset (e.g. 2.0 = camera pulled back 2 blocks)")));
        this.addRenderableWidget(this.offsetZBox);

        // Look At Target Checkbox in Col 2 Row 3
        boolean lookAt = currentTarget == null || currentTarget.lookAtTarget();
        this.lookAtCheckbox = new CyberpunkCheckbox(col2X, r3Y, colW, 18,
                Component.literal("Look At Target (Auto-Aim)"),
                lookAt, checked -> {
                });
        this.lookAtCheckbox.setTooltip(Tooltip.create(Component
                .literal("When enabled, the camera automatically turns to face the tracked entity's head smoothly")));
        this.addRenderableWidget(this.lookAtCheckbox);
    }

    private void setupDropdownMutualExclusion() {
        if (letterSoundDropdown != null)
            letterSoundDropdown.setOnOpenListener(() -> closeAllDropdownsExcept(letterSoundDropdown));
        if (lineStyleDropdown != null)
            lineStyleDropdown.setOnOpenListener(() -> closeAllDropdownsExcept(lineStyleDropdown));
        if (cameraModeDropdown != null)
            cameraModeDropdown.setOnOpenListener(() -> closeAllDropdownsExcept(cameraModeDropdown));
        if (anchorDropdown != null)
            anchorDropdown.setOnOpenListener(() -> closeAllDropdownsExcept(anchorDropdown));
        if (easingDropdown != null)
            easingDropdown.setOnOpenListener(() -> closeAllDropdownsExcept(easingDropdown));
    }

    private void closeAllDropdownsExcept(CyberpunkDropdown<?> keep) {
        if (letterSoundDropdown != null && letterSoundDropdown != keep)
            letterSoundDropdown.setOpen(false);
        if (lineStyleDropdown != null && lineStyleDropdown != keep)
            lineStyleDropdown.setOpen(false);
        if (cameraModeDropdown != null && cameraModeDropdown != keep)
            cameraModeDropdown.setOpen(false);
        if (anchorDropdown != null && anchorDropdown != keep)
            anchorDropdown.setOpen(false);
        if (easingDropdown != null && easingDropdown != keep)
            easingDropdown.setOpen(false);
    }

    private CyberpunkDropdown<?> getOpenDropdown() {
        if (lineStyleDropdown != null && lineStyleDropdown.isOpen())
            return lineStyleDropdown;
        if (activeTab == 0) {
            if (letterSoundDropdown != null && letterSoundDropdown.isOpen())
                return letterSoundDropdown;
        } else if (activeTab == 1) {
            if (cameraModeDropdown != null && cameraModeDropdown.isOpen())
                return cameraModeDropdown;
            if (anchorDropdown != null && anchorDropdown.isOpen())
                return anchorDropdown;
            if (easingDropdown != null && easingDropdown.isOpen())
                return easingDropdown;
        }
        return null;
    }

    private void insertRichTag(String tagOrTemplate, boolean isWrapper) {
        if (textBox == null)
            return;
        if (isWrapper && textBox.hasSelection()) {
            String selected = textBox.getSelectedText();
            textBox.insertText("{" + tagOrTemplate + "}" + selected + "{/" + tagOrTemplate + "}");
        } else if (isWrapper) {
            textBox.insertText("{" + tagOrTemplate + "}text{/" + tagOrTemplate + "}");
        } else {
            textBox.insertText(tagOrTemplate);
        }
    }

    private void insertFormattingCode(String code) {
        if (textBox != null && textBox.isFocused()) {
            textBox.insertText(code);
        } else if (speakerBox != null && speakerBox.isFocused()) {
            speakerBox.insertText(code);
        } else if (textBox != null) {
            textBox.insertText(code);
        }
    }

    private void startTypingTest() {
        String rawText = textBox != null ? textBox.getValue() : line.getText();
        this.testTokens = DialogTextParser.parse(rawText, 0xFFFFFFFF);
        this.testTotalChars = DialogTextParser.countVisibleChars(testTokens);
        this.testTokenIndex = 0;
        this.testPauseRemaining = 0;
        this.testRevealedChars = 0;
        this.testCharTimer = 0;
        this.lastTypingTickTime = System.currentTimeMillis();
        this.isTypingTestActive = true;
    }

    private void playTestVoiceSound() {
        String snd = letterSoundBox != null ? letterSoundBox.getValue().trim() : line.getLetterSound();
        if (snd != null && !snd.isEmpty()) {
            try {
                float minP = 0.8f;
                float maxP = 1.2f;
                try {
                    if (letterPitchMinBox != null)
                        minP = Float.parseFloat(letterPitchMinBox.getValue().trim());
                } catch (Exception ignored) {
                }
                try {
                    if (letterPitchMaxBox != null)
                        maxP = Float.parseFloat(letterPitchMaxBox.getValue().trim());
                } catch (Exception ignored) {
                }
                float pitch = minP + (float) Math.random() * (Math.max(minP, maxP) - Math.min(minP, maxP));
                pitch = Math.max(0.1f, Math.min(2.0f, pitch));
                SoundEvent soundEvent = ModSounds.resolveSound(snd);
                if (soundEvent != null) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, pitch));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void playTestLineSound() {
        String snd = soundBox != null ? soundBox.getValue().trim() : line.getSound();
        if (snd != null && !snd.isEmpty()) {
            try {
                SoundEvent soundEvent = ModSounds.resolveSound(snd);
                if (soundEvent != null) {
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(soundEvent, line.getPitch()));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void openCameraSetupScreen() {
        applyValues();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new CameraSetupScreen(this, this.line, updatedLine -> {
                this.line.setUseCamera(updatedLine.isUseCamera());
                this.line.setCameraX(updatedLine.getCameraX());
                this.line.setCameraY(updatedLine.getCameraY());
                this.line.setCameraZ(updatedLine.getCameraZ());
                this.line.setCameraYaw(updatedLine.getCameraYaw());
                this.line.setCameraPitch(updatedLine.getCameraPitch());
                this.line.setCameraFov(updatedLine.getCameraFov());
                if (updatedLine.isUseCamera()) {
                    this.line.setCameraTarget(null);
                    this.line.setSpeakerSelector(null);
                }
            }));
        }
    }

    private void applyValues() {
        if (speakerBox != null)
            line.setSpeaker(speakerBox.getValue());
        if (textBox != null)
            line.setText(textBox.getValue());

        if (waitForInputCheckbox != null)
            line.setWaitForInput(waitForInputCheckbox.isChecked());
        if (delayBox != null) {
            try {
                line.setDelayTicks(Math.max(1, Integer.parseInt(delayBox.getValue().trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        if (speedBox != null) {
            try {
                line.setCharSpeedTicks(Math.max(0, Integer.parseInt(speedBox.getValue().trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        if (soundBox != null)
            line.setSound(soundBox.getValue().trim());
        if (letterSoundBox != null)
            line.setLetterSound(letterSoundBox.getValue().trim());
        if (letterPitchMinBox != null) {
            try {
                line.setLetterSoundPitchMin(Math.max(0.1f, Float.parseFloat(letterPitchMinBox.getValue().trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        if (letterPitchMaxBox != null) {
            try {
                line.setLetterSoundPitchMax(Math.max(0.1f, Float.parseFloat(letterPitchMaxBox.getValue().trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        if (lineStyleDropdown != null) {
            line.setStyleId(lineStyleDropdown.getSelectedValue());
        }

        // Apply camera values
        if (cameraModeDropdown != null && cameraModeDropdown.getSelectedValue() != null) {
            CameraMode mode = cameraModeDropdown.getSelectedValue();
            this.activeCameraMode = mode;
            if (mode == CameraMode.DEFAULT) {
                line.setUseCamera(false);
                line.setCameraTarget(null);
                line.setSpeakerSelector(null);
            } else if (mode == CameraMode.ENTITY_TAG) {
                line.setUseCamera(true);
                String tag = tagBox != null ? tagBox.getValue().trim() : "";
                CameraTarget.AnchorPoint anchor = anchorDropdown != null && anchorDropdown.getSelectedValue() != null
                        ? anchorDropdown.getSelectedValue()
                        : CameraTarget.AnchorPoint.EYES;
                double ox = parseDouble(offsetXBox, 0.0);
                double oy = parseDouble(offsetYBox, 0.5);
                double oz = parseDouble(offsetZBox, 2.0);
                boolean lookAt = lookAtCheckbox != null && lookAtCheckbox.isChecked();
                line.setCameraTarget(new CameraTarget(CameraTarget.TargetType.ENTITY_TAG, Optional.of(tag),
                        Optional.empty(), anchor, new Vec3(ox, oy, oz), lookAt, 0.0f, 0.0f));
                line.setSpeakerSelector(null);
            } else if (mode == CameraMode.SPEAKER_SELECTOR) {
                line.setUseCamera(true);
                String selector = selectorBox != null ? selectorBox.getValue().trim() : "";
                CameraTarget.AnchorPoint anchor = anchorDropdown != null && anchorDropdown.getSelectedValue() != null
                        ? anchorDropdown.getSelectedValue()
                        : CameraTarget.AnchorPoint.EYES;
                double ox = parseDouble(offsetXBox, 0.0);
                double oy = parseDouble(offsetYBox, 0.5);
                double oz = parseDouble(offsetZBox, 2.0);
                boolean lookAt = lookAtCheckbox != null && lookAtCheckbox.isChecked();
                line.setCameraTarget(new CameraTarget(CameraTarget.TargetType.SELECTOR, Optional.empty(),
                        Optional.of(selector), anchor, new Vec3(ox, oy, oz), lookAt, 0.0f, 0.0f));
                line.setSpeakerSelector(selector);
            } else if (mode == CameraMode.STATIC_3D) {
                line.setUseCamera(true);
                line.setCameraTarget(null);
                line.setSpeakerSelector(null);
            }
        }

        if (interpTicksBox != null) {
            try {
                line.setInterpolationTicks(Math.max(0, Integer.parseInt(interpTicksBox.getValue().trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        if (easingDropdown != null && easingDropdown.getSelectedValue() != null) {
            line.setEasing(easingDropdown.getSelectedValue().name());
        }
    }

    private double parseDouble(EditBox box, double defaultVal) {
        if (box == null)
            return defaultVal;
        try {
            return Double.parseDouble(box.getValue().trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }

        CyberpunkDropdown<?> openDropdown = getOpenDropdown();
        if (openDropdown != null) {
            if (openDropdown.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
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

        CyberpunkDropdown<?> openDropdown = getOpenDropdown();
        if (openDropdown != null) {
            if (openDropdown.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }

        CyberpunkDropdown<?> openDropdown = getOpenDropdown();
        if (openDropdown != null) {
            if (openDropdown.mouseScrolled(mouseX, mouseY, amount)) {
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, amount);
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

        // Dim background overlay
        guiGraphics.fill(0, 0, effWidth, effHeight, 0xCC030609);

        int panelWidth = 520;
        int panelHeight = 450;
        int panelLeft = (effWidth - panelWidth) / 2;
        int panelTop = (effHeight - panelHeight) / 2;

        // Update real-time typing animation test
        if (isTypingTestActive) {
            long now = System.currentTimeMillis();
            if (now - lastTypingTickTime >= 50) { // 50ms per tick
                lastTypingTickTime = now;
                int speed = 1;
                try {
                    if (speedBox != null)
                        speed = Math.max(0, Integer.parseInt(speedBox.getValue().trim()));
                } catch (Exception ignored) {
                }

                if (speed == 0) {
                    testRevealedChars = testTotalChars;
                    isTypingTestActive = false;
                } else {
                    if (testPauseRemaining > 0) {
                        testPauseRemaining--;
                    } else {
                        testCharTimer++;
                        if (testCharTimer >= speed) {
                            testCharTimer = 0;
                            while (testTokenIndex < testTokens.size()) {
                                DialogTextToken.Token token = testTokens.get(testTokenIndex++);
                                if (token instanceof DialogTextToken.TextChar tc) {
                                    testRevealedChars++;
                                    if (!Character.isWhitespace(tc.character())) {
                                        playTestVoiceSound();
                                    }
                                    break;
                                } else if (token instanceof DialogTextToken.Pause pause) {
                                    testPauseRemaining = pause.ticks();
                                    break;
                                } else if (token instanceof DialogTextToken.SoundTrigger st) {
                                    SoundEvent soundEvent = ModSounds.resolveSound(st.sound().toString());
                                    if (soundEvent != null) {
                                        Minecraft.getInstance().getSoundManager()
                                                .play(SimpleSoundInstance.forUI(soundEvent, st.pitch()));
                                    }
                                }
                            }
                            if (testRevealedChars >= testTotalChars && testPauseRemaining <= 0) {
                                isTypingTestActive = false;
                            }
                        }
                    }
                }
            }
        }

        // Main Studio Form Container
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, CYAN_BG);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, CYAN_MAIN);
        guiGraphics.fill(panelLeft, panelTop + panelHeight - 2, panelLeft + panelWidth, panelTop + panelHeight,
                CYAN_MAIN);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 2, panelTop + panelHeight, CYAN_MAIN);
        guiGraphics.fill(panelLeft + panelWidth - 2, panelTop, panelLeft + panelWidth, panelTop + panelHeight,
                CYAN_MAIN);

        // Header Title
        guiGraphics.drawString(this.font, Component.literal("DIALOG LINE STUDIO EDITOR").withStyle(ChatFormatting.BOLD),
                panelLeft + 16, panelTop + 8, CYAN_MAIN);

        // 1:1 Live RPG Box Preview
        int boxW = 340;
        int boxH = 50;
        int boxX = panelLeft + (panelWidth - boxW) / 2;
        int boxY = panelTop + 24;

        DialogStyle previewStyle = line.getStyleId() != null
                ? DialogStyleRegistry.get(line.getStyleId())
                : (lineStyleDropdown != null && lineStyleDropdown.getSelectedValue() != null
                        ? DialogStyleRegistry.get(lineStyleDropdown.getSelectedValue())
                        : DialogStyleRegistry.CYBERPUNK);

        int bgCol = previewStyle.backgroundColor();
        int borderCol = previewStyle.borderColor();

        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, bgCol);
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 1, borderCol);
        guiGraphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, borderCol);
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, borderCol);
        guiGraphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, borderCol);

        if (previewStyle.drawCornerNotches()) {
            guiGraphics.fill(boxX + 2, boxY + 2, boxX + boxW - 2, boxY + 3, previewStyle.innerBorderColor());
            guiGraphics.fill(boxX + 2, boxY + boxH - 3, boxX + boxW - 2, boxY + boxH - 2,
                    previewStyle.innerBorderColor());
            guiGraphics.fill(boxX + 2, boxY + 2, boxX + 3, boxY + boxH - 2, previewStyle.innerBorderColor());
            guiGraphics.fill(boxX + boxW - 3, boxY + 2, boxX + boxW - 2, boxY + boxH - 2,
                    previewStyle.innerBorderColor());

            guiGraphics.fill(boxX + 3, boxY + 3, boxX + 7, boxY + 5, borderCol);
            guiGraphics.fill(boxX + 3, boxY + 3, boxX + 5, boxY + 7, borderCol);
            guiGraphics.fill(boxX + boxW - 7, boxY + 3, boxX + boxW - 3, boxY + 5, borderCol);
            guiGraphics.fill(boxX + boxW - 5, boxY + 3, boxX + boxW - 3, boxY + 7, borderCol);
        }

        // Speaker Badge
        String speakerVal = speakerBox != null ? speakerBox.getValue() : line.getSpeaker();
        if (speakerVal != null && !speakerVal.trim().isEmpty()) {
            Component speakerComp = DialogFormatUtil.formatText(speakerVal);
            int speakerWidth = this.font.width(speakerComp);
            int badgeX = boxX + 12;
            int badgeY = boxY - 10;
            int badgeW = speakerWidth + 12;
            int badgeH = 13;

            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, previewStyle.speakerBadgeBgColor());
            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, previewStyle.speakerBadgeBorderColor());
            guiGraphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, previewStyle.speakerBadgeBorderColor());
            guiGraphics.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH,
                    previewStyle.speakerBadgeBorderColor());

            DialogFormatUtil.renderAnimatedText(guiGraphics, this.font, speakerVal, badgeX + 6, badgeY + 2, badgeW,
                    previewStyle.speakerTextColor());
        }

        // Render preview dialog text
        String rawText = textBox != null ? textBox.getValue() : line.getText();
        String displayText = rawText;
        if (isTypingTestActive) {
            displayText = DialogFormatUtil.getRevealedText(rawText, testRevealedChars);
        }

        int contentX = boxX + 14;
        int currentTextY = boxY + 8;
        int maxTextWidth = boxW - 28;
        DialogFormatUtil.renderAnimatedText(guiGraphics, this.font, displayText, contentX, currentTextY, maxTextWidth,
                previewStyle.defaultTextColor());

        // Next line prompt or delay indicator
        boolean waitForInput = waitForInputCheckbox != null ? waitForInputCheckbox.isChecked() : line.isWaitForInput();
        if (waitForInput) {
            long now = System.currentTimeMillis();
            boolean pulse = (now / 300) % 2 == 0;
            int promptColor = pulse ? 0xFFFFFFFF : borderCol;
            guiGraphics.drawString(this.font, Component.literal("▼").withStyle(ChatFormatting.BOLD), boxX + boxW - 14,
                    boxY + boxH - 12, promptColor, false);
        } else {
            String delayStr = delayBox != null ? delayBox.getValue().trim() : String.valueOf(line.getDelayTicks());
            guiGraphics.drawString(this.font, Component.literal("⏱ " + delayStr + "t"), boxX + boxW - 38,
                    boxY + boxH - 12, 0xFF8899AA, false);
        }

        // Form labels & Character count / RPG box overflow counter
        int halfW = (panelWidth - 32 - 12) / 2;
        guiGraphics.drawString(this.font, Component.literal("Speaker Tag:"), panelLeft + 16, panelTop + 71, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("Dialog Box Style (Frame & Theme):"), panelLeft + 16 + halfW + 12, panelTop + 71, CYAN_MAIN);
        guiGraphics.drawString(this.font, Component.literal("Dialog Text (editable with style tags):"), panelLeft + 16, panelTop + 101, 0xAAAAAA);

        Component textComp = DialogFormatUtil.formatText(rawText);
        List<FormattedCharSequence> rpgLines = this.font.split(textComp, 298);
        int visibleChars = DialogFormatUtil.getVisibleCharCount(rawText);
        boolean isOverflow = rpgLines.size() > 4;
        String countStr;
        int countColor;
        if (isOverflow) {
            countStr = "⚠ OVERFLOW: " + visibleChars + " chars (" + rpgLines.size() + "/4 Lines)";
            countColor = 0xFFFF3355;
        } else {
            countStr = "Chars: " + visibleChars + " (Line " + Math.max(1, rpgLines.size()) + "/4)";
            countColor = 0xFF00FF55;
        }
        guiGraphics.drawString(this.font,
                Component.literal(countStr).withStyle(isOverflow ? ChatFormatting.BOLD : ChatFormatting.RESET),
                panelLeft + panelWidth - 16 - this.font.width(countStr), panelTop + 101, countColor, false);

        // Tab Content Dynamic Labels
        int col1X = panelLeft + 16;
        int col2X = panelLeft + 266;
        int contentY = panelTop + 205 + 24;

        if (activeTab == 0) {
            guiGraphics.drawString(this.font, Component.literal("Voice Sound Preset:"), col1X, contentY - 10, 0xAAAAAA);
            guiGraphics.drawString(this.font, Component.literal("Char Speed (ticks/char):"), col2X, contentY - 10,
                    0xAAAAAA);
            if (!waitForInput) {
                guiGraphics.drawString(this.font, Component.literal("Line Delay (ticks):"), col2X + 126, contentY - 10,
                        0xAAAAAA);
            }

            guiGraphics.drawString(this.font, Component.literal("Custom Voice Sound ID:"), col1X, contentY + 24,
                    0xAAAAAA);
            guiGraphics.drawString(this.font, Component.literal("Min Pitch (0.8):"), col1X, contentY + 58, 0xAAAAAA);
            guiGraphics.drawString(this.font, Component.literal("Max Pitch (1.2):"), col1X + 126, contentY + 58,
                    0xAAAAAA);
            guiGraphics.drawString(this.font, Component.literal("Line Sound (on start):"), col2X, contentY + 58,
                    0xAAAAAA);
        } else {
            guiGraphics.drawString(this.font, Component.literal("Camera Staging Mode:"), col1X, contentY - 10,
                    0xAAAAAA);
            if (activeCameraMode != CameraMode.DEFAULT) {
                guiGraphics.drawString(this.font, Component.literal("Glide Duration (ticks):"), col2X, contentY - 10,
                        0xAAAAAA);
                guiGraphics.drawString(this.font, Component.literal("Camera Easing Curve:"), col2X + 106, contentY - 10,
                        0xAAAAAA);
            }

            if (activeCameraMode == CameraMode.ENTITY_TAG) {
                guiGraphics.drawString(this.font, Component.literal("Entity Scoreboard Tag:"), col1X, contentY + 24,
                        0xAAAAAA);
                guiGraphics.drawString(this.font, Component.literal("Anchor Point:"), col2X, contentY + 24, 0xAAAAAA);
                guiGraphics.drawString(this.font, Component.literal("3D Offset (X, Y, Z):"), col1X, contentY + 58,
                        0xAAAAAA);
                guiGraphics.drawString(this.font,
                        Component
                                .literal("Interpolation Ticks: 20t = 1.0s smooth glide. Easing controls acceleration."),
                        col1X, contentY + 96, 0xFF778899);
            } else if (activeCameraMode == CameraMode.SPEAKER_SELECTOR) {
                guiGraphics.drawString(this.font, Component.literal("Target Selector (@e[...]):"), col1X, contentY + 24,
                        0xAAAAAA);
                guiGraphics.drawString(this.font, Component.literal("Anchor Point:"), col2X, contentY + 24, 0xAAAAAA);
                guiGraphics.drawString(this.font, Component.literal("3D Offset (X, Y, Z):"), col1X, contentY + 58,
                        0xAAAAAA);
                guiGraphics.drawString(this.font,
                        Component
                                .literal("Interpolation Ticks: 20t = 1.0s smooth glide. Easing controls acceleration."),
                        col1X, contentY + 96, 0xFF778899);
            } else if (activeCameraMode == CameraMode.STATIC_3D) {
                String camCoords = String.format(Locale.US,
                        "X: %.1f, Y: %.1f, Z: %.1f | Yaw: %.0f°, Pitch: %.0f° | FOV: %.0f°",
                        line.getCameraX(), line.getCameraY(), line.getCameraZ(), line.getCameraYaw(),
                        line.getCameraPitch(), line.getCameraFov());
                guiGraphics.drawString(this.font, Component.literal(camCoords), col1X, contentY + 60, 0xFF00FFCC);
                guiGraphics.drawString(this.font,
                        Component.literal("Static camera shot: overrides player view to fixed coordinates and angles."),
                        col1X, contentY + 96, 0xFF778899);
            } else {
                guiGraphics.drawString(this.font,
                        Component.literal("Standard player view: Camera stays locked to player's eyes."), col1X,
                        contentY + 36, 0xFF778899);
                guiGraphics.drawString(this.font,
                        Component.literal("Select Entity Tag or Selector above to smoothly glide camera to an NPC!"),
                        col1X, contentY + 54, 0xFF00E5FF);
            }
        }

        super.render(guiGraphics, scaledMouseX, scaledMouseY, partialTick);

        // Render open dropdown menu on top of everything
        CyberpunkDropdown<?> openDropdown = getOpenDropdown();
        if (openDropdown != null) {
            openDropdown.renderOverlay(guiGraphics, scaledMouseX, scaledMouseY);
        }

        guiGraphics.pose().popPose();
    }

    public static class CyberpunkAnimatedFxButton extends CyberpunkButton {
        private final String animatedPreviewText;

        public CyberpunkAnimatedFxButton(int x, int y, int width, int height, String animatedPreviewText, OnPress onPress, int accentColor) {
            super(x, y, width, height, Component.literal(""), onPress, accentColor, false);
            this.animatedPreviewText = animatedPreviewText;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            Font font = Minecraft.getInstance().font;
            int textW = font.width(DialogFormatUtil.formatText(animatedPreviewText));
            int textX = this.getX() + (this.width - textW) / 2;
            int textY = this.getY() + (this.height - 8) / 2;

            DialogFormatUtil.renderAnimatedText(guiGraphics, font, animatedPreviewText, textX, textY, this.width, 0xFFFFFFFF);
        }
    }
}
