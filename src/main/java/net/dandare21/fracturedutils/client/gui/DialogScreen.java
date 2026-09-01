package net.dandare21.fracturedutils.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.dandare21.fracturedutils.dialog.DialogLine;
import net.dandare21.fracturedutils.dialog.DialogManager;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.C2SDeleteDialogSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SRequestOpenDialogUiPacket;
import net.dandare21.fracturedutils.network.packet.C2SSaveDialogSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SStartDialogSequencePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class DialogScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int CARD_BORDER = 0xAA00E5FF;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final Map<String, String> savedServerSequenceFiles;
    private final Map<String, String> workingServerSequenceFiles;
    private final Map<String, String> savedClientSequenceFiles;
    private final Map<String, String> workingClientSequenceFiles;
    private boolean isClientMode = false;

    private String currentFileName;
    private List<DialogLine> currentLines = new ArrayList<>();

    private DialogListWidget dialogListWidget;
    private EditBox newFileEditBox;

    private static class SidebarRow {
        String fileName;
        CyberpunkButton selectBtn;
        final List<CyberpunkButton> controlBtns = new ArrayList<>();
    }

    private final List<SidebarRow> sidebarRows = new ArrayList<>();

    private double sidebarScrollAmount = 0;
    private boolean isDraggingSidebarScrollbar = false;
    private double dragOffsetY = 0;

    private String saveFeedbackMessage = null;
    private long saveFeedbackTime = 0;

    private int leftPanelLeft;
    private int leftPanelWidth;
    private int rightPanelLeft;
    private int rightPanelWidth;
    private int mainTop;
    private int mainHeight;

    public DialogScreen(Map<String, String> serverSequenceFiles) {
        super(Component.literal("Dialog Orchestrator"));
        this.savedServerSequenceFiles = serverSequenceFiles != null ? new HashMap<>(serverSequenceFiles) : new HashMap<>();
        this.workingServerSequenceFiles = new HashMap<>(this.savedServerSequenceFiles);
        this.savedClientSequenceFiles = loadLocalClientSequences();
        this.workingClientSequenceFiles = new HashMap<>(this.savedClientSequenceFiles);

        if (this.savedServerSequenceFiles != null && !this.savedServerSequenceFiles.isEmpty()) {
            this.isClientMode = false;
        } else {
            boolean isMultiplayer = Minecraft.getInstance().getCurrentServer() != null || !Minecraft.getInstance().isSingleplayer();
            this.isClientMode = !isMultiplayer;
        }

        Map<String, String> activeMap = getActiveSequenceMap();
        if (!activeMap.isEmpty()) {
            this.currentFileName = activeMap.keySet().iterator().next();
            loadCurrentFileLines();
        } else {
            this.currentFileName = "new_dialog.json";
            activeMap.put(currentFileName, "[]");
            getActiveSavedMap().put(currentFileName, "[]");
            this.currentLines = new ArrayList<>();
        }
    }

    public DialogListWidget getDialogListWidget() {
        return dialogListWidget;
    }

    private Map<String, String> getActiveSavedMap() {
        return isClientMode ? savedClientSequenceFiles : savedServerSequenceFiles;
    }

    private Map<String, String> getActiveWorkingMap() {
        return isClientMode ? workingClientSequenceFiles : workingServerSequenceFiles;
    }

    private Map<String, String> getActiveSequenceMap() {
        return getActiveWorkingMap();
    }

    private Map<String, String> loadLocalClientSequences() {
        Map<String, String> map = new HashMap<>();
        File dir = DialogManager.getInstance().getDirectory();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try {
                    String content = Files.readString(f.toPath());
                    map.put(f.getName(), content);
                } catch (Exception ignored) {
                }
            }
        }
        if (map.isEmpty()) {
            String defaultFile = "client_dialog.json";
            map.put(defaultFile, "[]");
            saveLocalClientSequence(defaultFile, "[]");
        }
        return map;
    }

    private void saveLocalClientSequence(String fileName, String jsonContent) {
        File dir = DialogManager.getInstance().getDirectory();
        File file = new File(dir, DialogManager.getInstance().sanitizeFileName(fileName));
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(jsonContent);
        } catch (IOException ignored) {
        }
    }

    private void deleteLocalClientSequence(String fileName) {
        File dir = DialogManager.getInstance().getDirectory();
        File file = new File(dir, DialogManager.getInstance().sanitizeFileName(fileName));
        if (file.exists()) {
            file.delete();
        }
    }

    private void loadCurrentFileLines() {
        Map<String, String> map = getActiveSequenceMap();
        String content = map.get(currentFileName);
        if (content != null) {
            try {
                List<DialogLine> parsed = GSON.fromJson(content, new TypeToken<List<DialogLine>>() {}.getType());
                this.currentLines = parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
            } catch (Exception e) {
                this.currentLines = new ArrayList<>();
            }
        } else {
            this.currentLines = new ArrayList<>();
        }
    }

    private void syncCurrentLinesToWorkingMap() {
        if (currentFileName != null) {
            String json = GSON.toJson(currentLines);
            getActiveWorkingMap().put(currentFileName, json);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public double getLayoutScale() {
        int targetW = 640;
        int targetH = 360;
        if (this.width <= 0 || this.height <= 0) return 1.0;
        double scaleX = (double) this.width / targetW;
        double scaleY = (double) this.height / targetH;
        return Math.min(1.0, Math.min(scaleX, scaleY));
    }

    @Override
    protected void init() {
        super.init();

        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        this.leftPanelLeft = 12;
        this.leftPanelWidth = Math.max(200, (int) (effWidth * 0.32));
        this.rightPanelLeft = leftPanelLeft + leftPanelWidth + 12;
        this.rightPanelWidth = effWidth - rightPanelLeft - 12;

        this.mainTop = 42;
        this.mainHeight = effHeight - mainTop - 12;

        // Close UI Button "✕"
        int closeW = 20;
        int closeH = 20;
        int closeX = effWidth - closeW - 8;
        int closeY = 8;
        WaitingRoomScreen.CyberpunkCloseButton closeBtn = new WaitingRoomScreen.CyberpunkCloseButton(closeX, closeY, closeW, closeH, b -> this.onClose());
        closeBtn.setTooltip(Tooltip.create(Component.literal("Close UI")));
        this.addRenderableWidget(closeBtn);

        // Right Panel: Dialog Lines List Widget
        int listLeft = rightPanelLeft + 10;
        int listTop = mainTop + 35;
        int listWidth = rightPanelWidth - 20;
        int listHeight = mainHeight - 75;

        this.dialogListWidget = new DialogListWidget(this, this.minecraft, listWidth, listHeight, listTop, listTop + listHeight, 44);
        this.dialogListWidget.setLeftPos(listLeft);
        this.dialogListWidget.updateEntries(currentLines);
        this.addRenderableWidget(this.dialogListWidget);

        // Sidebar Header Control Buttons: Folder 📁, Reload 🔄, Mode toggle
        boolean isMultiplayer = Minecraft.getInstance().getCurrentServer() != null || !Minecraft.getInstance().isSingleplayer();
        int headerBtnY = mainTop + 7;

        int folderBtnX = leftPanelLeft + leftPanelWidth - 22;
        this.addRenderableWidget(new CyberpunkButton(folderBtnX, headerBtnY, 18, 16, Component.literal("📁"), b -> {
            File dir = DialogManager.getInstance().getDirectory();
            Util.getPlatform().openFile(dir);
        }, 0xFF00E5FF, false, Component.literal("Open Dialog Sequences Folder")));

        int reloadBtnX = folderBtnX - 22;
        this.addRenderableWidget(new CyberpunkButton(reloadBtnX, headerBtnY, 18, 16, Component.literal("🔄"), b -> {
            if (isClientMode) {
                Map<String, String> loaded = loadLocalClientSequences();
                this.savedClientSequenceFiles.clear();
                this.savedClientSequenceFiles.putAll(loaded);
                this.workingClientSequenceFiles.clear();
                this.workingClientSequenceFiles.putAll(loaded);
                if (!workingClientSequenceFiles.containsKey(currentFileName)) {
                    currentFileName = workingClientSequenceFiles.keySet().iterator().next();
                }
                loadCurrentFileLines();
                this.saveFeedbackMessage = "✓ RELOADED LOCAL DIALOG SEQUENCES";
            } else {
                ModMessages.sendToServer(new C2SRequestOpenDialogUiPacket());
                this.saveFeedbackMessage = "✓ REQUESTED SERVER DIALOG SEQUENCES RELOAD";
            }
            this.saveFeedbackTime = System.currentTimeMillis();
            this.rebuildWidgets();
        }, 0xFF00E5FF, false, Component.literal("Reload Sequences")));

        if (isMultiplayer) {
            int modeBtnW = 68;
            int modeBtnX = reloadBtnX - modeBtnW - 4;
            String modeStr = isClientMode ? "💻 CLIENT" : "🌐 SERVER";
            this.addRenderableWidget(new CyberpunkButton(modeBtnX, headerBtnY, modeBtnW, 16, Component.literal(modeStr), b -> {
                this.isClientMode = !this.isClientMode;
                Map<String, String> activeMap = getActiveSequenceMap();
                if (!activeMap.containsKey(currentFileName)) {
                    if (!activeMap.isEmpty()) {
                        currentFileName = activeMap.keySet().iterator().next();
                    } else {
                        currentFileName = "new_dialog.json";
                        activeMap.put(currentFileName, "[]");
                        getActiveSavedMap().put(currentFileName, "[]");
                    }
                }
                loadCurrentFileLines();
                this.rebuildWidgets();
            }, isClientMode ? 0xFFFFBB00 : 0xFF00E5FF, false, Component.literal("Switch editing mode")));
        }

        // New Sequence Input Box + Button
        int inputY = mainTop + mainHeight - 32;
        int newBtnW = 50;
        int inputW = leftPanelWidth - 20 - newBtnW - 4;

        this.newFileEditBox = new EditBox(this.font, leftPanelLeft + 10, inputY, inputW, 20, Component.literal("New Dialog File"));
        this.newFileEditBox.setMaxLength(64);
        this.newFileEditBox.setHint(Component.literal("sequence_name.json"));
        this.addRenderableWidget(this.newFileEditBox);

        this.addRenderableWidget(new CyberpunkButton(leftPanelLeft + 10 + inputW + 4, inputY, newBtnW, 20, Component.literal("+ NEW"), b -> {
            String name = newFileEditBox.getValue().trim();
            if (!name.isEmpty()) {
                name = DialogManager.getInstance().sanitizeFileName(name);
                Map<String, String> activeMap = getActiveSequenceMap();
                if (!activeMap.containsKey(name)) {
                    activeMap.put(name, "[]");
                    currentFileName = name;
                    currentLines = new ArrayList<>();
                    syncCurrentLinesToWorkingMap();
                    newFileEditBox.setValue("");
                    this.rebuildWidgets();
                }
            }
        }, CYAN_MAIN, false, Component.literal("Create new empty dialog sequence")));

        // Build Sidebar File Rows
        rebuildSidebarRows();

        // Right Panel Action Buttons: Add Line (+ ADD DIALOG LINE), Save (💾 SAVE TO SERVER)
        int rightBottomY = mainTop + mainHeight - 32;

        this.addRenderableWidget(new CyberpunkButton(rightPanelLeft + 10, rightBottomY, 140, 22, Component.literal("+ ADD DIALOG LINE"), b -> {
            openAddLineModal();
        }, CYAN_MAIN, false, Component.literal("Add a new dialog line to sequence")));

        boolean isUnsaved = isFileModified(currentFileName);
        String saveBtnLabel = isClientMode ? "💾 SAVE TO CLIENT" : "💾 SAVE TO SERVER";
        int saveBtnColor = isUnsaved ? 0xFFFFBB00 : CYAN_MAIN;

        CyberpunkButton saveServerBtn = new CyberpunkButton(rightPanelLeft + rightPanelWidth - 160, rightBottomY, 150, 22, Component.literal(saveBtnLabel), b -> {
            saveCurrentSequence();
        }, saveBtnColor, isUnsaved, Component.literal("Save current dialog sequence file"));
        this.addRenderableWidget(saveServerBtn);
    }

    private void rebuildSidebarRows() {
        sidebarRows.clear();
        Map<String, String> activeMap = getActiveSequenceMap();
        List<String> sortedNames = new ArrayList<>(activeMap.keySet());
        Collections.sort(sortedNames);

        int rowY = mainTop + 30;
        int rowH = 26;
        int sidebarContentHeight = sortedNames.size() * rowH;
        int maxScroll = Math.max(0, sidebarContentHeight - (mainHeight - 65));
        sidebarScrollAmount = Math.max(0, Math.min(sidebarScrollAmount, maxScroll));

        for (int i = 0; i < sortedNames.size(); i++) {
            String fileName = sortedNames.get(i);
            int currentY = rowY + (i * rowH) - (int) sidebarScrollAmount;

            if (currentY + rowH < mainTop + 28 || currentY > mainTop + mainHeight - 35) {
                continue;
            }

            SidebarRow row = new SidebarRow();
            row.fileName = fileName;

            boolean isSelected = fileName.equalsIgnoreCase(currentFileName);
            boolean isModified = isFileModified(fileName);

            String displayName = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
            if (isModified) {
                displayName += " *";
            }

            int btnW = leftPanelWidth - 20 - 95;
            row.selectBtn = new CyberpunkButton(leftPanelLeft + 10, currentY, btnW, 22, Component.literal(displayName), b -> {
                if (!fileName.equalsIgnoreCase(currentFileName)) {
                    syncCurrentLinesToWorkingMap();
                    currentFileName = fileName;
                    loadCurrentFileLines();
                    this.rebuildWidgets();
                }
            }, isSelected ? 0xFF00FF55 : (isModified ? 0xFFFFBB00 : CYAN_MAIN), isSelected, Component.literal("Select dialog sequence"));
            this.addRenderableWidget(row.selectBtn);

            // Row Action Controls: Play ▶, Duplicate ⧉, Rename ✎, Delete 🗑
            int ctrlX = leftPanelLeft + 10 + btnW + 2;

            // Play ▶
            CyberpunkButton playBtn = new CyberpunkButton(ctrlX, currentY, 20, 22, Component.literal("▶"), b -> {
                if (isClientMode) {
                    saveCurrentSequence();
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("▶ Local preview dialog: " + fileName).withStyle(ChatFormatting.GREEN));
                } else {
                    saveCurrentSequence();
                    ModMessages.sendToServer(new C2SStartDialogSequencePacket(fileName));
                }
            }, 0xFF00FF55, false, Component.literal("Play dialog sequence for all players"));
            row.controlBtns.add(playBtn);
            this.addRenderableWidget(playBtn);

            // Duplicate ⧉
            ctrlX += 22;
            CyberpunkButton dupBtn = new CyberpunkButton(ctrlX, currentY, 20, 22, Component.literal("⧉"), b -> {
                duplicateSequence(fileName);
            }, CYAN_MAIN, false, Component.literal("Duplicate sequence file"));
            row.controlBtns.add(dupBtn);
            this.addRenderableWidget(dupBtn);

            // Rename ✎
            ctrlX += 22;
            CyberpunkButton renameBtn = new CyberpunkButton(ctrlX, currentY, 20, 22, Component.literal("✎"), b -> {
                openRenameModal(fileName);
            }, CYAN_MAIN, false, Component.literal("Rename sequence file"));
            row.controlBtns.add(renameBtn);
            this.addRenderableWidget(renameBtn);

            // Delete 🗑
            ctrlX += 22;
            CyberpunkButton delBtn = new CyberpunkButton(ctrlX, currentY, 20, 22, Component.literal("🗑"), b -> {
                openDeleteModal(fileName);
            }, 0xFFFF3355, false, Component.literal("Delete sequence file"));
            row.controlBtns.add(delBtn);
            this.addRenderableWidget(delBtn);

            sidebarRows.add(row);
        }
    }

    private boolean isFileModified(String fileName) {
        if (fileName == null) return false;
        String working = getActiveWorkingMap().get(fileName);
        String saved = getActiveSavedMap().get(fileName);
        if (working == null && saved == null) return false;
        if (working == null || saved == null) return true;
        return !working.trim().equals(saved.trim());
    }

    private void saveCurrentSequence() {
        syncCurrentLinesToWorkingMap();
        String json = getActiveWorkingMap().get(currentFileName);
        if (json != null) {
            if (isClientMode) {
                saveLocalClientSequence(currentFileName, json);
                savedClientSequenceFiles.put(currentFileName, json);
                saveFeedbackMessage = "✓ SAVED TO CLIENT: " + currentFileName;
            } else {
                ModMessages.sendToServer(new C2SSaveDialogSequencePacket(currentFileName, json));
                savedServerSequenceFiles.put(currentFileName, json);
                saveFeedbackMessage = "✓ SENT SAVE TO SERVER: " + currentFileName;
            }
            saveFeedbackTime = System.currentTimeMillis();
            this.rebuildWidgets();
        }
    }

    public void openAddLineModal() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new EditDialogLineModalScreen(this, null, newLine -> {
                currentLines.add(newLine);
                syncCurrentLinesToWorkingMap();
                this.dialogListWidget.updateEntries(currentLines);
                this.rebuildWidgets();
            }));
        }
    }

    public void openEditLineModal(DialogLine line, int index) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new EditDialogLineModalScreen(this, line, updatedLine -> {
                if (index >= 0 && index < currentLines.size()) {
                    currentLines.set(index, updatedLine);
                    syncCurrentLinesToWorkingMap();
                    this.dialogListWidget.updateEntries(currentLines);
                    this.rebuildWidgets();
                }
            }));
        }
    }

    public void duplicateLine(int index) {
        if (index >= 0 && index < currentLines.size()) {
            DialogLine copy = currentLines.get(index).copy();
            currentLines.add(index + 1, copy);
            syncCurrentLinesToWorkingMap();
            this.dialogListWidget.updateEntries(currentLines);
            this.rebuildWidgets();
        }
    }

    public void moveLine(int from, int to) {
        if (from >= 0 && from < currentLines.size() && to >= 0 && to < currentLines.size()) {
            DialogLine line = currentLines.remove(from);
            currentLines.add(to, line);
            syncCurrentLinesToWorkingMap();
            this.dialogListWidget.updateEntries(currentLines);
            this.rebuildWidgets();
        }
    }

    public void deleteLine(int index) {
        if (index >= 0 && index < currentLines.size()) {
            currentLines.remove(index);
            syncCurrentLinesToWorkingMap();
            this.dialogListWidget.updateEntries(currentLines);
            this.rebuildWidgets();
        }
    }

    public void reorderLines(int from, int to) {
        if (from >= 0 && from < currentLines.size() && to >= 0 && to <= currentLines.size()) {
            DialogLine line = currentLines.remove(from);
            int insertIndex = (to > from) ? to - 1 : to;
            currentLines.add(insertIndex, line);
            syncCurrentLinesToWorkingMap();
            this.dialogListWidget.updateEntries(currentLines);
            this.rebuildWidgets();
        }
    }

    private void duplicateSequence(String fileName) {
        Map<String, String> activeMap = getActiveSequenceMap();
        String content = activeMap.get(fileName);
        if (content != null) {
            String newName = "copy_" + fileName;
            int counter = 1;
            while (activeMap.containsKey(newName)) {
                newName = "copy" + counter + "_" + fileName;
                counter++;
            }
            activeMap.put(newName, content);
            currentFileName = newName;
            loadCurrentFileLines();
            this.rebuildWidgets();
        }
    }

    private void openRenameModal(String fileName) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new RenameSequenceModalScreen(this, fileName, newName -> {
                String cleanNewName = DialogManager.getInstance().sanitizeFileName(newName);
                Map<String, String> workingMap = getActiveWorkingMap();
                Map<String, String> savedMap = getActiveSavedMap();

                String content = workingMap.remove(fileName);
                String savedContent = savedMap.remove(fileName);

                if (content != null) {
                    workingMap.put(cleanNewName, content);
                }
                if (savedContent != null) {
                    savedMap.put(cleanNewName, savedContent);
                }

                if (isClientMode) {
                    deleteLocalClientSequence(fileName);
                    saveLocalClientSequence(cleanNewName, content != null ? content : "[]");
                } else {
                    ModMessages.sendToServer(new C2SDeleteDialogSequencePacket(fileName));
                    ModMessages.sendToServer(new C2SSaveDialogSequencePacket(cleanNewName, content != null ? content : "[]"));
                }

                if (fileName.equalsIgnoreCase(currentFileName)) {
                    currentFileName = cleanNewName;
                }
                this.rebuildWidgets();
            }));
        }
    }

    private void openDeleteModal(String fileName) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ConfirmDeleteSequenceModalScreen(this, fileName, () -> {
                getActiveWorkingMap().remove(fileName);
                getActiveSavedMap().remove(fileName);

                if (isClientMode) {
                    deleteLocalClientSequence(fileName);
                } else {
                    ModMessages.sendToServer(new C2SDeleteDialogSequencePacket(fileName));
                }

                Map<String, String> activeMap = getActiveSequenceMap();
                if (fileName.equalsIgnoreCase(currentFileName)) {
                    if (!activeMap.isEmpty()) {
                        currentFileName = activeMap.keySet().iterator().next();
                    } else {
                        currentFileName = "new_dialog.json";
                        activeMap.put(currentFileName, "[]");
                        getActiveSavedMap().put(currentFileName, "[]");
                    }
                    loadCurrentFileLines();
                }
                this.rebuildWidgets();
            }));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }
        if (mouseX >= leftPanelLeft && mouseX <= leftPanelLeft + leftPanelWidth && mouseY >= mainTop + 28 && mouseY <= mainTop + mainHeight - 35) {
            Map<String, String> activeMap = getActiveSequenceMap();
            int sidebarContentHeight = activeMap.size() * 26;
            int maxScroll = Math.max(0, sidebarContentHeight - (mainHeight - 65));
            sidebarScrollAmount = Math.max(0, Math.min(sidebarScrollAmount - delta * 18, maxScroll));
            this.rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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

        // Dark Cyberpunk Fullscreen Overlay
        guiGraphics.fill(0, 0, effWidth, effHeight, 0xEE030609);

        // Header Title Bar
        guiGraphics.fill(0, 0, effWidth, 34, CYAN_BG);
        guiGraphics.fill(0, 33, effWidth, 34, CYAN_MAIN);
        guiGraphics.drawString(this.font, Component.literal("DIALOG ORCHESTRATOR").withStyle(ChatFormatting.BOLD), 16, 11, CYAN_MAIN);

        // Left Panel (Sequence File List Sidebar)
        guiGraphics.fill(leftPanelLeft, mainTop, leftPanelLeft + leftPanelWidth, mainTop + mainHeight, CYAN_BG);
        guiGraphics.fill(leftPanelLeft, mainTop, leftPanelLeft + leftPanelWidth, mainTop + 1, CARD_BORDER);
        guiGraphics.fill(leftPanelLeft, mainTop + mainHeight - 1, leftPanelLeft + leftPanelWidth, mainTop + mainHeight, CARD_BORDER);
        guiGraphics.fill(leftPanelLeft, mainTop, leftPanelLeft + 1, mainTop + mainHeight, CARD_BORDER);
        guiGraphics.fill(leftPanelLeft + leftPanelWidth - 1, mainTop, leftPanelLeft + leftPanelWidth, mainTop + mainHeight, CARD_BORDER);

        guiGraphics.drawString(this.font, Component.literal("DIALOG SEQUENCES"), leftPanelLeft + 10, mainTop + 10, CYAN_MAIN);

        // Right Panel (Sequence Detail Editor View)
        guiGraphics.fill(rightPanelLeft, mainTop, rightPanelLeft + rightPanelWidth, mainTop + mainHeight, CYAN_BG);
        guiGraphics.fill(rightPanelLeft, mainTop, rightPanelLeft + rightPanelWidth, mainTop + 1, CARD_BORDER);
        guiGraphics.fill(rightPanelLeft, mainTop + mainHeight - 1, rightPanelLeft + rightPanelWidth, mainTop + mainHeight, CARD_BORDER);
        guiGraphics.fill(rightPanelLeft, mainTop, rightPanelLeft + 1, mainTop + mainHeight, CARD_BORDER);
        guiGraphics.fill(rightPanelLeft + rightPanelWidth - 1, mainTop, rightPanelLeft + rightPanelWidth, mainTop + mainHeight, CARD_BORDER);

        // Right Panel Header Details
        String sequenceTitle = "FILE: " + (currentFileName != null ? currentFileName : "None");
        if (isFileModified(currentFileName)) {
            sequenceTitle += " *";
        }
        guiGraphics.drawString(this.font, Component.literal(sequenceTitle).withStyle(ChatFormatting.BOLD), rightPanelLeft + 14, mainTop + 12, CYAN_MAIN);
        guiGraphics.drawString(this.font, Component.literal("Lines: " + currentLines.size()), rightPanelLeft + rightPanelWidth - 80, mainTop + 12, 0xAAAAAA);

        // Save Feedback Banner
        if (saveFeedbackMessage != null) {
            long elapsed = System.currentTimeMillis() - saveFeedbackTime;
            if (elapsed < 4000) {
                guiGraphics.drawString(this.font, Component.literal(saveFeedbackMessage), rightPanelLeft + 200, mainTop + 12, 0xFF00FF55);
            } else {
                saveFeedbackMessage = null;
            }
        }

        super.render(guiGraphics, scaledMouseX, scaledMouseY, partialTick);
        guiGraphics.pose().popPose();
    }
}
