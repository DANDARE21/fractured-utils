package net.dandare21.fracturedutils.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.dandare21.fracturedutils.client.ClientOpMonitorData;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.C2SDeleteSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SRequestOpenOrchestratorUiPacket;
import net.dandare21.fracturedutils.network.packet.C2SSaveSequencePacket;
import net.dandare21.fracturedutils.orchestrator.OrchestratorManager;
import net.dandare21.fracturedutils.orchestrator.action.ActionAdapter;
import net.dandare21.fracturedutils.orchestrator.action.CommandAction;
import net.dandare21.fracturedutils.orchestrator.action.OrchestratorAction;
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

public class OrchestratorScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int CARD_BORDER = 0xAA00E5FF;

    private static final Gson GSON = ActionAdapter.registerAll(new GsonBuilder())
            .setPrettyPrinting()
            .create();

    private final Map<String, String> savedServerSequenceFiles;
    private final Map<String, String> workingServerSequenceFiles;
    private final Map<String, String> savedClientSequenceFiles;
    private final Map<String, String> workingClientSequenceFiles;
    private boolean isClientMode = false;

    private String currentFileName;
    private List<OrchestratorAction> currentActions = new ArrayList<>();

    private ActionListWidget actionListWidget;
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

    public OrchestratorScreen(Map<String, String> serverSequenceFiles) {
        super(Component.literal("Command Orchestrator"));
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
            loadCurrentFileActions();
        } else {
            this.currentFileName = "new_sequence.json";
            activeMap.put(currentFileName, "[]");
            getActiveSavedMap().put(currentFileName, "[]");
            this.currentActions = new ArrayList<>();
        }
    }

    public ActionListWidget getActionListWidget() {
        return actionListWidget;
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
        File dir = OrchestratorManager.getInstance().getDirectory();
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
            String defaultFile = "client_sequence.json";
            map.put(defaultFile, "[]");
            saveLocalClientSequence(defaultFile, "[]");
        }
        return map;
    }

    private void saveLocalClientSequence(String fileName, String jsonContent) {
        File dir = OrchestratorManager.getInstance().getDirectory();
        File file = new File(dir, OrchestratorManager.getInstance().sanitizeFileName(fileName));
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(jsonContent);
        } catch (IOException ignored) {
        }
    }

    private void deleteLocalClientSequence(String fileName) {
        File dir = OrchestratorManager.getInstance().getDirectory();
        File file = new File(dir, OrchestratorManager.getInstance().sanitizeFileName(fileName));
        if (file.exists()) {
            file.delete();
        }
    }

    private void loadCurrentFileActions() {
        Map<String, String> map = getActiveWorkingMap();
        String json = map.getOrDefault(currentFileName, "[]");
        try {
            List<OrchestratorAction> parsed = GSON.fromJson(json, new TypeToken<List<OrchestratorAction>>() {}.getType());
            this.currentActions = parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
        } catch (Exception e) {
            this.currentActions = new ArrayList<>();
        }
    }

    private void syncCurrentActionsToJson() {
        if (currentFileName != null && !currentFileName.isEmpty()) {
            String json = GSON.toJson(currentActions);
            getActiveWorkingMap().put(currentFileName, json);
        }
    }

    public String getCurrentFileName() {
        return currentFileName;
    }

    public boolean hasUnsavedChanges(String fileName) {
        if (fileName == null || fileName.isEmpty()) return false;
        String working = getActiveWorkingMap().get(fileName);
        String saved = getActiveSavedMap().get(fileName);
        if (working == null && saved == null) return false;
        if (working == null || saved == null) return true;
        return !working.equals(saved);
    }

    public boolean hasUnsavedChanges() {
        return hasUnsavedChanges(currentFileName);
    }

    public static net.dandare21.fracturedutils.network.packet.S2CSyncSequenceTelemetryPacket.SequenceTelemetryData getActiveTelemetryForSequence(String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        String cleanName = OrchestratorManager.getInstance().sanitizeFileName(fileName);
        for (net.dandare21.fracturedutils.network.packet.S2CSyncSequenceTelemetryPacket.SequenceTelemetryData seq : ClientOpMonitorData.getTelemetryList()) {
            String seqClean = OrchestratorManager.getInstance().sanitizeFileName(seq.getSequenceName());
            if (seqClean.equalsIgnoreCase(cleanName) && !"FINISHED".equalsIgnoreCase(seq.getState())) {
                return seq;
            }
        }
        return null;
    }

    public net.dandare21.fracturedutils.network.packet.S2CSyncSequenceTelemetryPacket.SequenceTelemetryData getActiveTelemetry() {
        return getActiveTelemetryForSequence(currentFileName);
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

        // Upper-Right Close "✕" Button
        int closeW = 20;
        int closeH = 20;
        int closeX = effWidth - closeW - 8;
        int closeY = 8;
        WaitingRoomScreen.CyberpunkCloseButton closeBtn = new WaitingRoomScreen.CyberpunkCloseButton(closeX, closeY, closeW, closeH, b -> this.onClose());
        closeBtn.setTooltip(Tooltip.create(Component.literal("Close UI")));
        this.addRenderableWidget(closeBtn);

        // OP Active Sequence Monitor Toggle Button [👁 OP MONITOR]
        int opBtnW = 105;
        int opBtnX = closeX - opBtnW - 8;
        boolean opEnabled = ClientOpMonitorData.isEnabled();
        String opLabel = opEnabled ? "👁 OP MONITOR: ON" : "👁 OP MONITOR: OFF";
        int opColor = opEnabled ? 0xFF00FFCC : 0xFF8899AA;

        this.addRenderableWidget(new CyberpunkButton(opBtnX, 8, opBtnW, 20, Component.literal(opLabel), b -> {
            boolean newState = ClientOpMonitorData.toggleEnabled();
            this.saveFeedbackMessage = newState ? "✓ OP Active Sequence Monitor: ENABLED" : "✓ OP Active Sequence Monitor: DISABLED";
            this.saveFeedbackTime = System.currentTimeMillis();
            this.rebuildWidgets();
        }, opColor, opEnabled, Component.literal("Toggle real-time OP Active Sequence HUD overlay")));

        // OP Active Sequence Monitor Config Settings Button [⚙ SETTINGS]
        int opSettingsBtnW = 20;
        int opSettingsBtnX = opBtnX - opSettingsBtnW - 4;
        this.addRenderableWidget(new CyberpunkButton(opSettingsBtnX, 8, opSettingsBtnW, 20, Component.literal("⚙"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new OpMonitorConfigScreen(this));
            }
        }, CYAN_MAIN, false, Component.literal("Configure OP Monitor HUD position and opacity")));

        // Right Panel: Action List Widget
        int listLeft = rightPanelLeft + 10;
        int listTop = mainTop + 35;
        int listWidth = rightPanelWidth - 20;
        int listHeight = mainHeight - 75;

        this.actionListWidget = new ActionListWidget(this, this.minecraft, listWidth, listHeight, listTop, listTop + listHeight, 36);
        this.actionListWidget.setLeftPos(listLeft);
        this.actionListWidget.updateEntries(currentActions);
        this.addRenderableWidget(this.actionListWidget);

        // Sidebar Header Control Buttons: [💻 CLIENT/🌐 SERVER Mode Toggle], [📁 Folder], [🔄 Reload]
        boolean isMultiplayer = Minecraft.getInstance().getCurrentServer() != null || !Minecraft.getInstance().isSingleplayer();
        int headerBtnY = mainTop + 7;

        // Open Folder Button 📁
        int folderBtnX = leftPanelLeft + leftPanelWidth - 22;
        this.addRenderableWidget(new CyberpunkButton(folderBtnX, headerBtnY, 18, 16, Component.literal("📁"), b -> {
            File dir = OrchestratorManager.getInstance().getDirectory();
            Util.getPlatform().openFile(dir);
        }, 0xFF00E5FF, false, Component.literal("Open Local Sequences Folder")));

        // Reload Button 🔄
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
                loadCurrentFileActions();
                this.saveFeedbackMessage = "✓ RELOADED LOCAL CLIENT SEQUENCES";
            } else {
                ModMessages.sendToServer(new C2SRequestOpenOrchestratorUiPacket());
                this.saveFeedbackMessage = "✓ REQUESTED SERVER SEQUENCES RELOAD";
            }
            this.saveFeedbackTime = System.currentTimeMillis();
            this.rebuildWidgets();
        }, 0xFF00E5FF, false, Component.literal("Reload Sequences")));

        // Mode Toggle Button (CLIENT / SERVER)
        if (isMultiplayer) {
            int modeBtnW = 68;
            int modeBtnX = reloadBtnX - modeBtnW - 4;
            String modeStr = isClientMode ? "💻 CLIENT" : "🌐 SERVER";
            String modeTooltip = isClientMode ? "Viewing Client local sequences. Click to switch to Server mode." : "Viewing Server synced sequences. Click to switch to Client mode.";
            this.addRenderableWidget(new CyberpunkButton(modeBtnX, headerBtnY, modeBtnW, 16, Component.literal(modeStr), b -> {
                syncCurrentActionsToJson();
                this.isClientMode = !this.isClientMode;
                Map<String, String> map = getActiveWorkingMap();
                if (!map.containsKey(currentFileName)) {
                    currentFileName = map.isEmpty() ? "new_sequence.json" : map.keySet().iterator().next();
                    if (map.isEmpty()) {
                        map.put(currentFileName, "[]");
                        getActiveSavedMap().put(currentFileName, "[]");
                    }
                }
                loadCurrentFileActions();
                this.rebuildWidgets();
            }, isClientMode ? 0xFF00FFCC : CYAN_MAIN, true, Component.literal(modeTooltip)));
        }

        // Sidebar Sequence Files List Rows
        this.sidebarRows.clear();
        int sidebarTop = mainTop + 32;

        Map<String, String> activeMap = getActiveSequenceMap();
        List<String> fileList = new ArrayList<>(activeMap.keySet());
        Collections.sort(fileList);

        double maxScroll = getMaxSidebarScroll();
        int scrollbarMargin = (maxScroll > 0 ? 14 : 6);
        int totalRowW = leftPanelWidth - 16 - scrollbarMargin;
        int actionBtnW = 16;
        int numControlBtns = isClientMode ? 3 : 2; // Upload ⬆ button only in Client Mode
        int selectBtnW = totalRowW - (actionBtnW * numControlBtns + (numControlBtns * 2));
        int fileBtnH = 20;

        for (int i = 0; i < fileList.size(); i++) {
            final String fileName = fileList.get(i);
            boolean isSelected = fileName.equals(currentFileName);
            String displayStr = (isSelected ? "► " : "") + (fileName.length() > 12 ? fileName.substring(0, 10) + ".." : fileName);

            int btnX = leftPanelLeft + 8;
            int btnY = sidebarTop + i * 24;

            SidebarRow row = new SidebarRow();
            row.fileName = fileName;

            // Select sequence button
            row.selectBtn = new CyberpunkButton(btnX, btnY, selectBtnW, fileBtnH, Component.literal(displayStr), b -> {
                syncCurrentActionsToJson();
                this.currentFileName = fileName;
                loadCurrentFileActions();
                this.rebuildWidgets();
            }, CYAN_MAIN, isSelected, Component.literal("Select sequence: " + fileName));

            int currentX = btnX + selectBtnW + 2;

            // Upload ⬆ button (ONLY added when client folder/mode is selected!)
            if (isClientMode) {
                CyberpunkButton uploadBtn = new CyberpunkButton(currentX, btnY, actionBtnW, fileBtnH, Component.literal("⬆"), b -> {
                    syncCurrentActionsToJson();
                    String json = getActiveWorkingMap().get(fileName);
                    if (json != null) {
                        getActiveSavedMap().put(fileName, json);
                        if (isClientMode) {
                            saveLocalClientSequence(fileName, json);
                        }
                        ModMessages.sendToServer(new C2SSaveSequencePacket(fileName, json));
                        this.saveFeedbackMessage = "✓ UPLOADED '" + fileName + "' TO SERVER";
                        this.saveFeedbackTime = System.currentTimeMillis();
                        this.rebuildWidgets();
                    }
                }, 0xFF00FFCC, false, Component.literal("Upload sequence to server"));
                row.controlBtns.add(uploadBtn);
                currentX += actionBtnW + 2;
            }

            // Rename ✏ button
            CyberpunkButton renameBtn = new CyberpunkButton(currentX, btnY, actionBtnW, fileBtnH, Component.literal("✏"), b -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new RenameSequenceModalScreen(this, fileName, newName -> {
                        if (!newName.equalsIgnoreCase(fileName)) {
                            String workingContent = getActiveWorkingMap().remove(fileName);
                            String savedContent = getActiveSavedMap().remove(fileName);
                            String contentToUse = workingContent != null ? workingContent : "[]";

                            getActiveWorkingMap().put(newName, contentToUse);
                            getActiveSavedMap().put(newName, savedContent != null ? savedContent : contentToUse);

                            if (isClientMode) {
                                deleteLocalClientSequence(fileName);
                                saveLocalClientSequence(newName, contentToUse);
                                getActiveSavedMap().put(newName, contentToUse);
                            } else {
                                ModMessages.sendToServer(new C2SDeleteSequencePacket(fileName));
                                ModMessages.sendToServer(new C2SSaveSequencePacket(newName, contentToUse));
                                getActiveSavedMap().put(newName, contentToUse);
                            }
                            if (currentFileName.equals(fileName)) {
                                currentFileName = newName;
                            }
                            rebuildWidgets();
                        }
                    }));
                }
            }, CYAN_MAIN, false, Component.literal("Rename sequence"));
            row.controlBtns.add(renameBtn);
            currentX += actionBtnW + 2;

            // Delete ✖ button
            CyberpunkButton deleteBtn = new CyberpunkButton(currentX, btnY, actionBtnW, fileBtnH, Component.literal("✖"), b -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new ConfirmDeleteSequenceModalScreen(this, fileName, () -> {
                        Map<String, String> workingMap = getActiveWorkingMap();
                        Map<String, String> savedMap = getActiveSavedMap();
                        if (workingMap.size() <= 1) {
                            workingMap.clear();
                            savedMap.clear();
                            String defaultFile = "new_sequence.json";
                            workingMap.put(defaultFile, "[]");
                            savedMap.put(defaultFile, "[]");
                            currentFileName = defaultFile;
                            currentActions = new ArrayList<>();
                            if (isClientMode) {
                                deleteLocalClientSequence(fileName);
                                saveLocalClientSequence(defaultFile, "[]");
                            } else {
                                ModMessages.sendToServer(new C2SDeleteSequencePacket(fileName));
                                ModMessages.sendToServer(new C2SSaveSequencePacket(defaultFile, "[]"));
                            }
                        } else {
                            workingMap.remove(fileName);
                            savedMap.remove(fileName);
                            if (isClientMode) {
                                deleteLocalClientSequence(fileName);
                            } else {
                                ModMessages.sendToServer(new C2SDeleteSequencePacket(fileName));
                            }
                            if (currentFileName.equals(fileName)) {
                                currentFileName = workingMap.keySet().iterator().next();
                                loadCurrentFileActions();
                            }
                        }
                        rebuildWidgets();
                    }));
                }
            }, 0xFFFF3355, false, Component.literal("Delete sequence"));
            row.controlBtns.add(deleteBtn);

            this.sidebarRows.add(row);
            this.addWidget(row.selectBtn);
            for (CyberpunkButton cb : row.controlBtns) {
                this.addWidget(cb);
            }
        }

        updateSidebarButtons();

        // New File Input Box & [+ NEW SEQUENCE] Button
        int newFileY = mainTop + mainHeight - 65;
        this.newFileEditBox = new EditBox(this.font, leftPanelLeft + 15, newFileY + 4, leftPanelWidth - 30, 16, Component.literal("New File"));
        this.newFileEditBox.setValue("seq_" + (activeMap.size() + 1) + ".json");
        this.newFileEditBox.setBordered(false);
        this.newFileEditBox.setTextColor(0xFFFFFFFF);
        this.addRenderableWidget(this.newFileEditBox);

        this.addRenderableWidget(new CyberpunkButton(leftPanelLeft + 10, newFileY + 28, leftPanelWidth - 20, 24, Component.literal("+ NEW SEQUENCE"), b -> {
            String name = newFileEditBox.getValue().trim();
            if (!name.endsWith(".json")) name += ".json";
            Map<String, String> workingMap = getActiveWorkingMap();
            Map<String, String> savedMap = getActiveSavedMap();
            if (!name.isEmpty() && !workingMap.containsKey(name)) {
                syncCurrentActionsToJson();
                this.currentFileName = name;
                this.currentActions = new ArrayList<>();
                workingMap.put(name, "[]");
                savedMap.put(name, "[]");
                if (isClientMode) {
                    saveLocalClientSequence(name, "[]");
                }
                this.rebuildWidgets();
            }
        }, CYAN_MAIN, false, Component.literal("Create a new sequence file")));

        // Right Panel Bottom Buttons: [+ ADD ACTION], [▶ EXECUTE], and [💾 SAVE SEQUENCE]
        int bottomY = mainTop + mainHeight - 34;

        this.addRenderableWidget(new CyberpunkButton(rightPanelLeft + 10, bottomY, 105, 26, Component.literal("+ ADD ACTION"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new EditActionModalScreen(this, new CommandAction("say Hello %player%"), action -> {
                    this.currentActions.add(action);
                    syncCurrentActionsToJson();
                    this.actionListWidget.updateEntries(currentActions);
                }));
            }
        }, CYAN_MAIN, false, Component.literal("Add a new action to sequence")));

        // [▶ EXECUTE] Button
        this.addRenderableWidget(new CyberpunkButton(rightPanelLeft + rightPanelWidth - 10 - 110 - 110 - 6, bottomY, 110, 26, Component.literal("▶ EXECUTE"), b -> {
            runSequenceFromAction(0);
        }, 0xFF55FF55, false, Component.literal("Save and execute sequence from start")));

        // [💾 SAVE SEQUENCE] Button
        String saveLabel = hasUnsavedChanges() ? "💾 SAVE SEQUENCE *" : "💾 SAVE SEQUENCE";
        int saveColor = hasUnsavedChanges() ? 0xFFFFAA00 : CYAN_MAIN;
        this.addRenderableWidget(new CyberpunkButton(rightPanelLeft + rightPanelWidth - 10 - 110, bottomY, 110, 26, Component.literal(saveLabel), b -> {
            syncCurrentActionsToJson();
            String json = getActiveWorkingMap().get(currentFileName);
            if (currentFileName != null && json != null) {
                getActiveSavedMap().put(currentFileName, json);
                if (isClientMode) {
                    saveLocalClientSequence(currentFileName, json);
                    this.saveFeedbackMessage = "✓ SAVED '" + currentFileName + "' LOCALLY";
                } else {
                    ModMessages.sendToServer(new C2SSaveSequencePacket(currentFileName, json));
                    this.saveFeedbackMessage = "✓ SAVED '" + currentFileName + "' TO SERVER";
                }
                this.saveFeedbackTime = System.currentTimeMillis();
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0f));
                }
                this.rebuildWidgets();
            }
        }, saveColor, false, Component.literal("Save current sequence")));
    }

    private double getMaxSidebarScroll() {
        int sidebarTop = mainTop + 32;
        int sidebarBottom = mainTop + mainHeight - 75;
        int sidebarHeight = sidebarBottom - sidebarTop;
        int contentHeight = getActiveSequenceMap().size() * 24;
        return Math.max(0, contentHeight - sidebarHeight);
    }

    private void updateSidebarButtons() {
        int sidebarTop = mainTop + 32;
        int sidebarBottom = mainTop + mainHeight - 75;

        for (int i = 0; i < sidebarRows.size(); i++) {
            SidebarRow row = sidebarRows.get(i);
            int btnY = sidebarTop + i * 24 - (int) sidebarScrollAmount;
            row.selectBtn.setY(btnY);
            boolean visible = (btnY + row.selectBtn.getHeight() >= sidebarTop && btnY <= sidebarBottom);
            row.selectBtn.visible = visible;
            for (CyberpunkButton cb : row.controlBtns) {
                cb.setY(btnY);
                cb.visible = visible;
            }

            if (row.fileName != null) {
                String fileName = row.fileName;
                boolean isSelected = fileName.equals(currentFileName);
                boolean isExecuting = getActiveTelemetryForSequence(fileName) != null;
                boolean isUnsaved = hasUnsavedChanges(fileName);

                String statusPrefix = "";
                if (isExecuting) {
                    statusPrefix = "▶ ";
                } else if (isUnsaved) {
                    statusPrefix = "* ";
                }

                String prefix = (isSelected ? "► " : "") + statusPrefix;
                int maxLen = (isExecuting || isUnsaved) ? 8 : 10;
                String displayStr = prefix + (fileName.length() > maxLen ? fileName.substring(0, maxLen) + ".." : fileName);
                row.selectBtn.setMessage(Component.literal(displayStr));

                String tooltipStr = "Select sequence: " + fileName;
                if (isExecuting) tooltipStr += " [RUNNING]";
                if (isUnsaved) tooltipStr += " [UNSAVED CHANGES]";
                row.selectBtn.setTooltip(Tooltip.create(Component.literal(tooltipStr)));
            }
        }
    }

    public int getActionListLeft() {
        return rightPanelLeft + 10;
    }

    public int getActionListWidth() {
        return rightPanelWidth - 20;
    }

    public int getEntryTop(int index) {
        int listTop = mainTop + 35;
        double scroll = actionListWidget != null ? actionListWidget.getScrollAmount() : 0;
        return (int) (listTop + index * 36 - scroll);
    }

    public void runSequenceFromAction(int actionIndex) {
        if (currentFileName == null || currentFileName.isEmpty()) {
            return;
        }
        syncCurrentActionsToJson();
        String json = getActiveWorkingMap().get(currentFileName);
        if (json != null) {
            getActiveSavedMap().put(currentFileName, json);
        }

        if (!isClientMode) {
            if (json != null) {
                ModMessages.sendToServer(new C2SSaveSequencePacket(currentFileName, json));
            }
            ModMessages.sendToServer(new net.dandare21.fracturedutils.network.packet.C2SStartSequencePacket(currentFileName, actionIndex));
            this.saveFeedbackMessage = "▶ STARTED '" + currentFileName + "' FROM ACTION #" + (actionIndex + 1);
            this.saveFeedbackTime = System.currentTimeMillis();
        } else {
            if (json != null) {
                saveLocalClientSequence(currentFileName, json);
            }
            if (this.minecraft != null && this.minecraft.player != null) {
                boolean started = OrchestratorManager.getInstance().startSequence(currentFileName, this.minecraft.player.getScoreboardName(), actionIndex);
                if (started) {
                    this.saveFeedbackMessage = "▶ STARTED '" + currentFileName + "' FROM ACTION #" + (actionIndex + 1);
                } else {
                    this.saveFeedbackMessage = "⚠ FAILED TO START SEQUENCE";
                }
                this.saveFeedbackTime = System.currentTimeMillis();
            }
        }
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0f));
            this.onClose();
        }
    }

    public void openEditModal(int index) {
        if (index >= 0 && index < currentActions.size()) {
            OrchestratorAction action = currentActions.get(index);
            if (this.minecraft != null) {
                this.minecraft.setScreen(new EditActionModalScreen(this, action.copy(), updatedAction -> {
                    currentActions.set(index, updatedAction);
                    syncCurrentActionsToJson();
                    actionListWidget.updateEntries(currentActions);
                }));
            }
        }
    }

    public void reorderAction(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && fromIndex < currentActions.size()) {
            OrchestratorAction moved = currentActions.remove(fromIndex);
            int insertAt = toIndex;
            if (fromIndex < toIndex) {
                insertAt--;
            }
            insertAt = Math.max(0, Math.min(currentActions.size(), insertAt));
            currentActions.add(insertAt, moved);
            syncCurrentActionsToJson();
            if (actionListWidget != null) {
                actionListWidget.updateEntries(currentActions);
            }
        }
    }

    public void deleteAction(int index) {
        if (index >= 0 && index < currentActions.size()) {
            currentActions.remove(index);
            syncCurrentActionsToJson();
            actionListWidget.updateEntries(currentActions);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }
        int sidebarTop = mainTop + 32;
        int sidebarBottom = mainTop + mainHeight - 75;

        if (mouseX >= leftPanelLeft && mouseX <= leftPanelLeft + leftPanelWidth && mouseY >= sidebarTop && mouseY <= sidebarBottom) {
            double maxScroll = getMaxSidebarScroll();
            if (maxScroll > 0) {
                this.sidebarScrollAmount = Math.max(0, Math.min(this.sidebarScrollAmount - amount * 24, maxScroll));
                updateSidebarButtons();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }
        if (button == 0) {
            double maxScroll = getMaxSidebarScroll();
            int sidebarTop = mainTop + 32;
            int sidebarBottom = mainTop + mainHeight - 75;

            // Check sidebar scrollbar drag click
            if (maxScroll > 0) {
                int scrollbarX = leftPanelLeft + leftPanelWidth - 14;
                int scrollbarY = sidebarTop;
                int scrollbarW = 6;
                int scrollbarH = sidebarBottom - scrollbarY;
                int contentH = getActiveSequenceMap().size() * 24;
                int thumbH = Math.max(16, (int) ((float) scrollbarH / contentH * scrollbarH));
                int thumbY = scrollbarY + (int) ((float) sidebarScrollAmount / maxScroll * (scrollbarH - thumbH));

                if (mouseX >= scrollbarX - 2 && mouseX <= scrollbarX + scrollbarW + 2 && mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarH) {
                    this.isDraggingSidebarScrollbar = true;
                    this.dragOffsetY = mouseY - thumbY;
                    return true;
                }
            }

            // Dispatch clicks to visible sidebar sequence row buttons
            if (mouseX >= leftPanelLeft && mouseX <= leftPanelLeft + leftPanelWidth && mouseY >= sidebarTop && mouseY <= sidebarBottom) {
                for (SidebarRow row : sidebarRows) {
                    if (row.selectBtn.visible) {
                        if (row.selectBtn.mouseClicked(mouseX, mouseY, button)) return true;
                        for (CyberpunkButton cb : row.controlBtns) {
                            if (cb.mouseClicked(mouseX, mouseY, button)) return true;
                        }
                    }
                }
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
        if (this.isDraggingSidebarScrollbar) {
            double maxScroll = getMaxSidebarScroll();
            int scrollbarY = mainTop + 32;
            int scrollbarH = mainTop + mainHeight - 75 - scrollbarY;
            int contentH = getActiveSequenceMap().size() * 24;
            int thumbH = Math.max(16, (int) ((float) scrollbarH / contentH * scrollbarH));
            double scrollableH = scrollbarH - thumbH;

            if (scrollableH > 0) {
                double relativeY = mouseY - scrollbarY - dragOffsetY;
                this.sidebarScrollAmount = Math.max(0, Math.min(maxScroll, (relativeY / scrollableH) * maxScroll));
                updateSidebarButtons();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isDraggingSidebarScrollbar) {
            this.isDraggingSidebarScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void drawGridOverlay(GuiGraphics guiGraphics, int effW, int effH) {
        int gridSize = 32;
        long time = System.currentTimeMillis();
        int offsetX = (int) ((time / 40) % gridSize);
        int offsetY = (int) ((time / 40) % gridSize);

        for (int x = -gridSize + offsetX; x < effW + gridSize; x += gridSize) {
            guiGraphics.fill(x, 0, x + 1, effH, 0x1200E5FF);
        }
        for (int y = -gridSize + offsetY; y < effH + gridSize; y += gridSize) {
            guiGraphics.fill(0, y, effW, y + 1, 0x1200E5FF);
        }
    }

    private void drawTopHeader(GuiGraphics guiGraphics, int effW) {
        guiGraphics.fill(0, 0, effW, 32, 0xEE04080D);
        guiGraphics.fill(0, 31, effW, 32, CYAN_MAIN);

        guiGraphics.fill(12, 10, 24, 26, CYAN_MAIN);
        guiGraphics.drawString(this.font, Component.literal("COMMAND ORCHESTRATOR").withStyle(ChatFormatting.BOLD), 30, 14, CYAN_MAIN, false);

        // Active Sequence File Badge in Header
        String modeTag = isClientMode ? "[CLIENT] " : "[SERVER] ";
        boolean isUnsaved = hasUnsavedChanges();
        net.dandare21.fracturedutils.network.packet.S2CSyncSequenceTelemetryPacket.SequenceTelemetryData telemetry = getActiveTelemetry();
        boolean isRunning = telemetry != null;

        String badgeText = "FILE: " + modeTag + (currentFileName != null ? currentFileName : "NONE");
        if (isUnsaved) {
            badgeText += " * [UNSAVED]";
        }
        if (isRunning) {
            badgeText += " [▶ " + telemetry.getState().toUpperCase() + "]";
        }

        int badgeColor = isRunning ? 0xFF00FF55 : (isUnsaved ? 0xFFFFAA00 : CYAN_MAIN);
        int badgeWidth = this.font.width(badgeText) + 16;
        int badgeX = (effW - badgeWidth) / 2;

        drawBorderBox(guiGraphics, badgeX, 6, badgeWidth, 24, badgeColor, 0xFF050B10);
        guiGraphics.drawCenteredString(this.font, Component.literal(badgeText).withStyle(ChatFormatting.BOLD), badgeX + (badgeWidth / 2), 14, badgeColor);
    }

    private void drawBorderBox(GuiGraphics guiGraphics, int x, int y, int w, int h, int borderColor, int fillColor) {
        guiGraphics.fill(x, y, x + w, y + h, fillColor);
        guiGraphics.fill(x, y, x + w, y + 1, borderColor);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        guiGraphics.fill(x, y, x + 1, y + h, borderColor);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateSidebarButtons();

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

        // 1. Deep Black Background
        guiGraphics.fill(0, 0, effWidth, effHeight, CYAN_BG);

        // 2. Wireframe Grid Overlay with Diagonal Scroll
        drawGridOverlay(guiGraphics, effWidth, effHeight);

        // 3. Top Header Bar
        drawTopHeader(guiGraphics, effWidth);

        // 4. Left Panel Container & Header
        Map<String, String> activeMap = getActiveSequenceMap();
        drawBorderBox(guiGraphics, leftPanelLeft, mainTop, leftPanelWidth, mainHeight, CARD_BORDER, 0x7708121B);
        String sideTitle = (isClientMode ? "CLIENT" : "SERVER") + " SEQUENCES (" + activeMap.size() + ")";
        guiGraphics.drawString(this.font, Component.literal(sideTitle).withStyle(ChatFormatting.BOLD), leftPanelLeft + 8, mainTop + 11, CYAN_MAIN, false);
        guiGraphics.fill(leftPanelLeft + 6, mainTop + 26, leftPanelLeft + leftPanelWidth - 6, mainTop + 27, 0x4400E5FF);

        // 5. Render Sidebar File Buttons (Clipped strictly within sidebar bounds)
        int sidebarTop = mainTop + 32;
        int sidebarBottom = mainTop + mainHeight - 75;
        guiGraphics.enableScissor((int) ((leftPanelLeft + 4) * scale), (int) (sidebarTop * scale), (int) ((leftPanelLeft + leftPanelWidth - 4) * scale), (int) (sidebarBottom * scale));
        for (SidebarRow row : sidebarRows) {
            if (row.selectBtn.visible) {
                row.selectBtn.render(guiGraphics, scaledMouseX, scaledMouseY, partialTick);
                for (CyberpunkButton cb : row.controlBtns) {
                    cb.render(guiGraphics, scaledMouseX, scaledMouseY, partialTick);
                }
            }
        }
        guiGraphics.disableScissor();

        // 6. Render Sidebar Cyberpunk Scrollbar if sequence files overflow
        double maxScroll = getMaxSidebarScroll();
        if (maxScroll > 0) {
            int scrollbarX = leftPanelLeft + leftPanelWidth - 14;
            int scrollbarY = sidebarTop;
            int scrollbarW = 6;
            int scrollbarH = sidebarBottom - sidebarTop;

            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0x77050B10);
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 1, scrollbarY + scrollbarH, 0xAA00E5FF);
            guiGraphics.fill(scrollbarX + scrollbarW - 1, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0xAA00E5FF);

            int contentH = activeMap.size() * 24;
            int thumbH = Math.max(16, (int) ((float) scrollbarH / contentH * scrollbarH));
            int thumbY = scrollbarY + (int) ((float) sidebarScrollAmount / maxScroll * (scrollbarH - thumbH));

            int thumbColor = isDraggingSidebarScrollbar ? 0xFF00FFFF : CYAN_MAIN;
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, thumbColor);
        }

        // 7. Render New File Container & Input Box Frame
        int newFileY = mainTop + mainHeight - 65;
        drawBorderBox(guiGraphics, leftPanelLeft + 10, newFileY, leftPanelWidth - 20, 24, 0xAA00E5FF, 0xEE08121B);

        // 8. Render Right Panel Container & Header
        drawBorderBox(guiGraphics, rightPanelLeft, mainTop, rightPanelWidth, mainHeight, CARD_BORDER, 0x7708121B);

        boolean isUnsaved = hasUnsavedChanges();
        net.dandare21.fracturedutils.network.packet.S2CSyncSequenceTelemetryPacket.SequenceTelemetryData telemetry = getActiveTelemetry();
        boolean isRunning = telemetry != null;

        String seqTitle = "ACTION SEQUENCE: " + currentFileName.toUpperCase();
        if (isUnsaved) {
            seqTitle += " * [UNSAVED CHANGES]";
        }
        if (isRunning) {
            seqTitle += " [▶ EXECUTING - " + telemetry.getState().toUpperCase() + "]";
        }
        int titleColor = isRunning ? 0xFF00FF55 : (isUnsaved ? 0xFFFFAA00 : CYAN_MAIN);
        guiGraphics.drawString(this.font, Component.literal(seqTitle).withStyle(ChatFormatting.BOLD), rightPanelLeft + 12, mainTop + 12, titleColor, false);
        guiGraphics.fill(rightPanelLeft + 10, mainTop + 26, rightPanelLeft + rightPanelWidth - 10, mainTop + 27, 0x4400E5FF);

        // Render Save Feedback Message Toast if active
        if (saveFeedbackMessage != null) {
            long timePassed = System.currentTimeMillis() - saveFeedbackTime;
            if (timePassed < 3000) {
                guiGraphics.drawString(this.font, saveFeedbackMessage, rightPanelLeft + 12, mainTop + mainHeight - 50, 0xFF00FFCC, false);
            } else {
                saveFeedbackMessage = null;
            }
        }

        super.render(guiGraphics, scaledMouseX, scaledMouseY, partialTick);
        guiGraphics.pose().popPose();
    }
}
