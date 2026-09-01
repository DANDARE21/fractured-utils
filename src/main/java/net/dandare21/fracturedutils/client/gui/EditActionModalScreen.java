package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.orchestrator.action.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class EditActionModalScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int RED_CANCEL = 0xFFFF3355;

    public enum DelayUnit {
        TICKS, SECONDS, MINUTES
    }

    private final Screen parentScreen;
    private OrchestratorAction action;
    private final Consumer<OrchestratorAction> onSave;

    private String actionType;
    private String waitUntilType = "delay";
    private DelayUnit delayUnit = DelayUnit.TICKS;

    private CyberpunkDropdown<String> actionTypeDropdown;
    private CyberpunkDropdown<String> subActionTypeDropdown;
    private CyberpunkDropdown<DelayUnit> unitDropdown;
    private CyberpunkDropdown<String> musicSequenceDropdown;

    private EditBox inputField;
    private EditBox xField;
    private EditBox yField;
    private EditBox zField;
    private EditBox yawField;
    private EditBox pitchField;
    private EditBox labelField;
    private EditBox radiusField;
    private EditBox targetSelectorField;
    private CyberpunkCheckbox requireAllPlayersCheckbox;
    private CyberpunkCheckbox opsOnlyVisibilityCheckbox;
    private CyberpunkCheckbox areaOpsOnlyCheckbox;
    private CyberpunkCheckbox showRadiusAreaCheckbox;
    private EditBox nameField;
    private EditBox descriptionField;
    private CyberpunkCheckbox showActiveWaitCheckbox;
    private CyberpunkButton setMyPositionButton;
    private CommandSuggestions commandSuggestions;

    public EditActionModalScreen(Screen parentScreen, OrchestratorAction action, Consumer<OrchestratorAction> onSave) {
        super(Component.literal("Edit Action"));
        this.parentScreen = parentScreen;
        this.action = action;
        this.onSave = onSave;

        if (action != null) {
            String t = action.getType();
            if (t.equalsIgnoreCase("delay") || t.equalsIgnoreCase("wait_until")) {
                this.actionType = "wait_until";
                this.waitUntilType = "delay";
            } else if (t.equalsIgnoreCase("await_trigger")) {
                this.actionType = "await_trigger";
            } else if (t.equalsIgnoreCase("play_music_sequence") || t.equalsIgnoreCase("music_sequence")) {
                this.actionType = "play_music_sequence";
            } else {
                this.actionType = t;
            }
        } else {
            this.actionType = "command";
        }

        if (action instanceof WaitUntilAction wua) {
            this.waitUntilType = wua.getWaitType();
            int ticks = wua.getTicks();
            if (ticks > 0 && ticks % 1200 == 0) {
                this.delayUnit = DelayUnit.MINUTES;
            } else if (ticks > 0 && ticks % 20 == 0) {
                this.delayUnit = DelayUnit.SECONDS;
            } else {
                this.delayUnit = DelayUnit.TICKS;
            }
        } else if (action instanceof DelayAction da) {
            int ticks = da.getTicks();
            if (ticks > 0 && ticks % 1200 == 0) {
                this.delayUnit = DelayUnit.MINUTES;
            } else if (ticks > 0 && ticks % 20 == 0) {
                this.delayUnit = DelayUnit.SECONDS;
            } else {
                this.delayUnit = DelayUnit.TICKS;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double getLayoutScale() {
        int targetW = 380;
        int targetH = 300;
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

        int panelWidth = 360;
        int panelHeight = 285;
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        if (action == null) {
            action = createActionForType(actionType);
        }

        // --- 1. Action Type Selection Dropdown ---
        List<CyberpunkDropdown.DropdownEntry<String>> actionEntries = new ArrayList<>();
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("command", Component.literal("Command Action"), Component.literal("Execute console command (%player% supported)")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("checkpoint", Component.literal("Checkpoint Action"), Component.literal("Set checkpoint location; teleports team & rewinds sequence on wipe")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("new_objective", Component.literal("New Objective"), Component.literal("Set mission objective text on HUD with optional active wait tracking")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("end_objective", Component.literal("End Objective"), Component.literal("Clear current active objective on HUD")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("play_music_sequence", Component.literal("Play Music Sequence"), Component.literal("Play a music sequence JSON synced to event music track")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("wait_until", Component.literal("Wait Until Action"), Component.literal("Pause sequence until condition is met")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("await_trigger", Component.literal("Await Trigger"), Component.literal("Wait for external trigger ID event")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("fork_sequence", Component.literal("Fork Sequence"), Component.literal("Asynchronously start sub-sequence")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("run_sequence", Component.literal("Run Sequence"), Component.literal("Synchronously execute sub-sequence")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("stall_parent", Component.literal("Stall Parent"), Component.literal("Stall execution of parent sequence")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("resume_parent", Component.literal("Resume Parent"), Component.literal("Resume execution of parent sequence")));

        this.actionTypeDropdown = new CyberpunkDropdown<>(left + 20, top + 34, panelWidth - 40, 20, Component.literal("Action Type"));
        this.actionTypeDropdown.setOptions(actionEntries);
        this.actionTypeDropdown.selectByValue(actionType);
        this.actionTypeDropdown.setMaxVisibleItems(5);
        this.actionTypeDropdown.setItemHeight(24);
        this.actionTypeDropdown.setOnOpenListener(() -> {
            if (subActionTypeDropdown != null) subActionTypeDropdown.setOpen(false);
            if (unitDropdown != null) unitDropdown.setOpen(false);
        });
        this.actionTypeDropdown.setOnSelect(entry -> {
            String newType = entry.getValue();
            if (!newType.equalsIgnoreCase(this.actionType)) {
                applyInputValue();
                this.actionType = newType;
                if (newType.equalsIgnoreCase("await_trigger")) {
                    this.waitUntilType = "trigger";
                }
                this.action = createActionForType(newType);
                this.rebuildWidgets();
            }
        });
        this.addRenderableWidget(this.actionTypeDropdown);

        // --- 2. Subaction Selection Dropdown (Only for Wait Until) ---
        if (actionType.equalsIgnoreCase("wait_until")) {
            List<CyberpunkDropdown.DropdownEntry<String>> subEntries = new ArrayList<>();
            subEntries.add(new CyberpunkDropdown.DropdownEntry<>("delay", Component.literal("Delay Duration"), Component.literal("Wait for specific ticks/seconds/minutes")));
            subEntries.add(new CyberpunkDropdown.DropdownEntry<>("proximity", Component.literal("Player Proximity Marker"), Component.literal("Spawns marker entity at (X, Y, Z) and waits for player radius")));
            subEntries.add(new CyberpunkDropdown.DropdownEntry<>("trigger", Component.literal("Event Trigger"), Component.literal("Wait for /orchestrator trigger event")));
            subEntries.add(new CyberpunkDropdown.DropdownEntry<>("operator_action", Component.literal("Operator Action Button"), Component.literal("Display interactive action button on HUD")));
            subEntries.add(new CyberpunkDropdown.DropdownEntry<>("dialog", Component.literal("Dialog End"), Component.literal("Wait until active dialog sequence finishes")));
            subEntries.add(new CyberpunkDropdown.DropdownEntry<>("video", Component.literal("Video / Cutscene End"), Component.literal("Wait until active video/cinematic ends")));
            subEntries.add(new CyberpunkDropdown.DropdownEntry<>("waiting_room", Component.literal("Waiting Room End"), Component.literal("Wait until active waiting room phase finishes")));
            subEntries.add(new CyberpunkDropdown.DropdownEntry<>("waiting_room_ready", Component.literal("Waiting Room All Ready"), Component.literal("Wait until all players in waiting room click ready")));
            subEntries.add(new CyberpunkDropdown.DropdownEntry<>("downloads", Component.literal("Cutscene Downloads End"), Component.literal("Wait until all players finish downloading remaining cutscenes")));

            this.subActionTypeDropdown = new CyberpunkDropdown<>(left + 20, top + 60, panelWidth - 40, 20, Component.literal("Subaction Condition"));
            this.subActionTypeDropdown.setOptions(subEntries);
            this.subActionTypeDropdown.selectByValue(waitUntilType);
            this.subActionTypeDropdown.setMaxVisibleItems(5);
            this.subActionTypeDropdown.setItemHeight(24);
            this.subActionTypeDropdown.setOnOpenListener(() -> {
                if (actionTypeDropdown != null) actionTypeDropdown.setOpen(false);
                if (unitDropdown != null) unitDropdown.setOpen(false);
            });
            this.subActionTypeDropdown.setOnSelect(entry -> {
                String newSub = entry.getValue();
                applyInputValue();
                this.waitUntilType = newSub;
                if (action instanceof WaitUntilAction wua) {
                    wua.setWaitType(newSub);
                }
                this.rebuildWidgets();
            });
            this.addRenderableWidget(this.subActionTypeDropdown);
        } else {
            this.subActionTypeDropdown = null;
        }

        // --- 3. Time Unit Dropdown (For Wait Until -> Delay) ---
        boolean isDelayMode = actionType.equalsIgnoreCase("wait_until") && waitUntilType.equalsIgnoreCase("delay");
        if (isDelayMode) {
            List<CyberpunkDropdown.DropdownEntry<DelayUnit>> unitEntries = new ArrayList<>();
            unitEntries.add(new CyberpunkDropdown.DropdownEntry<>(DelayUnit.TICKS, Component.literal("TICKS"), Component.literal("1 tick = 1/20 sec")));
            unitEntries.add(new CyberpunkDropdown.DropdownEntry<>(DelayUnit.SECONDS, Component.literal("SECONDS"), Component.literal("1 sec = 20 ticks")));
            unitEntries.add(new CyberpunkDropdown.DropdownEntry<>(DelayUnit.MINUTES, Component.literal("MINUTES"), Component.literal("1 min = 1200 ticks")));

            this.unitDropdown = new CyberpunkDropdown<>(left + panelWidth - 145, top + 86, 125, 18, Component.literal("Unit"));
            this.unitDropdown.setOptions(unitEntries);
            this.unitDropdown.selectByValue(delayUnit);
            this.unitDropdown.setMaxVisibleItems(3);
            this.unitDropdown.setItemHeight(20);
            this.unitDropdown.setOnOpenListener(() -> {
                if (actionTypeDropdown != null) actionTypeDropdown.setOpen(false);
                if (subActionTypeDropdown != null) subActionTypeDropdown.setOpen(false);
            });
            this.unitDropdown.setOnSelect(entry -> switchDelayUnit(entry.getValue()));
            this.addRenderableWidget(this.unitDropdown);
        } else {
            this.unitDropdown = null;
        }

        // --- 4. Input Field Setup ---
        int fieldY = (actionType.equalsIgnoreCase("wait_until") && waitUntilType.equalsIgnoreCase("delay")) ? top + 130 : (actionType.equalsIgnoreCase("wait_until") ? top + 116 : top + 120);
        this.inputField = new EditBox(this.font, left + 25, fieldY, panelWidth - 50, 18, Component.literal("Input"));
        this.inputField.setMaxLength(512);
        this.inputField.setBordered(false);
        this.inputField.setTextColor(0xFFFFFFFF);

        if (actionType.equalsIgnoreCase("command")) {
            if (action instanceof CommandAction ca) {
                this.inputField.setValue(ca.getRun());
            } else {
                this.inputField.setValue("say Hello %player%");
            }
            this.addRenderableWidget(this.inputField);

            this.commandSuggestions = new CommandSuggestions(
                    this.minecraft, this, this.inputField, this.font,
                    true, true, 0, 7, true, 0xEE081622
            );
            this.commandSuggestions.setAllowSuggestions(true);
            this.commandSuggestions.updateCommandInfo();

            this.inputField.setResponder(text -> {
                if (this.commandSuggestions != null) {
                    this.commandSuggestions.updateCommandInfo();
                }
            });
        } else if (actionType.equalsIgnoreCase("await_trigger")) {
            this.commandSuggestions = null;
            String trig = (action instanceof AwaitTriggerAction ata) ? ata.getTriggerId() : ((action instanceof WaitUntilAction wua) ? wua.getTriggerId() : "trigger_1");
            this.inputField.setValue(trig);
            this.addRenderableWidget(this.inputField);
        } else if (actionType.equalsIgnoreCase("wait_until")) {
            this.commandSuggestions = null;
            if (action instanceof WaitUntilAction wua) {
                if (waitUntilType.equalsIgnoreCase("delay")) {
                    updateDelayInputField(wua.getTicks());
                    this.addRenderableWidget(this.inputField);
                } else if (waitUntilType.equalsIgnoreCase("operator_action")) {
                    this.inputField.setValue(wua.getLabel());
                    this.addRenderableWidget(this.inputField);
                } else if (waitUntilType.equalsIgnoreCase("dialog") || waitUntilType.equalsIgnoreCase("dialog_end")) {
                    this.inputField.setValue(wua.getTriggerId());
                    this.addRenderableWidget(this.inputField);
                } else if (waitUntilType.equalsIgnoreCase("trigger")) {
                    this.inputField.setValue(wua.getTriggerId().isEmpty() ? "trigger_1" : wua.getTriggerId());
                    this.addRenderableWidget(this.inputField);
                } else if (waitUntilType.equalsIgnoreCase("proximity") || waitUntilType.equalsIgnoreCase("marker") || waitUntilType.equalsIgnoreCase("player_proximity") || waitUntilType.equalsIgnoreCase("area")) {
                    double defaultX = 0.0, defaultY = 64.0, defaultZ = 0.0, defaultRadius = 3.0;
                    boolean defaultRequireAll = false;
                    boolean defaultMarkerOpsOnly = true;
                    boolean defaultAreaOpsOnly = true;
                    boolean defaultShowArea = true;
                    String defaultTargetSelector = "@a";
                    if (action instanceof WaitUntilAction w) {
                        defaultX = w.getX();
                        defaultY = w.getY();
                        defaultZ = w.getZ();
                        defaultRadius = w.getRadius();
                        defaultRequireAll = w.isRequireAllPlayers();
                        defaultMarkerOpsOnly = w.isOpsOnlyVisibility();
                        defaultAreaOpsOnly = w.isAreaOpsOnlyVisibility();
                        defaultShowArea = w.isShowRadiusArea();
                        defaultTargetSelector = w.getTargetSelector();
                    }

                    int boxW = 70;
                    int boxH = 18;
                    int boxY = top + 108;

                    this.xField = new EditBox(this.font, left + 22, boxY, boxW - 4, boxH, Component.literal("X"));
                    this.xField.setBordered(false);
                    this.xField.setValue(String.format(Locale.US, "%.1f", defaultX));
                    this.addRenderableWidget(this.xField);

                    this.yField = new EditBox(this.font, left + 102, boxY, boxW - 4, boxH, Component.literal("Y"));
                    this.yField.setBordered(false);
                    this.yField.setValue(String.format(Locale.US, "%.1f", defaultY));
                    this.addRenderableWidget(this.yField);

                    this.zField = new EditBox(this.font, left + 182, boxY, boxW - 4, boxH, Component.literal("Z"));
                    this.zField.setBordered(false);
                    this.zField.setValue(String.format(Locale.US, "%.1f", defaultZ));
                    this.addRenderableWidget(this.zField);

                    this.radiusField = new EditBox(this.font, left + 262, boxY, boxW - 4, boxH, Component.literal("Radius"));
                    this.radiusField.setBordered(false);
                    this.radiusField.setValue(String.format(Locale.US, "%.1f", defaultRadius));
                    this.addRenderableWidget(this.radiusField);

                    this.setMyPositionButton = new CyberpunkButton(
                            left + 20, top + 130, 130, 18,
                            Component.literal("📍 SET TO MY POS"),
                            b -> {
                                if (this.minecraft != null && this.minecraft.player != null) {
                                    double px = this.minecraft.player.getX();
                                    double py = this.minecraft.player.getY();
                                    double pz = this.minecraft.player.getZ();
                                    if (xField != null) xField.setValue(String.format(Locale.US, "%.1f", px));
                                    if (yField != null) yField.setValue(String.format(Locale.US, "%.1f", py));
                                    if (zField != null) zField.setValue(String.format(Locale.US, "%.1f", pz));
                                }
                            }, CYAN_MAIN, false, Component.literal("Copy your player's current X, Y, Z coordinates into fields")
                    );
                    this.addRenderableWidget(this.setMyPositionButton);

                    this.targetSelectorField = new EditBox(this.font, left + 162, top + 130, panelWidth - 182, boxH, Component.literal("Target Selector"));
                    this.targetSelectorField.setBordered(false);
                    this.targetSelectorField.setValue(defaultTargetSelector != null ? defaultTargetSelector : "@a");
                    this.addRenderableWidget(this.targetSelectorField);

                    this.requireAllPlayersCheckbox = new CyberpunkCheckbox(
                            left + 20, top + 152, panelWidth - 40, 18,
                            Component.literal("Require ALL Players inside Radius (Default: ANY Player)"),
                            defaultRequireAll, null
                    );
                    this.addRenderableWidget(this.requireAllPlayersCheckbox);

                    this.opsOnlyVisibilityCheckbox = new CyberpunkCheckbox(
                            left + 20, top + 172, panelWidth - 40, 18,
                            Component.literal("Show Marker Icon to OPS ONLY (Unchecked: All Players)"),
                            defaultMarkerOpsOnly, null
                    );
                    this.addRenderableWidget(this.opsOnlyVisibilityCheckbox);

                    this.showRadiusAreaCheckbox = new CyberpunkCheckbox(
                            left + 20, top + 192, panelWidth - 40, 18,
                            Component.literal("Render Gradient Area Cylinder Mesh around Radius"),
                            defaultShowArea, null
                    );
                    this.addRenderableWidget(this.showRadiusAreaCheckbox);

                    this.areaOpsOnlyCheckbox = new CyberpunkCheckbox(
                            left + 20, top + 212, panelWidth - 40, 18,
                            Component.literal("Show Area Cylinder Mesh to OPS ONLY (Unchecked: All Players)"),
                            defaultAreaOpsOnly, null
                    );
                    this.addRenderableWidget(this.areaOpsOnlyCheckbox);
                }
            } else if (action instanceof DelayAction da) {
                updateDelayInputField(da.getTicks());
                this.addRenderableWidget(this.inputField);
            }
        } else if (actionType.equalsIgnoreCase("fork_sequence")) {
            this.commandSuggestions = null;
            if (action instanceof ForkSequenceAction fsa) {
                String val = fsa.getStartIndex() > 0 ? fsa.getFile() + " " + (fsa.getStartIndex() + 1) : fsa.getFile();
                this.inputField.setValue(val);
            } else {
                this.inputField.setValue("sub_sequence.json");
            }
            this.addRenderableWidget(this.inputField);
        } else if (actionType.equalsIgnoreCase("run_sequence")) {
            this.commandSuggestions = null;
            if (action instanceof RunSequenceAction rsa) {
                String val = rsa.getStartIndex() > 0 ? rsa.getFile() + " " + (rsa.getStartIndex() + 1) : rsa.getFile();
                this.inputField.setValue(val);
            } else {
                this.inputField.setValue("sub_sequence.json");
            }
            this.addRenderableWidget(this.inputField);
        } else if (actionType.equalsIgnoreCase("checkpoint")) {
            this.commandSuggestions = null;
            double defaultX = 0.0, defaultY = 64.0, defaultZ = 0.0;
            float defaultYaw = 0.0f, defaultPitch = 0.0f;
            String defaultLabel = "";
            String defaultTargetSelector = "@a";
            if (action instanceof CheckpointAction cp) {
                defaultX = cp.getX();
                defaultY = cp.getY();
                defaultZ = cp.getZ();
                defaultYaw = cp.getYaw();
                defaultPitch = cp.getPitch();
                defaultLabel = cp.getLabel();
                defaultTargetSelector = cp.getTargetSelector();
            }

            int boxH = 18;

            // Row 1: X, Y, Z
            int r1Y = top + 105;
            int boxW3 = 95;

            this.xField = new EditBox(this.font, left + 22, r1Y, boxW3 - 4, boxH, Component.literal("X"));
            this.xField.setBordered(false);
            this.xField.setValue(String.format(Locale.US, "%.1f", defaultX));
            this.addRenderableWidget(this.xField);

            this.yField = new EditBox(this.font, left + 132, r1Y, boxW3 - 4, boxH, Component.literal("Y"));
            this.yField.setBordered(false);
            this.yField.setValue(String.format(Locale.US, "%.1f", defaultY));
            this.addRenderableWidget(this.yField);

            this.zField = new EditBox(this.font, left + 242, r1Y, boxW3 - 4, boxH, Component.literal("Z"));
            this.zField.setBordered(false);
            this.zField.setValue(String.format(Locale.US, "%.1f", defaultZ));
            this.addRenderableWidget(this.zField);

            // Row 2: Yaw & Pitch
            int r2Y = top + 147;
            int boxW2 = 145;

            this.yawField = new EditBox(this.font, left + 22, r2Y, boxW2 - 4, boxH, Component.literal("Yaw"));
            this.yawField.setBordered(false);
            this.yawField.setValue(String.format(Locale.US, "%.1f", defaultYaw));
            this.addRenderableWidget(this.yawField);

            this.pitchField = new EditBox(this.font, left + 192, r2Y, boxW2 - 4, boxH, Component.literal("Pitch"));
            this.pitchField.setBordered(false);
            this.pitchField.setValue(String.format(Locale.US, "%.1f", defaultPitch));
            this.addRenderableWidget(this.pitchField);

            // Row 3: Checkpoint Label
            int r3Y = top + 189;
            this.labelField = new EditBox(this.font, left + 22, r3Y, panelWidth - 44, boxH, Component.literal("Label"));
            this.labelField.setBordered(false);
            this.labelField.setValue(defaultLabel != null ? defaultLabel : "");
            this.addRenderableWidget(this.labelField);

            // Row 4: Auto-Fill Button & Target Selector
            this.setMyPositionButton = new CyberpunkButton(
                    left + 20, top + 224, 180, 20,
                    Component.literal("📍 SET TO MY POS"),
                    b -> {
                        if (this.minecraft != null && this.minecraft.player != null) {
                            double px = this.minecraft.player.getX();
                            double py = this.minecraft.player.getY();
                            double pz = this.minecraft.player.getZ();
                            float yaw = this.minecraft.player.getYRot();
                            float pitch = this.minecraft.player.getXRot();
                            if (xField != null) xField.setValue(String.format(Locale.US, "%.1f", px));
                            if (yField != null) yField.setValue(String.format(Locale.US, "%.1f", py));
                            if (zField != null) zField.setValue(String.format(Locale.US, "%.1f", pz));
                            if (yawField != null) yawField.setValue(String.format(Locale.US, "%.1f", yaw));
                            if (pitchField != null) pitchField.setValue(String.format(Locale.US, "%.1f", pitch));
                        }
                    }, CYAN_MAIN, false, Component.literal("Copy player position & facing angles into checkpoint parameters")
            );
            this.addRenderableWidget(this.setMyPositionButton);

            this.targetSelectorField = new EditBox(this.font, left + 212, top + 225, 124, boxH, Component.literal("Target Selector"));
            this.targetSelectorField.setBordered(false);
            this.targetSelectorField.setValue(defaultTargetSelector != null ? defaultTargetSelector : "@a");
            this.addRenderableWidget(this.targetSelectorField);
        } else if (actionType.equalsIgnoreCase("new_objective")) {
            this.commandSuggestions = null;
            String defaultName = "New Objective";
            String defaultDesc = "";
            boolean defaultShowWait = false;
            if (action instanceof NewObjectiveAction noa) {
                defaultName = noa.getName();
                defaultDesc = noa.getDescription();
                defaultShowWait = noa.isShowActiveWait();
            }

            int boxH = 18;
            this.nameField = new EditBox(this.font, left + 25, top + 105, panelWidth - 50, boxH, Component.literal("Name"));
            this.nameField.setBordered(false);
            this.nameField.setValue(defaultName);
            this.addRenderableWidget(this.nameField);

            this.descriptionField = new EditBox(this.font, left + 25, top + 147, panelWidth - 50, boxH, Component.literal("Description"));
            this.descriptionField.setBordered(false);
            this.descriptionField.setValue(defaultDesc);
            this.addRenderableWidget(this.descriptionField);

            this.showActiveWaitCheckbox = new CyberpunkCheckbox(
                    left + 20, top + 185, panelWidth - 40, 18,
                    Component.literal("Show Active Wait Status on HUD (if wait action active)"),
                    defaultShowWait, null
            );
            this.addRenderableWidget(this.showActiveWaitCheckbox);
        } else if (actionType.equalsIgnoreCase("play_music_sequence")) {
            this.commandSuggestions = null;
            String defaultSeqFile = "music_sequence.json";
            boolean defaultAwait = false;
            if (action instanceof PlayMusicSequenceAction pmsa) {
                defaultSeqFile = pmsa.getSequenceFile();
                defaultAwait = pmsa.isAwaitCompletion();
            }

            List<String> availableMusicSeqs = net.dandare21.fracturedutils.sound.sequence.MusicSequenceManager.getInstance().getSequenceFileNames();
            Set<String> allFiles = new java.util.LinkedHashSet<>(availableMusicSeqs);
            if (defaultSeqFile != null && !defaultSeqFile.isEmpty()) {
                allFiles.add(defaultSeqFile);
            }

            List<CyberpunkDropdown.DropdownEntry<String>> seqEntries = new ArrayList<>();
            for (String file : allFiles) {
                seqEntries.add(new CyberpunkDropdown.DropdownEntry<>(file, Component.literal(file), Component.literal("Music Sequence File")));
            }

            if (seqEntries.isEmpty()) {
                seqEntries.add(new CyberpunkDropdown.DropdownEntry<>("new_music_sequence.json", Component.literal("new_music_sequence.json")));
            }

            this.musicSequenceDropdown = new CyberpunkDropdown<>(left + 20, top + 116, panelWidth - 40, 20, Component.literal("Select Music Sequence"));
            this.musicSequenceDropdown.setOptions(seqEntries);
            this.musicSequenceDropdown.selectByValue(defaultSeqFile);
            this.musicSequenceDropdown.setMaxVisibleItems(4);
            this.musicSequenceDropdown.setItemHeight(22);
            this.musicSequenceDropdown.setOnOpenListener(() -> {
                if (actionTypeDropdown != null) actionTypeDropdown.setOpen(false);
                if (subActionTypeDropdown != null) subActionTypeDropdown.setOpen(false);
                if (unitDropdown != null) unitDropdown.setOpen(false);
            });
            this.addRenderableWidget(this.musicSequenceDropdown);

            this.showActiveWaitCheckbox = new CyberpunkCheckbox(
                    left + 20, top + 155, panelWidth - 40, 18,
                    Component.literal("Wait until Music Sequence finishes before continuing"),
                    defaultAwait, null
            );
            this.addRenderableWidget(this.showActiveWaitCheckbox);
        } else if (actionType.equalsIgnoreCase("end_objective")) {
            this.commandSuggestions = null;
        } else {
            this.commandSuggestions = null;
        }

        // --- 5. Save & Cancel Buttons ---
        int bottomY = top + panelHeight - 28;
        this.addRenderableWidget(new CyberpunkButton(left + panelWidth / 2 - 95, bottomY, 90, 24, Component.literal("SAVE"), b -> {
            applyInputValue();
            if (onSave != null) {
                onSave.accept(action);
            }
            if (this.minecraft != null) {
                this.minecraft.setScreen(parentScreen);
            }
        }, CYAN_MAIN, false, Component.literal("Save action changes")));

        this.addRenderableWidget(new CyberpunkButton(left + panelWidth / 2 + 5, bottomY, 90, 24, Component.literal("CANCEL"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parentScreen);
            }
        }, RED_CANCEL, false, Component.literal("Cancel action editing")));
    }

    private void switchDelayUnit(DelayUnit newUnit) {
        if (this.delayUnit == newUnit) return;
        int currentTicks = calculateDelayTicks();
        this.delayUnit = newUnit;
        if (action instanceof WaitUntilAction wua) {
            wua.setTicks(currentTicks);
        } else if (action instanceof DelayAction da) {
            da.setTicks(currentTicks);
        }
        updateDelayInputField(currentTicks);
    }

    private void updateDelayInputField(int totalTicks) {
        if (inputField == null) return;
        if (delayUnit == DelayUnit.MINUTES) {
            double mins = totalTicks / 1200.0;
            inputField.setValue(mins == (int) mins ? String.valueOf((int) mins) : String.format(Locale.US, "%.2f", mins));
        } else if (delayUnit == DelayUnit.SECONDS) {
            double secs = totalTicks / 20.0;
            inputField.setValue(secs == (int) secs ? String.valueOf((int) secs) : String.format(Locale.US, "%.1f", secs));
        } else {
            inputField.setValue(String.valueOf(totalTicks));
        }
    }

    private int calculateDelayTicks() {
        if (inputField == null) return 0;
        String val = inputField.getValue().trim();
        if (val.isEmpty()) return 0;
        try {
            double d = Double.parseDouble(val);
            if (delayUnit == DelayUnit.MINUTES) {
                return (int) Math.round(d * 1200);
            } else if (delayUnit == DelayUnit.SECONDS) {
                return (int) Math.round(d * 20);
            } else {
                return (int) Math.round(d);
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private OrchestratorAction createActionForType(String type) {
        return switch (type.toLowerCase()) {
            case "checkpoint" -> new CheckpointAction(0.0, 64.0, 0.0, 0.0f, 0.0f, "");
            case "new_objective" -> new NewObjectiveAction("New Objective", "Description...", true);
            case "end_objective" -> new EndObjectiveAction();
            case "play_music_sequence", "music_sequence" -> new PlayMusicSequenceAction("music_sequence.json", false);
            case "wait_until" -> new WaitUntilAction(waitUntilType, 20, "", "Resume Sequence");
            case "await_trigger" -> new AwaitTriggerAction("trigger_1");
            case "fork_sequence" -> new ForkSequenceAction("sub_sequence.json");
            case "run_sequence" -> new RunSequenceAction("sub_sequence.json");
            case "stall_parent" -> new StallParentAction();
            case "resume_parent" -> new ResumeParentAction();
            default -> new CommandAction("say Hello %player%");
        };
    }

    private void applyInputValue() {
        boolean isProximityMode = actionType.equalsIgnoreCase("wait_until") &&
                (waitUntilType.equalsIgnoreCase("proximity") || waitUntilType.equalsIgnoreCase("marker") || waitUntilType.equalsIgnoreCase("player_proximity") || waitUntilType.equalsIgnoreCase("area"));

        String val = inputField != null ? inputField.getValue().trim() : "";

        if (action instanceof CommandAction ca) {
            ca.setRun(val);
        } else if (action instanceof CheckpointAction cp) {
            if (xField != null) try { cp.setX(Double.parseDouble(xField.getValue().trim())); } catch (Exception ignored) {}
            if (yField != null) try { cp.setY(Double.parseDouble(yField.getValue().trim())); } catch (Exception ignored) {}
            if (zField != null) try { cp.setZ(Double.parseDouble(zField.getValue().trim())); } catch (Exception ignored) {}
            if (yawField != null) try { cp.setYaw(Float.parseFloat(yawField.getValue().trim())); } catch (Exception ignored) {}
            if (pitchField != null) try { cp.setPitch(Float.parseFloat(pitchField.getValue().trim())); } catch (Exception ignored) {}
            if (labelField != null) cp.setLabel(labelField.getValue().trim());
            if (targetSelectorField != null) cp.setTargetSelector(targetSelectorField.getValue().trim());
        } else if (action instanceof NewObjectiveAction noa) {
            if (nameField != null) noa.setName(nameField.getValue().trim());
            if (descriptionField != null) noa.setDescription(descriptionField.getValue().trim());
            if (showActiveWaitCheckbox != null) noa.setShowActiveWait(showActiveWaitCheckbox.isChecked());
        } else if (action instanceof PlayMusicSequenceAction pmsa) {
            if (musicSequenceDropdown != null && musicSequenceDropdown.getSelectedValue() != null) {
                pmsa.setSequenceFile(musicSequenceDropdown.getSelectedValue());
            } else {
                pmsa.setSequenceFile(val);
            }
            if (showActiveWaitCheckbox != null) {
                pmsa.setAwaitCompletion(showActiveWaitCheckbox.isChecked());
            }
        } else if (action instanceof AwaitTriggerAction ata) {
            ata.setTriggerId(val);
        } else if (action instanceof WaitUntilAction wua) {
            wua.setWaitType(waitUntilType);
            if (waitUntilType.equalsIgnoreCase("delay")) {
                wua.setTicks(calculateDelayTicks());
            } else if (waitUntilType.equalsIgnoreCase("operator_action")) {
                wua.setLabel(val);
                wua.setTriggerId("");
            } else if (waitUntilType.equalsIgnoreCase("dialog") || waitUntilType.equalsIgnoreCase("dialog_end")) {
                wua.setTriggerId(val);
            } else if (waitUntilType.equalsIgnoreCase("trigger")) {
                wua.setTriggerId(val);
            } else if (isProximityMode) {
                if (xField != null) try { wua.setX(Double.parseDouble(xField.getValue().trim())); } catch (Exception ignored) {}
                if (yField != null) try { wua.setY(Double.parseDouble(yField.getValue().trim())); } catch (Exception ignored) {}
                if (zField != null) try { wua.setZ(Double.parseDouble(zField.getValue().trim())); } catch (Exception ignored) {}
                if (radiusField != null) try { wua.setRadius(Double.parseDouble(radiusField.getValue().trim())); } catch (Exception ignored) {}
                if (targetSelectorField != null) wua.setTargetSelector(targetSelectorField.getValue().trim());
                if (requireAllPlayersCheckbox != null) {
                    wua.setRequireAllPlayers(requireAllPlayersCheckbox.isChecked());
                }
                if (opsOnlyVisibilityCheckbox != null) {
                    wua.setOpsOnlyVisibility(opsOnlyVisibilityCheckbox.isChecked());
                }
                if (showRadiusAreaCheckbox != null) {
                    wua.setShowRadiusArea(showRadiusAreaCheckbox.isChecked());
                }
                if (areaOpsOnlyCheckbox != null) {
                    wua.setAreaOpsOnlyVisibility(areaOpsOnlyCheckbox.isChecked());
                }
                wua.setTriggerId("");
            } else {
                wua.setTriggerId("");
            }
        } else if (action instanceof DelayAction da) {
            da.setTicks(calculateDelayTicks());
        } else if (action instanceof ForkSequenceAction fsa) {
            parseSubsequenceInput(val, fsa::setFile, fsa::setStartIndex);
        } else if (action instanceof RunSequenceAction rsa) {
            parseSubsequenceInput(val, rsa::setFile, rsa::setStartIndex);
        }
    }

    private void parseSubsequenceInput(String val, Consumer<String> setFile, Consumer<Integer> setStartIndex) {
        if (val == null || val.isBlank()) {
            setFile.accept("");
            setStartIndex.accept(0);
            return;
        }
        String[] parts = val.trim().split("\\s+");
        setFile.accept(parts[0]);
        int startIndex = 0;
        if (parts.length >= 2) {
            try {
                int num = Integer.parseInt(parts[1]);
                startIndex = Math.max(0, num - 1);
            } catch (NumberFormatException ignored) {}
        }
        setStartIndex.accept(startIndex);
    }

    private double getSuggestionOffsetY() {
        int panelHeight = 285;
        int top = (this.height - panelHeight) / 2;
        int fieldY = top + 120;
        return (fieldY + 24) - (this.height - 12);
    }

    private boolean isDropdownOpen() {
        return (actionTypeDropdown != null && actionTypeDropdown.isOpen()) ||
                (subActionTypeDropdown != null && subActionTypeDropdown.isOpen()) ||
                (unitDropdown != null && unitDropdown.isOpen()) ||
                (musicSequenceDropdown != null && musicSequenceDropdown.isOpen());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isDropdownOpen() && this.commandSuggestions != null && this.commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (musicSequenceDropdown != null && musicSequenceDropdown.mouseScrolled(mouseX, mouseY, amount)) return true;
        if (subActionTypeDropdown != null && subActionTypeDropdown.mouseScrolled(mouseX, mouseY, amount)) return true;
        if (unitDropdown != null && unitDropdown.mouseScrolled(mouseX, mouseY, amount)) return true;
        if (actionTypeDropdown != null && actionTypeDropdown.mouseScrolled(mouseX, mouseY, amount)) return true;

        if (this.commandSuggestions != null && this.commandSuggestions.mouseScrolled(amount)) {
            return true;
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
        if (musicSequenceDropdown != null && musicSequenceDropdown.isOpen()) {
            if (musicSequenceDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        }
        if (subActionTypeDropdown != null && subActionTypeDropdown.isOpen()) {
            if (subActionTypeDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        }
        if (unitDropdown != null && unitDropdown.isOpen()) {
            if (unitDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        }
        if (actionTypeDropdown != null && actionTypeDropdown.isOpen()) {
            if (actionTypeDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        }

        if (this.commandSuggestions != null) {
            double offsetY = getSuggestionOffsetY();
            if (this.commandSuggestions.mouseClicked(mouseX, mouseY - offsetY, button)) {
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
        if (musicSequenceDropdown != null && musicSequenceDropdown.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (subActionTypeDropdown != null && subActionTypeDropdown.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (unitDropdown != null && unitDropdown.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (actionTypeDropdown != null && actionTypeDropdown.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }
        if (musicSequenceDropdown != null && musicSequenceDropdown.mouseReleased(mouseX, mouseY, button)) return true;
        if (subActionTypeDropdown != null && subActionTypeDropdown.mouseReleased(mouseX, mouseY, button)) return true;
        if (unitDropdown != null && unitDropdown.mouseReleased(mouseX, mouseY, button)) return true;
        if (actionTypeDropdown != null && actionTypeDropdown.mouseReleased(mouseX, mouseY, button)) return true;

        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void drawGridOverlay(GuiGraphics graphics) {
        int step = 24;
        int color = 0x08FFFFFF;
        for (int x = 0; x < this.width; x += step) {
            graphics.fill(x, 0, x + 1, this.height, color);
        }
        for (int y = 0; y < this.height; y += step) {
            graphics.fill(0, y, this.width, y + 1, color);
        }
    }

    private void drawBorderBox(GuiGraphics graphics, int x, int y, int w, int h, int borderColor, int fillColor) {
        graphics.fill(x, y, x + w, y + h, fillColor);
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        double scale = getLayoutScale();
        graphics.pose().pushPose();
        int scaledMouseX = mouseX;
        int scaledMouseY = mouseY;
        if (scale < 1.0) {
            graphics.pose().scale((float) scale, (float) scale, 1.0f);
            scaledMouseX = (int) (mouseX / scale);
            scaledMouseY = (int) (mouseY / scale);
        }

        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        graphics.fill(0, 0, effWidth, effHeight, CYAN_BG);
        drawGridOverlay(graphics);

        int panelWidth = 360;
        int panelHeight = 285;
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        drawBorderBox(graphics, left, top, panelWidth, panelHeight, CYAN_MAIN, 0xEE060C12);

        // Header Title Bar
        graphics.fill(left, top, left + panelWidth, top + 26, 0xEE081622);
        graphics.fill(left, top + 25, left + panelWidth, top + 26, CYAN_MAIN);
        graphics.drawString(this.font, "EDIT ACTION: " + actionType.toUpperCase(), left + 12, top + 8, CYAN_MAIN, false);

        boolean isCommandInvalid = false;
        if (actionType.equalsIgnoreCase("command") && inputField != null) {
            isCommandInvalid = !CommandAction.isValidCommand(this.minecraft, inputField.getValue());
            this.inputField.setTextColor(isCommandInvalid ? 0xFFFF5555 : 0xFFFFFFFF);
        }

        // Subaction or Prompt Label logic
        String promptLabel;
        int promptColor;
        boolean showInputFieldBox = true;

        boolean isProximityMode = actionType.equalsIgnoreCase("wait_until") &&
                (waitUntilType.equalsIgnoreCase("proximity") || waitUntilType.equalsIgnoreCase("marker") || waitUntilType.equalsIgnoreCase("player_proximity") || waitUntilType.equalsIgnoreCase("area"));

        if (actionType.equalsIgnoreCase("command")) {
            promptLabel = isCommandInvalid ? "Command String (%player% supported) - ⚠ INVALID SYNTAX" : "Command String (%player% supported):";
            promptColor = isCommandInvalid ? 0xFFFF3355 : 0xFFAABBCC;
        } else if (actionType.equalsIgnoreCase("await_trigger")) {
            promptLabel = "Trigger ID Event Name:";
            promptColor = 0xFFAABBCC;
        } else if (actionType.equalsIgnoreCase("play_music_sequence")) {
            promptLabel = "Select Music Sequence JSON File:";
            promptColor = 0xFFAA55FF;
            showInputFieldBox = false;
        } else if (actionType.equalsIgnoreCase("wait_until")) {
            promptLabel = switch (waitUntilType.toLowerCase()) {
                case "delay" -> "Wait Duration (" + delayUnit.name() + "):";
                case "proximity", "marker", "player_proximity", "area" -> "Marker Location Coordinates & Radius:";
                case "trigger" -> "Trigger ID Event Name:";
                case "operator_action" -> "Operator Action Button Description:";
                case "dialog", "dialog_end", "dialogs_end", "dialog_sequence", "dialog_finish" -> "Optional Dialog File Name (leave empty for ANY active dialog):";
                case "video", "video_end", "cutscene", "cinematic" -> "Pauses sequence until active video/cinematic playback ends.";
                case "waiting_room", "waiting_room_end", "waitingroom" -> "Pauses sequence until active event waiting room ends.";
                case "waiting_room_ready", "waiting_room_all_ready", "waitingroom_ready" -> "Pauses sequence until all players in waiting room click ready.";
                case "downloads", "downloads_end", "cutscene_downloads", "video_downloads" -> "Pauses sequence until all players finish downloading remaining cutscenes.";
                default -> "Pauses sequence until trigger event occurs.";
            };
            promptColor = 0xFFAABBCC;
            if (isProximityMode || waitUntilType.equalsIgnoreCase("video") || waitUntilType.equalsIgnoreCase("waiting_room") || waitUntilType.equalsIgnoreCase("waiting_room_ready") || waitUntilType.equalsIgnoreCase("downloads")) {
                showInputFieldBox = false;
            }
        } else if (actionType.equalsIgnoreCase("stall_parent") || actionType.equalsIgnoreCase("resume_parent")) {
            promptLabel = actionType.equalsIgnoreCase("stall_parent") ? "No input required. Pauses parent sequence." : "No input required. Wakes up parent sequence.";
            promptColor = 0xFFAABBCC;
            showInputFieldBox = false;
        } else if (actionType.equalsIgnoreCase("checkpoint")) {
            promptLabel = "Checkpoint Action Parameters:";
            promptColor = 0xFFAABBCC;
            showInputFieldBox = false;
        } else if (actionType.equalsIgnoreCase("new_objective") || actionType.equalsIgnoreCase("end_objective")) {
            promptLabel = actionType.equalsIgnoreCase("new_objective") ? "Configure Mission Objective Parameters:" : "End Active Objective Action:";
            promptColor = 0xFF00E5FF;
            showInputFieldBox = false;
        } else {
            promptLabel = switch (actionType.toLowerCase()) {
                case "fork_sequence", "run_sequence" -> "Subsequence JSON File Name [Start Action #]:";
                default -> "Value:";
            };
            promptColor = 0xFFAABBCC;
        }

        // Draw Prompt Label / Headers
        if (isProximityMode) {
            graphics.drawString(this.font, "X Coord", left + 20, top + 94, 0xFFAABBCC, false);
            graphics.drawString(this.font, "Y Coord", left + 100, top + 94, 0xFFAABBCC, false);
            graphics.drawString(this.font, "Z Coord", left + 180, top + 94, 0xFFAABBCC, false);
            graphics.drawString(this.font, "Radius (m)", left + 260, top + 94, 0xFFAABBCC, false);

            drawBorderBox(graphics, left + 20, top + 106, 70, 20, 0xAA00E5FF, 0xEE08121B);
            drawBorderBox(graphics, left + 100, top + 106, 70, 20, 0xAA00E5FF, 0xEE08121B);
            drawBorderBox(graphics, left + 180, top + 106, 70, 20, 0xAA00E5FF, 0xEE08121B);
            drawBorderBox(graphics, left + 260, top + 106, 70, 20, 0xAA00E5FF, 0xEE08121B);

            graphics.drawString(this.font, "Target Selector (e.g. @a, @a[team=RED])", left + 160, top + 118, 0xFFAABBCC, false);
            drawBorderBox(graphics, left + 160, top + 128, panelWidth - 180, 20, 0xAA00E5FF, 0xEE08121B);
        } else if (actionType.equalsIgnoreCase("checkpoint")) {
            // Row 1: X, Y, Z
            graphics.drawString(this.font, "X Position", left + 20, top + 93, 0xFFAABBCC, false);
            graphics.drawString(this.font, "Y Position", left + 130, top + 93, 0xFFAABBCC, false);
            graphics.drawString(this.font, "Z Position", left + 240, top + 93, 0xFFAABBCC, false);
            drawBorderBox(graphics, left + 20, top + 104, 95, 20, 0xAA00E5FF, 0xEE08121B);
            drawBorderBox(graphics, left + 130, top + 104, 95, 20, 0xAA00E5FF, 0xEE08121B);
            drawBorderBox(graphics, left + 240, top + 104, 95, 20, 0xAA00E5FF, 0xEE08121B);

            // Row 2: Yaw, Pitch
            graphics.drawString(this.font, "Yaw Angle (Horizontal)", left + 20, top + 135, 0xFFAABBCC, false);
            graphics.drawString(this.font, "Pitch Angle (Vertical)", left + 190, top + 135, 0xFFAABBCC, false);
            drawBorderBox(graphics, left + 20, top + 146, 145, 20, 0xAA00E5FF, 0xEE08121B);
            drawBorderBox(graphics, left + 190, top + 146, 145, 20, 0xAA00E5FF, 0xEE08121B);

            // Row 3: Label
            graphics.drawString(this.font, "Checkpoint Label / Description (Optional)", left + 20, top + 177, 0xFFAABBCC, false);
            drawBorderBox(graphics, left + 20, top + 188, panelWidth - 40, 20, 0xAA00E5FF, 0xEE08121B);

            // Row 4: Target Selector
            graphics.drawString(this.font, "Target Selector", left + 210, top + 213, 0xFFAABBCC, false);
            drawBorderBox(graphics, left + 210, top + 223, 128, 20, 0xAA00E5FF, 0xEE08121B);
        } else if (actionType.equalsIgnoreCase("new_objective")) {
            graphics.drawString(this.font, "Objective Title / Header", left + 20, top + 93, 0xFFAABBCC, false);
            drawBorderBox(graphics, left + 20, top + 104, panelWidth - 40, 20, 0xAA00E5FF, 0xEE08121B);

            graphics.drawString(this.font, "Objective Description / Instructions", left + 20, top + 135, 0xFFAABBCC, false);
            drawBorderBox(graphics, left + 20, top + 146, panelWidth - 40, 20, 0xAA00E5FF, 0xEE08121B);
        } else if (actionType.equalsIgnoreCase("end_objective")) {
            graphics.drawString(this.font, "Clears current active objective from the HUD overlay.", left + 20, top + 114, 0xFFAABBCC, false);
        } else {
            int labelY = (actionType.equalsIgnoreCase("wait_until") && waitUntilType.equalsIgnoreCase("delay")) ? top + 114 : top + 104;
            graphics.drawString(this.font, promptLabel, left + 20, labelY, promptColor, false);
        }

        // Draw Delay Summary Breakdown
        boolean isDelayMode = actionType.equalsIgnoreCase("wait_until") && waitUntilType.equalsIgnoreCase("delay");
        if (isDelayMode) {
            int totalTicks = calculateDelayTicks();
            String summary = String.format(Locale.US, "= %d Ticks  |  %.1fs  |  %.2fm", totalTicks, totalTicks / 20.0f, totalTicks / 1200.0f);
            graphics.drawString(this.font, summary, left + 20, top + 152, CYAN_MAIN, false);
        }

        // Draw Input Box Frame if applicable
        if (showInputFieldBox) {
            int fieldY = (actionType.equalsIgnoreCase("wait_until") && waitUntilType.equalsIgnoreCase("delay")) ? top + 128 : (actionType.equalsIgnoreCase("wait_until") ? top + 114 : top + 118);
            int boxBorderColor = isCommandInvalid ? 0xFFFF3355 : 0xAA00E5FF;
            drawBorderBox(graphics, left + 20, fieldY, panelWidth - 40, 22, boxBorderColor, 0xEE08121B);
        }

        // Render base widgets (Buttons, EditBoxes, Dropdown base bars)
        super.render(graphics, scaledMouseX, scaledMouseY, partialTick);

        // Render Command Suggestions popup anchored underneath input field
        if (this.commandSuggestions != null) {
            double offsetY = getSuggestionOffsetY();
            graphics.pose().pushPose();
            graphics.pose().translate(0, offsetY, 350);
            this.commandSuggestions.render(graphics, scaledMouseX, scaledMouseY);
            graphics.pose().popPose();
        }

        // Render Dropdown overlays on top of everything!
        if (actionTypeDropdown != null) actionTypeDropdown.renderOverlay(graphics, scaledMouseX, scaledMouseY);
        if (subActionTypeDropdown != null) subActionTypeDropdown.renderOverlay(graphics, scaledMouseX, scaledMouseY);
        if (unitDropdown != null) unitDropdown.renderOverlay(graphics, scaledMouseX, scaledMouseY);
        if (musicSequenceDropdown != null) musicSequenceDropdown.renderOverlay(graphics, scaledMouseX, scaledMouseY);

        graphics.pose().popPose();
    }
}
