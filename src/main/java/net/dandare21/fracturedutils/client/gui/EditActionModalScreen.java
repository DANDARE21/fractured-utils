package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.orchestrator.action.*;
import net.dandare21.fracturedutils.puppet.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class EditActionModalScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BRIGHT = 0xFF33F0FF;
    private static final int CYAN_BG = 0xFF050A0F;
    private static final int PANEL_BG = 0xFA070E16;
    private static final int HEADER_BG = 0xEE091622;
    private static final int BORDER_CYAN = 0xFF00E5FF;
    private static final int BORDER_MUTED = 0xFF172D3D;
    private static final int TEXT_MUTED = 0xFF658A9F;
    private static final int TEXT_LABEL = 0xFFA0C2D4;
    private static final int RED_CANCEL = 0xFFFF2A6D;
    private static final int GREEN_VALID = 0xFF00FF88;

    public enum DelayUnit {
        TICKS, SECONDS, MINUTES
    }

    private final Screen parentScreen;
    private OrchestratorAction action;
    private final Consumer<OrchestratorAction> onSave;

    private String actionType;
    private String waitUntilType = "delay";
    private DelayUnit delayUnit = DelayUnit.TICKS;

    // Dropdowns
    private CyberpunkDropdown<String> actionTypeDropdown;
    private CyberpunkDropdown<String> subActionTypeDropdown;
    private CyberpunkDropdown<DelayUnit> unitDropdown;
    private CyberpunkDropdown<String> musicSequenceDropdown;
    private CyberpunkDropdown<String> nearbyEntityDropdown;
    private CyberpunkDropdown<String> puppetActionDropdown;

    // Fields
    private EditBox inputField;
    private EditBox xField;
    private EditBox yField;
    private EditBox zField;
    private EditBox yawField;
    private EditBox pitchField;
    private EditBox labelField;
    private EditBox radiusField;
    private EditBox targetSelectorField;
    private EditBox nameField;
    private EditBox descriptionField;
    private EditBox entityUuidField;
    private EditBox speedField;
    private EditBox windupTicksField;
    private EditBox durationTicksField;
    private EditBox lookTargetField;

    // Cyberpunk Matrix Cards (styled toggle cards)
    private CyberpunkMatrixCard suppressAiCard;
    private CyberpunkMatrixCard suppressNavCard;
    private CyberpunkMatrixCard suppressTargetingCard;
    private CyberpunkMatrixCard suppressLookCard;
    private CyberpunkMatrixCard suppressActionsCard;
    private CyberpunkMatrixCard puppetingActiveCard;

    private CyberpunkMatrixCard requireAllPlayersCard;
    private CyberpunkMatrixCard opsOnlyVisibilityCard;
    private CyberpunkMatrixCard areaOpsOnlyCard;
    private CyberpunkMatrixCard showRadiusAreaCard;
    private CyberpunkMatrixCard showActiveWaitCard;

    // Action Buttons
    private CyberpunkButton setMyPositionButton;
    private CyberpunkButton pickLookedEntityButton;
    private CyberpunkButton pickLookTargetButton;
    private CyberpunkButton saveConfigButton;
    private CyberpunkButton cancelConfigButton;
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
        int targetW = 468;
        int targetH = 320;
        if (this.width <= 0 || this.height <= 0) return 1.0;
        double scaleX = (double) this.width / targetW;
        double scaleY = (double) this.height / targetH;
        return Math.min(1.0, Math.min(scaleX, scaleY));
    }

    private String getCategoryBadgeForAction(String type) {
        return switch (type.toLowerCase()) {
            case "puppet_action", "puppet_move_to", "puppet_look_at", "puppet_stop_action" -> "SYS::PUPPET";
            case "puppet_suppress_ai" -> "SYS::OVERRIDE";
            case "command" -> "SYS::CONSOLE";
            case "wait_until", "await_trigger" -> "SYS::WAIT";
            case "checkpoint" -> "SYS::NAV";
            case "new_objective", "end_objective" -> "SYS::HUD";
            case "play_music_sequence" -> "SYS::AUDIO";
            case "fork_sequence", "run_sequence", "stall_parent", "resume_parent" -> "SYS::FLOW";
            default -> "SYS::CORE";
        };
    }

    private static boolean isValidUUID(String str) {
        if (str == null || str.isBlank()) return false;
        try {
            UUID.fromString(str.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private int getResolvedMatchesCount(String selector) {
        if (selector == null || selector.isBlank() || this.minecraft == null || this.minecraft.level == null) {
            return 0;
        }
        String sel = selector.trim();
        int count = 0;
        String typeFilter = null;
        if (sel.contains("type=")) {
            int idx = sel.indexOf("type=");
            int endIdx = sel.indexOf("]", idx);
            if (endIdx < 0) endIdx = sel.indexOf(",", idx);
            if (endIdx < 0) endIdx = sel.length();
            typeFilter = sel.substring(idx + 5, endIdx).trim();
        }

        for (Entity e : this.minecraft.level.entitiesForRendering()) {
            if (typeFilter != null && !typeFilter.isEmpty()) {
                String entityTypeStr = EntityType.getKey(e.getType()).toString();
                if (entityTypeStr.equalsIgnoreCase(typeFilter) || entityTypeStr.endsWith(":" + typeFilter)) {
                    count++;
                }
            } else {
                count++;
            }
        }
        return count;
    }

    @Override
    protected void init() {
        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        int panelWidth = 448;
        int panelHeight = 304;
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        if (action == null) {
            action = createActionForType(actionType);
        }

        this.puppetActionDropdown = null;
        this.subActionTypeDropdown = null;
        this.unitDropdown = null;
        this.musicSequenceDropdown = null;
        this.nearbyEntityDropdown = null;

        int colWidth = (panelWidth - 32) / 2; // ~208px each
        int col1Left = left + 12;
        int col2Left = left + panelWidth - 12 - colWidth;

        // --- 1. Step 01: Action Mode Selection Dropdown (Left Column) ---
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
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("puppet_action", Component.literal("Execute Puppet Action"), Component.literal("Execute registered puppet attack/routine")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("puppet_move_to", Component.literal("Puppet Move To"), Component.literal("Direct puppet pathfinding to coordinates")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("puppet_look_at", Component.literal("Puppet Look At"), Component.literal("Direct puppet look angle to target or coordinates")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("puppet_suppress_ai", Component.literal("Puppet Suppress AI"), Component.literal("Configure AI aspect suppression flags")));
        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("puppet_stop_action", Component.literal("Puppet Stop Action"), Component.literal("Stop active action & reset suppression")));

        this.actionTypeDropdown = new CyberpunkDropdown<>(col1Left, top + 46, colWidth, 20, Component.literal("Action Type"));
        this.actionTypeDropdown.setOptions(actionEntries);
        this.actionTypeDropdown.selectByValue(actionType);
        this.actionTypeDropdown.setMaxVisibleItems(6);
        this.actionTypeDropdown.setItemHeight(22);
        this.actionTypeDropdown.setOnOpenListener(() -> {
            if (subActionTypeDropdown != null) subActionTypeDropdown.setOpen(false);
            if (unitDropdown != null) unitDropdown.setOpen(false);
            if (musicSequenceDropdown != null) musicSequenceDropdown.setOpen(false);
            if (nearbyEntityDropdown != null) nearbyEntityDropdown.setOpen(false);
            if (puppetActionDropdown != null) puppetActionDropdown.setOpen(false);
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

        // --- 2. Step 02: Dynamic Contextual Second Dropdown (Right Column) ---
        boolean isPuppetActionType = actionType.equalsIgnoreCase("puppet_action") ||
                actionType.equalsIgnoreCase("puppet_move_to") ||
                actionType.equalsIgnoreCase("puppet_look_at") ||
                actionType.equalsIgnoreCase("puppet_suppress_ai") ||
                actionType.equalsIgnoreCase("puppet_stop_action");

        if (isPuppetActionType) {
            List<CyberpunkDropdown.DropdownEntry<String>> nearbyEntries = new ArrayList<>();
            nearbyEntries.add(new CyberpunkDropdown.DropdownEntry<>("", Component.literal("-- Select Nearby Entity --"), Component.literal("Scans loaded entities within 64 blocks")));

            if (this.minecraft != null && this.minecraft.level != null && this.minecraft.player != null) {
                Vec3 pPos = this.minecraft.player.position();
                AABB area = new AABB(pPos.x - 64, pPos.y - 64, pPos.z - 64, pPos.x + 64, pPos.y + 64, pPos.z + 64);
                List<Entity> nearby = new ArrayList<>(this.minecraft.level.getEntities((Entity) null, area, e -> e != this.minecraft.player));
                nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(this.minecraft.player)));

                for (Entity e : nearby) {
                    boolean isPuppet = e instanceof IPuppetEntity;
                    String name = e.getDisplayName().getString();
                    String typeStr = EntityType.getKey(e.getType()).toString();
                    String uuidStr = e.getUUID().toString();
                    double dist = Math.sqrt(e.distanceToSqr(this.minecraft.player));

                    String label = String.format(Locale.ROOT, "%s%s (%.1fm)", isPuppet ? "🎭 " : "", name, dist);
                    String details = typeStr + " | " + uuidStr;
                    nearbyEntries.add(new CyberpunkDropdown.DropdownEntry<>(uuidStr, Component.literal(label), Component.literal(details)));
                }
            }

            this.nearbyEntityDropdown = new CyberpunkDropdown<>(col2Left, top + 46, colWidth, 20, Component.literal("Select Nearby Entity"));
            this.nearbyEntityDropdown.setOptions(nearbyEntries);
            this.nearbyEntityDropdown.setMaxVisibleItems(5);
            this.nearbyEntityDropdown.setItemHeight(22);
            this.nearbyEntityDropdown.setOnOpenListener(() -> {
                if (actionTypeDropdown != null) actionTypeDropdown.setOpen(false);
                if (subActionTypeDropdown != null) subActionTypeDropdown.setOpen(false);
                if (unitDropdown != null) unitDropdown.setOpen(false);
                if (musicSequenceDropdown != null) musicSequenceDropdown.setOpen(false);
                if (puppetActionDropdown != null) puppetActionDropdown.setOpen(false);
            });
            this.nearbyEntityDropdown.setOnSelect(entry -> {
                String uuidStr = entry.getValue();
                if (uuidStr != null && !uuidStr.isEmpty()) {
                    if (entityUuidField != null) {
                        entityUuidField.setValue(uuidStr);
                    }
                    if (this.minecraft != null && this.minecraft.level != null) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            for (Entity e : this.minecraft.level.entitiesForRendering()) {
                                if (e.getUUID().equals(uuid)) {
                                    String typeStr = EntityType.getKey(e.getType()).toString();
                                    if (targetSelectorField != null) {
                                        targetSelectorField.setValue("@e[type=" + typeStr + ",limit=1,sort=nearest]");
                                    }
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    refreshPuppetActionSuggestions();
                }
            });
            this.addRenderableWidget(this.nearbyEntityDropdown);
        } else if (actionType.equalsIgnoreCase("wait_until")) {
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

            this.subActionTypeDropdown = new CyberpunkDropdown<>(col2Left, top + 46, colWidth, 20, Component.literal("Subaction Condition"));
            this.subActionTypeDropdown.setOptions(subEntries);
            this.subActionTypeDropdown.selectByValue(waitUntilType);
            this.subActionTypeDropdown.setMaxVisibleItems(5);
            this.subActionTypeDropdown.setItemHeight(22);
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
        } else if (actionType.equalsIgnoreCase("play_music_sequence")) {
            String defaultSeqFile = "music_sequence.json";
            if (action instanceof PlayMusicSequenceAction pmsa) {
                defaultSeqFile = pmsa.getSequenceFile();
            }

            List<String> availableMusicSeqs = net.dandare21.fracturedutils.sound.sequence.MusicSequenceManager.getInstance().getSequenceFileNames();
            Set<String> allFiles = new java.util.LinkedHashSet<>(availableMusicSeqs);
            if (defaultSeqFile != null && !defaultSeqFile.isEmpty()) {
                allFiles.add(defaultSeqFile);
            }

            List<CyberpunkDropdown.DropdownEntry<String>> seqEntries = new ArrayList<>();
            for (String file : allFiles) {
                seqEntries.add(new CyberpunkDropdown.DropdownEntry<>(file, Component.literal(file), Component.literal("Music Sequence JSON")));
            }
            if (seqEntries.isEmpty()) {
                seqEntries.add(new CyberpunkDropdown.DropdownEntry<>("new_music_sequence.json", Component.literal("new_music_sequence.json")));
            }

            this.musicSequenceDropdown = new CyberpunkDropdown<>(col2Left, top + 46, colWidth, 20, Component.literal("Select Music Sequence"));
            this.musicSequenceDropdown.setOptions(seqEntries);
            this.musicSequenceDropdown.selectByValue(defaultSeqFile);
            this.musicSequenceDropdown.setMaxVisibleItems(4);
            this.musicSequenceDropdown.setItemHeight(22);
            this.musicSequenceDropdown.setOnOpenListener(() -> {
                if (actionTypeDropdown != null) actionTypeDropdown.setOpen(false);
            });
            this.addRenderableWidget(this.musicSequenceDropdown);
        }

        // --- 3. Body Section & Parameters Setup ---
        int boxH = 18;
        this.inputField = new EditBox(this.font, left + 14, top + 98, panelWidth - 28, boxH, Component.literal("Input"));
        this.inputField.setMaxLength(512);
        this.inputField.setBordered(false);
        this.inputField.setTextColor(0xFFFFFFFF);

        if (actionType.equalsIgnoreCase("puppet_suppress_ai")) {
            this.commandSuggestions = null;
            boolean defaultAi = false, defaultNav = false, defaultTargeting = false, defaultLook = false, defaultActions = false, defaultActive = true;
            String defaultUuid = "", defaultSelector = "";
            if (action instanceof PuppetSuppressAction psa) {
                defaultAi = psa.isSuppressAi();
                defaultNav = psa.isSuppressNavigation();
                defaultTargeting = psa.isSuppressTargeting();
                defaultLook = psa.isSuppressLook();
                defaultActions = psa.isSuppressActions();
                defaultActive = psa.isPuppetingActive();
                defaultUuid = psa.getEntityUuid();
                defaultSelector = psa.getTargetSelector();
            }

            int cardW = colWidth;
            int cardH = 20;
            int r1Y = top + 90;
            int r2Y = top + 113;
            int r3Y = top + 136;

            this.suppressAiCard = new CyberpunkMatrixCard(col1Left, r1Y, cardW, cardH, Component.literal("Disable Mob AI"), defaultAi, null);
            this.addRenderableWidget(this.suppressAiCard);

            this.puppetingActiveCard = new CyberpunkMatrixCard(col2Left, r1Y, cardW, cardH, Component.literal("Puppeting Active Flag"), defaultActive, null);
            this.addRenderableWidget(this.puppetingActiveCard);

            this.suppressNavCard = new CyberpunkMatrixCard(col1Left, r2Y, cardW, cardH, Component.literal("Suppress Navigation"), defaultNav, null);
            this.addRenderableWidget(this.suppressNavCard);

            this.suppressTargetingCard = new CyberpunkMatrixCard(col2Left, r2Y, cardW, cardH, Component.literal("Suppress Targeting"), defaultTargeting, null);
            this.addRenderableWidget(this.suppressTargetingCard);

            this.suppressLookCard = new CyberpunkMatrixCard(col1Left, r3Y, cardW, cardH, Component.literal("Suppress Look Control"), defaultLook, null);
            this.addRenderableWidget(this.suppressLookCard);

            this.suppressActionsCard = new CyberpunkMatrixCard(col2Left, r3Y, cardW, cardH, Component.literal("Disable Puppet Actions"), defaultActions, null);
            this.addRenderableWidget(this.suppressActionsCard);

            // Row 4: Entity UUID
            int uuidY = top + 172;
            int uuidW = panelWidth - 146;
            this.entityUuidField = new EditBox(this.font, left + 14, uuidY + 1, uuidW, boxH, Component.literal("Entity UUID"));
            this.entityUuidField.setMaxLength(128);
            this.entityUuidField.setBordered(false);
            this.entityUuidField.setValue(defaultUuid);
            this.addRenderableWidget(this.entityUuidField);

            this.pickLookedEntityButton = new CyberpunkButton(
                    left + panelWidth - 124, uuidY - 1, 112, 20,
                    Component.literal("🎯 TARGET MOB"),
                    b -> {
                        Entity target = getLookedAtEntity();
                        if (target != null) {
                            if (entityUuidField != null) entityUuidField.setValue(target.getUUID().toString());
                            if (targetSelectorField != null) {
                                String typeStr = EntityType.getKey(target.getType()).toString();
                                targetSelectorField.setValue("@e[type=" + typeStr + ",limit=1,sort=nearest]");
                            }
                        }
                    }, CYAN_MAIN, false, Component.literal("Capture UUID and selector of entity under player crosshairs")
            );
            this.addRenderableWidget(this.pickLookedEntityButton);

            // Row 5: Target Selector
            int selY = top + 208;
            this.targetSelectorField = new EditBox(this.font, left + 14, selY + 1, panelWidth - 28, boxH, Component.literal("Target Selector"));
            this.targetSelectorField.setMaxLength(256);
            this.targetSelectorField.setBordered(false);
            this.targetSelectorField.setValue(defaultSelector);
            this.addRenderableWidget(this.targetSelectorField);

        } else if (actionType.equalsIgnoreCase("puppet_action")) {
            this.commandSuggestions = null;
            String defaultActionId = "", defaultUuid = "", defaultSelector = "";
            int defaultWindup = 0, defaultDuration = 20;
            if (action instanceof ExecutePuppetAction epa) {
                defaultActionId = epa.getActionId();
                defaultUuid = epa.getEntityUuid();
                defaultSelector = epa.getTargetSelector();
                defaultWindup = epa.getWindupTicks();
                defaultDuration = epa.getDurationTicks();
            }

            // Dropdown for registered puppet actions
            this.puppetActionDropdown = new CyberpunkDropdown<>(left + 12, top + 90, panelWidth - 24, 20, Component.literal("Select Puppet Action"));
            this.puppetActionDropdown.setMaxVisibleItems(4);
            this.puppetActionDropdown.setItemHeight(22);
            this.puppetActionDropdown.setOnOpenListener(() -> {
                if (actionTypeDropdown != null) actionTypeDropdown.setOpen(false);
                if (nearbyEntityDropdown != null) nearbyEntityDropdown.setOpen(false);
            });
            this.puppetActionDropdown.setOnSelect(entry -> {
                String actionIdStr = entry.getValue();
                if (actionIdStr != null && !actionIdStr.isEmpty() && inputField != null) {
                    inputField.setValue(actionIdStr);
                }
            });
            this.addRenderableWidget(this.puppetActionDropdown);

            int r2Y = top + 126;
            this.inputField.setX(left + 14);
            this.inputField.setY(r2Y + 1);
            this.inputField.setWidth(230);
            this.inputField.setHeight(boxH);
            this.inputField.setValue(defaultActionId);
            this.inputField.setResponder(text -> {
                if (this.puppetActionDropdown != null) {
                    this.puppetActionDropdown.selectByValue(text.trim());
                }
            });
            this.addRenderableWidget(this.inputField);

            this.windupTicksField = new EditBox(this.font, left + 258, r2Y + 1, 78, boxH, Component.literal("Windup"));
            this.windupTicksField.setBordered(false);
            this.windupTicksField.setValue(String.valueOf(defaultWindup));
            this.addRenderableWidget(this.windupTicksField);

            this.durationTicksField = new EditBox(this.font, left + 350, r2Y + 1, 84, boxH, Component.literal("Duration"));
            this.durationTicksField.setBordered(false);
            this.durationTicksField.setValue(String.valueOf(defaultDuration));
            this.addRenderableWidget(this.durationTicksField);

            int uuidY = top + 162;
            this.entityUuidField = new EditBox(this.font, left + 14, uuidY + 1, panelWidth - 146, boxH, Component.literal("Entity UUID"));
            this.entityUuidField.setMaxLength(128);
            this.entityUuidField.setBordered(false);
            this.entityUuidField.setValue(defaultUuid);
            this.entityUuidField.setResponder(text -> refreshPuppetActionSuggestions());
            this.addRenderableWidget(this.entityUuidField);

            this.pickLookedEntityButton = new CyberpunkButton(
                    left + panelWidth - 124, uuidY - 1, 112, 20,
                    Component.literal("🎯 TARGET MOB"),
                    b -> {
                        Entity target = getLookedAtEntity();
                        if (target != null) {
                            if (entityUuidField != null) entityUuidField.setValue(target.getUUID().toString());
                            if (targetSelectorField != null) {
                                String typeStr = EntityType.getKey(target.getType()).toString();
                                targetSelectorField.setValue("@e[type=" + typeStr + ",limit=1,sort=nearest]");
                            }
                            refreshPuppetActionSuggestions();
                        }
                    }, CYAN_MAIN, false, Component.literal("Capture UUID and selector of mob under crosshairs")
            );
            this.addRenderableWidget(this.pickLookedEntityButton);

            int selY = top + 198;
            this.targetSelectorField = new EditBox(this.font, left + 14, selY + 1, panelWidth - 28, boxH, Component.literal("Target Selector"));
            this.targetSelectorField.setMaxLength(256);
            this.targetSelectorField.setBordered(false);
            this.targetSelectorField.setValue(defaultSelector);
            this.targetSelectorField.setResponder(text -> refreshPuppetActionSuggestions());
            this.addRenderableWidget(this.targetSelectorField);

            refreshPuppetActionSuggestions();

        } else if (actionType.equalsIgnoreCase("puppet_move_to")) {
            this.commandSuggestions = null;
            double defaultX = 0.0, defaultY = 64.0, defaultZ = 0.0, defaultSpeed = 1.0;
            String defaultUuid = "", defaultSelector = "";
            if (action instanceof PuppetMoveToAction pmt) {
                defaultX = pmt.getX();
                defaultY = pmt.getY();
                defaultZ = pmt.getZ();
                defaultSpeed = pmt.getSpeed();
                defaultUuid = pmt.getEntityUuid();
                defaultSelector = pmt.getTargetSelector();
            }

            int r1Y = top + 90;
            int boxW = 66;
            this.xField = new EditBox(this.font, left + 14, r1Y + 1, boxW, boxH, Component.literal("X"));
            this.xField.setBordered(false);
            this.xField.setValue(String.format(Locale.US, "%.1f", defaultX));
            this.addRenderableWidget(this.xField);

            this.yField = new EditBox(this.font, left + 88, r1Y + 1, boxW, boxH, Component.literal("Y"));
            this.yField.setBordered(false);
            this.yField.setValue(String.format(Locale.US, "%.1f", defaultY));
            this.addRenderableWidget(this.yField);

            this.zField = new EditBox(this.font, left + 162, r1Y + 1, boxW, boxH, Component.literal("Z"));
            this.zField.setBordered(false);
            this.zField.setValue(String.format(Locale.US, "%.1f", defaultZ));
            this.addRenderableWidget(this.zField);

            this.speedField = new EditBox(this.font, left + 236, r1Y + 1, 62, boxH, Component.literal("Speed"));
            this.speedField.setBordered(false);
            this.speedField.setValue(String.format(Locale.US, "%.1f", defaultSpeed));
            this.addRenderableWidget(this.speedField);

            this.setMyPositionButton = new CyberpunkButton(
                    left + 308, r1Y - 1, 128, 20,
                    Component.literal("📍 SET MY POS"),
                    b -> {
                        if (this.minecraft != null && this.minecraft.player != null) {
                            double px = this.minecraft.player.getX();
                            double py = this.minecraft.player.getY();
                            double pz = this.minecraft.player.getZ();
                            if (xField != null) xField.setValue(String.format(Locale.US, "%.1f", px));
                            if (yField != null) yField.setValue(String.format(Locale.US, "%.1f", py));
                            if (zField != null) zField.setValue(String.format(Locale.US, "%.1f", pz));
                        }
                    }, CYAN_MAIN, false, Component.literal("Copy player position into target coordinates")
            );
            this.addRenderableWidget(this.setMyPositionButton);

            int uuidY = top + 138;
            this.entityUuidField = new EditBox(this.font, left + 14, uuidY + 1, panelWidth - 146, boxH, Component.literal("Entity UUID"));
            this.entityUuidField.setMaxLength(128);
            this.entityUuidField.setBordered(false);
            this.entityUuidField.setValue(defaultUuid);
            this.addRenderableWidget(this.entityUuidField);

            this.pickLookedEntityButton = new CyberpunkButton(
                    left + panelWidth - 124, uuidY - 1, 112, 20,
                    Component.literal("🎯 TARGET MOB"),
                    b -> {
                        Entity target = getLookedAtEntity();
                        if (target != null) {
                            if (entityUuidField != null) entityUuidField.setValue(target.getUUID().toString());
                            if (targetSelectorField != null) {
                                String typeStr = EntityType.getKey(target.getType()).toString();
                                targetSelectorField.setValue("@e[type=" + typeStr + ",limit=1,sort=nearest]");
                            }
                        }
                    }, CYAN_MAIN, false, Component.literal("Capture UUID and selector of mob under crosshairs")
            );
            this.addRenderableWidget(this.pickLookedEntityButton);

            int selY = top + 184;
            this.targetSelectorField = new EditBox(this.font, left + 14, selY + 1, panelWidth - 28, boxH, Component.literal("Target Selector"));
            this.targetSelectorField.setMaxLength(256);
            this.targetSelectorField.setBordered(false);
            this.targetSelectorField.setValue(defaultSelector);
            this.addRenderableWidget(this.targetSelectorField);

        } else if (actionType.equalsIgnoreCase("puppet_look_at")) {
            this.commandSuggestions = null;
            double defaultX = 0.0, defaultY = 64.0, defaultZ = 0.0;
            String defaultLookSelector = "", defaultUuid = "", defaultSelector = "";
            if (action instanceof PuppetLookAtAction pla) {
                defaultX = pla.getX();
                defaultY = pla.getY();
                defaultZ = pla.getZ();
                defaultLookSelector = pla.getLookTargetSelector();
                defaultUuid = pla.getEntityUuid();
                defaultSelector = pla.getTargetSelector();
            }

            int r1Y = top + 90;
            int boxW = 86;
            this.xField = new EditBox(this.font, left + 14, r1Y + 1, boxW, boxH, Component.literal("X"));
            this.xField.setBordered(false);
            this.xField.setValue(String.format(Locale.US, "%.1f", defaultX));
            this.addRenderableWidget(this.xField);

            this.yField = new EditBox(this.font, left + 110, r1Y + 1, boxW, boxH, Component.literal("Y"));
            this.yField.setBordered(false);
            this.yField.setValue(String.format(Locale.US, "%.1f", defaultY));
            this.addRenderableWidget(this.yField);

            this.zField = new EditBox(this.font, left + 206, r1Y + 1, boxW, boxH, Component.literal("Z"));
            this.zField.setBordered(false);
            this.zField.setValue(String.format(Locale.US, "%.1f", defaultZ));
            this.addRenderableWidget(this.zField);

            this.setMyPositionButton = new CyberpunkButton(
                    left + 308, r1Y - 1, 128, 20,
                    Component.literal("📍 SET MY POS"),
                    b -> {
                        if (this.minecraft != null && this.minecraft.player != null) {
                            double px = this.minecraft.player.getX();
                            double py = this.minecraft.player.getY();
                            double pz = this.minecraft.player.getZ();
                            if (xField != null) xField.setValue(String.format(Locale.US, "%.1f", px));
                            if (yField != null) yField.setValue(String.format(Locale.US, "%.1f", py));
                            if (zField != null) zField.setValue(String.format(Locale.US, "%.1f", pz));
                        }
                    }, CYAN_MAIN, false, Component.literal("Copy player position into target coordinates")
            );
            this.addRenderableWidget(this.setMyPositionButton);

            int lookY = top + 130;
            this.lookTargetField = new EditBox(this.font, left + 14, lookY + 1, panelWidth - 146, boxH, Component.literal("Look Target"));
            this.lookTargetField.setMaxLength(256);
            this.lookTargetField.setBordered(false);
            this.lookTargetField.setValue(defaultLookSelector);
            this.addRenderableWidget(this.lookTargetField);

            this.pickLookTargetButton = new CyberpunkButton(
                    left + panelWidth - 124, lookY - 1, 112, 20,
                    Component.literal("🎯 LOOK TARGET"),
                    b -> {
                        Entity target = getLookedAtEntity();
                        if (target != null && lookTargetField != null) {
                            lookTargetField.setValue(target.getUUID().toString());
                        } else if (lookTargetField != null) {
                            lookTargetField.setValue("@p");
                        }
                    }, CYAN_MAIN, false, Component.literal("Set look target to entity under crosshair (or @p)")
            );
            this.addRenderableWidget(this.pickLookTargetButton);

            int uuidY = top + 168;
            this.entityUuidField = new EditBox(this.font, left + 14, uuidY + 1, panelWidth - 146, boxH, Component.literal("Entity UUID"));
            this.entityUuidField.setMaxLength(128);
            this.entityUuidField.setBordered(false);
            this.entityUuidField.setValue(defaultUuid);
            this.addRenderableWidget(this.entityUuidField);

            this.pickLookedEntityButton = new CyberpunkButton(
                    left + panelWidth - 124, uuidY - 1, 112, 20,
                    Component.literal("🎯 TARGET MOB"),
                    b -> {
                        Entity target = getLookedAtEntity();
                        if (target != null) {
                            if (entityUuidField != null) entityUuidField.setValue(target.getUUID().toString());
                            if (targetSelectorField != null) {
                                String typeStr = EntityType.getKey(target.getType()).toString();
                                targetSelectorField.setValue("@e[type=" + typeStr + ",limit=1,sort=nearest]");
                            }
                        }
                    }, CYAN_MAIN, false, Component.literal("Capture UUID and selector of mob under crosshairs")
            );
            this.addRenderableWidget(this.pickLookedEntityButton);

            int selY = top + 206;
            this.targetSelectorField = new EditBox(this.font, left + 14, selY + 1, panelWidth - 28, boxH, Component.literal("Target Selector"));
            this.targetSelectorField.setMaxLength(256);
            this.targetSelectorField.setBordered(false);
            this.targetSelectorField.setValue(defaultSelector);
            this.addRenderableWidget(this.targetSelectorField);

        } else if (actionType.equalsIgnoreCase("puppet_stop_action")) {
            this.commandSuggestions = null;
            String defaultUuid = "", defaultSelector = "";
            if (action instanceof PuppetStopAction pst) {
                defaultUuid = pst.getEntityUuid();
                defaultSelector = pst.getTargetSelector();
            }

            int uuidY = top + 130;
            this.entityUuidField = new EditBox(this.font, left + 14, uuidY + 1, panelWidth - 146, boxH, Component.literal("Entity UUID"));
            this.entityUuidField.setMaxLength(128);
            this.entityUuidField.setBordered(false);
            this.entityUuidField.setValue(defaultUuid);
            this.addRenderableWidget(this.entityUuidField);

            this.pickLookedEntityButton = new CyberpunkButton(
                    left + panelWidth - 124, uuidY - 1, 112, 20,
                    Component.literal("🎯 TARGET MOB"),
                    b -> {
                        Entity target = getLookedAtEntity();
                        if (target != null) {
                            if (entityUuidField != null) entityUuidField.setValue(target.getUUID().toString());
                            if (targetSelectorField != null) {
                                String typeStr = EntityType.getKey(target.getType()).toString();
                                targetSelectorField.setValue("@e[type=" + typeStr + ",limit=1,sort=nearest]");
                            }
                        }
                    }, CYAN_MAIN, false, Component.literal("Capture UUID and selector of mob under crosshairs")
            );
            this.addRenderableWidget(this.pickLookedEntityButton);

            int selY = top + 178;
            this.targetSelectorField = new EditBox(this.font, left + 14, selY + 1, panelWidth - 28, boxH, Component.literal("Target Selector"));
            this.targetSelectorField.setMaxLength(256);
            this.targetSelectorField.setBordered(false);
            this.targetSelectorField.setValue(defaultSelector);
            this.addRenderableWidget(this.targetSelectorField);

        } else if (actionType.equalsIgnoreCase("command")) {
            if (action instanceof CommandAction ca) {
                this.inputField.setValue(ca.getRun());
            } else {
                this.inputField.setValue("say Hello %player%");
            }
            this.inputField.setX(left + 14);
            this.inputField.setY(top + 98);
            this.inputField.setWidth(panelWidth - 28);
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

            // Clickable Quick Insert Badges
            int snippetY = top + 130;
            int curX = left + 12;
            String[] tokens = new String[]{"%player%", "@a", "@p", "@s", "/title %player% actionbar", "/playsound"};
            for (String tok : tokens) {
                int btnW = this.font.width(tok) + 12;
                this.addRenderableWidget(new CyberpunkButton(curX, snippetY, btnW, 16, Component.literal(tok), b -> {
                    String current = inputField.getValue();
                    if (!current.isEmpty() && !current.endsWith(" ")) {
                        inputField.setValue(current + " " + tok);
                    } else {
                        inputField.setValue(current + tok);
                    }
                    inputField.moveCursorToEnd();
                }, 0xFF356A88, false, Component.literal("Click to append " + tok)));
                curX += btnW + 5;
            }

        } else if (actionType.equalsIgnoreCase("wait_until")) {
            this.commandSuggestions = null;
            if (action instanceof WaitUntilAction wua) {
                if (waitUntilType.equalsIgnoreCase("delay")) {
                    List<CyberpunkDropdown.DropdownEntry<DelayUnit>> unitEntries = new ArrayList<>();
                    unitEntries.add(new CyberpunkDropdown.DropdownEntry<>(DelayUnit.TICKS, Component.literal("TICKS"), Component.literal("1 tick = 1/20 sec")));
                    unitEntries.add(new CyberpunkDropdown.DropdownEntry<>(DelayUnit.SECONDS, Component.literal("SECONDS"), Component.literal("1 sec = 20 ticks")));
                    unitEntries.add(new CyberpunkDropdown.DropdownEntry<>(DelayUnit.MINUTES, Component.literal("MINUTES"), Component.literal("1 min = 1200 ticks")));

                    this.unitDropdown = new CyberpunkDropdown<>(left + panelWidth - 146, top + 98, 134, 20, Component.literal("Unit"));
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

                    this.inputField.setX(left + 14);
                    this.inputField.setY(top + 98);
                    this.inputField.setWidth(panelWidth - 168);
                    updateDelayInputField(wua.getTicks());
                    this.addRenderableWidget(this.inputField);

                } else if (waitUntilType.equalsIgnoreCase("operator_action")) {
                    this.inputField.setValue(wua.getLabel());
                    this.inputField.setX(left + 14);
                    this.inputField.setY(top + 98);
                    this.inputField.setWidth(panelWidth - 28);
                    this.addRenderableWidget(this.inputField);

                } else if (waitUntilType.equalsIgnoreCase("dialog") || waitUntilType.equalsIgnoreCase("dialog_end")) {
                    this.inputField.setValue(wua.getTriggerId());
                    this.inputField.setX(left + 14);
                    this.inputField.setY(top + 98);
                    this.inputField.setWidth(panelWidth - 28);
                    this.addRenderableWidget(this.inputField);

                } else if (waitUntilType.equalsIgnoreCase("trigger")) {
                    this.inputField.setValue(wua.getTriggerId().isEmpty() ? "trigger_1" : wua.getTriggerId());
                    this.inputField.setX(left + 14);
                    this.inputField.setY(top + 98);
                    this.inputField.setWidth(panelWidth - 28);
                    this.addRenderableWidget(this.inputField);

                } else if (waitUntilType.equalsIgnoreCase("proximity") || waitUntilType.equalsIgnoreCase("marker") || waitUntilType.equalsIgnoreCase("player_proximity") || waitUntilType.equalsIgnoreCase("area")) {
                    double defaultX = wua.getX(), defaultY = wua.getY(), defaultZ = wua.getZ(), defaultRadius = wua.getRadius();
                    boolean defaultRequireAll = wua.isRequireAllPlayers();
                    boolean defaultMarkerOpsOnly = wua.isOpsOnlyVisibility();
                    boolean defaultAreaOpsOnly = wua.isAreaOpsOnlyVisibility();
                    boolean defaultShowArea = wua.isShowRadiusArea();
                    String defaultTargetSelector = wua.getTargetSelector();

                    int r1Y = top + 90;
                    int coordW = 66;
                    this.xField = new EditBox(this.font, left + 14, r1Y + 1, coordW, boxH, Component.literal("X"));
                    this.xField.setBordered(false);
                    this.xField.setValue(String.format(Locale.US, "%.1f", defaultX));
                    this.addRenderableWidget(this.xField);

                    this.yField = new EditBox(this.font, left + 88, r1Y + 1, coordW, boxH, Component.literal("Y"));
                    this.yField.setBordered(false);
                    this.yField.setValue(String.format(Locale.US, "%.1f", defaultY));
                    this.addRenderableWidget(this.yField);

                    this.zField = new EditBox(this.font, left + 162, r1Y + 1, coordW, boxH, Component.literal("Z"));
                    this.zField.setBordered(false);
                    this.zField.setValue(String.format(Locale.US, "%.1f", defaultZ));
                    this.addRenderableWidget(this.zField);

                    this.radiusField = new EditBox(this.font, left + 236, r1Y + 1, 62, boxH, Component.literal("Radius"));
                    this.radiusField.setBordered(false);
                    this.radiusField.setValue(String.format(Locale.US, "%.1f", defaultRadius));
                    this.addRenderableWidget(this.radiusField);

                    this.setMyPositionButton = new CyberpunkButton(
                            left + 308, r1Y - 1, 128, 20,
                            Component.literal("📍 SET MY POS"),
                            b -> {
                                if (this.minecraft != null && this.minecraft.player != null) {
                                    double px = this.minecraft.player.getX();
                                    double py = this.minecraft.player.getY();
                                    double pz = this.minecraft.player.getZ();
                                    if (xField != null) xField.setValue(String.format(Locale.US, "%.1f", px));
                                    if (yField != null) yField.setValue(String.format(Locale.US, "%.1f", py));
                                    if (zField != null) zField.setValue(String.format(Locale.US, "%.1f", pz));
                                }
                            }, CYAN_MAIN, false, Component.literal("Copy player position into target coordinates")
                    );
                    this.addRenderableWidget(this.setMyPositionButton);

                    int selY = top + 128;
                    this.targetSelectorField = new EditBox(this.font, left + 14, selY + 1, panelWidth - 28, boxH, Component.literal("Target Selector"));
                    this.targetSelectorField.setMaxLength(256);
                    this.targetSelectorField.setBordered(false);
                    this.targetSelectorField.setValue(defaultTargetSelector != null ? defaultTargetSelector : "@a");
                    this.addRenderableWidget(this.targetSelectorField);

                    int cardW = colWidth;
                    int cardH = 20;
                    int m1Y = top + 164;
                    int m2Y = top + 188;

                    this.requireAllPlayersCard = new CyberpunkMatrixCard(col1Left, m1Y, cardW, cardH, Component.literal("Require ALL Players"), defaultRequireAll, null);
                    this.addRenderableWidget(this.requireAllPlayersCard);

                    this.opsOnlyVisibilityCard = new CyberpunkMatrixCard(col2Left, m1Y, cardW, cardH, Component.literal("Marker Icon: OPS ONLY"), defaultMarkerOpsOnly, null);
                    this.addRenderableWidget(this.opsOnlyVisibilityCard);

                    this.showRadiusAreaCard = new CyberpunkMatrixCard(col1Left, m2Y, cardW, cardH, Component.literal("Render Cylinder Mesh"), defaultShowArea, null);
                    this.addRenderableWidget(this.showRadiusAreaCard);

                    this.areaOpsOnlyCard = new CyberpunkMatrixCard(col2Left, m2Y, cardW, cardH, Component.literal("Cylinder Mesh: OPS ONLY"), defaultAreaOpsOnly, null);
                    this.addRenderableWidget(this.areaOpsOnlyCard);
                }
            }

        } else if (actionType.equalsIgnoreCase("await_trigger")) {
            this.commandSuggestions = null;
            String trig = (action instanceof AwaitTriggerAction ata) ? ata.getTriggerId() : "trigger_1";
            this.inputField.setValue(trig);
            this.inputField.setX(left + 14);
            this.inputField.setY(top + 98);
            this.inputField.setWidth(panelWidth - 28);
            this.addRenderableWidget(this.inputField);

        } else if (actionType.equalsIgnoreCase("checkpoint")) {
            this.commandSuggestions = null;
            double defaultX = 0.0, defaultY = 64.0, defaultZ = 0.0;
            float defaultYaw = 0.0f, defaultPitch = 0.0f;
            String defaultLabel = "", defaultTargetSelector = "@a";
            if (action instanceof CheckpointAction cp) {
                defaultX = cp.getX();
                defaultY = cp.getY();
                defaultZ = cp.getZ();
                defaultYaw = cp.getYaw();
                defaultPitch = cp.getPitch();
                defaultLabel = cp.getLabel();
                defaultTargetSelector = cp.getTargetSelector();
            }

            int r1Y = top + 90;
            int boxW = 86;
            this.xField = new EditBox(this.font, left + 14, r1Y + 1, boxW, boxH, Component.literal("X"));
            this.xField.setBordered(false);
            this.xField.setValue(String.format(Locale.US, "%.1f", defaultX));
            this.addRenderableWidget(this.xField);

            this.yField = new EditBox(this.font, left + 110, r1Y + 1, boxW, boxH, Component.literal("Y"));
            this.yField.setBordered(false);
            this.yField.setValue(String.format(Locale.US, "%.1f", defaultY));
            this.addRenderableWidget(this.yField);

            this.zField = new EditBox(this.font, left + 206, r1Y + 1, boxW, boxH, Component.literal("Z"));
            this.zField.setBordered(false);
            this.zField.setValue(String.format(Locale.US, "%.1f", defaultZ));
            this.addRenderableWidget(this.zField);

            this.setMyPositionButton = new CyberpunkButton(
                    left + 308, r1Y - 1, 128, 20,
                    Component.literal("📍 SET MY POS"),
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
                    }, CYAN_MAIN, false, Component.literal("Capture player position & facing angles")
            );
            this.addRenderableWidget(this.setMyPositionButton);

            int r2Y = top + 130;
            int angleW = (colWidth - 8) / 2;
            this.yawField = new EditBox(this.font, left + 14, r2Y + 1, angleW, boxH, Component.literal("Yaw"));
            this.yawField.setBordered(false);
            this.yawField.setValue(String.format(Locale.US, "%.1f", defaultYaw));
            this.addRenderableWidget(this.yawField);

            this.pitchField = new EditBox(this.font, left + 14 + angleW + 8, r2Y + 1, angleW, boxH, Component.literal("Pitch"));
            this.pitchField.setBordered(false);
            this.pitchField.setValue(String.format(Locale.US, "%.1f", defaultPitch));
            this.addRenderableWidget(this.pitchField);

            this.labelField = new EditBox(this.font, col2Left, r2Y + 1, colWidth, boxH, Component.literal("Label"));
            this.labelField.setMaxLength(256);
            this.labelField.setBordered(false);
            this.labelField.setValue(defaultLabel != null ? defaultLabel : "");
            this.addRenderableWidget(this.labelField);

            int selY = top + 172;
            this.targetSelectorField = new EditBox(this.font, left + 14, selY + 1, panelWidth - 28, boxH, Component.literal("Target Selector"));
            this.targetSelectorField.setMaxLength(256);
            this.targetSelectorField.setBordered(false);
            this.targetSelectorField.setValue(defaultTargetSelector != null ? defaultTargetSelector : "@a");
            this.addRenderableWidget(this.targetSelectorField);

        } else if (actionType.equalsIgnoreCase("new_objective")) {
            this.commandSuggestions = null;
            String defaultName = "New Objective", defaultDesc = "";
            boolean defaultShowWait = false;
            if (action instanceof NewObjectiveAction noa) {
                defaultName = noa.getName();
                defaultDesc = noa.getDescription();
                defaultShowWait = noa.isShowActiveWait();
            }

            this.nameField = new EditBox(this.font, left + 14, top + 98, panelWidth - 28, boxH, Component.literal("Name"));
            this.nameField.setMaxLength(256);
            this.nameField.setBordered(false);
            this.nameField.setValue(defaultName);
            this.addRenderableWidget(this.nameField);

            this.descriptionField = new EditBox(this.font, left + 14, top + 138, panelWidth - 28, boxH, Component.literal("Description"));
            this.descriptionField.setMaxLength(256);
            this.descriptionField.setBordered(false);
            this.descriptionField.setValue(defaultDesc);
            this.addRenderableWidget(this.descriptionField);

            this.showActiveWaitCard = new CyberpunkMatrixCard(left + 12, top + 172, panelWidth - 24, 20, Component.literal("Show Active Wait Tracker on HUD"), defaultShowWait, null);
            this.addRenderableWidget(this.showActiveWaitCard);

        } else if (actionType.equalsIgnoreCase("play_music_sequence")) {
            this.commandSuggestions = null;
            boolean defaultAwait = false;
            if (action instanceof PlayMusicSequenceAction pmsa) {
                defaultAwait = pmsa.isAwaitCompletion();
            }

            this.showActiveWaitCard = new CyberpunkMatrixCard(left + 12, top + 104, panelWidth - 24, 22, Component.literal("Block sequence execution until music sequence completes"), defaultAwait, null);
            this.addRenderableWidget(this.showActiveWaitCard);

        } else if (actionType.equalsIgnoreCase("fork_sequence") || actionType.equalsIgnoreCase("run_sequence")) {
            this.commandSuggestions = null;
            String val = "sub_sequence.json";
            if (action instanceof ForkSequenceAction fsa) {
                val = fsa.getStartIndex() > 0 ? fsa.getFile() + " " + (fsa.getStartIndex() + 1) : fsa.getFile();
            } else if (action instanceof RunSequenceAction rsa) {
                val = rsa.getStartIndex() > 0 ? rsa.getFile() + " " + (rsa.getStartIndex() + 1) : rsa.getFile();
            }
            this.inputField.setValue(val);
            this.inputField.setX(left + 14);
            this.inputField.setY(top + 98);
            this.inputField.setWidth(panelWidth - 28);
            this.addRenderableWidget(this.inputField);
        }

        // --- 4. Window Close Button (Top Right) ---
        this.addRenderableWidget(new CyberpunkButton(left + panelWidth - 24, top + 7, 16, 16, Component.literal("✕"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parentScreen);
            }
        }, 0xFF58849E, false, Component.literal("Close (Discard Changes)")));

        // --- 5. Save & Cancel Footer Buttons (Matching Image) ---
        int bottomY = top + panelHeight - 28;
        this.cancelConfigButton = new CyberpunkButton(
                left + panelWidth - 198, bottomY, 78, 22,
                Component.literal("CANCEL"),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(parentScreen);
                    }
                }, RED_CANCEL, false, Component.literal("Discard changes and return")
        );
        this.addRenderableWidget(this.cancelConfigButton);

        this.saveConfigButton = new CyberpunkButton(
                left + panelWidth - 114, bottomY, 102, 22,
                Component.literal("✔ SAVE CONFIG"),
                b -> saveAndExit(),
                CYAN_MAIN, false, Component.literal("Commit changes and save action")
        );
        this.saveConfigButton.setSolidPrimary(true);
        this.addRenderableWidget(this.saveConfigButton);
    }

    private void saveAndExit() {
        applyInputValue();
        if (onSave != null) {
            onSave.accept(action);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
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
            case "puppet_action", "execute_puppet_action" -> new ExecutePuppetAction("", "", "", 20);
            case "puppet_move_to", "puppet_move" -> new PuppetMoveToAction(0.0, 64.0, 0.0, 1.0, "", "");
            case "puppet_look_at", "puppet_look" -> new PuppetLookAtAction(0.0, 64.0, 0.0, "", "", "");
            case "puppet_suppress_ai", "puppet_suppress" -> new PuppetSuppressAction(false, false, false, false, false, true, "", "");
            case "puppet_stop_action", "puppet_stop" -> new PuppetStopAction("", "");
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
            if (showActiveWaitCard != null) noa.setShowActiveWait(showActiveWaitCard.isChecked());
        } else if (action instanceof PlayMusicSequenceAction pmsa) {
            if (musicSequenceDropdown != null && musicSequenceDropdown.getSelectedValue() != null) {
                pmsa.setSequenceFile(musicSequenceDropdown.getSelectedValue());
            } else {
                pmsa.setSequenceFile(val);
            }
            if (showActiveWaitCard != null) {
                pmsa.setAwaitCompletion(showActiveWaitCard.isChecked());
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
                if (requireAllPlayersCard != null) {
                    wua.setRequireAllPlayers(requireAllPlayersCard.isChecked());
                }
                if (opsOnlyVisibilityCard != null) {
                    wua.setOpsOnlyVisibility(opsOnlyVisibilityCard.isChecked());
                }
                if (showRadiusAreaCard != null) {
                    wua.setShowRadiusArea(showRadiusAreaCard.isChecked());
                }
                if (areaOpsOnlyCard != null) {
                    wua.setAreaOpsOnlyVisibility(areaOpsOnlyCard.isChecked());
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
        } else if (action instanceof ExecutePuppetAction epa) {
            epa.setActionId(val);
            if (windupTicksField != null) try { epa.setWindupTicks(Integer.parseInt(windupTicksField.getValue().trim())); } catch (Exception ignored) {}
            if (durationTicksField != null) try { epa.setDurationTicks(Integer.parseInt(durationTicksField.getValue().trim())); } catch (Exception ignored) {}
            if (entityUuidField != null) epa.setEntityUuid(entityUuidField.getValue().trim());
            if (targetSelectorField != null) epa.setTargetSelector(targetSelectorField.getValue().trim());
        } else if (action instanceof PuppetMoveToAction pmt) {
            if (xField != null) try { pmt.setX(Double.parseDouble(xField.getValue().trim())); } catch (Exception ignored) {}
            if (yField != null) try { pmt.setY(Double.parseDouble(yField.getValue().trim())); } catch (Exception ignored) {}
            if (zField != null) try { pmt.setZ(Double.parseDouble(zField.getValue().trim())); } catch (Exception ignored) {}
            if (speedField != null) try { pmt.setSpeed(Double.parseDouble(speedField.getValue().trim())); } catch (Exception ignored) {}
            if (entityUuidField != null) pmt.setEntityUuid(entityUuidField.getValue().trim());
            if (targetSelectorField != null) pmt.setTargetSelector(targetSelectorField.getValue().trim());
        } else if (action instanceof PuppetLookAtAction pla) {
            if (xField != null) try { pla.setX(Double.parseDouble(xField.getValue().trim())); } catch (Exception ignored) {}
            if (yField != null) try { pla.setY(Double.parseDouble(yField.getValue().trim())); } catch (Exception ignored) {}
            if (zField != null) try { pla.setZ(Double.parseDouble(zField.getValue().trim())); } catch (Exception ignored) {}
            if (lookTargetField != null) pla.setLookTargetSelector(lookTargetField.getValue().trim());
            if (entityUuidField != null) pla.setEntityUuid(entityUuidField.getValue().trim());
            if (targetSelectorField != null) pla.setTargetSelector(targetSelectorField.getValue().trim());
        } else if (action instanceof PuppetSuppressAction psa) {
            if (suppressAiCard != null) psa.setSuppressAi(suppressAiCard.isChecked());
            if (suppressNavCard != null) psa.setSuppressNavigation(suppressNavCard.isChecked());
            if (suppressTargetingCard != null) psa.setSuppressTargeting(suppressTargetingCard.isChecked());
            if (suppressLookCard != null) psa.setSuppressLook(suppressLookCard.isChecked());
            if (suppressActionsCard != null) psa.setSuppressActions(suppressActionsCard.isChecked());
            if (puppetingActiveCard != null) psa.setPuppetingActive(puppetingActiveCard.isChecked());
            if (entityUuidField != null) psa.setEntityUuid(entityUuidField.getValue().trim());
            if (targetSelectorField != null) psa.setTargetSelector(targetSelectorField.getValue().trim());
        } else if (action instanceof PuppetStopAction pst) {
            if (entityUuidField != null) pst.setEntityUuid(entityUuidField.getValue().trim());
            if (targetSelectorField != null) pst.setTargetSelector(targetSelectorField.getValue().trim());
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

    private Entity getLookedAtEntity() {
        if (this.minecraft == null) return null;
        if (this.minecraft.crosshairPickEntity != null) {
            return this.minecraft.crosshairPickEntity;
        }
        if (this.minecraft.hitResult instanceof EntityHitResult ehr) {
            return ehr.getEntity();
        }
        return null;
    }

    private double getSuggestionOffsetY() {
        int panelHeight = 304;
        int top = (this.height - panelHeight) / 2;
        int fieldY = top + 98;
        return (fieldY + 24) - (this.height - 12);
    }

    private boolean isDropdownOpen() {
        return (actionTypeDropdown != null && actionTypeDropdown.isOpen()) ||
                (subActionTypeDropdown != null && subActionTypeDropdown.isOpen()) ||
                (unitDropdown != null && unitDropdown.isOpen()) ||
                (musicSequenceDropdown != null && musicSequenceDropdown.isOpen()) ||
                (nearbyEntityDropdown != null && nearbyEntityDropdown.isOpen()) ||
                (puppetActionDropdown != null && puppetActionDropdown.isOpen());
    }

    private void refreshPuppetActionSuggestions() {
        if (this.puppetActionDropdown == null || this.minecraft == null || this.minecraft.level == null) return;

        List<CyberpunkDropdown.DropdownEntry<String>> actionEntries = new ArrayList<>();
        String currentEntityUuid = entityUuidField != null ? entityUuidField.getValue().trim() : "";
        String currentSelector = targetSelectorField != null ? targetSelectorField.getValue().trim() : "";

        List<IPuppetEntity> specificPuppets = new ArrayList<>();
        List<IPuppetEntity> candidatePuppets = new ArrayList<>();

        if (!currentEntityUuid.isEmpty()) {
            try {
                UUID uuid = UUID.fromString(currentEntityUuid);
                for (Entity e : this.minecraft.level.entitiesForRendering()) {
                    if (e.getUUID().equals(uuid) && e instanceof IPuppetEntity puppet) {
                        specificPuppets.add(puppet);
                    }
                }
            } catch (Exception ignored) {}
        }

        String typeFilter = null;
        if (!currentSelector.isEmpty() && currentSelector.contains("type=")) {
            int idx = currentSelector.indexOf("type=");
            int endIdx = currentSelector.indexOf("]", idx);
            if (endIdx < 0) endIdx = currentSelector.indexOf(",", idx);
            if (endIdx < 0) endIdx = currentSelector.length();
            typeFilter = currentSelector.substring(idx + 5, endIdx).trim();
        }

        if (specificPuppets.isEmpty() && !currentSelector.isEmpty()) {
            for (Entity e : this.minecraft.level.entitiesForRendering()) {
                if (e instanceof IPuppetEntity puppet) {
                    if (typeFilter != null && !typeFilter.isEmpty()) {
                        String entityTypeStr = EntityType.getKey(e.getType()).toString();
                        if (entityTypeStr.equalsIgnoreCase(typeFilter) || entityTypeStr.endsWith(":" + typeFilter)) {
                            specificPuppets.add(puppet);
                        }
                    } else {
                        candidatePuppets.add(puppet);
                    }
                }
            }
        }

        if (specificPuppets.isEmpty() && candidatePuppets.isEmpty()) {
            for (Entity e : this.minecraft.level.entitiesForRendering()) {
                if (e instanceof IPuppetEntity puppet) {
                    candidatePuppets.add(puppet);
                }
            }
        }

        List<IPuppetEntity> targetPuppets = !specificPuppets.isEmpty() ? specificPuppets : candidatePuppets;
        Set<String> addedActionIds = new java.util.LinkedHashSet<>();
        int totalFound = 0;

        for (IPuppetEntity puppet : targetPuppets) {
            if (puppet.getPuppetController() != null) {
                Map<net.minecraft.resources.ResourceLocation, PuppetAction> actionsMap = puppet.getPuppetController().getActions();
                Entity entity = (Entity) puppet;
                String entityName = entity.getDisplayName().getString();
                String entityType = EntityType.getKey(entity.getType()).toString();

                for (net.minecraft.resources.ResourceLocation actionRes : actionsMap.keySet()) {
                    String actionIdStr = actionRes.toString();
                    if (addedActionIds.add(actionIdStr)) {
                        totalFound++;
                        String label = "⚡ " + actionIdStr;
                        String details = entityName + " (" + entityType + ")";
                        actionEntries.add(new CyberpunkDropdown.DropdownEntry<>(actionIdStr, Component.literal(label), Component.literal(details)));
                    }
                }
            }
        }

        for (net.dandare21.fracturedutils.puppet.fsm.PuppetActionType<?> globalAction :
                net.dandare21.fracturedutils.puppet.registry.ModPuppetActions.getAll()) {
            String actionIdStr = globalAction.getId().toString();
            if (addedActionIds.add(actionIdStr)) {
                totalFound++;
                String label = "⚡ " + actionIdStr;
                String details = "Global Boss Puppet Action";
                actionEntries.add(new CyberpunkDropdown.DropdownEntry<>(actionIdStr, Component.literal(label), Component.literal(details)));
            }
        }

        if (actionEntries.isEmpty()) {
            actionEntries.add(new CyberpunkDropdown.DropdownEntry<>("", Component.literal("-- No Registered Actions Found --"), Component.literal("Select a mob or enter entity selector")));
        } else {
            actionEntries.add(0, new CyberpunkDropdown.DropdownEntry<>("", Component.literal("-- Select Puppet Action (" + totalFound + " available) --"), Component.literal("Click to choose action ID")));
        }

        String currentVal = inputField != null ? inputField.getValue().trim() : "";
        this.puppetActionDropdown.setOptions(actionEntries);

        if ((currentVal.isEmpty() || currentVal.equalsIgnoreCase("mymod:action_id")) && !addedActionIds.isEmpty()) {
            String firstAction = addedActionIds.iterator().next();
            if (inputField != null) {
                inputField.setValue(firstAction);
            }
            this.puppetActionDropdown.selectByValue(firstAction);
        } else if (!currentVal.isEmpty() && addedActionIds.contains(currentVal)) {
            this.puppetActionDropdown.selectByValue(currentVal);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isDropdownOpen() && this.commandSuggestions != null && this.commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (!isDropdownOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                saveAndExit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(parentScreen);
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (puppetActionDropdown != null && puppetActionDropdown.mouseScrolled(mouseX, mouseY, amount)) return true;
        if (nearbyEntityDropdown != null && nearbyEntityDropdown.mouseScrolled(mouseX, mouseY, amount)) return true;
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
        if (puppetActionDropdown != null && puppetActionDropdown.isOpen()) {
            if (puppetActionDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        }
        if (nearbyEntityDropdown != null && nearbyEntityDropdown.isOpen()) {
            if (nearbyEntityDropdown.mouseClicked(mouseX, mouseY, button)) return true;
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
        if (puppetActionDropdown != null && puppetActionDropdown.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (nearbyEntityDropdown != null && nearbyEntityDropdown.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
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
        if (puppetActionDropdown != null && puppetActionDropdown.mouseReleased(mouseX, mouseY, button)) return true;
        if (nearbyEntityDropdown != null && nearbyEntityDropdown.mouseReleased(mouseX, mouseY, button)) return true;
        if (musicSequenceDropdown != null && musicSequenceDropdown.mouseReleased(mouseX, mouseY, button)) return true;
        if (subActionTypeDropdown != null && subActionTypeDropdown.mouseReleased(mouseX, mouseY, button)) return true;
        if (unitDropdown != null && unitDropdown.mouseReleased(mouseX, mouseY, button)) return true;
        if (actionTypeDropdown != null && actionTypeDropdown.mouseReleased(mouseX, mouseY, button)) return true;

        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void drawGridOverlay(GuiGraphics graphics) {
        int step = 20;
        int color = 0x0700E5FF;
        for (int x = 0; x < this.width; x += step) {
            graphics.fill(x, 0, x + 1, this.height, color);
        }
        for (int y = 0; y < this.height; y += step) {
            graphics.fill(0, y, this.width, y + 1, color);
        }
    }

    private void drawTechPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        // Main dark backdrop
        graphics.fill(x, y, x + w, y + h, PANEL_BG);

        // Cyberpunk cyan glowing border
        graphics.fill(x, y, x + w, y + 1, BORDER_CYAN);
        graphics.fill(x, y + h - 1, x + w, y + h, BORDER_CYAN);
        graphics.fill(x, y, x + 1, y + h, BORDER_CYAN);
        graphics.fill(x + w - 1, y, x + w, y + h, BORDER_CYAN);

        // Tech Corner Chamfers & Brackets
        int notch = 4;
        graphics.fill(x + 1, y + 1, x + notch, y + 2, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + notch, 0xFFFFFFFF);

        graphics.fill(x + w - notch, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + notch, 0xFFFFFFFF);

        graphics.fill(x + 1, y + h - 2, x + notch, y + h - 1, 0xFFFFFFFF);
        graphics.fill(x + 1, y + h - notch, x + 2, y + h - 1, 0xFFFFFFFF);

        graphics.fill(x + w - notch, y + h - 2, x + w - 1, y + h - 1, 0xFFFFFFFF);
        graphics.fill(x + w - 2, y + h - notch, x + w - 1, y + h - 1, 0xFFFFFFFF);
    }

    private void drawInputFrame(GuiGraphics graphics, int x, int y, int w, int h, boolean focused, boolean invalid) {
        int fill = 0xEE060F17;
        int border = invalid ? 0xFFFF3355 : (focused ? 0xFFFFFFFF : BORDER_MUTED);
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
    }

    private void drawPillBadge(GuiGraphics graphics, int x, int y, String text, int borderColor, int textColor) {
        int tw = this.font.width(text);
        int bw = tw + 8;
        int bh = 11;
        graphics.fill(x, y, x + bw, y + bh, 0xEE06121C);
        graphics.fill(x, y, x + bw, y + 1, borderColor);
        graphics.fill(x, y + bh - 1, x + bw, y + bh, borderColor);
        graphics.fill(x, y, x + 1, y + bh, borderColor);
        graphics.fill(x + bw - 1, y, x + bw, y + bh, borderColor);
        graphics.drawString(this.font, text, x + 4, y + 2, textColor, false);
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

        int panelWidth = 448;
        int panelHeight = 304;
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        drawTechPanel(graphics, left, top, panelWidth, panelHeight);

        // --- 1. Header Bar ---
        graphics.fill(left, top, left + panelWidth, top + 30, HEADER_BG);
        graphics.fill(left, top + 29, left + panelWidth, top + 30, BORDER_CYAN);

        // Glyph Box `</>`
        int glyphX = left + 10;
        int glyphY = top + 7;
        graphics.fill(glyphX, glyphY, glyphX + 18, glyphY + 16, 0xEE05101A);
        graphics.fill(glyphX, glyphY, glyphX + 18, glyphY + 1, BORDER_CYAN);
        graphics.fill(glyphX, glyphY + 15, glyphX + 18, glyphY + 16, BORDER_CYAN);
        graphics.fill(glyphX, glyphY, glyphX + 1, glyphY + 16, BORDER_CYAN);
        graphics.fill(glyphX + 17, glyphY, glyphX + 18, glyphY + 16, BORDER_CYAN);
        graphics.drawString(this.font, "</>", glyphX + 2, glyphY + 4, CYAN_MAIN, false);

        // Header Subtitle + Pill Badge
        int subX = left + 34;
        int subY = top + 6;
        graphics.drawString(this.font, "CONFIG PROTOCOL // MOD_ACTION", subX, subY, TEXT_MUTED, false);
        int protoW = this.font.width("CONFIG PROTOCOL // MOD_ACTION");
        drawPillBadge(graphics, subX + protoW + 6, subY - 1, "• SYNCED", GREEN_VALID, GREEN_VALID);

        // Header Main Title
        String displayActionTitle = "EDIT ACTION: " + actionType.toUpperCase(Locale.ROOT);
        graphics.drawString(this.font, displayActionTitle, subX, top + 17, CYAN_MAIN, false);

        // Window Controls (minimize dummy `—`)
        graphics.drawString(this.font, "—", left + panelWidth - 42, top + 9, TEXT_MUTED, false);

        // --- 2. Top Row (01. Action Mode & 02. Secondary Context) ---
        int colWidth = (panelWidth - 32) / 2;
        int col1Left = left + 12;
        int col2Left = left + panelWidth - 12 - colWidth;

        // Col 1 Header
        graphics.drawString(this.font, "01. ACTION MODE", col1Left, top + 35, CYAN_MAIN, false);
        String categoryBadge = getCategoryBadgeForAction(actionType);
        int catW = this.font.width(categoryBadge);
        graphics.drawString(this.font, categoryBadge, col1Left + colWidth - catW, top + 35, TEXT_MUTED, false);

        // Col 2 Header
        boolean isPuppet = actionType.equalsIgnoreCase("puppet_action") ||
                actionType.equalsIgnoreCase("puppet_move_to") ||
                actionType.equalsIgnoreCase("puppet_look_at") ||
                actionType.equalsIgnoreCase("puppet_suppress_ai") ||
                actionType.equalsIgnoreCase("puppet_stop_action");

        if (isPuppet) {
            graphics.drawString(this.font, "02. RADAR PROXIMITY ENTITY", col2Left, top + 35, CYAN_MAIN, false);
            int detectedCount = nearbyEntityDropdown != null ? Math.max(0, nearbyEntityDropdown.getVisibleItemCount()) : 0;
            String radarBadge = "RADAR: 64m";
            int radW = this.font.width(radarBadge);
            graphics.drawString(this.font, radarBadge, col2Left + colWidth - radW, top + 35, TEXT_MUTED, false);
        } else if (actionType.equalsIgnoreCase("wait_until")) {
            graphics.drawString(this.font, "02. WAIT CONDITION", col2Left, top + 35, CYAN_MAIN, false);
            String waitBadge = "EVENT MATRIX";
            int wbW = this.font.width(waitBadge);
            graphics.drawString(this.font, waitBadge, col2Left + colWidth - wbW, top + 35, TEXT_MUTED, false);
        } else if (actionType.equalsIgnoreCase("play_music_sequence")) {
            graphics.drawString(this.font, "02. MUSIC SEQUENCE FILE", col2Left, top + 35, CYAN_MAIN, false);
            String audioBadge = "AUDIO SYNC";
            int abW = this.font.width(audioBadge);
            graphics.drawString(this.font, audioBadge, col2Left + colWidth - abW, top + 35, TEXT_MUTED, false);
        } else if (actionType.equalsIgnoreCase("command")) {
            graphics.drawString(this.font, "02. COMMAND CONTEXT", col2Left, top + 35, CYAN_MAIN, false);
            String cmdBadge = "CONSOLE";
            int cbW = this.font.width(cmdBadge);
            graphics.drawString(this.font, cmdBadge, col2Left + colWidth - cbW, top + 35, TEXT_MUTED, false);
        }

        // --- 3. Body Section Rendering & Field Frames ---
        if (actionType.equalsIgnoreCase("puppet_suppress_ai")) {
            int engagedCount = 0;
            if (suppressAiCard != null && suppressAiCard.isChecked()) engagedCount++;
            if (puppetingActiveCard != null && puppetingActiveCard.isChecked()) engagedCount++;
            if (suppressNavCard != null && suppressNavCard.isChecked()) engagedCount++;
            if (suppressTargetingCard != null && suppressTargetingCard.isChecked()) engagedCount++;
            if (suppressLookCard != null && suppressLookCard.isChecked()) engagedCount++;
            if (suppressActionsCard != null && suppressActionsCard.isChecked()) engagedCount++;

            graphics.drawString(this.font, "■ BEHAVIOR FLAGS & SUPPRESSION MATRIX", left + 12, top + 77, CYAN_BRIGHT, false);
            String matrixBadge = engagedCount + " OF 6 MATRICES ENGAGED";
            int mbW = this.font.width(matrixBadge);
            graphics.drawString(this.font, matrixBadge, left + panelWidth - 12 - mbW, top + 77, TEXT_MUTED, false);

            // Entity UUID
            int uuidY = top + 172;
            int uuidW = panelWidth - 142;
            String uuidVal = entityUuidField != null ? entityUuidField.getValue().trim() : "";
            boolean isFocusedUuid = entityUuidField != null && entityUuidField.isFocused();
            boolean hasUuid = !uuidVal.isEmpty();
            boolean validUuid = isValidUUID(uuidVal);

            graphics.drawString(this.font, "ENTITY UUID (OPTIONAL // UNIQUE POINTER)", left + 12, top + 161, TEXT_LABEL, false);
            if (hasUuid) {
                if (validUuid) {
                    drawPillBadge(graphics, left + panelWidth - 12 - this.font.width("• VALID UUID FORMAT") - 8, top + 159, "• VALID UUID FORMAT", GREEN_VALID, GREEN_VALID);
                } else {
                    drawPillBadge(graphics, left + panelWidth - 12 - this.font.width("⚠ INVALID UUID") - 8, top + 159, "⚠ INVALID UUID", RED_CANCEL, RED_CANCEL);
                }
            } else {
                String blankTag = "OPTIONAL // USING SELECTOR";
                graphics.drawString(this.font, blankTag, left + panelWidth - 12 - this.font.width(blankTag), top + 161, TEXT_MUTED, false);
            }
            drawInputFrame(graphics, left + 12, uuidY, uuidW, 20, isFocusedUuid, hasUuid && !validUuid);

            // Target Selector
            int selY = top + 208;
            int selW = panelWidth - 24;
            String selVal = targetSelectorField != null ? targetSelectorField.getValue().trim() : "";
            boolean isFocusedSel = targetSelectorField != null && targetSelectorField.isFocused();

            graphics.drawString(this.font, "TARGET SELECTOR QUERY (e.g. @e[tag=puppet])", left + 12, top + 197, TEXT_LABEL, false);
            int matches = getResolvedMatchesCount(selVal);
            String matchBadge = !selVal.isEmpty() ? (matches == 1 ? "RESOLVED: 1 MATCH" : "RESOLVED: " + matches + " MATCHES") : "SYNTAX GUIDE";
            int matchColor = (!selVal.isEmpty() && matches > 0) ? CYAN_MAIN : TEXT_MUTED;
            drawPillBadge(graphics, left + panelWidth - 12 - this.font.width(matchBadge) - 8, top + 195, matchBadge, matchColor, matchColor);

            drawInputFrame(graphics, left + 12, selY, selW, 20, isFocusedSel, false);

            graphics.drawString(this.font, "ⓘ Evaluates automatically if Entity UUID field is blank or unreachable.", left + 12, top + 233, TEXT_MUTED, false);

        } else if (actionType.equalsIgnoreCase("puppet_action")) {
            graphics.drawString(this.font, "■ PUPPET ROUTINE & TIMING PARAMETERS", left + 12, top + 77, CYAN_BRIGHT, false);

            int r2Y = top + 126;
            graphics.drawString(this.font, "ACTION ID (e.g. fracturedutils:radial_blast)", left + 12, top + 115, TEXT_LABEL, false);
            graphics.drawString(this.font, "WINDUP", left + 256, top + 115, TEXT_LABEL, false);
            graphics.drawString(this.font, "DURATION", left + 348, top + 115, TEXT_LABEL, false);

            drawInputFrame(graphics, left + 12, r2Y, 234, 20, inputField != null && inputField.isFocused(), false);
            drawInputFrame(graphics, left + 256, r2Y, 82, 20, windupTicksField != null && windupTicksField.isFocused(), false);
            drawInputFrame(graphics, left + 348, r2Y, 88, 20, durationTicksField != null && durationTicksField.isFocused(), false);

            int uuidY = top + 162;
            int uuidW = panelWidth - 142;
            String uuidVal = entityUuidField != null ? entityUuidField.getValue().trim() : "";
            boolean hasUuid = !uuidVal.isEmpty();
            boolean validUuid = isValidUUID(uuidVal);

            graphics.drawString(this.font, "ENTITY UUID (OPTIONAL)", left + 12, top + 151, TEXT_LABEL, false);
            drawInputFrame(graphics, left + 12, uuidY, uuidW, 20, entityUuidField != null && entityUuidField.isFocused(), hasUuid && !validUuid);

            int selY = top + 198;
            int selW = panelWidth - 24;
            String selVal = targetSelectorField != null ? targetSelectorField.getValue().trim() : "";
            graphics.drawString(this.font, "TARGET SELECTOR QUERY", left + 12, top + 187, TEXT_LABEL, false);
            int matches = getResolvedMatchesCount(selVal);
            String matchBadge = !selVal.isEmpty() ? (matches == 1 ? "RESOLVED: 1 MATCH" : "RESOLVED: " + matches + " MATCHES") : "SYNTAX GUIDE";
            drawPillBadge(graphics, left + panelWidth - 12 - this.font.width(matchBadge) - 8, top + 185, matchBadge, CYAN_MAIN, CYAN_MAIN);
            drawInputFrame(graphics, left + 12, selY, selW, 20, targetSelectorField != null && targetSelectorField.isFocused(), false);

            graphics.drawString(this.font, "ⓘ Executes registered attack/routine on the designated puppet mob.", left + 12, top + 224, TEXT_MUTED, false);

        } else if (actionType.equalsIgnoreCase("puppet_move_to")) {
            graphics.drawString(this.font, "■ NAVIGATION VECTOR & TARGET COORDINATES", left + 12, top + 77, CYAN_BRIGHT, false);

            int r1Y = top + 90;
            graphics.drawString(this.font, "X", left + 12, top + 79, TEXT_LABEL, false);
            graphics.drawString(this.font, "Y", left + 86, top + 79, TEXT_LABEL, false);
            graphics.drawString(this.font, "Z", left + 160, top + 79, TEXT_LABEL, false);
            graphics.drawString(this.font, "SPEED", left + 234, top + 79, TEXT_LABEL, false);

            drawInputFrame(graphics, left + 12, r1Y, 70, 20, xField != null && xField.isFocused(), false);
            drawInputFrame(graphics, left + 86, r1Y, 70, 20, yField != null && yField.isFocused(), false);
            drawInputFrame(graphics, left + 160, r1Y, 70, 20, zField != null && zField.isFocused(), false);
            drawInputFrame(graphics, left + 234, r1Y, 66, 20, speedField != null && speedField.isFocused(), false);

            int uuidY = top + 138;
            int uuidW = panelWidth - 142;
            String uuidVal = entityUuidField != null ? entityUuidField.getValue().trim() : "";
            boolean hasUuid = !uuidVal.isEmpty();
            boolean validUuid = isValidUUID(uuidVal);

            graphics.drawString(this.font, "ENTITY UUID (OPTIONAL)", left + 12, top + 126, TEXT_LABEL, false);
            drawInputFrame(graphics, left + 12, uuidY, uuidW, 20, entityUuidField != null && entityUuidField.isFocused(), hasUuid && !validUuid);

            int selY = top + 184;
            int selW = panelWidth - 24;
            String selVal = targetSelectorField != null ? targetSelectorField.getValue().trim() : "";
            graphics.drawString(this.font, "TARGET SELECTOR QUERY", left + 12, top + 172, TEXT_LABEL, false);
            int matches = getResolvedMatchesCount(selVal);
            String matchBadge = !selVal.isEmpty() ? (matches == 1 ? "RESOLVED: 1 MATCH" : "RESOLVED: " + matches + " MATCHES") : "SYNTAX GUIDE";
            drawPillBadge(graphics, left + panelWidth - 12 - this.font.width(matchBadge) - 8, top + 170, matchBadge, CYAN_MAIN, CYAN_MAIN);
            drawInputFrame(graphics, left + 12, selY, selW, 20, targetSelectorField != null && targetSelectorField.isFocused(), false);

            graphics.drawString(this.font, "ⓘ Directs the puppet mob's AI pathfinding towards target coordinates.", left + 12, top + 210, TEXT_MUTED, false);

        } else if (actionType.equalsIgnoreCase("puppet_look_at")) {
            graphics.drawString(this.font, "■ LOOK ORIENTATION & GAZE TARGET", left + 12, top + 77, CYAN_BRIGHT, false);

            int r1Y = top + 90;
            graphics.drawString(this.font, "X", left + 12, top + 79, TEXT_LABEL, false);
            graphics.drawString(this.font, "Y", left + 108, top + 79, TEXT_LABEL, false);
            graphics.drawString(this.font, "Z", left + 204, top + 79, TEXT_LABEL, false);

            drawInputFrame(graphics, left + 12, r1Y, 92, 20, xField != null && xField.isFocused(), false);
            drawInputFrame(graphics, left + 108, r1Y, 92, 20, yField != null && yField.isFocused(), false);
            drawInputFrame(graphics, left + 204, r1Y, 92, 20, zField != null && zField.isFocused(), false);

            int lookY = top + 130;
            int lookW = panelWidth - 142;
            graphics.drawString(this.font, "LOOK AT TARGET (e.g. @p or UUID)", left + 12, top + 118, TEXT_LABEL, false);
            drawInputFrame(graphics, left + 12, lookY, lookW, 20, lookTargetField != null && lookTargetField.isFocused(), false);

            int uuidY = top + 168;
            int uuidW = panelWidth - 142;
            String uuidVal = entityUuidField != null ? entityUuidField.getValue().trim() : "";
            boolean hasUuid = !uuidVal.isEmpty();
            boolean validUuid = isValidUUID(uuidVal);

            graphics.drawString(this.font, "PUPPET MOB UUID (OPTIONAL)", left + 12, top + 156, TEXT_LABEL, false);
            drawInputFrame(graphics, left + 12, uuidY, uuidW, 20, entityUuidField != null && entityUuidField.isFocused(), hasUuid && !validUuid);

            int selY = top + 206;
            int selW = panelWidth - 24;
            String selVal = targetSelectorField != null ? targetSelectorField.getValue().trim() : "";
            graphics.drawString(this.font, "TARGET SELECTOR QUERY", left + 12, top + 194, TEXT_LABEL, false);
            drawInputFrame(graphics, left + 12, selY, selW, 20, targetSelectorField != null && targetSelectorField.isFocused(), false);

            graphics.drawString(this.font, "ⓘ Forces puppet entity to orient its gaze towards coordinates or entity.", left + 12, top + 232, TEXT_MUTED, false);

        } else if (actionType.equalsIgnoreCase("puppet_stop_action")) {
            graphics.drawString(this.font, "■ PUPPET OVERRIDE & RESET", left + 12, top + 77, CYAN_BRIGHT, false);

            // Informative Card
            int cardY = top + 90;
            graphics.fill(left + 12, cardY, left + panelWidth - 12, cardY + 24, 0xEE091B26);
            graphics.fill(left + 12, cardY, left + panelWidth - 12, cardY + 1, 0xFF24485D);
            graphics.fill(left + 12, cardY + 23, left + panelWidth - 12, cardY + 24, 0xFF24485D);
            graphics.fill(left + 12, cardY, left + 13, cardY + 24, 0xFF24485D);
            graphics.fill(left + panelWidth - 13, cardY, left + panelWidth - 12, cardY + 24, 0xFF24485D);
            graphics.drawString(this.font, "⚡ Stops any active puppet action and restores default mob AI state.", left + 20, cardY + 8, CYAN_MAIN, false);

            int uuidY = top + 130;
            int uuidW = panelWidth - 142;
            String uuidVal = entityUuidField != null ? entityUuidField.getValue().trim() : "";
            boolean hasUuid = !uuidVal.isEmpty();
            boolean validUuid = isValidUUID(uuidVal);

            graphics.drawString(this.font, "PUPPET MOB UUID (OPTIONAL)", left + 12, top + 118, TEXT_LABEL, false);
            drawInputFrame(graphics, left + 12, uuidY, uuidW, 20, entityUuidField != null && entityUuidField.isFocused(), hasUuid && !validUuid);

            int selY = top + 178;
            int selW = panelWidth - 24;
            String selVal = targetSelectorField != null ? targetSelectorField.getValue().trim() : "";
            graphics.drawString(this.font, "TARGET SELECTOR QUERY", left + 12, top + 166, TEXT_LABEL, false);
            drawInputFrame(graphics, left + 12, selY, selW, 20, targetSelectorField != null && targetSelectorField.isFocused(), false);

            graphics.drawString(this.font, "ⓘ Clears running puppet behaviors and releases suppression flags.", left + 12, top + 204, TEXT_MUTED, false);

        } else if (actionType.equalsIgnoreCase("command")) {
            boolean isCommandInvalid = false;
            if (inputField != null) {
                isCommandInvalid = !CommandAction.isValidCommand(this.minecraft, inputField.getValue());
                this.inputField.setTextColor(isCommandInvalid ? 0xFFFF5555 : 0xFFFFFFFF);
            }

            graphics.drawString(this.font, "■ CONSOLE COMMAND PAYLOAD", left + 12, top + 77, CYAN_BRIGHT, false);
            String cmdStatus = isCommandInvalid ? "⚠ SYNTAX INVALID" : "• SYNTAX VALID";
            int cmdColor = isCommandInvalid ? RED_CANCEL : GREEN_VALID;
            drawPillBadge(graphics, left + panelWidth - 12 - this.font.width(cmdStatus) - 8, top + 75, cmdStatus, cmdColor, cmdColor);

            drawInputFrame(graphics, left + 12, top + 97, panelWidth - 24, 22, inputField != null && inputField.isFocused(), isCommandInvalid);

            graphics.drawString(this.font, "QUICK INSERT TOKENS:", left + 12, top + 121, TEXT_MUTED, false);

            int infoY = top + 152;
            graphics.fill(left + 12, infoY, left + panelWidth - 12, infoY + 50, 0xEE091822);
            graphics.fill(left + 12, infoY, left + panelWidth - 12, infoY + 1, BORDER_MUTED);
            graphics.fill(left + 12, infoY + 49, left + panelWidth - 12, infoY + 50, BORDER_MUTED);
            graphics.fill(left + 12, infoY, left + 13, infoY + 50, BORDER_MUTED);
            graphics.fill(left + panelWidth - 13, infoY, left + panelWidth - 12, infoY + 50, BORDER_MUTED);

            graphics.drawString(this.font, "PARAMETER SUBSTITUTIONS:", left + 20, infoY + 6, CYAN_MAIN, false);
            graphics.drawString(this.font, "%player% - Substituted with the triggering player's username", left + 20, infoY + 20, TEXT_LABEL, false);
            graphics.drawString(this.font, "%uuid%   - Substituted with the triggering player's UUID string", left + 20, infoY + 34, TEXT_LABEL, false);

            graphics.drawString(this.font, "ⓘ Runs with server console/operator permissions.", left + 12, top + 210, TEXT_MUTED, false);

        } else if (actionType.equalsIgnoreCase("wait_until")) {
            graphics.drawString(this.font, "■ TEMPORAL & TRIGGER CONDITIONS", left + 12, top + 77, CYAN_BRIGHT, false);

            if (waitUntilType.equalsIgnoreCase("delay")) {
                int totalTicks = calculateDelayTicks();
                String summary = String.format(Locale.US, "= %d Ticks  |  %.1fs  |  %.2fm", totalTicks, totalTicks / 20.0f, totalTicks / 1200.0f);
                drawPillBadge(graphics, left + panelWidth - 12 - this.font.width(summary) - 8, top + 75, summary, CYAN_MAIN, CYAN_MAIN);

                graphics.drawString(this.font, "DURATION (" + delayUnit.name() + ")", left + 12, top + 86, TEXT_LABEL, false);
                drawInputFrame(graphics, left + 12, top + 97, panelWidth - 164, 22, inputField != null && inputField.isFocused(), false);

                graphics.drawString(this.font, "ⓘ Pauses sequence execution. Automatically converts between units.", left + 12, top + 130, TEXT_MUTED, false);

            } else if (waitUntilType.equalsIgnoreCase("proximity") || waitUntilType.equalsIgnoreCase("marker") || waitUntilType.equalsIgnoreCase("player_proximity") || waitUntilType.equalsIgnoreCase("area")) {
                int r1Y = top + 90;
                graphics.drawString(this.font, "X", left + 12, top + 79, TEXT_LABEL, false);
                graphics.drawString(this.font, "Y", left + 86, top + 79, TEXT_LABEL, false);
                graphics.drawString(this.font, "Z", left + 160, top + 79, TEXT_LABEL, false);
                graphics.drawString(this.font, "RADIUS (m)", left + 234, top + 79, TEXT_LABEL, false);

                drawInputFrame(graphics, left + 12, r1Y, 70, 20, xField != null && xField.isFocused(), false);
                drawInputFrame(graphics, left + 86, r1Y, 70, 20, yField != null && yField.isFocused(), false);
                drawInputFrame(graphics, left + 160, r1Y, 70, 20, zField != null && zField.isFocused(), false);
                drawInputFrame(graphics, left + 234, r1Y, 66, 20, radiusField != null && radiusField.isFocused(), false);

                graphics.drawString(this.font, "TARGET SELECTOR (e.g. @a)", left + 12, top + 116, TEXT_LABEL, false);
                drawInputFrame(graphics, left + 12, top + 127, panelWidth - 24, 20, targetSelectorField != null && targetSelectorField.isFocused(), false);

                graphics.drawString(this.font, "DETECTION MATRIX CONFIGURATION", left + 12, top + 152, TEXT_MUTED, false);
                graphics.drawString(this.font, "ⓘ Pauses sequence until matching player enters the detection zone.", left + 12, top + 214, TEXT_MUTED, false);

            } else if (waitUntilType.equalsIgnoreCase("trigger") || waitUntilType.equalsIgnoreCase("await_trigger")) {
                graphics.drawString(this.font, "TRIGGER ID EVENT NAME", left + 12, top + 86, TEXT_LABEL, false);
                drawInputFrame(graphics, left + 12, top + 97, panelWidth - 24, 22, inputField != null && inputField.isFocused(), false);
                graphics.drawString(this.font, "ⓘ Pauses sequence until /orchestrator trigger <id> is invoked.", left + 12, top + 130, TEXT_MUTED, false);

            } else if (waitUntilType.equalsIgnoreCase("operator_action")) {
                graphics.drawString(this.font, "OPERATOR BUTTON HUD LABEL", left + 12, top + 86, TEXT_LABEL, false);
                drawInputFrame(graphics, left + 12, top + 97, panelWidth - 24, 22, inputField != null && inputField.isFocused(), false);
                graphics.drawString(this.font, "ⓘ Displays an interactive cyberpunk action prompt button on the operator HUD.", left + 12, top + 130, TEXT_MUTED, false);

            } else {
                graphics.drawString(this.font, "EVENT MONITOR", left + 12, top + 86, TEXT_LABEL, false);
                String info = switch (waitUntilType.toLowerCase()) {
                    case "dialog", "dialog_end" -> "Pauses sequence until active dialog sequence finishes.";
                    case "video", "video_end", "cutscene" -> "Pauses sequence until active cinematic cutscene ends.";
                    case "waiting_room", "waiting_room_end" -> "Pauses sequence until event waiting room countdown ends.";
                    case "waiting_room_ready" -> "Pauses sequence until all players click ready.";
                    case "downloads" -> "Pauses sequence until all clients finish downloading cutscenes.";
                    default -> "Pauses sequence until event triggers.";
                };
                graphics.drawString(this.font, info, left + 12, top + 104, CYAN_MAIN, false);
            }

        } else if (actionType.equalsIgnoreCase("checkpoint")) {
            graphics.drawString(this.font, "■ CHECKPOINT TELEPORT & RESTORE POINT", left + 12, top + 77, CYAN_BRIGHT, false);

            int r1Y = top + 90;
            graphics.drawString(this.font, "X", left + 12, top + 79, TEXT_LABEL, false);
            graphics.drawString(this.font, "Y", left + 108, top + 79, TEXT_LABEL, false);
            graphics.drawString(this.font, "Z", left + 204, top + 79, TEXT_LABEL, false);

            drawInputFrame(graphics, left + 12, r1Y, 92, 20, xField != null && xField.isFocused(), false);
            drawInputFrame(graphics, left + 108, r1Y, 92, 20, yField != null && yField.isFocused(), false);
            drawInputFrame(graphics, left + 204, r1Y, 92, 20, zField != null && zField.isFocused(), false);

            int r2Y = top + 130;
            int angleW = (colWidth - 8) / 2;
            graphics.drawString(this.font, "YAW (HORIZONTAL)", left + 12, top + 118, TEXT_LABEL, false);
            graphics.drawString(this.font, "PITCH (VERTICAL)", left + 14 + angleW + 8, top + 118, TEXT_LABEL, false);
            graphics.drawString(this.font, "CHECKPOINT LABEL", col2Left, top + 118, TEXT_LABEL, false);

            drawInputFrame(graphics, left + 12, r2Y, angleW, 20, yawField != null && yawField.isFocused(), false);
            drawInputFrame(graphics, left + 14 + angleW + 8, r2Y, angleW, 20, pitchField != null && pitchField.isFocused(), false);
            drawInputFrame(graphics, col2Left, r2Y, colWidth, 20, labelField != null && labelField.isFocused(), false);

            int selY = top + 172;
            graphics.drawString(this.font, "TARGET SELECTOR QUERY", left + 12, top + 159, TEXT_LABEL, false);
            drawInputFrame(graphics, left + 12, selY, panelWidth - 24, 20, targetSelectorField != null && targetSelectorField.isFocused(), false);

            graphics.drawString(this.font, "ⓘ Teleports players here and rewinds sequence on team wipe.", left + 12, top + 200, TEXT_MUTED, false);

        } else if (actionType.equalsIgnoreCase("new_objective")) {
            graphics.drawString(this.font, "■ MISSION OBJECTIVE HUD OVERLAY", left + 12, top + 77, CYAN_BRIGHT, false);

            graphics.drawString(this.font, "OBJECTIVE TITLE", left + 12, top + 86, TEXT_LABEL, false);
            drawInputFrame(graphics, left + 12, top + 97, panelWidth - 24, 20, nameField != null && nameField.isFocused(), false);

            graphics.drawString(this.font, "OBJECTIVE DESCRIPTION / INSTRUCTIONS", left + 12, top + 126, TEXT_LABEL, false);
            drawInputFrame(graphics, left + 12, top + 137, panelWidth - 24, 20, descriptionField != null && descriptionField.isFocused(), false);

            graphics.drawString(this.font, "ⓘ Displays an active objective card and progress on all player screens.", left + 12, top + 200, TEXT_MUTED, false);

        } else if (actionType.equalsIgnoreCase("end_objective")) {
            graphics.drawString(this.font, "■ CLEAR HUD OBJECTIVE", left + 12, top + 77, CYAN_BRIGHT, false);
            int cardY = top + 96;
            graphics.fill(left + 12, cardY, left + panelWidth - 12, cardY + 36, 0xEE091B26);
            graphics.fill(left + 12, cardY, left + panelWidth - 12, cardY + 1, 0xFF24485D);
            graphics.fill(left + 12, cardY + 35, left + panelWidth - 12, cardY + 36, 0xFF24485D);
            graphics.fill(left + 12, cardY, left + 13, cardY + 36, 0xFF24485D);
            graphics.fill(left + panelWidth - 13, cardY, left + panelWidth - 12, cardY + 36, 0xFF24485D);
            graphics.drawString(this.font, "Clears the active mission objective from the HUD overlay.", left + 20, cardY + 14, CYAN_MAIN, false);

        } else if (actionType.equalsIgnoreCase("play_music_sequence")) {
            graphics.drawString(this.font, "■ SOUNDTRACK & EVENT MUSIC SYNC", left + 12, top + 77, CYAN_BRIGHT, false);
            graphics.drawString(this.font, "ⓘ Coordinates musical cues and beats synced to audio waveforms.", left + 12, top + 136, TEXT_MUTED, false);

        } else if (actionType.equalsIgnoreCase("fork_sequence") || actionType.equalsIgnoreCase("run_sequence")) {
            graphics.drawString(this.font, "■ SEQUENCE SUBROUTINE EXECUTION", left + 12, top + 77, CYAN_BRIGHT, false);
            graphics.drawString(this.font, "SUBSEQUENCE FILE NAME & [START ACTION INDEX]", left + 12, top + 86, TEXT_LABEL, false);
            drawInputFrame(graphics, left + 12, top + 97, panelWidth - 24, 20, inputField != null && inputField.isFocused(), false);
            String modeStr = actionType.equalsIgnoreCase("fork_sequence") ? "Asynchronously forks sub-sequence in parallel." : "Synchronously runs sub-sequence and blocks until finished.";
            graphics.drawString(this.font, "ⓘ " + modeStr, left + 12, top + 130, TEXT_MUTED, false);

        } else if (actionType.equalsIgnoreCase("stall_parent") || actionType.equalsIgnoreCase("resume_parent")) {
            graphics.drawString(this.font, "■ PARENT FLOW CONTROL", left + 12, top + 77, CYAN_BRIGHT, false);
            String modeStr = actionType.equalsIgnoreCase("stall_parent") ? "Stalls the execution of the parent sequence until resumed." : "Wakes up and resumes execution of a stalled parent sequence.";
            graphics.drawString(this.font, "ⓘ " + modeStr, left + 12, top + 104, CYAN_MAIN, false);
        }

        // --- 4. Footer Bar & Hotkey Badges (Exact match with reference image) ---
        int footerY = top + panelHeight - 24;

        // [ESC] Cancel
        int escX = left + 12;
        graphics.fill(escX, footerY - 2, escX + 26, footerY + 9, 0xEE091B26);
        graphics.fill(escX, footerY - 2, escX + 26, footerY - 1, BORDER_MUTED);
        graphics.fill(escX, footerY + 8, escX + 26, footerY + 9, BORDER_MUTED);
        graphics.fill(escX, footerY - 2, escX + 1, footerY + 9, BORDER_MUTED);
        graphics.fill(escX + 25, footerY - 2, escX + 26, footerY + 9, BORDER_MUTED);
        graphics.drawString(this.font, "ESC", escX + 4, footerY, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "Cancel", escX + 31, footerY, TEXT_MUTED, false);

        // [ENTER] Commit
        int enterX = escX + 31 + this.font.width("Cancel") + 12;
        graphics.fill(enterX, footerY - 2, enterX + 36, footerY + 9, 0xEE091B26);
        graphics.fill(enterX, footerY - 2, enterX + 36, footerY - 1, BORDER_MUTED);
        graphics.fill(enterX, footerY + 8, enterX + 36, footerY + 9, BORDER_MUTED);
        graphics.fill(enterX, footerY - 2, enterX + 1, footerY + 9, BORDER_MUTED);
        graphics.fill(enterX + 35, footerY - 2, enterX + 36, footerY + 9, BORDER_MUTED);
        graphics.drawString(this.font, "ENTER", enterX + 4, footerY, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "Commit", enterX + 41, footerY, TEXT_MUTED, false);

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
        if (nearbyEntityDropdown != null) nearbyEntityDropdown.renderOverlay(graphics, scaledMouseX, scaledMouseY);
        if (puppetActionDropdown != null) puppetActionDropdown.renderOverlay(graphics, scaledMouseX, scaledMouseY);

        graphics.pose().popPose();
    }
}
