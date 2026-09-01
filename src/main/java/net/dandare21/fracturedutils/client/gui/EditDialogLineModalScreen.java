package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.dialog.DialogFormatUtil;
import net.dandare21.fracturedutils.dialog.DialogLine;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class EditDialogLineModalScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int RED_CANCEL = 0xFFFF3355;

    private final Screen parentScreen;
    private final DialogLine line;
    private final Consumer<DialogLine> onSave;

    private EditBox speakerBox;
    private CyberpunkMultilineEditBox textBox;
    private EditBox delayBox;
    private EditBox soundBox;
    private EditBox speedBox;
    private EditBox letterSoundBox;
    private EditBox letterPitchMinBox;
    private EditBox letterPitchMaxBox;
    private CyberpunkDropdown<String> letterSoundDropdown;
    private CyberpunkCheckbox waitForInputCheckbox;
    private CyberpunkButton cameraSetupBtn;

    // Real-Time Typing Animation Test State
    private boolean isTypingTestActive = false;
    private int typingRevealedChars = 0;
    private int typingTotalChars = 0;
    private int typingTickTimer = 0;
    private long lastTypingTickTime = 0;

    public EditDialogLineModalScreen(Screen parentScreen, DialogLine line, Consumer<DialogLine> onSave) {
        super(Component.literal("Edit Dialog Line"));
        this.parentScreen = parentScreen;
        this.line = line != null ? line.copy() : new DialogLine();
        this.onSave = onSave;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public double getLayoutScale() {
        int targetW = 540;
        int targetH = 470;
        if (this.width <= 0 || this.height <= 0) return 1.0;
        double scaleX = (double) this.width / targetW;
        double scaleY = (double) this.height / targetH;
        return Math.min(1.0, Math.min(scaleX, scaleY));
    }

    @Override
    protected void init() {
        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        int panelWidth = 520;
        int panelHeight = 450;

        int panelLeft = (effWidth - panelWidth) / 2;
        int panelTop = (effHeight - panelHeight) / 2;

        int fullW = panelWidth - 32;

        // --- PRIMARY FOCUS SECTION: FULL WIDTH SPEAKER TAG & MULTILINE DIALOG TEXT ---
        int formY = panelTop + 96;

        // 1. Speaker Tag EditBox (Full Width)
        this.speakerBox = new EditBox(this.font, panelLeft + 16, formY + 10, fullW, 18, Component.literal("Speaker"));
        this.speakerBox.setMaxLength(64);
        this.speakerBox.setValue(line.getSpeaker());
        this.addRenderableWidget(this.speakerBox);
        formY += 32;

        // 2. Multiline Dialog Text EditBox (Full Width & Wrapped Height)
        this.textBox = new CyberpunkMultilineEditBox(panelLeft + 16, formY + 10, fullW, 54, Component.literal("Dialog Text"));
        this.textBox.setMaxLength(512);
        this.textBox.setValue(line.getText());
        this.addRenderableWidget(this.textBox);
        formY += 68;

        // 3. Complete Formatting Palette Bar (22 Standard Codes + 6 Custom Animated FX with Live Previews)
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

        // Standard Palette Row 1
        for (int i = 0; i < row1Codes.length; i++) {
            final String code = row1Codes[i];
            String label = row1Labels[i];
            String name = row1Names[i];
            CyberpunkButton codeBtn = new CyberpunkButton(panelLeft + 16 + i * (btnW + gapX), paletteY, btnW, btnH,
                    DialogFormatUtil.formatText(code + label),
                    b -> insertFormattingCode(code));
            codeBtn.setTooltip(Tooltip.create(Component.literal(name)));
            this.addRenderableWidget(codeBtn);
        }

        // Standard Palette Row 2
        for (int i = 0; i < row2Codes.length; i++) {
            final String code = row2Codes[i];
            String label = row2Labels[i];
            String name = row2Names[i];
            CyberpunkButton codeBtn = new CyberpunkButton(panelLeft + 16 + i * (btnW + gapX), paletteY + btnH + 2, btnW, btnH,
                    DialogFormatUtil.formatText(code + label),
                    b -> insertFormattingCode(code));
            codeBtn.setTooltip(Tooltip.create(Component.literal(name)));
            this.addRenderableWidget(codeBtn);
        }

        // Custom Animated FX Palette Row 3 across full panel width (6 buttons, 76px wide each)
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

            CyberpunkAnimatedFxButton fxBtn = new CyberpunkAnimatedFxButton(panelLeft + 16 + i * (fxBtnW + fxGapX), fxY, fxBtnW, fxBtnH,
                    previewText,
                    b -> insertFormattingCode(code), accent);
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
        List<CyberpunkDropdown.DropdownEntry<String>> soundEntries = net.dandare21.fracturedutils.sound.DialogSoundRegistry.getAvailableSoundEntries(line.getLetterSound());
        this.letterSoundDropdown = new CyberpunkDropdown<>(col1X, gridY, colW, 18, Component.literal("Voice Sound Preset"));
        this.letterSoundDropdown.setOptions(soundEntries);
        this.letterSoundDropdown.setMaxVisibleItems(5);
        this.letterSoundDropdown.selectByValue(line.getLetterSound());
        this.letterSoundDropdown.setOnSelect(entry -> {
            if (letterSoundBox != null) {
                letterSoundBox.setValue(entry.getValue());
            }
        });

        int c1Y = gridY + 32;
        this.letterSoundBox = new EditBox(this.font, col1X, c1Y, colW - 34, 18, Component.literal("Letter Sound ID"));
        this.letterSoundBox.setMaxLength(128);
        this.letterSoundBox.setValue(line.getLetterSound());
        this.letterSoundBox.setResponder(val -> {
            if (letterSoundDropdown != null) {
                letterSoundDropdown.selectByValue(val.trim());
            }
        });
        this.addRenderableWidget(this.letterSoundBox);

        CyberpunkButton testLetterSoundBtn = new CyberpunkButton(col1X + colW - 30, c1Y, 30, 18, Component.literal("🔊"), b -> playTestVoiceSound());
        this.addRenderableWidget(testLetterSoundBtn);

        c1Y += 32;
        this.letterPitchMinBox = new EditBox(this.font, col1X, c1Y, 112, 18, Component.literal("Min Pitch"));
        this.letterPitchMinBox.setMaxLength(6);
        this.letterPitchMinBox.setValue(String.valueOf(line.getLetterSoundPitchMin()));
        this.addRenderableWidget(this.letterPitchMinBox);

        this.letterPitchMaxBox = new EditBox(this.font, col1X + 126, c1Y, 112, 18, Component.literal("Max Pitch"));
        this.letterPitchMaxBox.setMaxLength(6);
        this.letterPitchMaxBox.setValue(String.valueOf(line.getLetterSoundPitchMax()));
        this.addRenderableWidget(this.letterPitchMaxBox);

        // Column 2: Char Speed & Line Delay & Wait Checkbox & Line Sound ID
        int c2Y = gridY;
        this.speedBox = new EditBox(this.font, col2X, c2Y, 112, 18, Component.literal("Char Speed"));
        this.speedBox.setMaxLength(4);
        this.speedBox.setValue(String.valueOf(line.getCharSpeedTicks()));
        this.addRenderableWidget(this.speedBox);

        this.delayBox = new EditBox(this.font, col2X + 126, c2Y, 112, 18, Component.literal("Line Delay"));
        this.delayBox.setMaxLength(6);
        this.delayBox.setValue(String.valueOf(line.getDelayTicks()));
        this.delayBox.visible = !line.isWaitForInput();
        this.addRenderableWidget(this.delayBox);

        c2Y += 26;
        this.waitForInputCheckbox = new CyberpunkCheckbox(col2X, c2Y, colW, 18,
                Component.literal("Wait for User Input to Advance"),
                line.isWaitForInput(),
                checked -> {
                    if (delayBox != null) {
                        delayBox.visible = !checked;
                    }
                });
        this.addRenderableWidget(this.waitForInputCheckbox);

        c2Y += 32;
        this.soundBox = new EditBox(this.font, col2X, c2Y, colW - 34, 18, Component.literal("Line Sound ID"));
        this.soundBox.setMaxLength(128);
        this.soundBox.setValue(line.getSound());
        this.addRenderableWidget(this.soundBox);

        CyberpunkButton testLineSoundBtn = new CyberpunkButton(col2X + colW - 30, c2Y, 30, 18, Component.literal("🔊"), b -> playTestLineSound());
        this.addRenderableWidget(testLineSoundBtn);

        // Add letter sound dropdown as renderable widget at end so it overlays cleanly
        this.addRenderableWidget(this.letterSoundDropdown);

        // --- FOOTER ACTION BAR: CAMERA SETUP, TEST TYPING, CANCEL & SAVE ---
        int footerY = panelTop + panelHeight - 26;

        this.cameraSetupBtn = new CyberpunkButton(col1X, footerY, 200, 20, Component.literal("📷 Camera Setup..."), b -> openCameraSetupScreen());
        this.addRenderableWidget(this.cameraSetupBtn);

        CyberpunkButton testTypingBtn = new CyberpunkButton(col1X + 208, footerY, 95, 20, Component.literal("▶ Test Typing"), b -> startTypingTest(), 0xFF00FFCC, false);
        this.addRenderableWidget(testTypingBtn);

        CyberpunkButton cancelBtn = new CyberpunkButton(panelLeft + panelWidth - 188, footerY, 85, 20, Component.literal("✕ Cancel"), b -> {
            this.minecraft.setScreen(parentScreen);
        }, RED_CANCEL, false);
        this.addRenderableWidget(cancelBtn);

        CyberpunkButton saveBtn = new CyberpunkButton(panelLeft + panelWidth - 96, footerY, 80, 20, Component.literal("✓ Save"), b -> {
            applyValues();
            if (onSave != null) {
                onSave.accept(line);
            }
            this.minecraft.setScreen(parentScreen);
        });
        this.addRenderableWidget(saveBtn);
    }

    private void startTypingTest() {
        this.typingTotalChars = DialogFormatUtil.getVisibleCharCount(textBox.getValue());
        this.typingRevealedChars = 0;
        this.typingTickTimer = 0;
        this.lastTypingTickTime = System.currentTimeMillis();
        this.isTypingTestActive = true;
    }

    private void playTestVoiceSound() {
        String snd = letterSoundBox.getValue().trim();
        if (!snd.isEmpty()) {
            try {
                float minP = 0.8f;
                float maxP = 1.2f;
                try { minP = Float.parseFloat(letterPitchMinBox.getValue().trim()); } catch (Exception ignored) {}
                try { maxP = Float.parseFloat(letterPitchMaxBox.getValue().trim()); } catch (Exception ignored) {}
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
        String snd = soundBox.getValue().trim();
        if (!snd.isEmpty()) {
            try {
                SoundEvent soundEvent = ModSounds.resolveSound(snd);
                if (soundEvent != null) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, line.getPitch()));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void openCameraSetupScreen() {
        applyValues();
        this.minecraft.setScreen(new CameraSetupScreen(this, this.line, updatedLine -> {
            this.line.setUseCamera(updatedLine.isUseCamera());
            this.line.setCameraX(updatedLine.getCameraX());
            this.line.setCameraY(updatedLine.getCameraY());
            this.line.setCameraZ(updatedLine.getCameraZ());
            this.line.setCameraYaw(updatedLine.getCameraYaw());
            this.line.setCameraPitch(updatedLine.getCameraPitch());
            this.line.setCameraFov(updatedLine.getCameraFov());
        }));
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

    private void applyValues() {
        line.setSpeaker(speakerBox.getValue());
        line.setText(textBox.getValue());
        line.setWaitForInput(waitForInputCheckbox.isChecked());
        try {
            line.setDelayTicks(Math.max(1, Integer.parseInt(delayBox.getValue().trim())));
        } catch (NumberFormatException e) {
            line.setDelayTicks(40);
        }
        try {
            line.setCharSpeedTicks(Math.max(0, Integer.parseInt(speedBox.getValue().trim())));
        } catch (NumberFormatException e) {
            line.setCharSpeedTicks(1);
        }
        line.setSound(soundBox.getValue().trim());
        line.setLetterSound(letterSoundBox.getValue().trim());
        try {
            line.setLetterSoundPitchMin(Math.max(0.1f, Float.parseFloat(letterPitchMinBox.getValue().trim())));
        } catch (NumberFormatException e) {
            line.setLetterSoundPitchMin(0.8f);
        }
        try {
            line.setLetterSoundPitchMax(Math.max(0.1f, Float.parseFloat(letterPitchMaxBox.getValue().trim())));
        } catch (NumberFormatException e) {
            line.setLetterSoundPitchMax(1.2f);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }

        if (letterSoundDropdown != null && letterSoundDropdown.isOpen()) {
            if (letterSoundDropdown.mouseClicked(mouseX, mouseY, button)) {
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
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }

        if (letterSoundDropdown != null && letterSoundDropdown.isOpen()) {
            if (letterSoundDropdown.mouseScrolled(mouseX, mouseY, amount)) {
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

        // Dim Background Overlay
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
                try { speed = Math.max(0, Integer.parseInt(speedBox.getValue().trim())); } catch (Exception ignored) {}

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

        // --- MAIN STUDIO FORM CONTAINER ---
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, CYAN_BG);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, CYAN_MAIN);
        guiGraphics.fill(panelLeft, panelTop + panelHeight - 2, panelLeft + panelWidth, panelTop + panelHeight, CYAN_MAIN);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 2, panelTop + panelHeight, CYAN_MAIN);
        guiGraphics.fill(panelLeft + panelWidth - 2, panelTop, panelLeft + panelWidth, panelTop + panelHeight, CYAN_MAIN);

        // Header Title (Top-Left)
        guiGraphics.drawString(this.font, Component.literal("DIALOG LINE STUDIO EDITOR").withStyle(ChatFormatting.BOLD), panelLeft + 16, panelTop + 8, CYAN_MAIN);

        // --- TOP SECTION: 1:1 EXACT LIVE RPG DIALOG HUD PREVIEW ---
        int boxW = 330;
        int boxH = 58;
        int boxX = panelLeft + (panelWidth - boxW) / 2;
        int boxY = panelTop + 34; // Leaves 26px clearance so Speaker Badge at boxY - 12 (panelTop + 22) never overlaps the title!

        int alphaBits = 0xF5000000;

        // 1. Dark RPG Frame Background Fill
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, alphaBits | 0x05090C);

        // 2. Double Cyberpunk Border
        int borderColor = alphaBits | 0x00E5FF;
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 1, borderColor);
        guiGraphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, borderColor);
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, borderColor);
        guiGraphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, borderColor);

        // Inner Border
        guiGraphics.fill(boxX + 2, boxY + 2, boxX + boxW - 2, boxY + 3, alphaBits | 0x4400E5FF);
        guiGraphics.fill(boxX + 2, boxY + boxH - 3, boxX + boxW - 2, boxY + boxH - 2, alphaBits | 0x4400E5FF);
        guiGraphics.fill(boxX + 2, boxY + 2, boxX + 3, boxY + boxH - 2, alphaBits | 0x4400E5FF);
        guiGraphics.fill(boxX + boxW - 3, boxY + 2, boxX + boxW - 2, boxY + boxH - 2, alphaBits | 0x4400E5FF);

        // Cyberpunk Corner Accent Notches
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

        // Speaker Badge with Custom Animated Effect Rendering!
        String speakerVal = speakerBox.getValue();
        if (speakerVal != null && !speakerVal.trim().isEmpty()) {
            Component speakerComp = DialogFormatUtil.formatText(speakerVal);
            int speakerWidth = this.font.width(speakerComp);

            int badgeX = boxX + 12;
            int badgeY = boxY - 12;
            int badgeW = speakerWidth + 12;
            int badgeH = 14;

            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, alphaBits | 0x0A1622);
            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, borderColor);
            guiGraphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, borderColor);
            guiGraphics.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, borderColor);

            DialogFormatUtil.renderAnimatedText(guiGraphics, this.font, speakerVal, badgeX + 6, badgeY + 3, badgeW, 0xFFFFFFFF);
            currentTextY += 2;
        }

        // Formatted Wrapped Dialog Text Lines with Custom Animated Effects
        String rawText = textBox.getValue();
        String displayText = rawText;
        if (isTypingTestActive) {
            displayText = DialogFormatUtil.getRevealedText(rawText, typingRevealedChars);
        }

        DialogFormatUtil.renderAnimatedText(guiGraphics, this.font, displayText, contentX, currentTextY, maxTextWidth, 0xFFFFFFFF);

        // Next Line Prompt / State Badge Icon in bottom-right corner of preview
        boolean waitForInput = waitForInputCheckbox.isChecked();
        if (waitForInput) {
            long now = System.currentTimeMillis();
            boolean pulse = (now / 300) % 2 == 0;
            int promptColor = pulse ? 0xFFFFFFFF : CYAN_MAIN;
            guiGraphics.drawString(this.font, Component.literal("▼").withStyle(ChatFormatting.BOLD), boxX + boxW - 18, boxY + boxH - 14, promptColor, false);
        } else {
            String delayStr = delayBox.getValue().trim();
            guiGraphics.drawString(this.font, Component.literal("⏱ " + delayStr + "t"), boxX + boxW - 40, boxY + boxH - 14, 0xFF8899AA, false);
        }

        // --- FORM LABELS, FIELD CAPTIONS & REAL-TIME RPG OVERFLOW COUNTER ---
        int formY = panelTop + 96;

        guiGraphics.drawString(this.font, Component.literal("Speaker Tag (supports color & custom FX):"), panelLeft + 16, formY, 0xAAAAAA);
        formY += 32;

        guiGraphics.drawString(this.font, Component.literal("Dialog Text (supports color & custom FX):"), panelLeft + 16, formY, 0xAAAAAA);

        // Calculate Real-time RPG Box Line Count & Character Limit Overflow Warning (4 Lines Max in RPG Box)
        Component textComp = DialogFormatUtil.formatText(rawText);
        List<FormattedCharSequence> rpgLines = this.font.split(textComp, 298); // 298px max text width in RPG Box
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

        guiGraphics.drawString(this.font, Component.literal("Standard Formatting & Custom Animated FX Palette:"), panelLeft + 16, formY, CYAN_MAIN);
        formY += 66;

        // Secondary Controls Grid Section
        int col1X = panelLeft + 16;
        int col2X = panelLeft + 266;
        int gridY = formY + 10;

        guiGraphics.drawString(this.font, Component.literal("Voice Sound Preset:"), col1X, gridY - 10, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("Custom Voice Sound ID:"), col1X, gridY + 22, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("Min Pitch (0.8):"), col1X, gridY + 54, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("Max Pitch (1.2):"), col1X + 126, gridY + 54, 0xAAAAAA);

        guiGraphics.drawString(this.font, Component.literal("Char Speed (ticks):"), col2X, gridY - 10, 0xAAAAAA);
        if (!waitForInput) {
            guiGraphics.drawString(this.font, Component.literal("Line Delay (ticks):"), col2X + 126, gridY - 10, 0xAAAAAA);
        }
        guiGraphics.drawString(this.font, Component.literal("Line Sound ID (triggers on start):"), col2X, gridY + 48, 0xAAAAAA);

        // Camera Info Badge in Footer
        if (line.isUseCamera()) {
            String camText = String.format(Locale.US, "📷 Cam: Enabled (X:%.1f Y:%.1f Z:%.1f FOV:%.0f°)",
                    line.getCameraX(), line.getCameraY(), line.getCameraZ(), line.getCameraFov());
            cameraSetupBtn.setMessage(Component.literal(camText));
        } else {
            cameraSetupBtn.setMessage(Component.literal("📷 Camera: Default (Player Eye)"));
        }

        super.render(guiGraphics, scaledMouseX, scaledMouseY, partialTick);

        if (letterSoundDropdown != null) {
            letterSoundDropdown.renderOverlay(guiGraphics, scaledMouseX, scaledMouseY);
        }

        guiGraphics.pose().popPose();
    }

    // Custom Button Class for Custom Animated FX Palette Buttons with Live Animated Previews!
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

            // Render live animated text effect inside palette button!
            DialogFormatUtil.renderAnimatedText(guiGraphics, font, animatedPreviewText, textX, textY, this.width, 0xFFFFFFFF);
        }
    }
}
