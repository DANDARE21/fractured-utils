package net.dandare21.fracturedutils.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dandare21.fracturedutils.sound.event.ClientAudioConfig;
import net.dandare21.fracturedutils.sound.event.ClientAudioPackManager;
import net.dandare21.fracturedutils.sound.event.EventAudioClientController;
import net.dandare21.fracturedutils.sound.event.EventAudioManager;
import net.dandare21.fracturedutils.dialog.DialogFormatUtil;
import net.dandare21.fracturedutils.dialog.DialogLine;
import net.dandare21.fracturedutils.sound.sequence.MusicSequence;
import net.dandare21.fracturedutils.sound.sequence.MusicSequenceChannel;
import net.dandare21.fracturedutils.sound.sequence.MusicSequenceEntry;
import net.dandare21.fracturedutils.sound.sequence.MusicSequenceManager;
import net.dandare21.fracturedutils.client.camera.CustomCameraManager;
import net.dandare21.fracturedutils.client.camera.CameraSequenceTrack;
import net.dandare21.fracturedutils.client.camera.CameraTransform;
import net.dandare21.fracturedutils.client.camera.CameraPathRenderer;
import net.dandare21.fracturedutils.client.camera.SequencerViewportManager;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectInstance;
import net.dandare21.fracturedutils.screeneffect.client.ClientScreenEffectHandler;
import net.dandare21.fracturedutils.screeneffect.effects.CinematicBarsEffect;
import net.dandare21.fracturedutils.screeneffect.effects.HueShiftEffect;
import net.dandare21.fracturedutils.screeneffect.effects.ImpactFrameEffect;
import net.dandare21.fracturedutils.screeneffect.effects.InvertColorsEffect;
import net.dandare21.fracturedutils.screeneffect.effects.ScreenShakeEffect;
import net.dandare21.fracturedutils.screeneffect.effects.StrobeEffect;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.C2SDeleteMusicSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SSaveMusicSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SSpawnPuppetPacket;
import net.dandare21.fracturedutils.network.packet.C2SStartMusicSequencePacket;
import net.dandare21.fracturedutils.puppet.fsm.ActionTimingPhase;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionType;
import net.dandare21.fracturedutils.puppet.registry.ModPuppetActions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class MusicSequenceScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int CARD_BORDER = 0xAA00E5FF;
    private static final int PLAYHEAD_COLOR = 0xFFFF3355;
    private static final int TEXT_MUTED = 0xFF7A9BB0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Modal State Management
    public enum ModalType {
        NONE,
        ADD_CHANNEL,
        CONFIRM_DELETE_CHANNEL,
        CONFIRM_UNSAVED_CHANGES
    }

    public enum AddChannelStep {
        SELECT_TYPE,
        PUPPET_CONFIG
    }

    public enum PuppetTargetMode {
        REGISTER_NEW,
        EXISTING_ACTOR,
        SUB_CHANNEL
    }

    private ModalType activeModal = ModalType.NONE;
    private Runnable pendingCloseAction = null;
    private int modalDeleteChannelIndex = -1;

    // Add Channel Modal Sub-State
    private AddChannelStep modalAddChannelStep = AddChannelStep.SELECT_TYPE;
    private String modalSelectedType = MusicSequenceChannel.TYPE_COMMAND;
    private EditBox modalChannelNameBox;
    private EditBox modalActorNameBox;
    private EditBox modalActorTagBox;
    private PuppetTargetMode puppetTargetMode = PuppetTargetMode.REGISTER_NEW;
    private String modalSelectedEntityType = "fractured_utils:void_herald";
    private CyberpunkDropdown<String> modalEntityCatalogDropdown;
    private CyberpunkDropdown<String> modalExistingMobsDropdown;
    private CyberpunkDropdown<String> modalParentPuppetDropdown;
    private String modalSelectedParentChannelId = "";
    private boolean modalTagError = false;
    private boolean modalUserCustomizedName = false;
    private String modalStatusMessage = null;
    private long modalStatusMessageTime = 0L;

    private final Map<String, String> savedServerSequenceFiles;
    private final Map<String, String> workingServerSequenceFiles;
    private final Map<String, String> savedClientSequenceFiles;
    private final Map<String, String> workingClientSequenceFiles;
    private final List<String> availableTracks;
    private boolean isClientMode = false;

    private String currentFileName;
    private MusicSequence currentSequence = new MusicSequence();

    private MusicSequenceSelectScreen parentSelectScreen;

    private CyberpunkDropdown<String> fileDropdown;
    private CyberpunkDropdown<String> trackDropdown;
    private CyberpunkCheckbox loopingCheckbox;
    private EditBox bpmEditBox;
    private CyberpunkButton playPreviewBtn;

    // Timeline Zoom & Scroll Controls
    private double pixelsPerSecond = 50.0; // horizontal zoom (pixels per second)
    private double timeScrollMs = 0.0; // timeline horizontal scroll offset in milliseconds
    private double playheadMs = 0.0; // active preview playhead position in milliseconds
    private boolean isPreviewPlaying = false;
    private boolean isPreviewCameraActive = false;
    private boolean cameraPreviewEnabled = true;
    private CameraSequenceTrack cachedCameraTrack = null;
    private long lastPreviewTickTime = 0;
    private long lastDragAudioSeekTime = 0;

    // Timeline Drag, Pan & Selection
    private int draggedEntryIndex = -1;
    private MusicSequenceEntry draggedEntry = null;
    private boolean isDraggingPlayhead = false;
    private boolean isDraggingStartMarker = false;
    private boolean isDraggingEndMarker = false;
    private boolean isDraggingTimelineScroll = false;
    private boolean isPanningTimeline = false;
    private double lastDragMouseX = 0;
    private double lastPanMouseX = 0;
    private boolean autoFollowPlayhead = true;
    private int selectedEntryIndex = -1;

    // Timeline Duration Stretching
    private boolean isStretchingDuration = false;
    private MusicSequenceEntry stretchingEntry = null;
    private int stretchingEntryIndex = -1;

    // Puppet Phase Dragging ("Time Zones")
    private boolean isDraggingPuppetPhase = false;
    private MusicSequenceEntry puppetDraggingEntry = null;
    private int puppetDraggingEntryIndex = -1;
    private int puppetDraggingPhaseIndex = -1;
    private int puppetOriginalPhaseMs = 0;
    private int puppetOriginalNextPhaseMs = 0;
    private long puppetPhaseDragStartMs = 0;

    // Channel Layout & Premiere-Style Track Header Definitions
    public static final int TRACK_HEADER_WIDTH = 135;
    private double channelScrollY = 0.0;

    private String saveFeedbackMessage = null;
    private long saveFeedbackTime = 0;

    // Mine-imator / Axiom Style Viewport & Inspector State
    private SequencerRightPanel rightPanel;
    private boolean isRightPanelCollapsed = false;
    private CyberpunkButton inspectorToggleBtn;
    private int lastTransportEndX = 460;
    private boolean isViewportNavigating = false;
    private double lastViewportMouseX = 0;
    private double lastViewportMouseY = 0;
    private long lastFrameNanoTime = 0L;
    private boolean showCompositionGuides = false;
    private boolean isPreviewScreenEffects = true;
    private CyberpunkButton screenEffectsToggleBtn;
    private CyberpunkButton cameraModeBtn;
    private CyberpunkButton guidesToggleBtn;

    public MusicSequenceScreen(Map<String, String> serverSequenceFiles, List<String> availableTracks) {
        super(Component.literal("Timeline Music Sequence Orchestrator"));
        this.savedServerSequenceFiles = serverSequenceFiles != null ? new HashMap<>(serverSequenceFiles) : new HashMap<>();
        this.workingServerSequenceFiles = new HashMap<>(this.savedServerSequenceFiles);
        this.savedClientSequenceFiles = loadLocalClientSequences();
        this.workingClientSequenceFiles = new HashMap<>(this.savedClientSequenceFiles);

        Set<String> trackSet = new LinkedHashSet<>();
        if (availableTracks != null) {
            trackSet.addAll(availableTracks);
        }
        trackSet.addAll(net.dandare21.fracturedutils.sound.event.AudioTrackDiscovery.getAllAvailableTracks());

        this.availableTracks = new ArrayList<>(trackSet);

        if (this.savedServerSequenceFiles != null && !this.savedServerSequenceFiles.isEmpty()) {
            this.isClientMode = false;
        } else {
            boolean isMultiplayer = Minecraft.getInstance().getCurrentServer() != null || !Minecraft.getInstance().isSingleplayer();
            this.isClientMode = !isMultiplayer;
        }

        Map<String, String> activeMap = getActiveSequenceMap();
        if (!activeMap.isEmpty()) {
            this.currentFileName = activeMap.keySet().iterator().next();
            loadCurrentFileSequence();
        } else {
            this.currentFileName = "new_music_sequence.json";
            this.currentSequence = new MusicSequence(currentFileName, "", false, 1.0f, 1.0f, new ArrayList<>());
            activeMap.put(currentFileName, GSON.toJson(currentSequence));
            getActiveSavedMap().put(currentFileName, GSON.toJson(currentSequence));
        }
    }

    public void setParentSelectScreen(MusicSequenceSelectScreen parentSelectScreen) {
        this.parentSelectScreen = parentSelectScreen;
    }

    public MusicSequenceSelectScreen getParentSelectScreen() {
        return parentSelectScreen;
    }

    public Map<String, String> getWorkingServerSequenceFiles() {
        return workingServerSequenceFiles;
    }

    public List<String> getAvailableTracks() {
        return availableTracks;
    }

    public boolean isClientMode() {
        return isClientMode;
    }

    public CameraSequenceTrack getActiveCameraTrack() {
        return cachedCameraTrack;
    }

    public boolean isCameraPreviewEnabled() {
        return cameraPreviewEnabled;
    }

    public void selectSequenceFileDirectly(String fileName, boolean clientMode) {
        this.isClientMode = clientMode;
        if (isPreviewCameraActive) {
            CustomCameraManager.clearCustomCamera();
            this.isPreviewCameraActive = false;
        }
        saveCurrentSequenceToWorkingMap();
        this.currentFileName = fileName;
        loadCurrentFileSequence();
        this.playheadMs = 0;
        this.timeScrollMs = 0;
        if (this.minecraft != null && this.minecraft.screen == this) {
            this.init();
        }
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
        File dir = MusicSequenceManager.getInstance().getDirectory();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try {
                    String content = Files.readString(f.toPath());
                    map.put(f.getName(), content);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private void loadCurrentFileSequence() {
        Map<String, String> map = getActiveSequenceMap();
        String json = map.get(currentFileName);
        if (json != null) {
            try {
                MusicSequence seq = GSON.fromJson(json, MusicSequence.class);
                if (seq != null) {
                    this.currentSequence = seq;
                    this.currentSequence.getChannels();
                    this.currentSequence.sortEntriesByTimestamp();
                    return;
                }
            } catch (Exception ignored) {}
        }
        this.currentSequence = new MusicSequence(currentFileName, "", false, 1.0f, 1.0f, new ArrayList<>());
        this.currentSequence.getChannels();
    }

    public void saveCurrentSequenceToWorkingMap() {
        if (currentFileName != null && currentSequence != null) {
            currentSequence.sortEntriesByTimestamp();
            if (currentSequence.getEndMs() <= 0) {
                currentSequence.setEndMs(getEffectiveEndMs());
            }
            String json = GSON.toJson(currentSequence);
            getActiveWorkingMap().put(currentFileName, json);
        }
    }

    public boolean hasUnsavedChanges() {
        Map<String, String> saved = getActiveSavedMap();
        Map<String, String> working = getActiveWorkingMap();
        if (saved.size() != working.size()) return true;
        for (Map.Entry<String, String> entry : working.entrySet()) {
            String savedContent = saved.get(entry.getKey());
            if (savedContent == null || !savedContent.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public int getRightPanelWidth() {
        if (isRightPanelCollapsed) return 0;
        if (this.width < 520) {
            return Math.max(160, Math.min(200, (int) (this.width * 0.40)));
        } else if (this.width < 720) {
            return Math.max(180, Math.min(240, (int) (this.width * 0.32)));
        } else {
            return Math.max(220, Math.min(320, (int) (this.width * 0.28)));
        }
    }

    public int getMainAreaWidth() {
        return this.width - getRightPanelWidth();
    }

    public int getTimelineHeight() {
        int topBarH = 32;
        int maxAllowedH = Math.max(80, this.height - topBarH - 65);
        int desired;
        if (this.height < 280) {
            desired = (int) (this.height * 0.40);
        } else if (this.height < 400) {
            desired = (int) (this.height * 0.42);
        } else {
            desired = (int) (this.height * 0.44);
        }
        return Math.max(85, Math.min(maxAllowedH, desired));
    }

    public int getTimelineTop() {
        return this.height - getTimelineHeight();
    }

    public int getTimelineToolbarHeight() {
        return this.height < 320 ? 20 : 24;
    }

    public int getRulerHeight() {
        return this.height < 320 ? 16 : 20;
    }

    public int getWaveformHeight() {
        return this.height < 320 ? 22 : 32;
    }

    public int getChannelHeight() {
        return this.height < 320 ? 22 : 26;
    }

    public int getTrackHeaderWidth() {
        int tw = getTimelineWidth();
        if (tw < 300) {
            return Math.max(65, (int) (tw * 0.26));
        } else if (tw < 480) {
            return Math.max(85, Math.min(110, (int) (tw * 0.25)));
        } else {
            return TRACK_HEADER_WIDTH;
        }
    }

    public void toggleRightPanel() {
        this.isRightPanelCollapsed = !this.isRightPanelCollapsed;
        this.init();
    }

    public boolean isRightPanelCollapsed() {
        return this.isRightPanelCollapsed;
    }

    private double getLayoutScale() {
        if (this.width <= 0 || this.height <= 0) return 1.0;
        int targetW = 500;
        int targetH = 350;
        double scaleX = (double) this.width / targetW;
        double scaleY = (double) this.height / targetH;
        return Math.min(1.0, Math.min(scaleX, scaleY));
    }

    public MusicSequence getCurrentSequence() {
        return this.currentSequence;
    }

    public String getCurrentFileName() {
        return this.currentFileName;
    }

    public int getSelectedEntryIndex() {
        return this.selectedEntryIndex;
    }

    public MusicSequenceEntry getSelectedEntry() {
        if (selectedEntryIndex >= 0 && selectedEntryIndex < currentSequence.getEntries().size()) {
            return currentSequence.getEntries().get(selectedEntryIndex);
        }
        return null;
    }

    public MusicSequenceChannel getSelectedChannel() {
        MusicSequenceEntry sel = getSelectedEntry();
        if (sel != null && sel.getChannelId() != null) {
            MusicSequenceChannel ch = getChannelById(sel.getChannelId());
            if (ch != null) return ch;
        }
        if (!currentSequence.getChannels().isEmpty()) {
            return currentSequence.getChannels().get(0);
        }
        return null;
    }

    public MusicSequenceChannel getChannelById(String channelId) {
        if (channelId == null) return null;
        for (MusicSequenceChannel ch : currentSequence.getChannels()) {
            if (channelId.equalsIgnoreCase(ch.getId())) {
                return ch;
            }
        }
        return null;
    }

    public boolean isChannelType(MusicSequenceEntry entry, String type) {
        if (entry == null || entry.getChannelId() == null) return false;
        MusicSequenceChannel ch = getChannelById(entry.getChannelId());
        return ch != null && type.equalsIgnoreCase(ch.getType());
    }

    public String getPuppetActorTag(MusicSequenceEntry entry) {
        if (entry.getPuppetParams() != null && entry.getPuppetParams().containsKey("actorTag") && !entry.getPuppetParams().get("actorTag").isBlank()) {
            return entry.getPuppetParams().get("actorTag");
        }
        if (entry.getChannelId() != null) {
            MusicSequenceChannel ch = getChannelById(entry.getChannelId());
            if (ch != null) {
                if (ch.getActorTag() != null && !ch.getActorTag().isBlank()) {
                    return ch.getActorTag();
                }
                if (ch.getName() != null && !ch.getName().isBlank()) {
                    return ch.getName().toLowerCase(Locale.ROOT).replace(" ", "_");
                }
            }
        }
        return "puppet";
    }

    public String getPuppetActionId(MusicSequenceEntry entry) {
        if (entry.getPuppetParams() != null && entry.getPuppetParams().containsKey("action")) {
            return entry.getPuppetParams().get("action");
        }
        String cmd = entry.getCommand();
        if (cmd != null) {
            String q = cmd.trim();
            if (q.startsWith("puppet_action")) {
                for (String part : q.split("\\s+")) {
                    if (part.startsWith("action:")) {
                        return part.substring(7).trim();
                    }
                }
            } else if (!q.isBlank() && !q.startsWith("summon") && !q.startsWith("kill") && !q.startsWith("puppet_")) {
                return q;
            }
        }
        return ModPuppetActions.LEAP_SLAM.getId().toString();
    }

    public void updatePuppetEntryCommand(MusicSequenceEntry entry) {
        if ("EXECUTE_ACTION".equalsIgnoreCase(entry.getSubAction()) || entry.getSubAction() == null || entry.getSubAction().isEmpty()) {
            String actId = getPuppetActionId(entry);
            String tag = getPuppetActorTag(entry);
            String target = entry.getPuppetParam("target", "@p");
            PuppetActionType<?> act = ModPuppetActions.resolve(actId);
            List<ActionTimingPhase> phases = act.getTimingPhases();

            StringBuilder sb = new StringBuilder();
            sb.append("puppet_action tag:").append(tag).append(" action:").append(actId).append(" target:").append(target);
            for (int i = 0; i < phases.size(); i++) {
                ActionTimingPhase phase = phases.get(i);
                int ms = entry.getPhaseMs(phase, i);
                sb.append(" ").append(phase.id()).append(":").append(ms);
            }
            if (entry.getPuppetParams() != null) {
                for (Map.Entry<String, String> pe : entry.getPuppetParams().entrySet()) {
                    String k = pe.getKey();
                    if ("action".equals(k) || "target".equals(k) || "actorTag".equals(k)) continue;
                    if ("windup".equals(k) || "jump".equals(k) || "duration".equals(k) || "recovery".equals(k)) continue;
                    sb.append(" ").append(k).append(":").append(pe.getValue());
                }
            }
            entry.setCommand(sb.toString());
        }
    }

    public void updateScreenEffectEntryCommand(MusicSequenceEntry entry) {
        if (entry == null) return;
        int duration = entry.getTotalDurationMs() > 0 ? entry.getTotalDurationMs() : (entry.getDurationMs() > 0 ? entry.getDurationMs() : 1000);
        String mode = entry.getSubAction();
        if (mode == null || mode.isBlank()) mode = "SCREEN_SHAKE";

        if ("INVERT_COLORS".equalsIgnoreCase(mode)) {
            entry.setScreenEffectId("fractured_utils:invert_colors");
            entry.setCommand("screeneffect invert " + duration + (entry.isScreenEffectPulse() ? " pulse " + entry.getScreenEffectFrequency() : ""));
            entry.setDescription("Invert Colors (" + duration + "ms" + (entry.isScreenEffectPulse() ? ", pulse" : "") + ")");
        } else if ("STROBE".equalsIgnoreCase(mode)) {
            entry.setScreenEffectId("fractured_utils:strobe");
            float freq = entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 10.0f;
            entry.setCommand("screeneffect strobe " + duration + " " + freq);
            entry.setDescription("Strobe Flash (" + freq + "Hz, " + duration + "ms)");
        } else if ("HUE_SHIFT".equalsIgnoreCase(mode)) {
            entry.setScreenEffectId("fractured_utils:hue_shift");
            entry.setCommand("screeneffect hue_shift " + duration + (entry.isScreenEffectContinuous() ? " cycle " + entry.getScreenEffectFrequency() : " angle " + (int)entry.getScreenEffectAngle()));
            entry.setDescription("Hue Shift (" + (entry.isScreenEffectContinuous() ? entry.getScreenEffectFrequency() + " rot/s" : (int)entry.getScreenEffectAngle() + "°") + ", " + duration + "ms)");
        } else if ("IMPACT_FRAME".equalsIgnoreCase(mode)) {
            entry.setScreenEffectId("fractured_utils:impact_frame");
            int primary = entry.getScreenEffectColor() != 0 ? entry.getScreenEffectColor() : 0xFFFFFFFF;
            int secondary = entry.getScreenEffectSecondaryColor() != 0 ? entry.getScreenEffectSecondaryColor() : 0xFF000000;
            String st = (entry.getScreenEffectStyle() != null && !entry.getScreenEffectStyle().isBlank()) ? entry.getScreenEffectStyle() : "DRAW";
            entry.setCommand("screeneffect impact_frame " + duration + " " + String.format(Locale.ROOT, "#%06X", primary & 0x00FFFFFF) + " " + String.format(Locale.ROOT, "#%06X", secondary & 0x00FFFFFF));
            entry.setDescription("Impact Frame: " + st + " (" + duration + "ms)");
        } else if ("CINEMATIC_BARS".equalsIgnoreCase(mode)) {
            entry.setScreenEffectId("fractured_utils:cinematic_bars");
            int inMs = entry.getScreenEffectTransitionInMs() > 0 ? entry.getScreenEffectTransitionInMs() : 500;
            int outMs = entry.getScreenEffectTransitionOutMs() > 0 ? entry.getScreenEffectTransitionOutMs() : 500;
            entry.setCommand("screeneffect cinematic_bars " + duration + " " + inMs + " " + outMs);
            int pct = (int)(entry.getScreenEffectBarHeight() * 100.0f);
            if (pct <= 0) pct = 12;
            entry.setDescription("Cinematic Bars (" + pct + "%, " + duration + "ms)");
        } else {
            // SCREEN_SHAKE
            entry.setScreenEffectId("fractured_utils:screen_shake");
            float intensity = entry.getScreenEffectIntensity() > 0 ? entry.getScreenEffectIntensity() : 2.0f;
            entry.setCommand(String.format(Locale.ROOT, "screeneffect shake %d %.1f", duration, intensity));
            entry.setDescription("Screen Shake (" + String.format(Locale.ROOT, "%.1f", intensity) + " amp, " + duration + "ms)");
        }
    }

    public double getPlayheadMs() {
        return this.playheadMs;
    }

    public void setPlayheadMs(double ms) {
        this.playheadMs = Math.max(0.0, ms);
        if (isPreviewPlaying) {
            relocatePlaybackAudio((long) this.playheadMs);
        }
        updatePreviewCamera();
        updatePreviewScreenEffects();
    }

    public void duplicateSelectedEntry() {
        if (selectedEntryIndex >= 0 && selectedEntryIndex < currentSequence.getEntries().size()) {
            duplicateEntry(selectedEntryIndex);
            if (rightPanel != null) {
                rightPanel.updateSelection(selectedEntryIndex, getSelectedEntry());
            }
        }
    }

    public void deleteSelectedEntry() {
        if (selectedEntryIndex >= 0 && selectedEntryIndex < currentSequence.getEntries().size()) {
            deleteEntry(selectedEntryIndex);
            this.selectedEntryIndex = -1;
            if (rightPanel != null) {
                rightPanel.updateSelection(-1, null);
            }
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int mainAreaW = getMainAreaWidth();
        int rightPanelW = getRightPanelWidth();
        int topBarH = 32;
        int timelineTop = getTimelineTop();
        int viewportH = Math.max(10, timelineTop - topBarH);

        // Configure Axiom-style 3D camera viewport
        SequencerViewportManager.setViewportBounds(0, topBarH, mainAreaW, viewportH);
        SequencerViewportManager.setActive(true);
        SequencerViewportManager.initWorkCameraFromCurrent();

        int topY = 6;
        int leftX = 8;

        // --- Responsive Right-Side Top Bar Controls ---
        int rX = this.width - 4;

        // 1. Close Button
        rX -= 18;
        CyberpunkButton closeBtn = new CyberpunkButton(rX, topY, 18, 20, Component.literal("✕"), b -> this.onClose(), 0xFFFF3355, false);
        closeBtn.setTooltip(Tooltip.create(Component.literal("Close Sequencer")));
        this.addRenderableWidget(closeBtn);
        rX -= 4;

        // 2. Save Button
        boolean isSmallWidth = this.width < 560;
        int saveW = isSmallWidth ? 26 : 46;
        rX -= saveW;
        CyberpunkButton saveBtn = new CyberpunkButton(rX, topY, saveW, 20, Component.literal(isSmallWidth ? "💾" : "SAVE"), b -> saveCurrentFile(), CYAN_MAIN, false);
        saveBtn.setTooltip(Tooltip.create(Component.literal("Save sequence to disk [CTRL+S]")));
        this.addRenderableWidget(saveBtn);
        rX -= 4;

        // 3. Inspector Panel Toggle Button
        int panelBtnW = isSmallWidth ? 22 : 26;
        rX -= panelBtnW;
        this.inspectorToggleBtn = new CyberpunkButton(rX, topY, panelBtnW, 20,
                Component.literal(isRightPanelCollapsed ? "◀" : "▶"),
                b -> toggleRightPanel(),
                isRightPanelCollapsed ? 0xFF00FF88 : 0xFF7A9BB0,
                false
        );
        this.inspectorToggleBtn.setTooltip(Tooltip.create(Component.literal(isRightPanelCollapsed ? "Expand Inspector Panel [P]" : "Collapse Inspector Panel [P]")));
        this.addRenderableWidget(this.inspectorToggleBtn);
        rX -= 4;

        // 4. Composition Guides Toggle Button
        int guidesW = this.width < 520 ? 22 : (this.width < 680 ? 44 : 62);
        rX -= guidesW;
        String guidesText = this.width < 520 ? "📐" : (this.width < 680 ? (showCompositionGuides ? "G:ON" : "G:OFF") : (showCompositionGuides ? "GUIDES: ON" : "GUIDES: OFF"));
        this.guidesToggleBtn = new CyberpunkButton(rX, topY, guidesW, 20,
                Component.literal(guidesText),
                b -> {
                    this.showCompositionGuides = !this.showCompositionGuides;
                    this.init();
                },
                showCompositionGuides ? 0xFF00FF88 : 0xFF7A9BB0,
                false
        );
        this.guidesToggleBtn.setTooltip(Tooltip.create(Component.literal("Toggle Rule-of-Thirds & Reticle guides in Viewport [G]")));
        this.addRenderableWidget(this.guidesToggleBtn);
        rX -= 4;

        // 5. Screen Effects Live Preview Toggle Button
        int fxW = this.width < 520 ? 22 : (this.width < 680 ? 46 : 64);
        rX -= fxW;
        String fxText = this.width < 520 ? "⚡" : (this.width < 680 ? (isPreviewScreenEffects ? "FX:ON" : "FX:OFF") : (isPreviewScreenEffects ? "⚡ FX: ON" : "⚡ FX: OFF"));
        this.screenEffectsToggleBtn = new CyberpunkButton(rX, topY, fxW, 20,
                Component.literal(fxText),
                b -> {
                    this.isPreviewScreenEffects = !this.isPreviewScreenEffects;
                    if (!this.isPreviewScreenEffects) {
                        ClientScreenEffectHandler.clearAllEffects();
                    } else {
                        updatePreviewScreenEffects();
                    }
                    this.init();
                },
                isPreviewScreenEffects ? 0xFFFF00CC : 0xFF7A9BB0,
                false
        );
        this.screenEffectsToggleBtn.setTooltip(Tooltip.create(Component.literal("Toggle live Screen Effects preview when playhead is on top [X]")));
        this.addRenderableWidget(this.screenEffectsToggleBtn);
        rX -= 4;

        // 6. Camera Mode Toggle Button
        boolean isWorkCam = (SequencerViewportManager.getCurrentMode() == SequencerViewportManager.CameraMode.WORK_CAMERA);
        int camModeW = this.width < 520 ? 24 : (this.width < 680 ? 62 : 110);
        rX -= camModeW;
        String camText = this.width < 520 ? (isWorkCam ? "🎥" : "🎬") : (this.width < 680 ? (isWorkCam ? "🎥 WORK" : "🎬 SEQ") : (isWorkCam ? "🎥 WORK CAM" : "🎬 SEQUENCE CAM"));
        this.cameraModeBtn = new CyberpunkButton(rX, topY, camModeW, 20,
                Component.literal(camText),
                b -> toggleCameraMode(),
                isWorkCam ? 0xFFFFD700 : CYAN_MAIN,
                false
        );
        this.cameraModeBtn.setTooltip(Tooltip.create(Component.literal("Switch between Sequence keyframe camera and Interactive Work camera [Tab]")));
        this.addRenderableWidget(this.cameraModeBtn);

        // --- Responsive Left-Side Top Bar Controls (File, Song, BPM, Loop) ---
        int availLeft = rX - leftX - 8;
        int fileW;
        int newBtnW;
        int trackW;
        int bpmW;
        int loopW;

        if (availLeft < 260) {
            fileW = Math.max(55, (int) (availLeft * 0.32));
            newBtnW = 20;
            trackW = Math.max(60, (int) (availLeft * 0.36));
            bpmW = 28;
            loopW = 24;
        } else if (availLeft < 360) {
            fileW = 85;
            newBtnW = 34;
            trackW = 95;
            bpmW = 32;
            loopW = 44;
        } else {
            fileW = 115;
            newBtnW = 44;
            trackW = 125;
            bpmW = 36;
            loopW = 55;
        }

        int curLeftX = leftX;

        // File dropdown
        List<CyberpunkDropdown.DropdownEntry<String>> fileEntries = new ArrayList<>();
        List<String> sortedFiles = new ArrayList<>(getActiveSequenceMap().keySet());
        Collections.sort(sortedFiles);
        for (String f : sortedFiles) {
            fileEntries.add(new CyberpunkDropdown.DropdownEntry<>(f, Component.literal(f)));
        }
        this.fileDropdown = new CyberpunkDropdown<>(curLeftX, topY, fileW, 20, Component.literal("Sequence"));
        this.fileDropdown.setOptions(fileEntries);
        this.fileDropdown.selectByValue(currentFileName);
        this.fileDropdown.setOnSelect(entry -> selectSequenceFile(entry.getValue()));
        this.addRenderableWidget(this.fileDropdown);
        curLeftX += fileW + 3;

        // + NEW button
        CyberpunkButton addFileBtn = new CyberpunkButton(curLeftX, topY, newBtnW, 20, Component.literal(newBtnW <= 22 ? "+" : "+ NEW"), b -> openCreateSequenceModal(), CYAN_MAIN, false);
        addFileBtn.setTooltip(Tooltip.create(Component.literal("Create a new music sequence")));
        this.addRenderableWidget(addFileBtn);
        curLeftX += newBtnW + 3;

        // Track dropdown
        Set<String> dynamicTracks = new LinkedHashSet<>(availableTracks);
        dynamicTracks.addAll(net.dandare21.fracturedutils.sound.event.AudioTrackDiscovery.getAllAvailableTracks());
        if (currentSequence.getSongTrack() != null && !currentSequence.getSongTrack().isEmpty()) {
            dynamicTracks.add(currentSequence.getSongTrack());
        }
        List<CyberpunkDropdown.DropdownEntry<String>> songEntries = new ArrayList<>();
        songEntries.add(new CyberpunkDropdown.DropdownEntry<>("", Component.literal("[No Audio]")));
        for (String track : dynamicTracks) {
            String label = net.dandare21.fracturedutils.sound.event.AudioTrackDiscovery.formatTrackLabel(track);
            songEntries.add(new CyberpunkDropdown.DropdownEntry<>(track, Component.literal(label), Component.literal(track)));
        }
        this.trackDropdown = new CyberpunkDropdown<>(curLeftX, topY, trackW, 20, Component.literal("Song Track"));
        this.trackDropdown.setMaxVisibleItems(8);
        this.trackDropdown.setOptions(songEntries);
        this.trackDropdown.selectByValue(currentSequence.getSongTrack());
        this.trackDropdown.setOnSelect(selected -> {
            currentSequence.setSongTrack(selected.getValue());
            saveCurrentSequenceToWorkingMap();
        });
        this.addRenderableWidget(this.trackDropdown);
        curLeftX += trackW + 3;

        // BPM box
        this.bpmEditBox = new EditBox(this.font, curLeftX, topY, bpmW, 20, Component.literal("BPM"));
        this.bpmEditBox.setMaxLength(3);
        this.bpmEditBox.setValue(String.valueOf(currentSequence.getBpm()));
        this.bpmEditBox.setHint(Component.literal("BPM"));
        this.bpmEditBox.setTooltip(Tooltip.create(Component.literal("Song Tempo in BPM")));
        this.bpmEditBox.setResponder(val -> {
            try {
                int bpm = Integer.parseInt(val.trim());
                if (bpm >= 20 && bpm <= 300) {
                    currentSequence.setBpm(bpm);
                    saveCurrentSequenceToWorkingMap();
                }
            } catch (Exception ignored) {}
        });
        this.addRenderableWidget(this.bpmEditBox);
        curLeftX += bpmW + 3;

        // Loop checkbox
        String loopLabel = loopW <= 28 ? "🔁" : "Loop";
        this.loopingCheckbox = new CyberpunkCheckbox(
                curLeftX, topY, loopW, 20,
                Component.literal(loopLabel),
                currentSequence.isLooping(),
                checked -> {
                    currentSequence.setLooping(checked);
                    saveCurrentSequenceToWorkingMap();
                }
        );
        this.loopingCheckbox.setTooltip(Tooltip.create(Component.literal("Toggle loop playback")));
        this.addRenderableWidget(this.loopingCheckbox);

        // 3. Timeline Transport Controls Bar (Positioned directly above the timeline canvas)
        int toolbarHeight = getTimelineToolbarHeight();
        int toolbarY = timelineTop + 2;
        int btnH = Math.min(20, toolbarHeight - 2);
        int tX = 4;
        int tw = getTimelineWidth();
        boolean isCompactTb = tw < 480;

        int playW = isCompactTb ? 26 : 58;
        String playLabel = isCompactTb ? (isPreviewPlaying ? "⏸" : "▶") : (isPreviewPlaying ? "⏸ PAUSE" : "▶ PLAY");
        this.playPreviewBtn = new CyberpunkButton(tX, toolbarY, playW, btnH,
                Component.literal(playLabel),
                b -> togglePreviewPlayback(),
                isPreviewPlaying ? 0xFFFFD700 : 0xFF00FF88,
                false,
                Component.literal("Play / Pause [Space]")
        );
        this.addRenderableWidget(this.playPreviewBtn);
        tX += playW + 3;

        int stepW = isCompactTb ? 18 : 20;
        int dStepW = isCompactTb ? 18 : 22;

        CyberpunkButton rewindBtn = new CyberpunkButton(tX, toolbarY, stepW, btnH, Component.literal("|◄"), b -> setPlayheadMs(0.0), CYAN_MAIN, false, Component.literal("Jump to Start [Home]"));
        this.addRenderableWidget(rewindBtn);
        tX += stepW + 2;

        CyberpunkButton stepBackBtn = new CyberpunkButton(tX, toolbarY, dStepW, btnH, Component.literal("◄◄"), b -> setPlayheadMs(Math.max(0.0, playheadMs - 100.0)), CYAN_MAIN, false, Component.literal("Step Back 100ms [,]"));
        this.addRenderableWidget(stepBackBtn);
        tX += dStepW + 2;

        CyberpunkButton stepFwdBtn = new CyberpunkButton(tX, toolbarY, dStepW, btnH, Component.literal("►►"), b -> setPlayheadMs(playheadMs + 100.0), CYAN_MAIN, false, Component.literal("Step Forward 100ms [.]"));
        this.addRenderableWidget(stepFwdBtn);
        tX += dStepW + 2;

        CyberpunkButton jumpEndBtn = new CyberpunkButton(tX, toolbarY, stepW, btnH, Component.literal("►|"), b -> setPlayheadMs(getEffectiveEndMs()), CYAN_MAIN, false, Component.literal("Jump to End [End]"));
        this.addRenderableWidget(jumpEndBtn);
        tX += stepW + 3;

        int followW = isCompactTb ? 20 : 24;
        CyberpunkButton followBtn = new CyberpunkButton(tX, toolbarY, followW, btnH,
                Component.literal(autoFollowPlayhead ? "🎯" : "⚪"),
                b -> {
                    this.autoFollowPlayhead = !this.autoFollowPlayhead;
                    this.init();
                },
                autoFollowPlayhead ? 0xFF00FF88 : 0xFF7A9BB0,
                false,
                Component.literal("Toggle auto-follow timeline playhead")
        );
        this.addRenderableWidget(followBtn);
        tX += followW + 3;

        int zoomW = isCompactTb ? 18 : 20;
        CyberpunkButton zoomOutBtn = new CyberpunkButton(tX, toolbarY, zoomW, btnH, Component.literal("-"), b -> adjustZoom(0.8), CYAN_MAIN, false, Component.literal("Zoom Out Timeline"));
        this.addRenderableWidget(zoomOutBtn);
        tX += zoomW + 2;

        CyberpunkButton zoomInBtn = new CyberpunkButton(tX, toolbarY, zoomW, btnH, Component.literal("+"), b -> adjustZoom(1.25), CYAN_MAIN, false, Component.literal("Zoom In Timeline"));
        this.addRenderableWidget(zoomInBtn);
        tX += zoomW + 3;

        int addKeyW = isCompactTb ? 26 : 58;
        CyberpunkButton addKeyframeBtn = new CyberpunkButton(tX, toolbarY, addKeyW, btnH,
                Component.literal(isCompactTb ? "+E" : "+ ENTRY"),
                b -> createAndSelectNewEntryAtPlayhead(),
                CYAN_MAIN,
                false,
                Component.literal("Add keyframe at current playhead")
        );
        this.addRenderableWidget(addKeyframeBtn);
        tX += addKeyW + 3;

        int stampW = isCompactTb ? 26 : 68;
        CyberpunkButton stampCameraBtn = new CyberpunkButton(tX, toolbarY, stampW, btnH,
                Component.literal(isCompactTb ? "📷" : "📷 STAMP"),
                b -> stampCameraKeyframeAtPlayhead(),
                0xFF00FF88,
                false,
                Component.literal("Stamp current 3D view as camera keyframe at playhead [C]")
        );
        this.addRenderableWidget(stampCameraBtn);
        tX += stampW + 3;

        int pathW = isCompactTb ? 26 : 58;
        String pathLabel = isCompactTb
                ? (CameraPathRenderer.isPathPreviewEnabled() ? "🌐" : "⚪")
                : (CameraPathRenderer.isPathPreviewEnabled() ? "🌐 PATH" : "⚪ PATH");
        CyberpunkButton pathPreviewBtn = new CyberpunkButton(tX, toolbarY, pathW, btnH,
                Component.literal(pathLabel),
                b -> {
                    CameraPathRenderer.togglePathPreview();
                    this.init();
                },
                CameraPathRenderer.isPathPreviewEnabled() ? MusicSequenceChannel.COLOR_CAMERA : 0xFF7A9BB0,
                false,
                Component.literal("Toggle in-world 3D camera spline & frustum visualization")
        );
        this.addRenderableWidget(pathPreviewBtn);
        tX += pathW;
        this.lastTransportEndX = tX;

        // 4. Initialize Right Inspector Panel
        if (!isRightPanelCollapsed) {
            if (this.rightPanel == null) {
                this.rightPanel = new SequencerRightPanel(this, mainAreaW, topBarH, rightPanelW, this.height - topBarH);
            } else {
                this.rightPanel.setBounds(mainAreaW, topBarH, rightPanelW, this.height - topBarH);
            }
            if (selectedEntryIndex >= 0 && selectedEntryIndex < currentSequence.getEntries().size()) {
                this.rightPanel.updateSelection(selectedEntryIndex, currentSequence.getEntries().get(selectedEntryIndex));
            } else {
                this.rightPanel.rebuildWidgets();
            }
        } else if (this.rightPanel != null) {
            this.rightPanel.setBounds(this.width, topBarH, 0, this.height - topBarH);
        }

        if (activeModal == ModalType.ADD_CHANNEL) {
            updateModalWidgetsPosition();
        }
    }

    public void requestCloseWithUnsavedCheck(Runnable closeAction) {
        saveCurrentSequenceToWorkingMap();
        if (hasUnsavedChanges()) {
            this.pendingCloseAction = closeAction;
            this.activeModal = ModalType.CONFIRM_UNSAVED_CHANGES;
        } else {
            closeAction.run();
        }
    }

    private void togglePreviewPlayback() {
        if (!this.isPreviewPlaying) {
            long endMs = getEffectiveEndMs();
            if (this.playheadMs >= endMs || this.playheadMs < currentSequence.getStartMs()) {
                this.playheadMs = currentSequence.getStartMs();
            }
        }
        this.isPreviewPlaying = !this.isPreviewPlaying;
        this.lastPreviewTickTime = System.currentTimeMillis();

        if (this.isPreviewPlaying) {
            String songTrack = currentSequence.getSongTrack();
            if (songTrack != null && !songTrack.trim().isEmpty()) {
                EventAudioClientController.getInstance().playAudio(
                        songTrack,
                        net.dandare21.fracturedutils.sound.ModSoundSources.EVENT_MUSIC,
                        currentSequence.getVolume(),
                        currentSequence.getPitch(),
                        0,
                        (long) playheadMs,
                        true,
                        net.dandare21.fracturedutils.network.packet.S2CPlayEventAudioPacket.PlaybackMode.FIRE_AND_FORGET,
                        currentSequence.isLooping(),
                        2000
                );
            }
        } else {
            EventAudioClientController.getInstance().stopAudio(0);
            if (isPreviewCameraActive) {
                CustomCameraManager.clearCustomCamera();
                this.isPreviewCameraActive = false;
            }
        }
        this.init();
    }

    private void returnToSelectScreen() {
        requestCloseWithUnsavedCheck(this::returnToSelectScreenDirect);
    }

    private void returnToSelectScreenDirect() {
        saveCurrentSequenceToWorkingMap();
        if (isPreviewPlaying) {
            EventAudioClientController.getInstance().stopAudio(0);
            this.isPreviewPlaying = false;
        }
        if (isPreviewCameraActive) {
            CustomCameraManager.clearCustomCamera();
            this.isPreviewCameraActive = false;
        }
        if (this.minecraft != null) {
            if (this.parentSelectScreen != null) {
                this.parentSelectScreen.getWorkingServerSequenceFiles().clear();
                this.parentSelectScreen.getWorkingServerSequenceFiles().putAll(this.workingServerSequenceFiles);
                this.minecraft.setScreen(this.parentSelectScreen);
            } else {
                MusicSequenceSelectScreen selectScreen = new MusicSequenceSelectScreen(this);
                this.minecraft.setScreen(selectScreen);
            }
        }
    }

    @Override
    public void onClose() {
        requestCloseWithUnsavedCheck(this::onCloseDirect);
    }

    private void onCloseDirect() {
        SequencerViewportManager.setActive(false);
        if (isPreviewPlaying) {
            EventAudioClientController.getInstance().stopAudio(0);
            this.isPreviewPlaying = false;
        }
        CustomCameraManager.clearCustomCamera();
        this.isPreviewCameraActive = false;
        ClientScreenEffectHandler.clearAllEffects();
        if (this.parentSelectScreen != null && this.minecraft != null) {
            this.parentSelectScreen.getWorkingServerSequenceFiles().clear();
            this.parentSelectScreen.getWorkingServerSequenceFiles().putAll(this.workingServerSequenceFiles);
            this.minecraft.setScreen(this.parentSelectScreen);
        } else {
            super.onClose();
        }
    }

    private void adjustZoom(double factor) {
        this.pixelsPerSecond = Math.max(10.0, Math.min(300.0, this.pixelsPerSecond * factor));
    }

    private void selectSequenceFile(String fileName) {
        if (isPreviewCameraActive) {
            CustomCameraManager.clearCustomCamera();
            this.isPreviewCameraActive = false;
        }
        saveCurrentSequenceToWorkingMap();
        this.currentFileName = fileName;
        loadCurrentFileSequence();
        this.playheadMs = 0;
        this.timeScrollMs = 0;
        this.init();
    }

    private void openCreateSequenceModal() {
        saveCurrentSequenceToWorkingMap();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new CreateMusicSequenceModalScreen(this, availableTracks, newSequence -> {
                String fileName = newSequence.getFileName();
                if (fileName == null || fileName.isBlank()) fileName = "sequence.json";
                if (!fileName.endsWith(".json")) fileName += ".json";
                fileName = fileName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
                newSequence.setFileName(fileName);
                String json = GSON.toJson(newSequence);

                getActiveWorkingMap().put(fileName, json);
                if (isClientMode) {
                    MusicSequenceManager.getInstance().saveSequenceFile(fileName, json);
                    savedClientSequenceFiles.put(fileName, json);
                } else {
                    ModMessages.sendToServer(new C2SSaveMusicSequencePacket(fileName, json));
                    savedServerSequenceFiles.put(fileName, json);
                }
                selectSequenceFile(fileName);
            }));
        }
    }

    public void setSelectedEntryIndex(int index) {
        this.selectedEntryIndex = index;
        if (this.isRightPanelCollapsed) {
            this.isRightPanelCollapsed = false;
            this.init();
        }
        if (this.rightPanel != null) {
            this.rightPanel.setTab(SequencerRightPanel.InspectorTab.ACTION);
            MusicSequenceEntry entry = (index >= 0 && currentSequence != null && index < currentSequence.getEntries().size())
                    ? currentSequence.getEntries().get(index)
                    : null;
            this.rightPanel.updateSelection(index, entry);
        }
    }

    public void createAndSelectNewEntryAtPlayhead() {
        createAndSelectNewEntryAt(snapTimestamp((long) playheadMs, hasShiftDown()));
    }

    public void createAndSelectNewEntryAt(long targetTimestampMs) {
        if (currentSequence == null) return;
        saveCurrentSequenceToWorkingMap();

        String actionType = "COMMAND";
        String channelId = "";

        MusicSequenceEntry template = (selectedEntryIndex >= 0 && selectedEntryIndex < currentSequence.getEntries().size())
                ? currentSequence.getEntries().get(selectedEntryIndex)
                : null;

        MusicSequenceEntry newEntry;
        if (template != null) {
            newEntry = template.copy();
            newEntry.setTimestampMs(targetTimestampMs);
        } else {
            if (!currentSequence.getChannels().isEmpty()) {
                MusicSequenceChannel firstCh = currentSequence.getChannels().get(0);
                channelId = firstCh.getId();
                actionType = firstCh.getType();
            }
            newEntry = new MusicSequenceEntry(targetTimestampMs, actionType, channelId, "", "");
            if ("CAMERA".equalsIgnoreCase(actionType)) {
                newEntry.setUseCamera(true);
                boolean isWorkCam = (SequencerViewportManager.getCurrentMode() == SequencerViewportManager.CameraMode.WORK_CAMERA);
                Vec3 camPos = isWorkCam ? SequencerViewportManager.getWorkCameraPos() : (minecraft != null && minecraft.player != null ? minecraft.player.getEyePosition() : Vec3.ZERO);
                float camYaw = isWorkCam ? SequencerViewportManager.getWorkCameraYaw() : (minecraft != null && minecraft.player != null ? minecraft.player.getYRot() : 0.0f);
                float camPitch = isWorkCam ? SequencerViewportManager.getWorkCameraPitch() : (minecraft != null && minecraft.player != null ? minecraft.player.getXRot() : 0.0f);
                float camRoll = isWorkCam ? SequencerViewportManager.getWorkCameraRoll() : 0.0f;
                double camFov = isWorkCam ? SequencerViewportManager.getWorkCameraFov() : 70.0;

                newEntry.setCameraX(camPos.x);
                newEntry.setCameraY(camPos.y);
                newEntry.setCameraZ(camPos.z);
                newEntry.setCameraYaw(camYaw);
                newEntry.setCameraPitch(camPitch);
                newEntry.setCameraRoll(camRoll);
                newEntry.setCameraFov(camFov);
                newEntry.setCameraMode("KEYFRAME");
                newEntry.setSplineMode("CATMULL_ROM");
                newEntry.setCameraEasing("EASE_IN_OUT_CUBIC");
                newEntry.setCameraInterpolate(true);
            }
        }

        currentSequence.getEntries().add(newEntry);
        currentSequence.sortEntriesByTimestamp();
        int newIdx = currentSequence.getEntries().indexOf(newEntry);
        setSelectedEntryIndex(newIdx);
        saveCurrentSequenceToWorkingMap();
        playButtonSound();
    }

    public void openEditEntryModal(int index) {
        if (index >= 0 && currentSequence != null && index < currentSequence.getEntries().size()) {
            setSelectedEntryIndex(index);
        } else {
            createAndSelectNewEntryAt((long) playheadMs);
        }
    }

    public void openEditEntryModal(int index, long defaultTimestampMs) {
        if (index >= 0 && currentSequence != null && index < currentSequence.getEntries().size()) {
            setSelectedEntryIndex(index);
        } else {
            createAndSelectNewEntryAt(defaultTimestampMs);
        }
    }

    public void moveEntry(int fromIndex, int toIndex) {
        List<MusicSequenceEntry> entries = currentSequence.getEntries();
        if (fromIndex >= 0 && fromIndex < entries.size() && toIndex >= 0 && toIndex < entries.size()) {
            MusicSequenceEntry item = entries.remove(fromIndex);
            entries.add(toIndex, item);
            saveCurrentSequenceToWorkingMap();
            this.init();
        }
    }

    public void deleteEntry(int index) {
        if (index >= 0 && index < currentSequence.getEntries().size()) {
            currentSequence.getEntries().remove(index);
            if (this.selectedEntryIndex == index) {
                this.selectedEntryIndex = -1;
                if (this.rightPanel != null) {
                    this.rightPanel.updateSelection(-1, null);
                }
            } else if (this.selectedEntryIndex > index) {
                this.selectedEntryIndex--;
            }
            saveCurrentSequenceToWorkingMap();
            this.init();
        }
    }

    public void duplicateEntry(int index) {
        if (index >= 0 && index < currentSequence.getEntries().size()) {
            MusicSequenceEntry orig = currentSequence.getEntries().get(index);
            MusicSequenceEntry copy = orig.copy();
            long offset = copy.getTotalDurationMs() > 0 ? copy.getTotalDurationMs() : 250L;
            copy.setTimestampMs(orig.getTimestampMs() + offset);
            currentSequence.getEntries().add(copy);
            currentSequence.sortEntriesByTimestamp();
            this.selectedEntryIndex = currentSequence.getEntries().indexOf(copy);
            saveCurrentSequenceToWorkingMap();
            if (this.rightPanel != null) {
                this.rightPanel.updateSelection(this.selectedEntryIndex, copy);
            }
            playButtonSound();
            this.init();
        }
    }

    public void openConfirmDeleteChannelModal(int channelIndex) {
        this.modalDeleteChannelIndex = channelIndex;
        this.activeModal = ModalType.CONFIRM_DELETE_CHANNEL;
    }

    public void openConfirmDeleteChannelModal(MusicSequenceChannel channel) {
        if (channel != null) {
            int idx = currentSequence.getChannels().indexOf(channel);
            if (idx >= 0) {
                openConfirmDeleteChannelModal(idx);
            }
        }
    }

    public void deleteChannel(int index) {
        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        if (index >= 0 && index < channels.size()) {
            MusicSequenceChannel removed = channels.get(index);
            Set<String> idsToRemove = new HashSet<>();
            idsToRemove.add(removed.getId());

            // If deleting a parent channel, also delete all its child sub-channels
            if (!removed.isSubChannel()) {
                for (MusicSequenceChannel c : channels) {
                    if (c.isSubChannel() && removed.getId().equalsIgnoreCase(c.getParentChannelId())) {
                        idsToRemove.add(c.getId());
                    }
                }
            }

            channels.removeIf(c -> idsToRemove.contains(c.getId()));
            currentSequence.getEntries().removeIf(e -> idsToRemove.contains(e.getChannelId()));
            saveCurrentSequenceToWorkingMap();
            this.init();
        }
    }

    public void addSubChannel(MusicSequenceChannel targetChannel) {
        if (targetChannel == null) return;
        saveCurrentSequenceToWorkingMap();

        // 1. Identify the root parent channel
        MusicSequenceChannel rootParent = targetChannel;
        if (targetChannel.isSubChannel()) {
            for (MusicSequenceChannel c : currentSequence.getChannels()) {
                if (c.getId().equalsIgnoreCase(targetChannel.getParentChannelId())) {
                    rootParent = c;
                    break;
                }
            }
        }

        // 2. Count existing sub-channels for this root parent
        int existingCount = 0;
        int lastIndex = currentSequence.getChannels().indexOf(rootParent);
        for (int i = 0; i < currentSequence.getChannels().size(); i++) {
            MusicSequenceChannel c = currentSequence.getChannels().get(i);
            if (c.isSubChannel() && c.getParentChannelId().equalsIgnoreCase(rootParent.getId())) {
                existingCount++;
                if (i > lastIndex) {
                    lastIndex = i;
                }
            }
        }

        int nextTrackNum = existingCount + 2; // Track 1 is parent, Track 2 is first sub, etc.
        String actorName = !rootParent.getActorName().isBlank() ? rootParent.getActorName() : rootParent.getName();
        if (actorName.startsWith("Puppet: ")) {
            actorName = actorName.substring(8);
        }
        String subName = "↳ " + actorName + " (Track " + nextTrackNum + ")";

        MusicSequenceChannel newSub = new MusicSequenceChannel(
                UUID.randomUUID().toString(),
                MusicSequenceChannel.TYPE_PUPPET,
                subName,
                rootParent.getPuppetActor(),
                MusicSequenceChannel.COLOR_PUPPET_SUB
        );
        newSub.setParentChannelId(rootParent.getId());
        newSub.setActorName(rootParent.getActorName());
        newSub.setActorTag(rootParent.getActorTag());
        newSub.setActorEntityType(rootParent.getActorEntityType());
        newSub.setActorRegisteredOnly(rootParent.isActorRegisteredOnly());

        // Insert directly after the last sub-channel of this entity group (or after the parent)
        int insertIdx = Math.min(currentSequence.getChannels().size(), lastIndex + 1);
        currentSequence.getChannels().add(insertIdx, newSub);

        saveCurrentSequenceToWorkingMap();
        playButtonSound();
        this.init();
    }

    public void openAddChannelModal() {
        saveCurrentSequenceToWorkingMap();
        if (this.fileDropdown != null) this.fileDropdown.setOpen(false);
        if (this.trackDropdown != null) this.trackDropdown.setOpen(false);
        this.activeModal = ModalType.ADD_CHANNEL;
        this.modalAddChannelStep = AddChannelStep.SELECT_TYPE;
        this.modalSelectedType = MusicSequenceChannel.TYPE_COMMAND;
        this.puppetTargetMode = PuppetTargetMode.REGISTER_NEW;
        this.modalSelectedEntityType = "fractured_utils:void_herald";
        this.modalSelectedParentChannelId = "";
        this.modalTagError = false;
        this.modalUserCustomizedName = false;
        this.modalStatusMessage = null;
        updateModalWidgetsPosition();
    }

    private void sortEntries() {
        currentSequence.sortEntriesByTimestamp();
        saveCurrentSequenceToWorkingMap();
        this.init();
    }

    private void saveCurrentFile() {
        saveCurrentSequenceToWorkingMap();
        String json = getActiveWorkingMap().get(currentFileName);
        if (json == null) return;

        if (!isClientMode) {
            ModMessages.sendToServer(new C2SSaveMusicSequencePacket(currentFileName, json));
            savedServerSequenceFiles.put(currentFileName, json);
        } else {
            MusicSequenceManager.getInstance().saveSequenceFile(currentFileName, json);
            savedClientSequenceFiles.put(currentFileName, json);
        }

        saveFeedbackMessage = "✓ Saved '" + currentFileName + "' successfully!";
        saveFeedbackTime = System.currentTimeMillis();
    }

    private void startSequencePlayback(String fileName) {
        saveCurrentFile();
        if (!isClientMode) {
            ModMessages.sendToServer(new C2SStartMusicSequencePacket(fileName));
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSingleplayerServer() != null) {
                MusicSequenceManager.getInstance().startSequence(fileName, mc.getSingleplayerServer().getPlayerList().getPlayers());
            }
        }
        saveFeedbackMessage = "▶ Started sequence on server! (Close screen to watch in-game)";
        saveFeedbackTime = System.currentTimeMillis();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal("▶ Started music sequence '" + fileName + "' on server.")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    public long snapTimestamp(long rawMs, boolean bypassSnap) {
        if (bypassSnap) return Math.max(0L, rawMs);

        // 1. Check Beat Snap
        int bpm = currentSequence.getBpm();
        double beatMs = 60000.0 / (double) Math.max(20, bpm);
        long beatIndex = Math.round((double) rawMs / beatMs);
        long beatSnappedMs = (long) Math.round(beatIndex * beatMs);

        if (Math.abs(rawMs - beatSnappedMs) <= 35) {
            return Math.max(0L, beatSnappedMs);
        }

        // 2. Check 50ms Game Tick Snap (Minecraft game ticks = 50ms)
        long tickSnappedMs = Math.round((double) rawMs / 50.0) * 50L;
        return Math.max(0L, tickSnappedMs);
    }

    private long getEffectiveEndMs() {
        if (currentSequence == null) return 30000L;
        if (currentSequence.getEndMs() > 0) {
            return currentSequence.getEndMs();
        }
        MusicWaveformRenderer.TrackWaveformData waveData = MusicWaveformRenderer.getOrComputeTrueWaveform(currentSequence.getSongTrack());
        if (waveData != null && waveData.totalDurationMs > 0) {
            return waveData.totalDurationMs;
        }
        long maxEntry = 0L;
        for (MusicSequenceEntry entry : currentSequence.getEntries()) {
            if (entry.getTimestampMs() > maxEntry) {
                maxEntry = entry.getTimestampMs();
            }
        }
        return Math.max(30000L, maxEntry + 1000L);
    }

    private int getTimelineWidth() {
        return Math.max(100, getMainAreaWidth());
    }

    private int getTimelineTrackWidth() {
        return Math.max(80, getTimelineWidth() - getTrackHeaderWidth());
    }

    private void clampTimeScroll() {
        long maxDuration = getEffectiveEndMs();
        double timelineTrackWidth = getTimelineTrackWidth();
        double visibleMs = (timelineTrackWidth / pixelsPerSecond) * 1000.0;

        if (visibleMs >= maxDuration) {
            this.timeScrollMs = 0.0;
        } else {
            double marginMs = (60.0 / pixelsPerSecond) * 1000.0;
            double maxScroll = Math.max(0.0, maxDuration - visibleMs + marginMs);
            this.timeScrollMs = Math.max(0.0, Math.min(maxScroll, this.timeScrollMs));
        }
    }

    private void relocatePlaybackAudio(long offsetMs) {
        if (!isPreviewPlaying) return;
        this.lastPreviewTickTime = System.currentTimeMillis();

        String songTrack = currentSequence.getSongTrack();
        if (songTrack == null || songTrack.trim().isEmpty()) return;

        if (EventAudioClientController.getInstance().isPlaying()) {
            EventAudioClientController.getInstance().seekAudio(offsetMs);
        } else {
            EventAudioClientController.getInstance().playAudio(
                    songTrack,
                    net.dandare21.fracturedutils.sound.ModSoundSources.EVENT_MUSIC,
                    currentSequence.getVolume(),
                    currentSequence.getPitch(),
                    0,
                    offsetMs,
                    true,
                    net.dandare21.fracturedutils.network.packet.S2CPlayEventAudioPacket.PlaybackMode.FIRE_AND_FORGET,
                    currentSequence.isLooping(),
                    2000
            );
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (activeModal != ModalType.NONE) {
            double scale = getLayoutScale();
            double smX = scale < 1.0 ? mouseX / scale : mouseX;
            double smY = scale < 1.0 ? mouseY / scale : mouseY;
            if (modalAddChannelStep == AddChannelStep.PUPPET_CONFIG) {
                if (puppetTargetMode == PuppetTargetMode.REGISTER_NEW && modalEntityCatalogDropdown != null && modalEntityCatalogDropdown.isOpen()) {
                    if (modalEntityCatalogDropdown.mouseScrolled(smX, smY, delta)) return true;
                } else if (puppetTargetMode == PuppetTargetMode.EXISTING_ACTOR && modalExistingMobsDropdown != null && modalExistingMobsDropdown.isOpen()) {
                    if (modalExistingMobsDropdown.mouseScrolled(smX, smY, delta)) return true;
                } else if (puppetTargetMode == PuppetTargetMode.SUB_CHANNEL && modalParentPuppetDropdown != null && modalParentPuppetDropdown.isOpen()) {
                    if (modalParentPuppetDropdown.mouseScrolled(smX, smY, delta)) return true;
                }
            }
            return true;
        }

        if (fileDropdown != null && fileDropdown.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if (trackDropdown != null && trackDropdown.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if ((fileDropdown != null && fileDropdown.isOpen() && (fileDropdown.isMouseOverMenu(mouseX, mouseY) || fileDropdown.isMouseOverHeader(mouseX, mouseY))) ||
            (trackDropdown != null && trackDropdown.isOpen() && (trackDropdown.isMouseOverMenu(mouseX, mouseY) || trackDropdown.isMouseOverHeader(mouseX, mouseY)))) {
            return true;
        }

        int mainAreaW = getMainAreaWidth();

        // Priority Check: Right Inspector Panel Open Dropdowns or Command Suggestions Scrolling
        if (!isRightPanelCollapsed && rightPanel != null && (rightPanel.hasOpenDropdown() || rightPanel.hasCommandSuggestions())) {
            if (rightPanel.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }

        // 1. Right Inspector Panel
        if (!isRightPanelCollapsed && mouseX >= mainAreaW) {
            if (rightPanel != null && rightPanel.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        int topBarH = 32;
        int timelineTop = getTimelineTop();

        // 2. 3D Viewport Navigation Zoom / Flight Speed
        if (mouseY >= topBarH && mouseY < timelineTop) {
            if (SequencerViewportManager.getCurrentMode() == SequencerViewportManager.CameraMode.WORK_CAMERA) {
                if (hasControlDown()) {
                    double curFov = SequencerViewportManager.getWorkCameraFov();
                    curFov = Mth.clamp(curFov - delta * 2.0, 10.0, 130.0);
                    SequencerViewportManager.setWorkCamera(
                            SequencerViewportManager.getWorkCameraPos(),
                            SequencerViewportManager.getWorkCameraYaw(),
                            SequencerViewportManager.getWorkCameraPitch(),
                            SequencerViewportManager.getWorkCameraRoll(),
                            curFov
                    );
                    return true;
                } else {
                    double curSpeed = SequencerViewportManager.getSpeedMultiplier();
                    double factor = delta > 0 ? 1.2 : 0.8333;
                    SequencerViewportManager.setSpeedMultiplier(curSpeed * factor);
                    return true;
                }
            }
        }

        // 3. Timeline Area Scrolling & Zooming
        int timelineLeft = 0;
        int trackHeaderWidth = getTrackHeaderWidth();
        int trackHeaderLeft = timelineLeft;
        int timelineTrackLeft = timelineLeft + trackHeaderWidth;
        int timelineTrackWidth = getTimelineTrackWidth();

        // Vertical Track Scrolling over Left Track Headers
        if (mouseX >= trackHeaderLeft && mouseX < timelineTrackLeft) {
            channelScrollY = Math.max(0.0, channelScrollY - (delta * 24.0));
            return true;
        }

        if (mouseX >= timelineTrackLeft && mouseX <= timelineTrackLeft + timelineTrackWidth) {
            if (hasControlDown()) {
                // Modern DAW: Ctrl + Mouse Wheel = Mouse-Centered Zoom
                double oldPixelsPerSecond = this.pixelsPerSecond;
                double zoomFactor = delta > 0 ? 1.25 : 0.8;
                double newPixelsPerSecond = Math.max(10.0, Math.min(400.0, oldPixelsPerSecond * zoomFactor));

                double mouseTimeMs = timeScrollMs + ((mouseX - timelineTrackLeft) / oldPixelsPerSecond) * 1000.0;
                this.pixelsPerSecond = newPixelsPerSecond;
                this.timeScrollMs = Math.max(0.0, mouseTimeMs - ((mouseX - timelineTrackLeft) / newPixelsPerSecond) * 1000.0);
                clampTimeScroll();
                return true;
            } else if (hasShiftDown()) {
                // Shift + Wheel = vertical track scrolling
                channelScrollY = Math.max(0.0, channelScrollY - (delta * 24.0));
                return true;
            } else {
                // Horizontal Timeline Panning Scroll
                double scrollSpeed = 500.0 / (pixelsPerSecond / 50.0);
                timeScrollMs = Math.max(0.0, timeScrollMs - (delta * scrollSpeed));
                clampTimeScroll();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeModal != ModalType.NONE) {
            double scale = getLayoutScale();
            int modalMouseX = (int) (mouseX / scale);
            int modalMouseY = (int) (mouseY / scale);
            handleModalClick(modalMouseX, modalMouseY, button);
            return true;
        }

        if (fileDropdown != null && fileDropdown.isOpen() && fileDropdown.isMouseOverMenu(mouseX, mouseY)) {
            return fileDropdown.mouseClicked(mouseX, mouseY, button);
        }
        if (trackDropdown != null && trackDropdown.isOpen() && trackDropdown.isMouseOverMenu(mouseX, mouseY)) {
            return trackDropdown.mouseClicked(mouseX, mouseY, button);
        }

        // Priority Check: Right Inspector Panel Open Dropdowns or Command Suggestions
        if (!isRightPanelCollapsed && rightPanel != null && (rightPanel.hasOpenDropdown() || rightPanel.hasCommandSuggestions())) {
            if (rightPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        int mainAreaW = getMainAreaWidth();
        int topBarH = 32;
        int timelineTop = getTimelineTop();

        // 1. Check Click on Right Inspector Panel
        if (!isRightPanelCollapsed && mouseX >= mainAreaW) {
            if (rightPanel != null && rightPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        // Defocus inspector fields when clicking outside right inspector panel
        if (rightPanel != null) {
            rightPanel.unfocusAll();
        }

        // 2. Check Click on 3D Viewport (32 <= mouseY < timelineTop)
        if (mouseY >= topBarH && mouseY < timelineTop) {
            if (button == 1 || button == 2) {
                if (SequencerViewportManager.getCurrentMode() != SequencerViewportManager.CameraMode.WORK_CAMERA) {
                    SequencerViewportManager.setCurrentMode(SequencerViewportManager.CameraMode.WORK_CAMERA);
                    updatePreviewCamera();
                    this.init();
                }
                this.isViewportNavigating = true;
                this.lastViewportMouseX = mouseX;
                this.lastViewportMouseY = mouseY;
                return true;
            }
            return false;
        }

        // 3. Timeline Area Coordinates
        int timelineLeft = 0;
        int timelineWidth = mainAreaW;
        int trackHeaderWidth = getTrackHeaderWidth();
        int trackHeaderLeft = timelineLeft;
        int timelineTrackLeft = timelineLeft + trackHeaderWidth;
        int timelineTrackWidth = getTimelineTrackWidth();
        int rulerHeight = getRulerHeight();
        int waveformHeight = getWaveformHeight();
        int channelHeight = getChannelHeight();

        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        int channelCount = channels.size();
        int toolbarHeight = getTimelineToolbarHeight();
        int canvasY = timelineTop + toolbarHeight;
        int canvasBottom = this.height;
        int waveTopY = canvasY + rulerHeight;
        int trackAreaY = waveTopY + waveformHeight;

        // Check Click on Left Track Header Column
        if (mouseX >= trackHeaderLeft && mouseX < timelineTrackLeft) {
            int delL = trackHeaderWidth < 90 ? (timelineTrackLeft - 18) : (timelineTrackLeft - 22);
            int delR = trackHeaderWidth < 90 ? (timelineTrackLeft - 4) : (timelineTrackLeft - 6);
            int subL = trackHeaderWidth < 90 ? (timelineTrackLeft - 38) : (timelineTrackLeft - 50);
            int subR = trackHeaderWidth < 90 ? (timelineTrackLeft - 20) : (timelineTrackLeft - 25);

            // Check delete button [✕] and [+SUB] button for each channel row
            for (int c = 0; c < channels.size(); c++) {
                int trackY = trackAreaY + (c * channelHeight) - (int) channelScrollY;
                if (trackY >= trackAreaY && trackY + channelHeight <= canvasBottom) {
                    MusicSequenceChannel ch = channels.get(c);
                    // Delete button [✕]
                    if (mouseX >= delL && mouseX <= delR && mouseY >= trackY + 4 && mouseY <= trackY + channelHeight - 4) {
                        openConfirmDeleteChannelModal(c);
                        return true;
                    }
                    // [+SUB] button on puppet channels
                    if (MusicSequenceChannel.TYPE_PUPPET.equalsIgnoreCase(ch.getType())) {
                        if (mouseX >= subL && mouseX <= subR && mouseY >= trackY + 4 && mouseY <= trackY + channelHeight - 4) {
                            addSubChannel(ch);
                            return true;
                        }
                    }
                }
            }

            // Click on "+ ADD CHANNEL" button below all channels
            int addBtnY = trackAreaY + (channelCount * channelHeight) + 4 - (int) channelScrollY;
            if (addBtnY >= trackAreaY && addBtnY + 22 <= canvasBottom + 10) {
                if (mouseX >= trackHeaderLeft + 4 && mouseX <= timelineTrackLeft - 4 && mouseY >= addBtnY && mouseY <= addBtnY + 22) {
                    openAddChannelModal();
                    return true;
                }
            }
        }

        // Check Click on Right Timeline Tracks Canvas
        if (mouseX >= timelineTrackLeft && mouseX <= timelineLeft + timelineWidth) {
            // Middle Click (button 2) or Right Click on empty canvas -> Start Timeline Panning
            if (button == 2 || (button == 1 && mouseY >= canvasY && mouseY <= canvasBottom)) {
                boolean hitKeyframe = false;
                if (button == 1 && mouseY >= trackAreaY) {
                    List<MusicSequenceEntry> entries = currentSequence.getEntries();
                    for (int i = 0; i < entries.size(); i++) {
                        MusicSequenceEntry entry = entries.get(i);
                        int channelIndex = getChannelIndex(entry);
                        int entryTrackY = trackAreaY + (channelIndex * channelHeight) - (int) channelScrollY;
                        double entryX = timelineTrackLeft + ((entry.getTimestampMs() - timeScrollMs) / 1000.0) * pixelsPerSecond;
                        double totalW = (entry.getTotalDurationMs() / 1000.0) * pixelsPerSecond;
                        boolean hit = (totalW > 0)
                                ? (mouseX >= entryX - 4 && mouseX <= entryX + totalW + 4 && mouseY >= entryTrackY && mouseY <= entryTrackY + channelHeight)
                                : (Math.abs(mouseX - entryX) <= 8 && mouseY >= entryTrackY && mouseY <= entryTrackY + channelHeight);
                        if (hit) {
                            hitKeyframe = true;
                            break;
                        }
                    }
                }
                if (!hitKeyframe) {
                    this.isPanningTimeline = true;
                    this.lastPanMouseX = mouseX;
                    return true;
                }
            }

            // Direct Click on Playhead handle or vertical scrubber line
            double playheadX = timelineTrackLeft + ((playheadMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
            if (Math.abs(mouseX - playheadX) <= 8 && mouseY >= canvasY && mouseY <= canvasBottom) {
                this.isDraggingPlayhead = true;
                return true;
            }

            // Click on Ruler -> START Marker, END Marker, or Seek Playhead
            if (mouseY >= canvasY && mouseY <= canvasY + rulerHeight) {
                double startX = timelineTrackLeft + ((currentSequence.getStartMs() - timeScrollMs) / 1000.0) * pixelsPerSecond;
                double endX = timelineTrackLeft + ((getEffectiveEndMs() - timeScrollMs) / 1000.0) * pixelsPerSecond;

                if (Math.abs(mouseX - startX) <= 14) {
                    this.isDraggingStartMarker = true;
                    return true;
                }
                if (Math.abs(mouseX - endX) <= 14) {
                    this.isDraggingEndMarker = true;
                    return true;
                }

                double relX = mouseX - timelineTrackLeft;
                double clickedTimeMs = timeScrollMs + (relX / pixelsPerSecond) * 1000.0;
                this.playheadMs = Math.max(0.0, Math.min(getEffectiveEndMs(), clickedTimeMs));
                this.isDraggingPlayhead = true;
                relocatePlaybackAudio((long) this.playheadMs);
                return true;
            }

            // Click on Audio Waveform Channel -> Seek and Drag Playhead
            if (mouseY >= waveTopY && mouseY < trackAreaY) {
                double relX = mouseX - timelineTrackLeft;
                double clickedTimeMs = timeScrollMs + (relX / pixelsPerSecond) * 1000.0;
                this.playheadMs = Math.max(0.0, Math.min(getEffectiveEndMs(), clickedTimeMs));
                this.isDraggingPlayhead = true;
                relocatePlaybackAudio((long) this.playheadMs);
                return true;
            }

            // Check Click on Keyframe Nodes, Duration Bars, or Track Canvas
            if (mouseY >= trackAreaY && mouseY <= canvasBottom) {
                double relX = mouseX - timelineTrackLeft;
                long clickedMs = snapTimestamp((long) Math.max(0.0, timeScrollMs + (relX / pixelsPerSecond) * 1000.0), hasAltDown());

                // Check keyframe node and duration bar hits
                List<MusicSequenceEntry> entries = currentSequence.getEntries();
                for (int i = 0; i < entries.size(); i++) {
                    MusicSequenceEntry entry = entries.get(i);
                    int channelIndex = getChannelIndex(entry);
                    int entryTrackY = trackAreaY + (channelIndex * channelHeight) - (int) channelScrollY;
                    double entryX = timelineTrackLeft + ((entry.getTimestampMs() - timeScrollMs) / 1000.0) * pixelsPerSecond;
                    double totalW = (entry.getTotalDurationMs() / 1000.0) * pixelsPerSecond;

                    boolean isPuppet = "PUPPET".equalsIgnoreCase(entry.getActionType()) || (channelIndex < channels.size() && "PUPPET".equalsIgnoreCase(channels.get(channelIndex).getType()));
                    boolean isCamera = "CAMERA".equalsIgnoreCase(entry.getActionType()) || (channelIndex < channels.size() && "CAMERA".equalsIgnoreCase(channels.get(channelIndex).getType()));
                    boolean isScreenEffect = "SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) || "SCREEN_EFFECTS".equalsIgnoreCase(entry.getActionType()) || (channelIndex < channels.size() && MusicSequenceChannel.TYPE_SCREEN_EFFECT.equalsIgnoreCase(channels.get(channelIndex).getType()));
                    String pSub = entry.getSubAction() != null ? entry.getSubAction().toUpperCase(Locale.ROOT) : "";
                    boolean isPuppetNonDuration = isPuppet && ("DESPAWN".equals(pSub) || "SPAWN".equals(pSub) || "TOGGLE_AI".equals(pSub));

                    if (isScreenEffect && entry.getTotalDurationMs() <= 0) {
                        entry.setDurationMs(1000);
                        updateScreenEffectEntryCommand(entry);
                        totalW = (entry.getTotalDurationMs() / 1000.0) * pixelsPerSecond;
                    }

                    boolean hasDurationBar = (!isCamera && !isPuppetNonDuration && totalW > 0);

                    // Check clicking on right edge stretch/resize handle (all duration actions EXCEPT puppet and camera)
                    if (button == 0 && !isPuppet && !isCamera && hasDurationBar) {
                        double rightEdgeX = entryX + totalW;
                        if (Math.abs(mouseX - rightEdgeX) <= 6 && mouseY >= entryTrackY && mouseY <= entryTrackY + channelHeight) {
                            this.isStretchingDuration = true;
                            this.stretchingEntry = entry;
                            this.stretchingEntryIndex = i;
                            this.selectedEntryIndex = i;
                            if (this.rightPanel != null) {
                                this.rightPanel.updateSelection(i, entry);
                            }
                            return true;
                        }
                    }

                    // Check clicking on puppet action phase dividers to adjust individual phase durations ("time zones")
                    if (button == 0 && isPuppet && hasDurationBar) {
                        PuppetActionType<?> actType = ModPuppetActions.resolve(entry.getCommand());
                        List<ActionTimingPhase> phases = actType.getTimingPhases();
                        long cumMs = entry.getTimestampMs();
                        for (int p = 0; p < phases.size(); p++) {
                            ActionTimingPhase phase = phases.get(p);
                            int pMs = entry.getPhaseMs(phase, p);
                            cumMs += pMs;
                            double boundaryX = timelineTrackLeft + ((cumMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
                            if (Math.abs(mouseX - boundaryX) <= 6 && mouseY >= entryTrackY && mouseY <= entryTrackY + channelHeight) {
                                this.isDraggingPuppetPhase = true;
                                this.puppetDraggingEntry = entry;
                                this.puppetDraggingEntryIndex = i;
                                this.puppetDraggingPhaseIndex = p;
                                this.puppetOriginalPhaseMs = pMs;
                                this.puppetOriginalNextPhaseMs = (p < phases.size() - 1) ? entry.getPhaseMs(phases.get(p + 1), p + 1) : 0;
                                this.puppetPhaseDragStartMs = cumMs;
                                this.selectedEntryIndex = i;
                                if (this.rightPanel != null) {
                                    this.rightPanel.updateSelection(i, entry);
                                }
                                playButtonSound();
                                return true;
                            }
                        }
                    }

                    int hitBoxW = 16;
                    if (!hasDurationBar) {
                        String bTxt = "";
                        if (isPuppet) {
                            if ("DESPAWN".equals(pSub)) bTxt = "✕ DESPAWN";
                            else if ("SPAWN".equals(pSub)) bTxt = "★ SPAWN";
                            else if ("TOGGLE_AI".equals(pSub)) bTxt = "⚡ TOGGLE AI";
                        } else if (entry.getDescription() != null && !entry.getDescription().isBlank()) {
                            bTxt = entry.getDescription();
                        }
                        if (!bTxt.isEmpty()) {
                            hitBoxW = this.font.width(bTxt) + 16;
                        }
                    }

                    boolean hit = hasDurationBar
                            ? (mouseX >= entryX - 4 && mouseX <= entryX + totalW + 4 && mouseY >= entryTrackY && mouseY <= entryTrackY + channelHeight)
                            : ((mouseX >= entryX - 8 && mouseX <= entryX + Math.max(16, hitBoxW)) && mouseY >= entryTrackY && mouseY <= entryTrackY + channelHeight);

                    if (hit) {
                        this.selectedEntryIndex = i;
                        if (this.rightPanel != null) {
                            this.rightPanel.updateSelection(i, entry);
                        }
                        if (hasShiftDown() && (button == 0 || button == 1)) {
                            deleteEntry(i);
                            this.selectedEntryIndex = -1;
                            if (this.rightPanel != null) {
                                this.rightPanel.updateSelection(-1, null);
                            }
                            return true;
                        }
                        if (button == 0) {
                            if (hasAltDown()) {
                                // Alt + Drag: Duplicate entry and drag the clone!
                                MusicSequenceEntry copy = entry.copy();
                                currentSequence.getEntries().add(copy);
                                this.draggedEntry = copy;
                                this.draggedEntryIndex = currentSequence.getEntries().indexOf(copy);
                                this.selectedEntryIndex = this.draggedEntryIndex;
                                if (this.rightPanel != null) {
                                    this.rightPanel.updateSelection(this.selectedEntryIndex, copy);
                                }
                                this.lastDragMouseX = mouseX;
                                playButtonSound();
                                return true;
                            } else {
                                this.draggedEntry = entry;
                                this.draggedEntryIndex = i;
                                this.lastDragMouseX = mouseX;
                                return true;
                            }
                        } else if (button == 1) {
                            openEditEntryModal(i, entry.getTimestampMs());
                            return true;
                        }
                    }
                }

                // Click on empty space in action channel -> Create new keyframe
                if (button == 0) {
                    int clickedChannelIndex = (int) ((mouseY - trackAreaY + channelScrollY) / channelHeight);
                    if (clickedChannelIndex >= 0 && clickedChannelIndex < channels.size()) {
                        MusicSequenceChannel channel = channels.get(clickedChannelIndex);
                        MusicSequenceEntry newEntry = new MusicSequenceEntry(clickedMs, channel.getType(), channel.getId(), "", "");
                        if (channel.getType().equalsIgnoreCase("PUPPET")) {
                            boolean hasSpawn = false;
                            for (MusicSequenceEntry e : currentSequence.getEntries()) {
                                if (channel.getId().equalsIgnoreCase(e.getChannelId()) && "SPAWN".equalsIgnoreCase(e.getSubAction())) {
                                    hasSpawn = true;
                                    break;
                                }
                            }
                            if (!hasSpawn && !channel.isSubChannel()) {
                                newEntry.setSubAction("SPAWN");
                                String aTag = channel.getActorTag().isBlank() ? channel.getName().toLowerCase(Locale.ROOT).replace(" ", "_") : channel.getActorTag();
                                String eType = channel.getEntityTypeId().isBlank() ? "fractured_utils:void_herald" : channel.getEntityTypeId();
                                String aName = channel.getActorName().isBlank() ? channel.getName() : channel.getActorName();
                                String px = "~", py = "~", pz = "~";
                                float pYaw = 0.0f;
                                float pPitch = 0.0f;
                                if (minecraft != null && minecraft.player != null) {
                                    px = String.format(Locale.ROOT, "%.2f", minecraft.player.getX());
                                    py = String.format(Locale.ROOT, "%.2f", minecraft.player.getY());
                                    pz = String.format(Locale.ROOT, "%.2f", minecraft.player.getZ());
                                    pYaw = minecraft.player.getYRot();
                                    pPitch = 0.0f;
                                }
                                newEntry.setShowNametag(false);
                                newEntry.setSpawnYaw(pYaw);
                                newEntry.setSpawnPitch(pPitch);
                                newEntry.putPuppetParam("showNametag", "false");
                                newEntry.putPuppetParam("spawnYaw", String.format(Locale.ROOT, "%.2f", pYaw));
                                newEntry.putPuppetParam("spawnPitch", String.format(Locale.ROOT, "%.2f", pPitch));
                                newEntry.setCommand(String.format(Locale.ROOT, "summon %s %s %s %s {Tags:[\"%s\",\"puppet_actor\"],CustomName:'{\"text\":\"%s\"}',CustomNameVisible:0b,NoAI:0b,PersistenceRequired:1b,Rotation:[%.2ff,%.2ff]}",
                                        eType, px, py, pz, aTag, aName, pYaw, pPitch));
                                newEntry.setDescription("Spawn " + aName + " @ " + px + " " + py + " " + pz);
                            } else {
                                String aTag = channel.getActorTag().isBlank() ? channel.getName().toLowerCase(Locale.ROOT).replace(" ", "_") : channel.getActorTag();
                                newEntry.setSubAction("EXECUTE_ACTION");
                                PuppetActionType<?> defAct = ModPuppetActions.LEAP_SLAM;
                                List<ActionTimingPhase> phases = defAct.getTimingPhases();
                                int wMs = phases.size() > 0 ? phases.get(0).defaultMs() : 500;
                                int jMs = phases.size() > 1 ? phases.get(1).defaultMs() : 1500;
                                int dMs = phases.size() > 2 ? phases.get(2).defaultMs() : 800;
                                int rMs = phases.size() > 3 ? phases.get(3).defaultMs() : 600;
                                newEntry.setWindupMs(wMs);
                                newEntry.setJumpMs(jMs);
                                newEntry.setDurationMs(dMs);
                                newEntry.setRecoveryMs(rMs);
                                newEntry.setCommand(String.format(Locale.ROOT, "puppet_action tag:%s action:%s target:@p windup:%d jump:%d duration:%d recovery:%d",
                                        aTag, defAct.getId(), wMs, jMs, dMs, rMs));
                                newEntry.setDescription("Leap Slam");
                            }
                        } else if (channel.getType().equalsIgnoreCase("DIALOG")) {
                            DialogLine line = new DialogLine();
                            line.setSpeaker("Narrator");
                            line.setText("Dialog text here...");
                            line.setCharSpeedTicks(1);
                            line.setDelayTicks(40);
                            line.setWaitForInput(false);
                            line.setUseCamera(false);
                            newEntry.setDialog(line);
                            int visibleChars = DialogFormatUtil.getVisibleCharCount(line.getText());
                            int animTicks = line.getCharSpeedTicks() * visibleChars;
                            int totalTicks = animTicks + line.getDelayTicks();
                            newEntry.setDurationMs(totalTicks * 50);
                            newEntry.setCommand(line.getText());
                            newEntry.setSubAction("SINGLE_DIALOG");
                        } else if (channel.getType().equalsIgnoreCase("CAMERA")) {
                            newEntry.setActionType(MusicSequenceChannel.TYPE_CAMERA);
                            newEntry.setDurationMs(0);
                            newEntry.setUseCamera(true);

                            // Find the previous camera keyframe before clickedMs
                            MusicSequenceEntry prevCamEntry = null;
                            for (MusicSequenceEntry e : currentSequence.getEntries()) {
                                boolean isCam = MusicSequenceChannel.TYPE_CAMERA.equalsIgnoreCase(e.getActionType())
                                        || (channel.getId().equalsIgnoreCase(e.getChannelId()))
                                        || e.isUseCamera();
                                if (isCam && e.getTimestampMs() <= clickedMs) {
                                    if (prevCamEntry == null || e.getTimestampMs() >= prevCamEntry.getTimestampMs()) {
                                        prevCamEntry = e;
                                    }
                                }
                            }
                            if (prevCamEntry == null) {
                                long minDiff = Long.MAX_VALUE;
                                for (MusicSequenceEntry e : currentSequence.getEntries()) {
                                    boolean isCam = MusicSequenceChannel.TYPE_CAMERA.equalsIgnoreCase(e.getActionType())
                                            || (channel.getId().equalsIgnoreCase(e.getChannelId()))
                                            || e.isUseCamera();
                                    if (isCam) {
                                        long diff = Math.abs(e.getTimestampMs() - clickedMs);
                                        if (diff < minDiff) {
                                            minDiff = diff;
                                            prevCamEntry = e;
                                        }
                                    }
                                }
                            }

                            boolean isWorkCam = (SequencerViewportManager.getCurrentMode() == SequencerViewportManager.CameraMode.WORK_CAMERA);
                            Vec3 defaultCamPos = isWorkCam ? SequencerViewportManager.getWorkCameraPos() : (minecraft != null && minecraft.player != null ? minecraft.player.getEyePosition() : Vec3.ZERO);
                            float defaultCamYaw = isWorkCam ? SequencerViewportManager.getWorkCameraYaw() : (minecraft != null && minecraft.player != null ? minecraft.player.getYRot() : 0.0f);
                            float defaultCamPitch = isWorkCam ? SequencerViewportManager.getWorkCameraPitch() : (minecraft != null && minecraft.player != null ? minecraft.player.getXRot() : 0.0f);
                            float defaultCamRoll = isWorkCam ? SequencerViewportManager.getWorkCameraRoll() : 0.0f;
                            double defaultCamFov = isWorkCam ? SequencerViewportManager.getWorkCameraFov() : 70.0;

                            if (isWorkCam) {
                                newEntry.setCameraX(defaultCamPos.x);
                                newEntry.setCameraY(defaultCamPos.y);
                                newEntry.setCameraZ(defaultCamPos.z);
                                newEntry.setCameraYaw(defaultCamYaw);
                                newEntry.setCameraPitch(defaultCamPitch);
                                newEntry.setCameraRoll(defaultCamRoll);
                                newEntry.setCameraFov(defaultCamFov);
                            } else if (prevCamEntry != null) {
                                newEntry.setCameraX(prevCamEntry.getCameraX());
                                newEntry.setCameraY(prevCamEntry.getCameraY());
                                newEntry.setCameraZ(prevCamEntry.getCameraZ());
                                newEntry.setCameraYaw(prevCamEntry.getCameraYaw());
                                newEntry.setCameraPitch(prevCamEntry.getCameraPitch());
                                newEntry.setCameraRoll(prevCamEntry.getCameraRoll());
                                newEntry.setCameraFov(prevCamEntry.getCameraFov() > 0 ? prevCamEntry.getCameraFov() : 70.0);
                            } else {
                                newEntry.setCameraX(defaultCamPos.x);
                                newEntry.setCameraY(defaultCamPos.y);
                                newEntry.setCameraZ(defaultCamPos.z);
                                newEntry.setCameraYaw(defaultCamYaw);
                                newEntry.setCameraPitch(defaultCamPitch);
                                newEntry.setCameraRoll(defaultCamRoll);
                                newEntry.setCameraFov(defaultCamFov);
                            }

                            if (prevCamEntry != null) {
                                newEntry.setCameraMode(prevCamEntry.getCameraMode() != null ? prevCamEntry.getCameraMode() : "KEYFRAME");
                                newEntry.setSplineMode(prevCamEntry.getSplineMode() != null ? prevCamEntry.getSplineMode() : "CATMULL_ROM");
                                newEntry.setCameraEasing(prevCamEntry.getCameraEasing() != null ? prevCamEntry.getCameraEasing() : "EASE_IN_OUT_CUBIC");
                                newEntry.setCameraInterpolate(prevCamEntry.isCameraInterpolate());
                                newEntry.setCameraTarget(prevCamEntry.getCameraTarget());
                                newEntry.setCameraHeightOffset(prevCamEntry.getCameraHeightOffset());
                                newEntry.setCameraBackDistance(prevCamEntry.getCameraBackDistance());
                                newEntry.setCameraShoulderOffset(prevCamEntry.getCameraShoulderOffset());
                                newEntry.setInHandleX(prevCamEntry.getInHandleX());
                                newEntry.setInHandleY(prevCamEntry.getInHandleY());
                                newEntry.setInHandleZ(prevCamEntry.getInHandleZ());
                                newEntry.setOutHandleX(prevCamEntry.getOutHandleX());
                                newEntry.setOutHandleY(prevCamEntry.getOutHandleY());
                                newEntry.setOutHandleZ(prevCamEntry.getOutHandleZ());
                                newEntry.setLookAtEnabled(prevCamEntry.isLookAtEnabled());
                                newEntry.setLookAtMode(prevCamEntry.getLookAtMode());
                                newEntry.setLookAtX(prevCamEntry.getLookAtX());
                                newEntry.setLookAtY(prevCamEntry.getLookAtY());
                                newEntry.setLookAtZ(prevCamEntry.getLookAtZ());
                                newEntry.setLookAtTarget(prevCamEntry.getLookAtTarget());
                                newEntry.setLookAtWeight(prevCamEntry.getLookAtWeight());
                            } else {
                                newEntry.setCameraMode("KEYFRAME");
                                newEntry.setSplineMode("CATMULL_ROM");
                                newEntry.setCameraEasing("EASE_IN_OUT_CUBIC");
                                newEntry.setCameraInterpolate(true);
                            }

                            newEntry.setSubAction(newEntry.getCameraMode() != null ? newEntry.getCameraMode() : "KEYFRAME");
                            newEntry.setDescription(String.format(Locale.US, "Camera (%.1f, %.1f, %.1f)",
                                    newEntry.getCameraX(), newEntry.getCameraY(), newEntry.getCameraZ()));
                            newEntry.setCommand(String.format(Locale.US, "camera keyframe %.1f %.1f %.1f %.0f %.0f 0 70",
                                    newEntry.getCameraX(), newEntry.getCameraY(), newEntry.getCameraZ(),
                                    newEntry.getCameraYaw(), newEntry.getCameraPitch()));
                        } else if (channel.getType().equalsIgnoreCase(MusicSequenceChannel.TYPE_SCREEN_EFFECT) || channel.getType().equalsIgnoreCase("SCREEN_EFFECT") || channel.getType().equalsIgnoreCase("SCREEN_EFFECTS")) {
                            newEntry.setActionType(MusicSequenceChannel.TYPE_SCREEN_EFFECT);
                            newEntry.setSubAction("SCREEN_SHAKE");
                            newEntry.setScreenEffectId("fractured_utils:screen_shake");
                            newEntry.setDurationMs(1000);
                            newEntry.setScreenEffectIntensity(2.0f);
                            newEntry.setScreenEffectFrequency(20.0f);
                            newEntry.setScreenEffectDecay(true);
                            updateScreenEffectEntryCommand(newEntry);
                        }
                        currentSequence.getEntries().add(newEntry);
                        currentSequence.sortEntriesByTimestamp();
                        saveCurrentSequenceToWorkingMap();
                        int newIndex = currentSequence.getEntries().indexOf(newEntry);
                        setSelectedEntryIndex(newIndex);
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeModal != ModalType.NONE) {
            return true;
        }

        int mainAreaW = getMainAreaWidth();

        // 1. Right Inspector Panel Dragging
        if (!isRightPanelCollapsed && mouseX >= mainAreaW) {
            if (rightPanel != null && rightPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
            return true;
        }

        // 2. Viewport Navigation Dragging
        if (isViewportNavigating) {
            double deltaX = mouseX - lastViewportMouseX;
            double deltaY = mouseY - lastViewportMouseY;
            lastViewportMouseX = mouseX;
            lastViewportMouseY = mouseY;

            if (button == 1) { // Right-Click drag -> look around
                float sens = 0.15f;
                SequencerViewportManager.adjustWorkCameraAngles((float) (deltaX * sens), (float) (deltaY * sens));
                return true;
            } else if (button == 2) { // Middle-Click drag -> pan camera
                SequencerViewportManager.panWorkCamera(deltaX, deltaY);
                return true;
            }
        }

        // 3. Timeline Dragging
        int timelineTrackLeft = getTrackHeaderWidth();

        if (isDraggingPlayhead) {
            double relX = mouseX - timelineTrackLeft;
            double rawMs = timeScrollMs + (relX / pixelsPerSecond) * 1000.0;
            long endMs = getEffectiveEndMs();
            this.playheadMs = Math.max(0.0, Math.min(endMs, rawMs));

            long now = System.currentTimeMillis();
            if (isPreviewPlaying && now - lastDragAudioSeekTime >= 50L) {
                lastDragAudioSeekTime = now;
                relocatePlaybackAudio((long) this.playheadMs);
            }
            return true;
        }

        if (isPanningTimeline) {
            double deltaX = mouseX - lastPanMouseX;
            timeScrollMs = Math.max(0.0, timeScrollMs - (deltaX / pixelsPerSecond) * 1000.0);
            clampTimeScroll();
            lastPanMouseX = mouseX;
            return true;
        }

        if (isDraggingStartMarker) {
            double relX = mouseX - timelineTrackLeft;
            long rawMs = (long) Math.max(0.0, timeScrollMs + (relX / pixelsPerSecond) * 1000.0);
            long snappedMs = snapTimestamp(rawMs, hasAltDown());
            long maxAllowed = getEffectiveEndMs() - 50L;
            long clampedStart = Math.max(0L, Math.min(maxAllowed, snappedMs));
            currentSequence.setStartMs(clampedStart);
            saveCurrentSequenceToWorkingMap();
            return true;
        }

        if (isDraggingEndMarker) {
            double relX = mouseX - timelineTrackLeft;
            long rawMs = (long) Math.max(0.0, timeScrollMs + (relX / pixelsPerSecond) * 1000.0);
            long snappedMs = snapTimestamp(rawMs, hasAltDown());
            long minAllowed = currentSequence.getStartMs() + 50L;
            long clampedEnd = Math.max(minAllowed, snappedMs);
            currentSequence.setEndMs(clampedEnd);
            saveCurrentSequenceToWorkingMap();
            return true;
        }

        if (isDraggingPuppetPhase && puppetDraggingEntry != null && puppetDraggingPhaseIndex >= 0) {
            double relX = mouseX - timelineTrackLeft;
            long currentMouseMs = (long) Math.max(0.0, timeScrollMs + (relX / pixelsPerSecond) * 1000.0);
            long snappedMouseMs = snapTimestamp(currentMouseMs, hasAltDown());

            PuppetActionType<?> actType = ModPuppetActions.resolve(puppetDraggingEntry.getCommand());
            List<ActionTimingPhase> phases = actType.getTimingPhases();
            if (puppetDraggingPhaseIndex < phases.size()) {
                ActionTimingPhase currentPhase = phases.get(puppetDraggingPhaseIndex);

                // Calculate start timestamp of current phase
                long startOfCurrentPhaseMs = puppetDraggingEntry.getTimestampMs();
                for (int p = 0; p < puppetDraggingPhaseIndex; p++) {
                    startOfCurrentPhaseMs += puppetDraggingEntry.getPhaseMs(phases.get(p), p);
                }

                if (hasShiftDown() && puppetDraggingPhaseIndex < phases.size() - 1) {
                    // Shift + Drag: Split / Dual-roller edit (adjusts current and next phase inversely, keeping total fixed)
                    ActionTimingPhase nextPhase = phases.get(puppetDraggingPhaseIndex + 1);
                    long combinedMs = (long) puppetOriginalPhaseMs + puppetOriginalNextPhaseMs;
                    long newPhaseMs = Math.max(0L, Math.min(combinedMs, snappedMouseMs - startOfCurrentPhaseMs));
                    long newNextMs = combinedMs - newPhaseMs;

                    puppetDraggingEntry.setPhaseMs(currentPhase, puppetDraggingPhaseIndex, (int) newPhaseMs);
                    puppetDraggingEntry.setPhaseMs(nextPhase, puppetDraggingPhaseIndex + 1, (int) newNextMs);
                } else {
                    // Normal Drag: Ripple edit (adjusts current phase duration, shifting subsequent phases)
                    long newPhaseMs = Math.max(0L, snappedMouseMs - startOfCurrentPhaseMs);
                    puppetDraggingEntry.setPhaseMs(currentPhase, puppetDraggingPhaseIndex, (int) newPhaseMs);
                }

                updatePuppetEntryCommand(puppetDraggingEntry);

                if (this.rightPanel != null && selectedEntryIndex >= 0) {
                    this.rightPanel.updateSelection(selectedEntryIndex, puppetDraggingEntry);
                }
            }
            return true;
        }

        if (isStretchingDuration && stretchingEntry != null) {
            double relX = mouseX - timelineTrackLeft;
            long currentMouseMs = (long) Math.max(0.0, timeScrollMs + (relX / pixelsPerSecond) * 1000.0);
            long snappedMouseMs = snapTimestamp(currentMouseMs, hasAltDown());
            long newDurationMs = Math.max(50L, snappedMouseMs - stretchingEntry.getTimestampMs());

            if ("DIALOG".equalsIgnoreCase(stretchingEntry.getActionType()) && stretchingEntry.getDialog() != null) {
                int visibleChars = DialogFormatUtil.getVisibleCharCount(stretchingEntry.getDialog().getText());
                int animTicks = stretchingEntry.getDialog().getCharSpeedTicks() * visibleChars;
                int newTotalTicks = (int) (newDurationMs / 50);
                int newDelayTicks = Math.max(0, newTotalTicks - animTicks);
                stretchingEntry.getDialog().setDelayTicks(newDelayTicks);
                stretchingEntry.setDurationMs((int) newDurationMs);
            } else if ("SCREEN_EFFECT".equalsIgnoreCase(stretchingEntry.getActionType()) || "SCREEN_EFFECTS".equalsIgnoreCase(stretchingEntry.getActionType()) || isChannelType(stretchingEntry, MusicSequenceChannel.TYPE_SCREEN_EFFECT)) {
                stretchingEntry.setDurationMs((int) newDurationMs);
                updateScreenEffectEntryCommand(stretchingEntry);
            } else {
                stretchingEntry.setDurationMs((int) newDurationMs);
            }
            if (this.rightPanel != null && selectedEntryIndex >= 0) {
                this.rightPanel.updateSelection(selectedEntryIndex, stretchingEntry);
            }
            return true;
        }

        if (draggedEntry != null) {
            double relX = mouseX - timelineTrackLeft;
            long rawTs = (long) Math.max(0.0, timeScrollMs + (relX / pixelsPerSecond) * 1000.0);
            long snappedTs = snapTimestamp(rawTs, hasAltDown());
            draggedEntry.setTimestampMs(snappedTs);
            if (this.rightPanel != null && selectedEntryIndex >= 0) {
                this.rightPanel.updateSelection(selectedEntryIndex, draggedEntry);
            }
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeModal != ModalType.NONE) {
            return true;
        }

        if (!isRightPanelCollapsed && this.rightPanel != null) {
            this.rightPanel.mouseReleased(mouseX, mouseY, button);
        }

        if (button == 1 || button == 2) {
            this.isViewportNavigating = false;
        }

        isPanningTimeline = false;
        isDraggingStartMarker = false;
        isDraggingEndMarker = false;
        if (isDraggingPuppetPhase) {
            isDraggingPuppetPhase = false;
            puppetDraggingEntry = null;
            puppetDraggingEntryIndex = -1;
            puppetDraggingPhaseIndex = -1;
            saveCurrentSequenceToWorkingMap();
            if (this.rightPanel != null && selectedEntryIndex >= 0) {
                this.rightPanel.updateSelection(selectedEntryIndex, getSelectedEntry());
            }
        }
        if (isStretchingDuration) {
            isStretchingDuration = false;
            stretchingEntry = null;
            stretchingEntryIndex = -1;
            saveCurrentSequenceToWorkingMap();
            if (this.rightPanel != null && selectedEntryIndex >= 0) {
                this.rightPanel.updateSelection(selectedEntryIndex, getSelectedEntry());
            }
        }
        if (draggedEntry != null) {
            currentSequence.sortEntriesByTimestamp();
            if (selectedEntryIndex >= 0) {
                selectedEntryIndex = currentSequence.getEntries().indexOf(draggedEntry);
            }
            saveCurrentSequenceToWorkingMap();
            updatePreviewCamera();
            if (this.rightPanel != null && selectedEntryIndex >= 0) {
                this.rightPanel.updateSelection(selectedEntryIndex, getSelectedEntry());
            }
            draggedEntry = null;
            draggedEntryIndex = -1;
        }
        if (isDraggingPlayhead) {
            isDraggingPlayhead = false;
            if (isPreviewPlaying) {
                relocatePlaybackAudio((long) this.playheadMs);
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeModal != ModalType.NONE) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                handleModalCancel();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                handleModalConfirm();
                return true;
            }
            if (activeModal == ModalType.ADD_CHANNEL) {
                if (modalChannelNameBox != null && modalChannelNameBox.isFocused()) {
                    return modalChannelNameBox.keyPressed(keyCode, scanCode, modifiers);
                }
                if (modalActorNameBox != null && modalActorNameBox.isFocused()) {
                    return modalActorNameBox.keyPressed(keyCode, scanCode, modifiers);
                }
                if (modalActorTagBox != null && modalActorTagBox.isFocused()) {
                    return modalActorTagBox.keyPressed(keyCode, scanCode, modifiers);
                }
            }
            return true;
        }

        // If right inspector panel has an edit box focused, forward keystrokes there!
        if (!isRightPanelCollapsed && this.rightPanel != null && this.rightPanel.isAnyFieldFocused()) {
            if (this.rightPanel.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                this.rightPanel.unfocusAll();
                return true;
            }
            // Consume all other keys while an inspector edit box is focused to prevent screen hotkeys
            // (Space, P, G, Backspace/Delete, Home, End, C, K, Tab, etc.) from triggering!
            return true;
        }

        if (bpmEditBox != null && bpmEditBox.isFocused()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                bpmEditBox.setFocused(false);
                return true;
            }
            if (bpmEditBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        // Tab: Toggle Work Camera vs Sequence Camera
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            toggleCameraMode();
            return true;
        }

        // P: Toggle Right Inspector Panel
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_P) {
            toggleRightPanel();
            return true;
        }

        // G: Toggle Viewport Composition Guides
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_G) {
            this.showCompositionGuides = !this.showCompositionGuides;
            this.init();
            return true;
        }

        // X: Toggle Screen Effects Live Preview
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_X) {
            this.isPreviewScreenEffects = !this.isPreviewScreenEffects;
            if (!this.isPreviewScreenEffects) {
                ClientScreenEffectHandler.clearAllEffects();
            } else {
                updatePreviewScreenEffects();
            }
            this.init();
            return true;
        }

        // Ctrl + S: Quick Save
        if (hasControlDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
            saveCurrentFile();
            return true;
        }

        // Ctrl + D: Duplicate selected entry
        if (hasControlDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_D) {
            duplicateSelectedEntry();
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            togglePreviewPlayback();
            return true;
        }

        // C or K: Stamp Camera Keyframe at Playhead
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_K) {
            stampCameraKeyframeAtPlayhead();
            return true;
        }

        // R: Reset Work Camera Dutch Roll to level 0°
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R && SequencerViewportManager.getCurrentMode() == SequencerViewportManager.CameraMode.WORK_CAMERA) {
            SequencerViewportManager.resetWorkCameraRoll();
            playButtonSound();
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET) {
            jumpToPreviousCameraKeyframe();
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET) {
            jumpToNextCameraKeyframe();
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            deleteSelectedEntry();
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME) {
            this.playheadMs = 0.0;
            this.timeScrollMs = 0.0;
            relocatePlaybackAudio(0L);
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_END) {
            long maxTs = getEffectiveEndMs();
            this.playheadMs = maxTs;
            int timelineTrackWidth = getTimelineTrackWidth();
            this.timeScrollMs = Math.max(0.0, maxTs - (timelineTrackWidth * 0.5 / pixelsPerSecond) * 1000.0);
            clampTimeScroll();
            relocatePlaybackAudio(maxTs);
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            double stepMs = hasShiftDown() ? (60000.0 / currentSequence.getBpm()) : 50.0;
            this.playheadMs = Math.max(0.0, playheadMs - stepMs);
            relocatePlaybackAudio((long) this.playheadMs);
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            double stepMs = hasShiftDown() ? (60000.0 / currentSequence.getBpm()) : 50.0;
            this.playheadMs = Math.min(getEffectiveEndMs(), playheadMs + stepMs);
            relocatePlaybackAudio((long) this.playheadMs);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activeModal == ModalType.ADD_CHANNEL) {
            if (modalChannelNameBox != null && modalChannelNameBox.isFocused()) {
                return modalChannelNameBox.charTyped(codePoint, modifiers);
            }
            if (modalActorNameBox != null && modalActorNameBox.isFocused()) {
                return modalActorNameBox.charTyped(codePoint, modifiers);
            }
            if (modalActorTagBox != null && modalActorTagBox.isFocused()) {
                return modalActorTagBox.charTyped(codePoint, modifiers);
            }
            return true;
        }

        if (!isRightPanelCollapsed && this.rightPanel != null && this.rightPanel.isAnyFieldFocused()) {
            if (this.rightPanel.charTyped(codePoint, modifiers)) {
                return true;
            }
        }

        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (!isRightPanelCollapsed && this.rightPanel != null) {
            this.rightPanel.tick();
        }
        if (activeModal == ModalType.ADD_CHANNEL) {
            if (modalChannelNameBox != null) modalChannelNameBox.tick();
            if (modalActorNameBox != null) modalActorNameBox.tick();
            if (modalActorTagBox != null) modalActorTagBox.tick();
        }
    }

    private int getChannelIndex(String actionType) {
        if (actionType == null) return 0;
        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).getType().equalsIgnoreCase(actionType)) return i;
        }
        return 0;
    }

    private int getChannelIndex(MusicSequenceEntry entry) {
        if (entry == null) return 0;
        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        if (channels.isEmpty()) return 0;

        String chId = entry.getChannelId();
        if (chId != null && !chId.isBlank()) {
            for (int i = 0; i < channels.size(); i++) {
                if (channels.get(i).getId().equalsIgnoreCase(chId)) {
                    return i;
                }
            }
        }

        return getChannelIndex(entry.getActionType());
    }

    private void updateViewportFlight(float dt) {
        if (SequencerViewportManager.getCurrentMode() != SequencerViewportManager.CameraMode.WORK_CAMERA) return;
        if (rightPanel != null && rightPanel.isAnyFieldFocused()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        long window = mc.getWindow().getWindow();

        boolean rClick = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (!rClick) return;

        boolean w = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W);
        boolean s = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S);
        boolean a = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A);
        boolean d = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D);
        boolean space = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE);
        boolean shift = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT);
        boolean q = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_Q);
        boolean e = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_E);

        SequencerViewportManager.updateFlight(dt, w, s, a, d, space, shift, q, e);
    }

    private void renderViewportGuides(GuiGraphics guiGraphics, int vx, int vy, int vw, int vh) {
        int cx = vx + (vw / 2);
        int cy = vy + (vh / 2);
        int thirdsColor = 0x2200E5FF;
        int tX1 = vx + (vw / 3);
        int tX2 = vx + (vw * 2 / 3);
        int tY1 = vy + (vh / 3);
        int tY2 = vy + (vh * 2 / 3);

        guiGraphics.fill(tX1, vy, tX1 + 1, vy + vh, thirdsColor);
        guiGraphics.fill(tX2, vy, tX2 + 1, vy + vh, thirdsColor);
        guiGraphics.fill(vx, tY1, vx + vw, tY1 + 1, thirdsColor);
        guiGraphics.fill(vx, tY2, vx + vw, tY2 + 1, thirdsColor);

        // Center reticle
        int retColor = 0x4400FF88;
        guiGraphics.fill(cx - 8, cy, cx - 2, cy + 1, retColor);
        guiGraphics.fill(cx + 3, cy, cx + 9, cy + 1, retColor);
        guiGraphics.fill(cx, cy - 8, cx + 1, cy - 2, retColor);
        guiGraphics.fill(cx, cy + 3, cx + 1, cy + 9, retColor);
        guiGraphics.fill(cx, cy, cx + 1, cy + 1, 0x8800FF88);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        long nowNano = System.nanoTime();
        if (this.lastFrameNanoTime > 0L) {
            float dt = (float) ((nowNano - this.lastFrameNanoTime) / 1_000_000_000.0);
            dt = Mth.clamp(dt, 0.0001f, 0.05f);
            updateViewportFlight(dt);
        }
        this.lastFrameNanoTime = nowNano;

        double scale = getLayoutScale();
        int scaledMouseX = mouseX;
        int scaledMouseY = mouseY;

        int mainAreaW = getMainAreaWidth();
        int rightPanelW = getRightPanelWidth();
        int topBarH = 32;
        int timelineTop = getTimelineTop();
        int viewportH = Math.max(10, timelineTop - topBarH);

        // Keep Axiom-style 3D viewport synchronized
        SequencerViewportManager.setViewportBounds(0, topBarH, mainAreaW, viewportH);

        // Non-viewport background fills
        guiGraphics.fill(0, 0, this.width, topBarH, CYAN_BG);
        guiGraphics.fill(0, topBarH - 1, this.width, topBarH, CYAN_MAIN);
        guiGraphics.fill(0, timelineTop, mainAreaW, this.height, CYAN_BG);

        // 3D Viewport Framing Borders
        guiGraphics.fill(0, topBarH, mainAreaW, topBarH + 1, 0x5500E5FF);
        guiGraphics.fill(0, timelineTop - 1, mainAreaW, timelineTop, 0x5500E5FF);
        guiGraphics.fill(mainAreaW - 1, topBarH, mainAreaW, timelineTop, 0x8800E5FF);

        // Viewport Floating Status Chip (Top-left inside viewport)
        boolean isWorkCam = (SequencerViewportManager.getCurrentMode() == SequencerViewportManager.CameraMode.WORK_CAMERA);
        int chipW = Math.min(215, Math.max(90, mainAreaW - 16));
        int chipH = 18;
        if (viewportH >= 24 && chipW >= 90) {
            guiGraphics.fill(8, topBarH + 6, 8 + chipW, topBarH + 6 + chipH, 0xDD05090C);
            guiGraphics.fill(8, topBarH + 6, 8 + chipW, topBarH + 7, isWorkCam ? 0xFFFFD700 : 0xFF00E5FF);

            String modeBadge = isWorkCam ? (chipW > 150 ? "🎥 WORK CAM" : "🎥 WORK") : (chipW > 150 ? "🎬 SEQ CAM" : "🎬 SEQ");
            guiGraphics.drawString(this.font, modeBadge, 14, topBarH + 11, isWorkCam ? 0xFFFFD700 : CYAN_MAIN, false);

            if (chipW >= 150) {
                long curSec = (long) (playheadMs / 1000.0);
                long curMs = (long) (playheadMs % 1000.0);
                String playheadTimeStr = String.format("%02d:%02d.%03d", curSec / 60, curSec % 60, curMs);
                guiGraphics.drawString(this.font, playheadTimeStr, 14 + font.width(modeBadge) + 6, topBarH + 11, 0xFFFFFFFF, false);
            }

            if (chipW >= 200) {
                double camFov = CustomCameraManager.isFovActive() ? CustomCameraManager.getCustomFov() : 70.0;
                guiGraphics.drawString(this.font, String.format(Locale.US, "%.0f°", camFov), 8 + chipW - 24, topBarH + 11, TEXT_MUTED, false);
            }
        }

        if (isWorkCam && viewportH >= 36 && mainAreaW >= 220) {
            guiGraphics.drawString(this.font, "RMB+WASD: Fly | Q/E: Roll | R: 0° | Mid: Pan | C: Stamp | Tab: Mode", 12, timelineTop - 15, 0x88AABBCC, false);

            Vec3 wPos = SequencerViewportManager.getWorkCameraPos();
            float wYaw = SequencerViewportManager.getWorkCameraYaw();
            float wPitch = SequencerViewportManager.getWorkCameraPitch();
            float wRoll = SequencerViewportManager.getWorkCameraRoll();
            String coordsStr = String.format(Locale.US, "CAM: %.1f, %.1f, %.1f | Y:%.0f° P:%.0f° R:%.0f°", wPos.x, wPos.y, wPos.z, wYaw, wPitch, wRoll);
            int coordsW = this.font.width(coordsStr);
            if (mainAreaW >= coordsW + 28) {
                guiGraphics.drawString(this.font, coordsStr, mainAreaW - coordsW - 12, topBarH + 11, 0xAA00E5FF, false);
            }
        }

        if (showCompositionGuides) {
            renderViewportGuides(guiGraphics, 0, topBarH, mainAreaW, viewportH);
        }

        // Live Screen Effects Preview within Viewport
        if (isPreviewScreenEffects) {
            ClientScreenEffectHandler.renderScreenEffectsInArea(guiGraphics, partialTick, 0, topBarH, mainAreaW, viewportH);
        }

        int timelineLeft = 0;
        int timelineWidth = mainAreaW;
        int trackHeaderWidth = getTrackHeaderWidth();
        int trackHeaderLeft = timelineLeft;
        int timelineTrackLeft = timelineLeft + trackHeaderWidth;
        int timelineTrackWidth = getTimelineTrackWidth();
        int toolbarHeight = getTimelineToolbarHeight();
        int canvasY = timelineTop + toolbarHeight;
        int canvasBottom = this.height;
        int rulerHeight = getRulerHeight();
        int rulerY = canvasY;
        int waveformHeight = getWaveformHeight();
        int waveTopY = rulerY + rulerHeight;
        int trackAreaY = waveTopY + waveformHeight;
        int channelHeight = getChannelHeight();
        int channelAreaHeight = Math.max(10, canvasBottom - trackAreaY);

        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        int channelCount = channels.size();
        int totalChannelContentH = (channelCount * channelHeight) + 32;

        // Clamp channel vertical scroll
        int maxScrollY = Math.max(0, totalChannelContentH - channelAreaHeight);
        this.channelScrollY = Math.max(0.0, Math.min(maxScrollY, this.channelScrollY));

        // Update preview playhead position if playing & Auto-Follow timeline
        if (isPreviewPlaying) {
            long now = System.currentTimeMillis();
            long dt = now - lastPreviewTickTime;
            lastPreviewTickTime = now;
            if (!isDraggingPlayhead) {
                playheadMs += dt;
            }

            if (autoFollowPlayhead && !isDraggingPlayhead) {
                double playheadScreenX = timelineTrackLeft + ((playheadMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
                if (playheadScreenX > timelineTrackLeft + (timelineTrackWidth * 0.75)) {
                    timeScrollMs = Math.max(0.0, playheadMs - ((timelineTrackWidth * 0.35) / pixelsPerSecond) * 1000.0);
                    clampTimeScroll();
                } else if (playheadScreenX < timelineTrackLeft) {
                    timeScrollMs = Math.max(0.0, playheadMs - ((timelineTrackWidth * 0.1) / pixelsPerSecond) * 1000.0);
                    clampTimeScroll();
                }
            }
        }

        updatePreviewCamera();
        updatePreviewScreenEffects();

        // Top status indicators
        if (hasUnsavedChanges()) {
            guiGraphics.drawString(this.font, "* UNSAVED CHANGES", this.width - 380, 11, 0xFFFF3366, false);
        } else if (saveFeedbackMessage != null && System.currentTimeMillis() - saveFeedbackTime < 3000L) {
            guiGraphics.drawString(this.font, saveFeedbackMessage, this.width - 380, 11, 0xFF00FF88, false);
        }

        // 0. Transport Toolbar Background Strip
        guiGraphics.fill(timelineLeft, timelineTop, timelineLeft + timelineWidth, canvasY, 0xEE081622);
        guiGraphics.fill(timelineLeft, timelineTop, timelineLeft + timelineWidth, timelineTop + 1, CARD_BORDER);
        guiGraphics.fill(timelineLeft, canvasY - 1, timelineLeft + timelineWidth, canvasY, CARD_BORDER);
        guiGraphics.fill(timelineLeft + timelineWidth - 1, timelineTop, timelineLeft + timelineWidth, canvasY, CARD_BORDER);

        // Timecode readout on toolbar
        long curSec = (long) (playheadMs / 1000.0);
        long curMs = (long) (playheadMs % 1000.0);
        long totSec = getEffectiveEndMs() / 1000;
        long totMsRem = getEffectiveEndMs() % 1000;
        String timeDisplay = (timelineWidth < 400)
                ? String.format("%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, totSec / 60, totSec % 60)
                : String.format("%02d:%02d.%03d / %02d:%02d.%03d", curSec / 60, curSec % 60, curMs, totSec / 60, totSec % 60, totMsRem);
        int timeDisplayX = Math.max(lastTransportEndX + 6, 260);
        int timeW = this.font.width(timeDisplay);
        if (timeDisplayX + timeW <= timelineWidth - 4) {
            guiGraphics.drawString(this.font, timeDisplay, timeDisplayX, timelineTop + (toolbarHeight - 8) / 2, CYAN_MAIN, false);
        }

        String statDisplay = "BPM: " + currentSequence.getBpm() + "  |  " + (int) pixelsPerSecond + " px/s";
        int statW = this.font.width(statDisplay);
        if (timelineWidth - statW - 12 > timeDisplayX + timeW + 12) {
            guiGraphics.drawString(this.font, statDisplay, timelineWidth - statW - 8, timelineTop + (toolbarHeight - 8) / 2, TEXT_MUTED, false);
        }

        // Timeline Outer Canvas Box
        guiGraphics.fill(timelineLeft, canvasY, timelineLeft + timelineWidth, canvasBottom, CYAN_BG);
        guiGraphics.fill(timelineLeft, canvasY, timelineLeft + timelineWidth, canvasY + 1, CARD_BORDER);
        guiGraphics.fill(timelineLeft, canvasBottom - 1, timelineLeft + timelineWidth, canvasBottom, CARD_BORDER);
        guiGraphics.fill(timelineLeft + timelineWidth - 1, canvasY, timelineLeft + timelineWidth, canvasBottom, CARD_BORDER);

        // ==========================================
        // 1. LEFT COLUMN: PREMIERE-STYLE TRACK HEADERS
        // ==========================================

        // A. Header block for Ruler row ("TRACKS")
        guiGraphics.fill(trackHeaderLeft, rulerY, timelineTrackLeft, rulerY + rulerHeight, 0xEE081622);
        guiGraphics.fill(trackHeaderLeft, rulerY + rulerHeight - 1, timelineTrackLeft, rulerY + rulerHeight, 0xAA00E5FF);
        String headerLabel = trackHeaderWidth < 90 ? "CH" : "CHANNELS";
        guiGraphics.drawString(this.font, headerLabel, trackHeaderLeft + (trackHeaderWidth < 90 ? 6 : 10), rulerY + 6, CYAN_MAIN, false);

        // B. Header block for Waveform row ("🎵 AUDIO")
        guiGraphics.fill(trackHeaderLeft, waveTopY, timelineTrackLeft, waveTopY + waveformHeight, 0xEE05101A);
        guiGraphics.fill(trackHeaderLeft, waveTopY, trackHeaderLeft + 4, waveTopY + waveformHeight, CYAN_MAIN);
        guiGraphics.fill(trackHeaderLeft, waveTopY + waveformHeight - 1, timelineTrackLeft, waveTopY + waveformHeight, 0x6600E5FF);
        String audioLabel = trackHeaderWidth < 90 ? "🎵 AUDIO" : "🎵 AUDIO / SONG";
        guiGraphics.drawString(this.font, audioLabel, trackHeaderLeft + 8, waveTopY + 5, CYAN_MAIN, false);

        String trackName = currentSequence.getSongTrack();
        if (trackName != null && !trackName.isBlank()) {
            String shortSong = net.dandare21.fracturedutils.sound.event.AudioTrackDiscovery.formatTrackLabel(trackName);
            if (this.font.width(shortSong) > trackHeaderWidth - 16) {
                shortSong = this.font.plainSubstrByWidth(shortSong, trackHeaderWidth - 22) + "..";
            }
            guiGraphics.drawString(this.font, shortSong, trackHeaderLeft + 8, waveTopY + 18, 0xFFAABBCC, false);
        } else {
            guiGraphics.drawString(this.font, "[No Audio]", trackHeaderLeft + 8, waveTopY + 18, 0xFF778899, false);
        }

        // C. Scissored Channel Track Headers
        guiGraphics.enableScissor(
                trackHeaderLeft,
                trackAreaY,
                timelineTrackLeft,
                canvasBottom
        );

        for (int c = 0; c < channels.size(); c++) {
            MusicSequenceChannel ch = channels.get(c);
            int trackY = trackAreaY + (c * channelHeight) - (int) channelScrollY;
            if (trackY + channelHeight < trackAreaY || trackY > canvasBottom) {
                continue;
            }

            boolean isSub = ch.isSubChannel();
            boolean isPuppet = MusicSequenceChannel.TYPE_PUPPET.equalsIgnoreCase(ch.getType());

            int rowBg;
            if (isSub) {
                rowBg = (c % 2 == 0) ? 0xEE0D101C : 0xEE090C16;
            } else {
                rowBg = (c % 2 == 0) ? 0xEE0A1828 : 0xEE071220;
            }
            guiGraphics.fill(trackHeaderLeft, trackY, timelineTrackLeft, trackY + channelHeight, rowBg);
            guiGraphics.fill(trackHeaderLeft, trackY + channelHeight - 1, timelineTrackLeft, trackY + channelHeight, isSub ? 0x44BB77FF : 0x4400E5FF);

            // Left accent stripe / tree connector
            int stripeColor = isSub ? MusicSequenceChannel.COLOR_PUPPET_SUB : ch.getColor();
            if (isSub) {
                guiGraphics.fill(trackHeaderLeft + 7, trackY, trackHeaderLeft + 10, trackY + channelHeight, stripeColor);
                guiGraphics.fill(trackHeaderLeft + 7, trackY + 11, trackHeaderLeft + 14, trackY + 13, stripeColor);
            } else {
                guiGraphics.fill(trackHeaderLeft, trackY, trackHeaderLeft + 4, trackY + channelHeight, stripeColor);
            }

            int delL = trackHeaderWidth < 90 ? (timelineTrackLeft - 18) : (timelineTrackLeft - 22);
            int delR = trackHeaderWidth < 90 ? (timelineTrackLeft - 4) : (timelineTrackLeft - 6);
            int subL = trackHeaderWidth < 90 ? (timelineTrackLeft - 38) : (timelineTrackLeft - 50);
            int subR = trackHeaderWidth < 90 ? (timelineTrackLeft - 20) : (timelineTrackLeft - 25);
            int reservedRight = isPuppet ? (trackHeaderWidth < 90 ? 42 : 56) : (trackHeaderWidth < 90 ? 22 : 28);

            // Channel Title
            String title = (isSub && !ch.getName().startsWith("↳")) ? ("↳ " + ch.getName()) : ("CH " + (c + 1) + ": " + ch.getName());
            int titleX = isSub ? (trackHeaderLeft + 16) : (trackHeaderLeft + 8);
            int maxTitleW = Math.max(16, trackHeaderWidth - reservedRight - (isSub ? 16 : 8));
            if (this.font.width(title) > maxTitleW) {
                title = this.font.plainSubstrByWidth(title, maxTitleW - 6) + "..";
            }
            guiGraphics.drawString(this.font, title, titleX, trackY + 5, stripeColor, false);

            // Channel Sub-label (Actor / Type)
            String sub = ch.getType();
            if (isPuppet) {
                if (isSub) {
                    sub = "🔗 #" + ch.getActorTag();
                } else {
                    int subCount = 0;
                    for (MusicSequenceChannel other : channels) {
                        if (other.isSubChannel() && ch.getId().equalsIgnoreCase(other.getParentChannelId())) {
                            subCount++;
                        }
                    }
                    if (subCount > 0) {
                        sub = "🎭 #" + ch.getActorTag() + " (+" + subCount + " subs)";
                    } else if (ch.getActorTag() != null && !ch.getActorTag().isBlank()) {
                        sub = "🎭 #" + ch.getActorTag();
                    } else if (!ch.getPuppetActor().isBlank()) {
                        sub = "🎭 " + ch.getPuppetActor();
                    }
                }
            }
            int subX = isSub ? (trackHeaderLeft + 16) : (trackHeaderLeft + 8);
            int maxSubW = Math.max(16, trackHeaderWidth - reservedRight - (isSub ? 16 : 8));
            if (this.font.width(sub) > maxSubW) {
                sub = this.font.plainSubstrByWidth(sub, maxSubW - 6) + "..";
            }
            guiGraphics.drawString(this.font, sub, subX, trackY + 16, isSub ? 0xFFAABBCC : 0xFF8899AA, false);

            // [+SUB] Button for Puppet Channels
            if (isPuppet) {
                boolean isHoverSub = (scaledMouseX >= subL && scaledMouseX <= subR && scaledMouseY >= trackY + 4 && scaledMouseY <= trackY + channelHeight - 4);
                guiGraphics.fill(subL, trackY + 5, subR, trackY + channelHeight - 5, isHoverSub ? 0xDD9944FF : 0x336622AA);
                guiGraphics.fill(subL, trackY + 5, subR, trackY + 6, 0x88BB77FF);
                guiGraphics.fill(subL, trackY + channelHeight - 6, subR, trackY + channelHeight - 5, 0x88BB77FF);
                guiGraphics.fill(subL, trackY + 5, subL + 1, trackY + channelHeight - 5, 0x88BB77FF);
                guiGraphics.fill(subR - 1, trackY + 5, subR, trackY + channelHeight - 5, 0x88BB77FF);
                String subTxt = trackHeaderWidth < 90 ? "+S" : "+SUB";
                guiGraphics.drawCenteredString(this.font, subTxt, subL + ((subR - subL) / 2), trackY + 6, isHoverSub ? 0xFFFFFFFF : 0xFFDD99FF);
            }

            // Delete Channel Button [✕]
            boolean isHoverDel = (scaledMouseX >= delL && scaledMouseX <= delR && scaledMouseY >= trackY + 4 && scaledMouseY <= trackY + channelHeight - 4);
            guiGraphics.fill(delL, trackY + 5, delR, trackY + channelHeight - 5, isHoverDel ? 0x88FF3355 : 0x22334455);
            guiGraphics.drawCenteredString(this.font, "✕", delL + ((delR - delL) / 2), trackY + 6, isHoverDel ? 0xFFFFFFFF : 0xFFAABBCC);
        }

        // D. "+ ADD CHANNEL" Button below all channels
        int addBtnY = trackAreaY + (channelCount * channelHeight) + 4 - (int) channelScrollY;
        if (addBtnY >= trackAreaY - 20 && addBtnY <= canvasBottom) {
            boolean isHoverAdd = (scaledMouseX >= trackHeaderLeft + 4 && scaledMouseX <= timelineTrackLeft - 4 && scaledMouseY >= addBtnY && scaledMouseY <= addBtnY + 22);

            guiGraphics.fill(trackHeaderLeft + 4, addBtnY, timelineTrackLeft - 4, addBtnY + 22, isHoverAdd ? 0xDD00E5FF : 0x2200E5FF);
            guiGraphics.fill(trackHeaderLeft + 4, addBtnY, timelineTrackLeft - 4, addBtnY + 1, CARD_BORDER);
            guiGraphics.fill(trackHeaderLeft + 4, addBtnY + 21, timelineTrackLeft - 4, addBtnY + 22, CARD_BORDER);
            guiGraphics.fill(trackHeaderLeft + 4, addBtnY, trackHeaderLeft + 5, addBtnY + 22, CARD_BORDER);
            guiGraphics.fill(timelineTrackLeft - 5, addBtnY, timelineTrackLeft - 4, addBtnY + 22, CARD_BORDER);
            String addLabel = trackHeaderWidth < 90 ? "+ ADD" : "+ ADD CHANNEL";
            guiGraphics.drawCenteredString(this.font, addLabel, trackHeaderLeft + (trackHeaderWidth / 2), addBtnY + 7, isHoverAdd ? 0xFF000000 : CYAN_MAIN);
        }

        guiGraphics.disableScissor();

        // ==========================================
        // 2. RIGHT COLUMN: TIMELINE TRACKS & GRID
        // ==========================================

        // Band 1: Time Ruler (Top Axis)
        guiGraphics.enableScissor(timelineTrackLeft, rulerY, timelineLeft + timelineWidth, rulerY + rulerHeight);
        guiGraphics.fill(timelineTrackLeft, rulerY, timelineLeft + timelineWidth, rulerY + rulerHeight, 0xEE081622);
        guiGraphics.fill(timelineTrackLeft, rulerY + rulerHeight - 1, timelineLeft + timelineWidth, rulerY + rulerHeight, 0xAA00E5FF);

        double secondsVisible = timelineTrackWidth / pixelsPerSecond;
        int stepSec = secondsVisible > 30 ? 5 : 1;

        for (int sec = 0; sec <= (int) secondsVisible + 20; sec += stepSec) {
            double tickMs = (sec * 1000.0);
            double tickX = timelineTrackLeft + ((tickMs - timeScrollMs) / 1000.0) * pixelsPerSecond;

            if (tickX >= timelineTrackLeft && tickX <= timelineLeft + timelineWidth) {
                int tickY2 = rulerY + rulerHeight - (sec % 5 == 0 ? 12 : 6);
                guiGraphics.fill((int) tickX, tickY2, (int) tickX + 1, rulerY + rulerHeight, 0xAA00E5FF);

                if (sec % 5 == 0 || stepSec == 1) {
                    String timeLabel = String.format("%02d:%02d", sec / 60, sec % 60);
                    guiGraphics.drawString(this.font, timeLabel, (int) tickX - 12, rulerY + 4, 0xFFAABBCC, false);
                }
            }
        }
        guiGraphics.disableScissor();

        // Sequence START & END Marker Calculations & Loop Handling
        long startMs = currentSequence.getStartMs();
        long endMs = getEffectiveEndMs();

        // DAW Preview Playback Loop/Pause between startMs and endMs
        if (isPreviewPlaying && !isDraggingPlayhead) {
            if (playheadMs < startMs) {
                playheadMs = startMs;
            } else if (playheadMs >= endMs) {
                if (currentSequence.isLooping()) {
                    playheadMs = startMs;
                    EventAudioClientController.getInstance().playAudio(
                            currentSequence.getSongTrack(),
                            net.dandare21.fracturedutils.sound.ModSoundSources.EVENT_MUSIC,
                            currentSequence.getVolume(),
                            currentSequence.getPitch(),
                            0,
                            startMs,
                            true,
                            net.dandare21.fracturedutils.network.packet.S2CPlayEventAudioPacket.PlaybackMode.FIRE_AND_FORGET,
                            true,
                            2000
                    );
                } else {
                    playheadMs = endMs;
                    isPreviewPlaying = false;
                    EventAudioClientController.getInstance().stopAudio(0);
                    if (playPreviewBtn != null) {
                        playPreviewBtn.setMessage(Component.literal("▶ PLAY"));
                        playPreviewBtn.setAccentColor(0xFF00FF88);
                    }
                }
            }
        }

        double startX = timelineTrackLeft + ((startMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
        double endX = timelineTrackLeft + ((endMs - timeScrollMs) / 1000.0) * pixelsPerSecond;

        int bpm = currentSequence.getBpm();
        double beatMs = 60000.0 / (double) Math.max(20, bpm);
        double gameTickMs = 50.0;
        double firstTick = Math.floor(timeScrollMs / gameTickMs);
        double lastTick = Math.ceil((timeScrollMs + (timelineTrackWidth / pixelsPerSecond) * 1000.0) / gameTickMs);
        double firstBeat = Math.floor(timeScrollMs / beatMs);
        double lastBeat = Math.ceil((timeScrollMs + (timelineTrackWidth / pixelsPerSecond) * 1000.0) / beatMs);

        // Band 2: Channel Tracks Area (Clipped strictly at trackAreaY so channel rows never obscure waveform)
        guiGraphics.enableScissor(timelineTrackLeft, trackAreaY, timelineLeft + timelineWidth, canvasBottom);

        // Channel Tracks Backgrounds
        for (int c = 0; c < channels.size(); c++) {
            int trackY = trackAreaY + (c * channelHeight) - (int) channelScrollY;
            if (trackY + channelHeight < trackAreaY || trackY > canvasBottom) {
                continue;
            }
            int rowBg = (c % 2 == 0) ? 0xEE091624 : 0xEE060F1A;
            guiGraphics.fill(timelineTrackLeft, trackY, timelineLeft + timelineWidth, trackY + channelHeight, rowBg);
            guiGraphics.fill(timelineTrackLeft, trackY + channelHeight - 1, timelineLeft + timelineWidth, trackY + channelHeight, 0x3300E5FF);
        }

        // 50ms Game Tick Grid Lines in Channel Area
        for (double t = firstTick; t <= lastTick; t++) {
            double tMs = t * gameTickMs;
            double tickX = timelineTrackLeft + ((tMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
            if (tickX >= timelineTrackLeft && tickX <= timelineLeft + timelineWidth) {
                guiGraphics.fill((int) tickX, trackAreaY, (int) tickX + 1, canvasBottom, 0x1500E5FF);
            }
        }

        // Beat Lines & Rekordbox Red Bar Lines in Channel Area
        for (double b = firstBeat; b <= lastBeat; b++) {
            double bMs = b * beatMs;
            double beatX = timelineTrackLeft + ((bMs - timeScrollMs) / 1000.0) * pixelsPerSecond;

            if (beatX >= timelineTrackLeft && beatX <= timelineLeft + timelineWidth) {
                long beatIndex = Math.round(b);
                boolean isBarStart = (beatIndex % 4 == 0);

                if (isBarStart) {
                    guiGraphics.fill((int) beatX - 1, trackAreaY, (int) beatX + 1, canvasBottom, 0xFFFF0055);
                } else {
                    guiGraphics.fill((int) beatX, trackAreaY, (int) beatX + 1, canvasBottom, 0x3300E5FF);
                }
            }
        }

        // Dimmed Region BEFORE START in Channel Area
        if (startX > timelineTrackLeft) {
            int dimW = (int) Math.min((double) timelineTrackWidth, startX - timelineTrackLeft);
            if (dimW > 0) {
                guiGraphics.fill(timelineTrackLeft, trackAreaY, timelineTrackLeft + dimW, canvasBottom, 0xCC050A10);
            }
        }

        // Dimmed Region AFTER END in Channel Area
        if (endX < timelineLeft + timelineWidth) {
            int dimX = (int) Math.max((double) timelineTrackLeft, endX);
            int dimW = (timelineLeft + timelineWidth) - dimX;
            if (dimW > 0) {
                guiGraphics.fill(dimX, trackAreaY, dimX + dimW, canvasBottom, 0xCC050A10);
            }
        }

        // F. Action Keyframe Nodes & Multi-Color Segmented Duration Bars
        List<MusicSequenceEntry> entries = currentSequence.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            MusicSequenceEntry entry = entries.get(i);
            int channelIndex = getChannelIndex(entry);
            int chColor = (channelIndex < channels.size()) ? channels.get(channelIndex).getColor() : CYAN_MAIN;

            int entryTrackY = trackAreaY + (channelIndex * channelHeight) - (int) channelScrollY;
            double entryX = timelineTrackLeft + ((entry.getTimestampMs() - timeScrollMs) / 1000.0) * pixelsPerSecond;
            int totalDurationMs = entry.getTotalDurationMs();

            if (entryTrackY >= trackAreaY - 10 && entryTrackY <= canvasBottom) {
                boolean isSelected = (i == selectedEntryIndex);
                boolean isCamera = "CAMERA".equalsIgnoreCase(entry.getActionType()) || (channelIndex < channels.size() && "CAMERA".equalsIgnoreCase(channels.get(channelIndex).getType()));
                boolean isPuppet = "PUPPET".equalsIgnoreCase(entry.getActionType()) || (channelIndex < channels.size() && "PUPPET".equalsIgnoreCase(channels.get(channelIndex).getType()));
                String pSub = entry.getSubAction() != null ? entry.getSubAction().toUpperCase(Locale.ROOT) : "";
                boolean isPuppetNonDuration = isPuppet && ("DESPAWN".equals(pSub) || "SPAWN".equals(pSub) || "TOGGLE_AI".equals(pSub));

                if (isCamera) {
                    int kx = (int) entryX;
                    int railY = entryTrackY + (channelHeight / 2);

                    String mode = entry.getCameraMode();
                    if (mode == null || mode.isBlank()) mode = entry.getSubAction();
                    if (mode == null || mode.isBlank()) mode = "KEYFRAME";

                    boolean isEnable = "ENABLE".equalsIgnoreCase(mode);
                    boolean isDisable = "DISABLE".equalsIgnoreCase(mode) || "CLEAR".equalsIgnoreCase(mode);
                    boolean isTracking = "FOLLOW".equalsIgnoreCase(mode) || "OVER_THE_SHOULDER".equalsIgnoreCase(mode) || "TRACKING".equalsIgnoreCase(mode);

                    // 1. Connective Transition Rail between this keyframe and next camera keyframe on track
                    MusicSequenceEntry nextCam = null;
                    for (int j = 0; j < entries.size(); j++) {
                        MusicSequenceEntry candidate = entries.get(j);
                        if (candidate != entry && candidate.getTimestampMs() > entry.getTimestampMs()) {
                            if ("CAMERA".equalsIgnoreCase(candidate.getActionType()) || (candidate.getChannelId() != null && candidate.getChannelId().equalsIgnoreCase(entry.getChannelId()))) {
                                if (nextCam == null || candidate.getTimestampMs() < nextCam.getTimestampMs()) {
                                    nextCam = candidate;
                                }
                            }
                        }
                    }

                    if (!isDisable && nextCam != null) {
                        int nextKx = (int) (timelineTrackLeft + ((nextCam.getTimestampMs() - timeScrollMs) / 1000.0) * pixelsPerSecond);
                        if (nextKx > kx && nextKx >= timelineTrackLeft && kx <= timelineLeft + timelineWidth) {
                            int segStart = Math.max(kx, timelineTrackLeft);
                            int segEnd = Math.min(nextKx, timelineLeft + timelineWidth);

                            // Active camera shot connection band
                            guiGraphics.fill(segStart, railY - 2, segEnd, railY + 3, 0x3300E5FF);
                            guiGraphics.fill(segStart, railY - 1, segEnd, railY + 2, 0x8800E5FF);
                            guiGraphics.fill(segStart, railY, segEnd, railY + 1, 0xFF00E5FF);

                            // Interpolation wave or step
                            String spline = entry.getSplineMode();
                            if (spline == null || spline.isBlank()) spline = "CATMULL_ROM";
                            if ("STEP".equalsIgnoreCase(spline)) {
                                int midX = (segStart + segEnd) / 2;
                                guiGraphics.fill(segStart, railY + 4, midX, railY + 5, 0xAA00E5FF);
                                guiGraphics.fill(midX, railY - 4, midX + 1, railY + 5, 0xAA00E5FF);
                                guiGraphics.fill(midX, railY - 4, segEnd, railY - 3, 0xAA00E5FF);
                            } else if (!"LINEAR".equalsIgnoreCase(spline)) {
                                for (int wx = segStart + 4; wx < segEnd - 4; wx += 4) {
                                    float wt = (float) (wx - segStart) / Math.max(1, segEnd - segStart);
                                    int waveY = railY + (int) (Math.sin(wt * Math.PI * 2.0) * 4.0);
                                    guiGraphics.fill(wx, waveY, wx + 2, waveY + 1, 0xAA00E5FF);
                                }
                            }

                            // Transition span label in middle of segment
                            if (segEnd - segStart >= 60) {
                                long segMs = nextCam.getTimestampMs() - entry.getTimestampMs();
                                String transLabel = String.format(Locale.ROOT, "%.2fs (%s)", segMs / 1000.0, spline.toLowerCase(Locale.ROOT));
                                int tlW = this.font.width(transLabel);
                                int midX = (segStart + segEnd) / 2;
                                guiGraphics.fill(midX - (tlW / 2) - 3, railY - 12, midX + (tlW / 2) + 3, railY - 2, 0xCC060C14);
                                guiGraphics.drawCenteredString(this.font, transLabel, midX, railY - 11, 0xFF66DDFF);
                            }
                        }
                    }

                    // 2. Pure Point Keyframe Diamond Node at (kx, railY)
                    if (kx >= timelineTrackLeft - 20 && kx <= timelineLeft + timelineWidth + 20) {
                        boolean isHovered = (scaledMouseX >= kx - 8 && scaledMouseX <= kx + 8 &&
                                scaledMouseY >= entryTrackY && scaledMouseY <= entryTrackY + channelHeight);

                        int diamondColor = isEnable ? 0xFF00FF88 : (isDisable ? 0xFFFF3355 : (isTracking ? 0xFFAA55FF : 0xFF00E5FF));
                        int badgeColor = isEnable ? 0xFF00FF88 : (isDisable ? 0xFFFF5566 : (isTracking ? 0xFFCC88FF : 0xFF33FFFF));

                        String badgeText;
                        if (isEnable) {
                            badgeText = "▶ CAM ON";
                        } else if (isDisable) {
                            badgeText = "■ CAM OFF";
                        } else if (isTracking) {
                            String tgt = entry.getCameraTarget();
                            badgeText = "👤 " + (tgt == null || tgt.isBlank() ? "TRACKING" : tgt);
                        } else {
                            if (entry.getDescription() != null && !entry.getDescription().isBlank() && !entry.getDescription().startsWith("Camera (") && !entry.getDescription().equalsIgnoreCase("Camera")) {
                                badgeText = "◆ " + entry.getDescription();
                            } else {
                                badgeText = String.format(Locale.ROOT, "◆ [%.1f, %.1f, %.1f]", entry.getCameraX(), entry.getCameraY(), entry.getCameraZ());
                            }
                        }

                        // Selection / Hover Aura
                        if (isSelected) {
                            long now = System.currentTimeMillis();
                            int auraColor = (now / 300) % 2 == 0 ? 0xFFFFFFFF : diamondColor;
                            renderDiamond(guiGraphics, kx, railY, 8, auraColor, 0xFFFFFFFF);
                        } else if (isHovered) {
                            renderDiamond(guiGraphics, kx, railY, 7, 0x88FFFFFF, 0xFFFFFFFF);
                        }

                        // Prominent Diamond Node
                        renderDiamond(guiGraphics, kx, railY, 5, diamondColor, isSelected ? 0xFFFFFFFF : 0xFF000000);
                        guiGraphics.fill(kx - 1, railY - 1, kx + 2, railY + 2, isSelected ? 0xFFFFFFFF : 0xFF000000);

                        // Floating Action Badge Above Diamond
                        int bw = this.font.width(badgeText);
                        int badgeX = kx - (bw / 2);
                        badgeX = Math.max(timelineTrackLeft + 2, Math.min(timelineLeft + timelineWidth - bw - 2, badgeX));
                        int badgeY = railY - 16;
                        guiGraphics.fill(badgeX - 3, badgeY - 1, badgeX + bw + 3, badgeY + 9, 0xEE060C14);
                        guiGraphics.fill(badgeX - 3, badgeY - 1, badgeX + bw + 3, badgeY, isHovered || isSelected ? 0xFFFFFFFF : (badgeColor & 0x88FFFFFF));
                        guiGraphics.drawString(this.font, badgeText, badgeX, badgeY, isSelected ? 0xFFFFFFFF : badgeColor, false);

                        // Timestamp Badge Below Diamond
                        long sec = entry.getTimestampMs() / 1000;
                        long rem = entry.getTimestampMs() % 1000;
                        String timeTxt = String.format(Locale.ROOT, "%02d:%02d.%02d", sec / 60, sec % 60, rem / 10);
                        int tw = this.font.width(timeTxt);
                        int timeX = kx - (tw / 2);
                        timeX = Math.max(timelineTrackLeft + 2, Math.min(timelineLeft + timelineWidth - tw - 2, timeX));
                        int timeY = railY + 8;
                        guiGraphics.drawString(this.font, timeTxt, timeX, timeY, isSelected ? 0xFFFFFFFF : 0xFF88AACC, false);
                    }
                } else if (totalDurationMs > 0 && !isPuppetNonDuration) {
                    if ("DIALOG".equalsIgnoreCase(entry.getActionType())) {
                        double totalW = Math.max(12.0, (entry.getTotalDurationMs() / 1000.0) * pixelsPerSecond);
                        if (entryX + totalW >= timelineTrackLeft && entryX <= timelineLeft + timelineWidth) {
                            int bx = (int) entryX;
                            int bw = (int) totalW;
                            int barY = entryTrackY + 4;
                            int barHeight = channelHeight - 8;

                            boolean isHovered = (scaledMouseX >= bx - 4 && scaledMouseX <= bx + bw + 4 &&
                                    scaledMouseY >= entryTrackY && scaledMouseY <= entryTrackY + channelHeight);

                            // Selection Aura
                            if (isSelected) {
                                long now = System.currentTimeMillis();
                                int auraColor = (now / 300) % 2 == 0 ? 0xFFFFFFFF : 0xFFFFD700;
                                guiGraphics.fill(bx - 3, barY - 2, bx + bw + 3, barY + barHeight + 2, auraColor);
                            }

                            // Dark Amber Background
                            guiGraphics.fill(bx, barY, bx + bw, barY + barHeight, 0xEE161208);

                            // Left Gold Accent Notch
                            guiGraphics.fill(bx, barY, bx + 3, barY + barHeight, 0xFFFFD700);

                            // Dialog bar fill
                            guiGraphics.fill(bx + 3, barY + 1, bx + bw - 1, barY + barHeight - 1, isHovered ? 0x44FFD700 : 0x22FFD700);

                            // Bar Outer Border
                            int borderColor = isHovered ? 0xFFFFFFFF : (isSelected ? 0xFFFFFFFF : 0x88FFD700);
                            guiGraphics.fill(bx, barY, bx + bw, barY + 1, borderColor);
                            guiGraphics.fill(bx, barY + barHeight - 1, bx + bw, barY + barHeight, borderColor);
                            guiGraphics.fill(bx + bw - 1, barY, bx + bw, barY + barHeight, borderColor);

                            // Right Edge Stretch Handle (Resize Grip)
                            boolean isStretchHovered = (isHovered && Math.abs(scaledMouseX - (bx + bw)) <= 6);
                            int handleColor = isStretchHovered ? 0xFFFFFFFF : 0xFFFFD700;
                            guiGraphics.fill(bx + bw - 3, barY + 3, bx + bw - 1, barY + barHeight - 3, handleColor);
                            if (isStretchHovered) {
                                guiGraphics.fill(bx + bw - 4, barY + 1, bx + bw, barY + barHeight - 1, 0x55FFFFFF);
                            }

                            // Dialog Keyframe Icon / Node at Start
                            int barCenterY = barY + (barHeight / 2);
                            guiGraphics.fill(bx - 3, barCenterY - 3, bx + 3, barCenterY + 3, 0xFFFFD700);
                            guiGraphics.fill(bx - 2, barCenterY - 2, bx + 2, barCenterY + 2, 0xFF000000);
                            guiGraphics.fill(bx - 1, barCenterY - 1, bx + 1, barCenterY + 1, 0xFFFFFFFF);

                            // Text preview inside bar
                            String speaker = (entry.getDialog() != null && !entry.getDialog().getSpeaker().isBlank()) ? entry.getDialog().getSpeaker() : "";
                            String text = (entry.getDialog() != null && !entry.getDialog().getText().isBlank()) ? entry.getDialog().getText() : entry.getCommand();
                            String displayLabel = "💬 " + (speaker.isEmpty() ? text : speaker + ": " + text);
                            int textW = this.font.width(displayLabel);

                            int textAvailW = bw - 10;
                            if (textAvailW > 20) {
                                if (textW > textAvailW) {
                                    displayLabel = this.font.plainSubstrByWidth(displayLabel, textAvailW - 8) + "..";
                                }
                                guiGraphics.drawString(this.font, displayLabel, bx + 6, barY + 3, 0xFFFFE066, false);
                            }

                            // Duration label badge after bar
                            long durMs = entry.getTotalDurationMs();
                            String durBadge = String.format(Locale.ROOT, "%.1fs (%dms)", durMs / 1000.0, durMs);
                            guiGraphics.drawString(this.font, durBadge, bx + bw + 4, barY + 3, 0xFFAABBCC, false);
                        }
                    } else if ("SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) || "SCREEN_EFFECTS".equalsIgnoreCase(entry.getActionType()) || (channelIndex < channels.size() && MusicSequenceChannel.TYPE_SCREEN_EFFECT.equalsIgnoreCase(channels.get(channelIndex).getType()))) {
                        int durMs = entry.getTotalDurationMs() > 0 ? entry.getTotalDurationMs() : (entry.getDurationMs() > 0 ? entry.getDurationMs() : 1000);
                        if (entry.getDurationMs() <= 0) {
                            entry.setDurationMs(durMs);
                        }
                        double totalW = Math.max(16.0, (durMs / 1000.0) * pixelsPerSecond);
                        if (entryX + totalW >= timelineTrackLeft && entryX <= timelineLeft + timelineWidth) {
                            int bx = (int) entryX;
                            int bw = (int) totalW;
                            int barY = entryTrackY + 4;
                            int barHeight = channelHeight - 8;

                            boolean isHovered = (scaledMouseX >= bx - 4 && scaledMouseX <= bx + bw + 4 &&
                                    scaledMouseY >= entryTrackY && scaledMouseY <= entryTrackY + channelHeight);

                            String sub = entry.getSubAction() != null && !entry.getSubAction().isEmpty() ? entry.getSubAction().toUpperCase(Locale.ROOT) : "SCREEN_SHAKE";
                            int modeClr = switch (sub) {
                                case "INVERT_COLORS", "INVERT" -> 0xFF00FFCC;
                                case "STROBE" -> 0xFFFFD700;
                                case "HUE_SHIFT", "HUE" -> 0xFF33CCFF;
                                case "IMPACT_FRAME", "IMPACT" -> 0xFFFF2255;
                                case "CINEMATIC_BARS", "BARS", "CINEMATIC" -> 0xFFAA55FF;
                                default -> MusicSequenceChannel.COLOR_SCREEN_EFFECT;
                            };

                            // Selection Aura
                            if (isSelected) {
                                long now = System.currentTimeMillis();
                                int auraColor = (now / 300) % 2 == 0 ? 0xFFFFFFFF : modeClr;
                                guiGraphics.fill(bx - 3, barY - 2, bx + bw + 3, barY + barHeight + 2, auraColor);
                            }

                            // Dark Tinted Background
                            guiGraphics.fill(bx, barY, bx + bw, barY + barHeight, 0xEE140818);

                            // Left Accent Notch
                            guiGraphics.fill(bx, barY, bx + 3, barY + barHeight, modeClr);

                            // Screen Effect bar fill
                            int fillClr = (modeClr & 0x00FFFFFF) | (isHovered ? 0x33000000 : 0x1A000000);
                            guiGraphics.fill(bx + 3, barY + 1, bx + bw - 1, barY + barHeight - 1, fillClr);

                            // Bar Outer Border
                            int borderColor = isHovered ? 0xFFFFFFFF : (isSelected ? 0xFFFFFFFF : (0x88000000 | (modeClr & 0x00FFFFFF)));
                            guiGraphics.fill(bx, barY, bx + bw, barY + 1, borderColor);
                            guiGraphics.fill(bx, barY + barHeight - 1, bx + bw, barY + barHeight, borderColor);
                            guiGraphics.fill(bx + bw - 1, barY, bx + bw, barY + barHeight, borderColor);

                            // Right Edge Stretch Handle (Resize Grip)
                            boolean isStretchHovered = (isHovered && Math.abs(scaledMouseX - (bx + bw)) <= 6);
                            boolean isThisStretching = (isStretchingDuration && stretchingEntry == entry);
                            int handleColor = (isStretchHovered || isThisStretching) ? 0xFF00FF88 : modeClr;

                            // Handle grip styling
                            guiGraphics.fill(bx + bw - 3, barY + 3, bx + bw - 1, barY + barHeight - 3, handleColor);
                            if (isStretchHovered || isThisStretching) {
                                guiGraphics.fill(bx + bw - 1, barY - 2, bx + bw + 1, barY + barHeight + 2, 0xFFFFFFFF);
                                guiGraphics.fill(bx + bw - 3, barY - 3, bx + bw + 3, barY, handleColor);
                                guiGraphics.fill(bx + bw - 3, barY + barHeight, bx + bw + 3, barY + barHeight + 3, handleColor);

                                int cy = barY + (barHeight / 2);
                                guiGraphics.fill(bx + bw - 3, cy - 3, bx + bw + 3, cy + 3, 0xEE060C14);
                                guiGraphics.fill(bx + bw - 2, cy - 2, bx + bw + 2, cy + 2, handleColor);
                                guiGraphics.fill(bx + bw - 1, cy - 1, bx + bw + 1, cy + 1, 0xFFFFFFFF);

                                // Floating duration tooltip badge above stretch handle
                                String durTip = "DURATION: " + durMs + "ms (" + String.format(Locale.ROOT, "%.2fs", durMs / 1000.0) + ")";
                                int tipW = this.font.width(durTip);
                                int tipX = Math.max(timelineTrackLeft + 2, Math.min(timelineLeft + timelineWidth - tipW - 4, (bx + bw) - (tipW / 2)));
                                int tipY = barY - 14;
                                guiGraphics.fill(tipX - 3, tipY - 1, tipX + tipW + 3, tipY + 9, 0xEE060C14);
                                guiGraphics.fill(tipX - 3, tipY - 1, tipX + tipW + 3, tipY, handleColor);
                                guiGraphics.drawString(this.font, durTip, tipX, tipY, 0xFFFFFFFF, false);
                            }

                            // Screen Effect Keyframe Icon / Node at Start
                            int barCenterY = barY + (barHeight / 2);
                            guiGraphics.fill(bx - 3, barCenterY - 3, bx + 3, barCenterY + 3, modeClr);
                            guiGraphics.fill(bx - 2, barCenterY - 2, bx + 2, barCenterY + 2, 0xFF000000);
                            guiGraphics.fill(bx - 1, barCenterY - 1, bx + 1, barCenterY + 1, 0xFFFFFFFF);

                            // Text preview inside bar
                            String displayLabel = "⚡ " + (entry.getDescription().isEmpty() ? ("FX: " + sub) : entry.getDescription());
                            int textW = this.font.width(displayLabel);
                            int textAvailW = bw - 10;
                            if (textAvailW > 20) {
                                if (textW > textAvailW) {
                                    displayLabel = this.font.plainSubstrByWidth(displayLabel, textAvailW - 8) + "..";
                                }
                                guiGraphics.drawString(this.font, displayLabel, bx + 6, barY + 3, modeClr, false);
                            }

                            // Duration label badge after bar
                            String durBadge = String.format(Locale.ROOT, "%.1fs (%dms)", durMs / 1000.0, durMs);
                            guiGraphics.drawString(this.font, durBadge, bx + bw + 4, barY + 3, 0xFFAABBCC, false);
                        }
                    } else {
                        // Render multi-color segmented duration bar for puppet actions
                        PuppetActionType<?> actType = ModPuppetActions.resolve(entry.getCommand());
                        List<ActionTimingPhase> phases = actType.getTimingPhases();

                        int totalMs = 0;
                        for (ActionTimingPhase phase : phases) {
                            totalMs += switch (phase.id().toLowerCase(Locale.ROOT)) {
                                case "windup" -> entry.getWindupMs();
                                case "jump" -> entry.getJumpMs();
                                case "duration" -> entry.getDurationMs();
                                case "recovery" -> entry.getRecoveryMs();
                                default -> 0;
                            };
                        }
                        if (totalMs <= 0 && phases.isEmpty()) {
                            totalMs = entry.getWindupMs() + entry.getJumpMs() + entry.getDurationMs() + entry.getRecoveryMs();
                        }
                        double totalW = Math.max(8.0, (totalMs / 1000.0) * pixelsPerSecond);

                        if (entryX + totalW >= timelineTrackLeft && entryX <= timelineLeft + timelineWidth) {
                            int bx = (int) entryX;
                            int bw = (int) totalW;
                            int barY = entryTrackY + 4;
                            int barHeight = channelHeight - 8;

                            boolean isHovered = (scaledMouseX >= bx - 4 && scaledMouseX <= bx + bw + 4 &&
                                    scaledMouseY >= entryTrackY && scaledMouseY <= entryTrackY + channelHeight);

                            // Selection Aura
                            if (isSelected) {
                                long now = System.currentTimeMillis();
                                int auraColor = (now / 300) % 2 == 0 ? 0xFFFFFFFF : CYAN_MAIN;
                                guiGraphics.fill(bx - 3, barY - 2, bx + bw + 3, barY + barHeight + 2, auraColor);
                            }

                            // Background
                            guiGraphics.fill(bx, barY, bx + bw, barY + barHeight, 0xEE060C12);

                            int curX = bx;
                            int execX = curX;

                            for (int p = 0; p < phases.size(); p++) {
                                ActionTimingPhase phase = phases.get(p);
                                int ms = entry.getPhaseMs(phase, p);
                                int pw = (int) Math.round((ms / 1000.0) * pixelsPerSecond);
                                int clr = phase.color();
                                String pLabel = phase.label().toUpperCase(Locale.ROOT);
                                boolean isExec = phase.isExecutionPoint();

                                if (isExec) {
                                    execX = curX;
                                }

                                int pEnd = (p == phases.size() - 1) ? (bx + bw) : (curX + pw);

                                if (pw > 0) {
                                    guiGraphics.fill(curX, barY + 1, pEnd, barY + barHeight - 1, clr);

                                    // Left notch on first phase
                                    if (p == 0) {
                                        guiGraphics.fill(curX, barY, curX + 2, barY + barHeight, clr | 0x00333333);
                                    } else {
                                        // Divider line
                                        guiGraphics.fill(curX, barY, curX + 1, barY + barHeight, 0x55FFFFFF);
                                    }

                                    int textClr = isLightColor(clr) ? 0xFF000000 : 0xFFFFFFFF;
                                    String fullTxt = pLabel + " (" + ms + "ms)";
                                    int wAvail = pEnd - curX;
                                    if (wAvail >= this.font.width(fullTxt) + 6) {
                                        guiGraphics.drawCenteredString(this.font, fullTxt, curX + (wAvail / 2), barY + 3, textClr);
                                    } else if (wAvail >= this.font.width(ms + "ms") + 4) {
                                        guiGraphics.drawCenteredString(this.font, ms + "ms", curX + (wAvail / 2), barY + 3, textClr);
                                    }
                                }

                                curX = pEnd;
                            }

                            // Interactive Phase Divider Handles & Tooltips ("Time Zones")
                            int hoveredBoundaryIndex = -1;
                            String hoveredBoundaryTooltip = null;
                            int hoveredBoundaryColor = 0xFF00E5FF;

                            int scanX = bx;
                            for (int p = 0; p < phases.size(); p++) {
                                ActionTimingPhase phase = phases.get(p);
                                int ms = entry.getPhaseMs(phase, p);
                                int pw = (int) Math.round((ms / 1000.0) * pixelsPerSecond);
                                int pEnd = (p == phases.size() - 1) ? (bx + bw) : (scanX + pw);

                                boolean isThisDividerHovered = (scaledMouseX >= pEnd - 4 && scaledMouseX <= pEnd + 4 &&
                                        scaledMouseY >= barY - 2 && scaledMouseY <= barY + barHeight + 2);
                                boolean isThisDividerDragging = (isDraggingPuppetPhase && puppetDraggingEntry == entry && puppetDraggingPhaseIndex == p);

                                // Render handle grip styling
                                if (p < phases.size() - 1) {
                                    // Subtle grip notch on intermediate phase boundaries
                                    guiGraphics.fill(pEnd - 1, barY + 1, pEnd, barY + 4, 0x88FFFFFF);
                                    guiGraphics.fill(pEnd - 1, barY + barHeight - 4, pEnd, barY + barHeight - 1, 0x88FFFFFF);
                                } else {
                                    // Right edge resize grip handle
                                    guiGraphics.fill(pEnd - 3, barY + 3, pEnd - 1, barY + barHeight - 3, 0xAA00E5FF);
                                }

                                if (isThisDividerHovered || isThisDividerDragging) {
                                    hoveredBoundaryIndex = p;
                                    int handleColor = isThisDividerDragging ? 0xFF00FF88 : 0xFF00E5FF;
                                    hoveredBoundaryColor = handleColor;

                                    // Glowing handle line
                                    guiGraphics.fill(pEnd - 1, barY - 2, pEnd + 1, barY + barHeight + 2, 0xFFFFFFFF);
                                    guiGraphics.fill(pEnd - 3, barY - 3, pEnd + 3, barY, handleColor);
                                    guiGraphics.fill(pEnd - 3, barY + barHeight, pEnd + 3, barY + barHeight + 3, handleColor);

                                    // Center grip notch
                                    int cy = barY + (barHeight / 2);
                                    guiGraphics.fill(pEnd - 3, cy - 4, pEnd + 3, cy + 4, 0xEE060C14);
                                    guiGraphics.fill(pEnd - 2, cy - 3, pEnd + 2, cy + 3, handleColor);
                                    guiGraphics.fill(pEnd - 1, cy - 2, pEnd + 1, cy + 2, 0xFFFFFFFF);

                                    String modeTag = hasShiftDown() ? " [Split Dual-Edit]" : " [Ripple Edit]";
                                    if (p < phases.size() - 1) {
                                        ActionTimingPhase nextP = phases.get(p + 1);
                                        hoveredBoundaryTooltip = phase.label().toUpperCase(Locale.ROOT) + " (" + ms + "ms) ↔ " + nextP.label().toUpperCase(Locale.ROOT) + modeTag;
                                    } else {
                                        hoveredBoundaryTooltip = phase.label().toUpperCase(Locale.ROOT) + ": " + ms + "ms (Resize End)" + modeTag;
                                    }
                                }

                                scanX = pEnd;
                            }

                            // Floating tooltip badge above active phase divider
                            if (hoveredBoundaryTooltip != null && hoveredBoundaryIndex >= 0) {
                                int tipW = this.font.width(hoveredBoundaryTooltip);
                                int bX = bx;
                                for (int p = 0; p <= hoveredBoundaryIndex; p++) {
                                    int ms = entry.getPhaseMs(phases.get(p), p);
                                    int pw = (int) Math.round((ms / 1000.0) * pixelsPerSecond);
                                    bX = (p == phases.size() - 1) ? (bx + bw) : (bX + pw);
                                }
                                int tipX = bX - (tipW / 2);
                                tipX = Math.max(timelineTrackLeft + 2, Math.min(timelineLeft + timelineWidth - tipW - 4, tipX));
                                int tipY = barY - 14;
                                guiGraphics.fill(tipX - 3, tipY - 1, tipX + tipW + 3, tipY + 9, 0xEE060C14);
                                guiGraphics.fill(tipX - 3, tipY - 1, tipX + tipW + 3, tipY, hoveredBoundaryColor);
                                guiGraphics.drawString(this.font, hoveredBoundaryTooltip, tipX, tipY, 0xFFFFFFFF, false);
                            }

                            // Bar Outer Border
                            int borderColor = isHovered ? 0xFFFFFFFF : (isSelected ? 0xFFFFFFFF : CARD_BORDER);
                            guiGraphics.fill(bx, barY, bx + bw, barY + 1, borderColor);
                            guiGraphics.fill(bx, barY + barHeight - 1, bx + bw, barY + barHeight, borderColor);
                            guiGraphics.fill(bx + bw - 1, barY, bx + bw, barY + barHeight, borderColor);

                            // KEYFRAME SQUARE RIGHT WHEN ATTACK IS EXECUTED
                            int barCenterY = barY + (barHeight / 2);
                            guiGraphics.fill(execX - 1, barY - 3, execX + 1, barY + barHeight + 3, 0xFFFFFFFF);
                            guiGraphics.fill(execX - 5, barCenterY - 5, execX + 5, barCenterY + 5, 0xFF000000);
                            int sqBorder = isSelected ? 0xFFFFFFFF : 0xFFFFD700;
                            guiGraphics.fill(execX - 4, barCenterY - 4, execX + 4, barCenterY + 4, sqBorder);
                            guiGraphics.fill(execX - 3, barCenterY - 3, execX + 3, barCenterY + 3, 0xFFFF0055);
                            guiGraphics.fill(execX - 1, barCenterY - 1, execX + 1, barCenterY + 1, 0xFFFFFFFF);

                            // Impact timestamp badge
                            if (entry.getWindupMs() > 0 || entry.getJumpMs() > 0) {
                                long impactTimeMs = entry.getTimestampMs() + entry.getWindupMs() + entry.getJumpMs();
                                long sSec = impactTimeMs / 1000;
                                long sRem = impactTimeMs % 1000;
                                String impactTxt = String.format("%02d:%02d.%03d", sSec / 60, sSec % 60, sRem);
                                int impactTxtW = this.font.width(impactTxt);
                                int badgeX = execX - (impactTxtW / 2);
                                if (badgeX >= timelineTrackLeft && badgeX + impactTxtW <= timelineLeft + timelineWidth) {
                                    guiGraphics.fill(badgeX - 2, barY - 11, badgeX + impactTxtW + 2, barY - 1, 0xEE060C12);
                                    guiGraphics.drawString(this.font, impactTxt, badgeX, barY - 10, isSelected ? 0xFFFF0055 : 0xFFFFCC00, false);
                                }
                            }

                            // Start notch at bx if windup > 0
                            if (entry.getWindupMs() > 0) {
                                guiGraphics.fill(bx - 1, barY - 2, bx + 1, barY + barHeight + 2, 0xFFFFCC00);
                            }

                            // Label Badge after bar
                            String actionName = entry.getSubAction().isEmpty() ? entry.getActionType() : entry.getSubAction();
                            if (entry.getDescription() != null && !entry.getDescription().isBlank()) {
                                actionName = entry.getDescription();
                            }
                            String label = actionName + " (" + totalMs + "ms)";
                            if (this.font.width(label) > 140) {
                                label = this.font.plainSubstrByWidth(label, 134) + "..";
                            }
                            guiGraphics.drawString(this.font, label, bx + bw + 6, barY + 3, isSelected ? 0xFFFFFFFF : chColor, false);
                        }
                    }
                } else {
                    // Instant single keyframe node
                    if (entryX >= timelineTrackLeft - 30 && entryX <= timelineLeft + timelineWidth + 30) {
                        int kx = (int) entryX;
                        int ky = entryTrackY + (channelHeight / 2);

                        boolean isDespawn = isPuppet && "DESPAWN".equals(pSub);
                        boolean isSpawn = isPuppet && "SPAWN".equals(pSub);
                        boolean isToggleAi = isPuppet && "TOGGLE_AI".equals(pSub);

                        int baseNodeColor = chColor;
                        String badgeText = "";
                        int badgeColor = chColor;

                        if (isDespawn) {
                            baseNodeColor = 0xFFFF3355;
                            badgeColor = 0xFFFF5566;
                            String tag = entry.getPuppetParam("actorTag", "");
                            badgeText = tag.isEmpty() ? "✕ DESPAWN" : "✕ DESPAWN (#" + tag + ")";
                        } else if (isSpawn) {
                            baseNodeColor = 0xFF00FF88;
                            badgeColor = 0xFF00FF88;
                            String tag = entry.getPuppetParam("actorTag", "");
                            badgeText = tag.isEmpty() ? "★ SPAWN" : "★ SPAWN (#" + tag + ")";
                        } else if (isToggleAi) {
                            baseNodeColor = 0xFFFFD700;
                            badgeColor = 0xFFFFE066;
                            String aiState = entry.getPuppetParam("aiState", "FREEZE");
                            badgeText = "FREEZE".equalsIgnoreCase(aiState) ? "⚡ FREEZE AI" : "⚡ RESTORE AI";
                        } else {
                            if (entry.getDescription() != null && !entry.getDescription().isBlank()) {
                                badgeText = entry.getDescription();
                            } else if (entry.getCommand() != null && !entry.getCommand().isBlank()) {
                                badgeText = entry.getCommand();
                            }
                        }

                        int bw = !badgeText.isEmpty() ? this.font.width(badgeText) : 0;
                        boolean isHovered = (scaledMouseX >= kx - 8 && scaledMouseX <= kx + Math.max(16, bw + 14) &&
                                scaledMouseY >= entryTrackY && scaledMouseY <= entryTrackY + channelHeight);
                        int nodeColor = (isHovered || isSelected) ? 0xFFFFFFFF : baseNodeColor;

                        if (isSelected) {
                            long now = System.currentTimeMillis();
                            int auraColor = (now / 300) % 2 == 0 ? 0xFFFFFFFF : baseNodeColor;
                            guiGraphics.fill(kx - 8, ky - 8, kx + 8, ky + 8, auraColor);
                        } else if (isHovered) {
                            guiGraphics.fill(kx - 7, ky - 7, kx + 7, ky + 7, 0x88FFFFFF);
                        }

                        if (isDespawn) {
                            guiGraphics.fill(kx - 6, ky - 6, kx + 6, ky + 6, 0xEE1A050B);
                            guiGraphics.fill(kx - 5, ky - 5, kx + 5, ky + 5, nodeColor);
                            guiGraphics.fill(kx - 3, ky - 3, kx + 3, ky + 3, 0xFF000000);
                            guiGraphics.fill(kx - 1, ky - 1, kx + 2, ky + 2, 0xFFFF3355);
                        } else if (isSpawn) {
                            guiGraphics.fill(kx - 6, ky - 6, kx + 6, ky + 6, 0xEE051A0E);
                            guiGraphics.fill(kx - 5, ky - 5, kx + 5, ky + 5, nodeColor);
                            guiGraphics.fill(kx - 3, ky - 3, kx + 3, ky + 3, 0xFF000000);
                            guiGraphics.fill(kx - 1, ky - 1, kx + 2, ky + 2, 0xFF00FF88);
                        } else {
                            guiGraphics.fill(kx - 6, ky - 6, kx + 6, ky + 6, 0xEE060C12);
                            guiGraphics.fill(kx - 5, ky - 5, kx + 5, ky + 5, nodeColor);
                            guiGraphics.fill(kx - 3, ky - 3, kx + 3, ky + 3, 0xFF000000);
                            guiGraphics.fill(kx - 1, ky - 1, kx + 2, ky + 2, 0xFFFFFFFF);
                        }

                        // Badge text & timestamp
                        if (!badgeText.isEmpty()) {
                            int badgeX = kx + 9;
                            int badgeY = ky - 8;
                            int badgeBg = isDespawn ? 0xEE1E050A : (isSpawn ? 0xEE051E0E : 0xEE060C14);
                            guiGraphics.fill(badgeX - 3, badgeY - 1, badgeX + bw + 3, badgeY + 9, badgeBg);
                            guiGraphics.fill(badgeX - 3, badgeY - 1, badgeX + bw + 3, badgeY, isHovered || isSelected ? 0xFFFFFFFF : (badgeColor & 0x88FFFFFF));
                            guiGraphics.drawString(this.font, badgeText, badgeX, badgeY, isSelected ? 0xFFFFFFFF : badgeColor, false);
                        }

                        // Timestamp below node
                        long sec = entry.getTimestampMs() / 1000;
                        long msRem = entry.getTimestampMs() % 1000;
                        String timeBadge = String.format(Locale.ROOT, "%02d:%02d.%02d", sec / 60, sec % 60, msRem / 10);
                        int timeX = kx + 9;
                        int timeY = !badgeText.isEmpty() ? ky + 2 : ky - 4;
                        guiGraphics.drawString(this.font, timeBadge, timeX, timeY, isSelected ? 0xFFFFFFFF : 0xFF88AACC, false);
                    }
                }
            }
        }

        // Disable Band 2 (Channel Tracks Area) Scissor
        guiGraphics.disableScissor();

        // ==========================================
        // Band 3: PINNED Audio Waveform Visualization Track
        // ==========================================
        guiGraphics.enableScissor(timelineTrackLeft, waveTopY, timelineLeft + timelineWidth, trackAreaY);
        guiGraphics.fill(timelineTrackLeft, waveTopY, timelineLeft + timelineWidth, trackAreaY, 0xEE05101A);
        guiGraphics.fill(timelineTrackLeft, trackAreaY - 1, timelineLeft + timelineWidth, trackAreaY, 0x6600E5FF);

        MusicWaveformRenderer.renderWaveform(guiGraphics, currentSequence.getSongTrack(), timelineTrackLeft, waveTopY, timelineTrackWidth, waveformHeight, timeScrollMs, pixelsPerSecond);

        // Beat Lines & Rekordbox Red Bar Lines with Bar Numbers across Waveform Track
        for (double b = firstBeat; b <= lastBeat; b++) {
            double bMs = b * beatMs;
            double beatX = timelineTrackLeft + ((bMs - timeScrollMs) / 1000.0) * pixelsPerSecond;

            if (beatX >= timelineTrackLeft && beatX <= timelineLeft + timelineWidth) {
                long beatIndex = Math.round(b);
                boolean isBarStart = (beatIndex % 4 == 0);

                if (isBarStart) {
                    guiGraphics.fill((int) beatX - 1, waveTopY, (int) beatX + 1, trackAreaY, 0xFFFF0055);
                    long barNumber = (beatIndex / 4) + 1;
                    if (barNumber > 0) {
                        guiGraphics.drawString(this.font, "B" + barNumber, (int) beatX + 3, waveTopY + 2, 0xFFFF0055, false);
                    }
                } else {
                    guiGraphics.fill((int) beatX, waveTopY, (int) beatX + 1, trackAreaY, 0x3300E5FF);
                }
            }
        }
        guiGraphics.disableScissor();

        // ==========================================
        // Band 4: Overlays (Ruler/Waveform Dimming, Markers, Playhead, Scrollbar)
        // ==========================================
        guiGraphics.enableScissor(timelineTrackLeft, rulerY, timelineLeft + timelineWidth, canvasBottom);

        // Dimmed Region BEFORE START across Ruler & Waveform
        if (startX > timelineTrackLeft) {
            int dimW = (int) Math.min((double) timelineTrackWidth, startX - timelineTrackLeft);
            if (dimW > 0) {
                guiGraphics.fill(timelineTrackLeft, rulerY, timelineTrackLeft + dimW, trackAreaY, 0xCC050A10);
                guiGraphics.drawString(this.font, "BEFORE START", timelineTrackLeft + 6, rulerY + 4, 0xAA888888, false);
            }
        }

        // Dimmed Region AFTER END across Ruler & Waveform
        if (endX < timelineLeft + timelineWidth) {
            int dimX = (int) Math.max((double) timelineTrackLeft, endX);
            int dimW = (timelineLeft + timelineWidth) - dimX;
            if (dimW > 0) {
                guiGraphics.fill(dimX, rulerY, dimX + dimW, trackAreaY, 0xCC050A10);
                guiGraphics.drawString(this.font, "AFTER END", dimX + 6, rulerY + 4, 0xAA888888, false);
            }
        }

        // START Line & Flag Badge
        if (startX >= timelineTrackLeft && startX <= timelineLeft + timelineWidth) {
            int sx = (int) startX;
            guiGraphics.fill(sx - 1, rulerY, sx + 2, canvasBottom, 0xFF00FF88);
            guiGraphics.fill(sx - 14, rulerY, sx + 14, rulerY + 14, 0xEE00FF88);
            guiGraphics.drawCenteredString(this.font, "IN", sx, rulerY + 3, 0xFF000000);
        }

        // END Line & Flag Badge
        if (endX >= timelineTrackLeft && endX <= timelineLeft + timelineWidth) {
            int ex = (int) endX;
            guiGraphics.fill(ex - 1, rulerY, ex + 2, canvasBottom, 0xFFFF3366);
            guiGraphics.fill(ex - 16, rulerY, ex + 16, rulerY + 14, 0xEEFF3366);
            guiGraphics.drawCenteredString(this.font, "OUT", ex, rulerY + 3, 0xFFFFFFFF);
        }

        // Playhead Scrubber Line
        double playheadX = timelineTrackLeft + ((playheadMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
        if (playheadX >= timelineTrackLeft && playheadX <= timelineLeft + timelineWidth) {
            int px = (int) playheadX;
            guiGraphics.fill(px - 1, rulerY, px + 2, canvasBottom, PLAYHEAD_COLOR);

            // Playhead Handle Cap sitting at top of the ruler
            guiGraphics.fill(px - 5, rulerY, px + 6, rulerY + 10, PLAYHEAD_COLOR);
            guiGraphics.fill(px - 4, rulerY + 1, px + 5, rulerY + 9, 0xFFFFFFFF);
        }

        // Vertical Scrollbar on Right edge of Timeline Track Area
        if (maxScrollY > 0) {
            int scrollTrackH = channelAreaHeight - 4;
            int thumbH = Math.max(16, (int) ((double) channelAreaHeight / totalChannelContentH * scrollTrackH));
            int thumbY = trackAreaY + 2 + (int) ((channelScrollY / maxScrollY) * (scrollTrackH - thumbH));
            int barX = timelineLeft + timelineWidth - 4;
            guiGraphics.fill(barX, trackAreaY + 2, barX + 3, trackAreaY + 2 + scrollTrackH, 0x3300E5FF);
            guiGraphics.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0xAA00E5FF);
        }

        guiGraphics.disableScissor();

        // Vertical divider line separating Left Track Headers and Timeline Tracks
        guiGraphics.fill(timelineTrackLeft - 1, canvasY, timelineTrackLeft, canvasBottom, CARD_BORDER);

        int widgetMouseX = (activeModal != ModalType.NONE) ? -1000 : scaledMouseX;
        int widgetMouseY = (activeModal != ModalType.NONE) ? -1000 : scaledMouseY;
        super.render(guiGraphics, widgetMouseX, widgetMouseY, partialTick);

        if (!isRightPanelCollapsed && this.rightPanel != null) {
            this.rightPanel.render(guiGraphics, widgetMouseX, widgetMouseY, partialTick);
        }

        if (activeModal == ModalType.NONE) {
            if (this.fileDropdown != null) {
                this.fileDropdown.renderOverlay(guiGraphics, scaledMouseX, scaledMouseY);
            }
            if (this.trackDropdown != null) {
                this.trackDropdown.renderOverlay(guiGraphics, scaledMouseX, scaledMouseY);
            }
            if (!isRightPanelCollapsed && this.rightPanel != null) {
                this.rightPanel.renderOverlay(guiGraphics, widgetMouseX, widgetMouseY);
            }

            // [+SUB] Tooltip on Track Header
            if (scaledMouseX >= trackHeaderLeft && scaledMouseX < timelineTrackLeft) {
                for (int c = 0; c < channels.size(); c++) {
                    int trackY = trackAreaY + (c * channelHeight) - (int) channelScrollY;
                    if (trackY >= trackAreaY && trackY + channelHeight <= canvasBottom) {
                        MusicSequenceChannel ch = channels.get(c);
                        if (MusicSequenceChannel.TYPE_PUPPET.equalsIgnoreCase(ch.getType())) {
                            int subL = trackHeaderWidth < 90 ? (timelineTrackLeft - 38) : (timelineTrackLeft - 50);
                            int subR = trackHeaderWidth < 90 ? (timelineTrackLeft - 20) : (timelineTrackLeft - 25);
                            if (scaledMouseX >= subL && scaledMouseX <= subR && scaledMouseY >= trackY + 4 && scaledMouseY <= trackY + channelHeight - 4) {
                                guiGraphics.renderTooltip(this.font, Component.literal("Add Sub-Channel for this puppet entity (allows simultaneous actions)"), scaledMouseX, scaledMouseY);
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (activeModal != ModalType.NONE) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 800.0f);
            if (scale < 0.999) {
                guiGraphics.pose().scale((float) scale, (float) scale, 1.0f);
            }
            int effWidth = (int) (this.width / scale);
            int effHeight = (int) (this.height / scale);
            int modalMouseX = (int) (mouseX / scale);
            int modalMouseY = (int) (mouseY / scale);
            renderActiveModal(guiGraphics, modalMouseX, modalMouseY, partialTick, effWidth, effHeight);
            guiGraphics.pose().popPose();
        }
    }

    public void toggleCameraMode() {
        SequencerViewportManager.toggleMode();
        updatePreviewCamera();
        this.init();
    }

    public void updatePreviewCamera() {
        if (currentSequence == null) {
            cachedCameraTrack = null;
            return;
        }
        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        List<MusicSequenceEntry> cameraEntries = new ArrayList<>();

        for (MusicSequenceEntry e : currentSequence.getEntries()) {
            boolean isCamera = e.isUseCamera() || "CAMERA".equalsIgnoreCase(e.getActionType());
            if (!isCamera) {
                int chIdx = getChannelIndex(e);
                if (chIdx >= 0 && chIdx < channels.size()) {
                    if (MusicSequenceChannel.TYPE_CAMERA.equalsIgnoreCase(channels.get(chIdx).getType())) {
                        isCamera = true;
                    }
                }
            }
            if (isCamera) {
                cameraEntries.add(e);
            }
        }

        if (cameraEntries.isEmpty()) {
            cachedCameraTrack = null;
            if (SequencerViewportManager.getCurrentMode() == SequencerViewportManager.CameraMode.WORK_CAMERA) {
                SequencerViewportManager.applyCameraState();
            } else if (isPreviewCameraActive) {
                CustomCameraManager.clearCustomCamera();
                isPreviewCameraActive = false;
            }
            return;
        }

        cachedCameraTrack = CameraSequenceTrack.compile(cameraEntries, currentSequence.isLooping(), currentSequence.getStartMs(), currentSequence.getEndMs());

        // In Work Camera mode, ensure the interactive flight/pan camera state is applied and do NOT overwrite
        if (SequencerViewportManager.getCurrentMode() == SequencerViewportManager.CameraMode.WORK_CAMERA) {
            SequencerViewportManager.applyCameraState();
            return;
        }

        if (!cameraPreviewEnabled) {
            if (isPreviewCameraActive) {
                CustomCameraManager.clearCustomCamera();
                isPreviewCameraActive = false;
            }
            return;
        }

        CameraTransform frame = cachedCameraTrack.evaluate(playheadMs, 1.0f, Minecraft.getInstance());

        if (frame != null) {
            CustomCameraManager.setCustomTransform(frame, true);
            isPreviewCameraActive = true;
        } else {
            if (isPreviewCameraActive) {
                CustomCameraManager.clearCustomCamera();
                isPreviewCameraActive = false;
            }
        }
    }

    public void updatePreviewScreenEffects() {
        if (!isPreviewScreenEffects || currentSequence == null) {
            if (!ClientScreenEffectHandler.getActiveEffects().isEmpty()) {
                ClientScreenEffectHandler.clearAllEffects();
            }
            return;
        }

        List<MusicSequenceEntry> activeFxEntries = new ArrayList<>();
        for (MusicSequenceEntry e : currentSequence.getEntries()) {
            boolean isScreenEffect = "SCREEN_EFFECT".equalsIgnoreCase(e.getActionType()) ||
                    isChannelType(e, MusicSequenceChannel.TYPE_SCREEN_EFFECT);
            if (!isScreenEffect) continue;

            long start = e.getTimestampMs();
            int dur = e.getTotalDurationMs() > 0 ? e.getTotalDurationMs() : (e.getDurationMs() > 0 ? e.getDurationMs() : 1000);
            long end = start + dur;

            if (playheadMs >= start && playheadMs < end) {
                activeFxEntries.add(e);
            }
        }

        if (activeFxEntries.isEmpty()) {
            if (!ClientScreenEffectHandler.getActiveEffects().isEmpty()) {
                ClientScreenEffectHandler.clearAllEffects();
            }
            return;
        }

        Set<ResourceLocation> targetTypes = new HashSet<>();
        for (MusicSequenceEntry entry : activeFxEntries) {
            long start = entry.getTimestampMs();
            int dur = entry.getTotalDurationMs() > 0 ? entry.getTotalDurationMs() : (entry.getDurationMs() > 0 ? entry.getDurationMs() : 1000);
            long elapsedMs = Math.max(0, (long) (playheadMs - start));

            ScreenEffectInstance inst = createEffectInstanceForEntry(entry, dur);
            if (inst != null) {
                ResourceLocation typeId = inst.getType().getId();
                targetTypes.add(typeId);

                ScreenEffectInstance existing = ClientScreenEffectHandler.getActiveEffect(typeId);
                if (existing != null) {
                    existing.setStartTimeMs(System.currentTimeMillis() - elapsedMs);
                    existing.setDurationMs(dur);
                } else {
                    inst.setStartTimeMs(System.currentTimeMillis() - elapsedMs);
                    ClientScreenEffectHandler.playEffect(inst);
                }
            }
        }

        // Clean up effects that are no longer active at current playhead
        for (ScreenEffectInstance inst : ClientScreenEffectHandler.getActiveEffects()) {
            if (!targetTypes.contains(inst.getType().getId())) {
                ClientScreenEffectHandler.stopEffect(inst.getType().getId());
            }
        }
    }

    public ScreenEffectInstance createEffectInstanceForEntry(MusicSequenceEntry entry, int duration) {
        if (entry == null) return null;
        String mode = entry.getSubAction() != null && !entry.getSubAction().isBlank() ? entry.getSubAction().toUpperCase(Locale.ROOT) : "SCREEN_SHAKE";
        String effId = entry.getScreenEffectId();
        if (effId != null && (entry.getSubAction() == null || entry.getSubAction().isBlank())) {
            if (effId.contains("cinematic") || effId.contains("bars")) mode = "CINEMATIC_BARS";
            else if (effId.contains("strobe")) mode = "STROBE";
            else if (effId.contains("hue")) mode = "HUE_SHIFT";
            else if (effId.contains("impact")) mode = "IMPACT_FRAME";
            else if (effId.contains("invert")) mode = "INVERT_COLORS";
            else mode = "SCREEN_SHAKE";
        }

        if ("INVERT_COLORS".equalsIgnoreCase(mode)) {
            return new InvertColorsEffect.InvertColorsInstance(duration, entry.isScreenEffectPulse(), entry.getScreenEffectFrequency());
        } else if ("STROBE".equalsIgnoreCase(mode)) {
            float freq = entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 10.0f;
            int clr = entry.getScreenEffectColor() != 0 ? entry.getScreenEffectColor() : 0xFFFFFFFF;
            float alpha = entry.getScreenEffectMaxAlpha() > 0 ? entry.getScreenEffectMaxAlpha() : 0.85f;
            return new StrobeEffect.StrobeInstance(duration, freq, clr, entry.isScreenEffectSmooth(), alpha);
        } else if ("HUE_SHIFT".equalsIgnoreCase(mode)) {
            float freq = entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 0.5f;
            float intensity = entry.getScreenEffectIntensity() > 0 ? entry.getScreenEffectIntensity() : 1.0f;
            return new HueShiftEffect.HueShiftInstance(duration, freq, entry.isScreenEffectContinuous(), entry.getScreenEffectAngle(), intensity);
        } else if ("IMPACT_FRAME".equalsIgnoreCase(mode)) {
            int pColor = entry.getScreenEffectColor() != 0 ? entry.getScreenEffectColor() : 0xFFFFFFFF;
            int sColor = entry.getScreenEffectSecondaryColor() != 0 ? entry.getScreenEffectSecondaryColor() : 0xFF000000;
            float freq = entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 28.0f;
            int interval = (int) (1000.0f / freq);
            String style = entry.getScreenEffectStyle() != null && !entry.getScreenEffectStyle().isBlank() ? entry.getScreenEffectStyle() : "NOIR";
            return new ImpactFrameEffect.ImpactFrameInstance(duration, pColor, sColor, interval, entry.isScreenEffectPulse(), style);
        } else if ("CINEMATIC_BARS".equalsIgnoreCase(mode)) {
            int inMs = entry.getScreenEffectTransitionInMs() > 0 ? entry.getScreenEffectTransitionInMs() : 500;
            int outMs = entry.getScreenEffectTransitionOutMs() > 0 ? entry.getScreenEffectTransitionOutMs() : 500;
            float barH = entry.getScreenEffectBarHeight() > 0 ? entry.getScreenEffectBarHeight() : 0.125f;
            int clr = entry.getScreenEffectColor() != 0 ? entry.getScreenEffectColor() : 0xFF000000;
            return new CinematicBarsEffect.CinematicBarsInstance(duration, inMs, outMs, barH, clr);
        } else {
            float intensity = entry.getScreenEffectIntensity() > 0 ? entry.getScreenEffectIntensity() : 2.0f;
            float freq = entry.getScreenEffectFrequency() > 0 ? entry.getScreenEffectFrequency() : 20.0f;
            return new ScreenShakeEffect.ScreenShakeInstance(duration, intensity, freq, entry.isScreenEffectDecay());
        }
    }

    public void stampCameraKeyframeAtPlayhead() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. Find or create a Camera channel
        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        MusicSequenceChannel cameraChannel = null;
        for (MusicSequenceChannel ch : channels) {
            if (MusicSequenceChannel.TYPE_CAMERA.equalsIgnoreCase(ch.getType())) {
                cameraChannel = ch;
                break;
            }
        }
        if (cameraChannel == null) {
            cameraChannel = new MusicSequenceChannel(
                    java.util.UUID.randomUUID().toString(),
                    "Camera Track",
                    MusicSequenceChannel.TYPE_CAMERA,
                    null,
                    MusicSequenceChannel.COLOR_CAMERA
            );
            channels.add(cameraChannel);
        }

        boolean isWorkCam = (SequencerViewportManager.getCurrentMode() == SequencerViewportManager.CameraMode.WORK_CAMERA);
        Vec3 eye = isWorkCam ? SequencerViewportManager.getWorkCameraPos() : mc.player.getEyePosition();
        float yaw = isWorkCam ? SequencerViewportManager.getWorkCameraYaw() : mc.player.getYRot();
        float pitch = isWorkCam ? SequencerViewportManager.getWorkCameraPitch() : mc.player.getXRot();
        float roll = isWorkCam ? SequencerViewportManager.getWorkCameraRoll() : 0.0f;
        double fov = isWorkCam ? SequencerViewportManager.getWorkCameraFov() : (CustomCameraManager.isFovActive() ? CustomCameraManager.getCustomFov() : 70.0);
        long targetTs = (long) Math.round(playheadMs);

        // Check if an entry already exists at this timestamp on camera channel
        MusicSequenceEntry targetEntry = null;
        int camEntryCount = 0;
        for (MusicSequenceEntry e : currentSequence.getEntries()) {
            boolean isCam = e.isUseCamera() || "CAMERA".equalsIgnoreCase(e.getActionType()) ||
                    (e.getChannelId() != null && e.getChannelId().equalsIgnoreCase(cameraChannel.getId()));
            if (isCam) {
                camEntryCount++;
                if (Math.abs(e.getTimestampMs() - targetTs) <= 25) { // Snapped match
                    targetEntry = e;
                }
            }
        }

        if (targetEntry != null) {
            // Update existing keyframe coordinates and angles
            targetEntry.setCameraX(eye.x);
            targetEntry.setCameraY(eye.y);
            targetEntry.setCameraZ(eye.z);
            targetEntry.setCameraYaw(yaw);
            targetEntry.setCameraPitch(pitch);
            targetEntry.setCameraRoll(roll);
            targetEntry.setCameraFov(fov);
            targetEntry.setUseCamera(true);
            saveFeedbackMessage = String.format(Locale.ROOT, "Updated Camera Keyframe at %.3fs", targetTs / 1000.0);
            setSelectedEntryIndex(currentSequence.getEntries().indexOf(targetEntry));
        } else {
            // Create brand new keyframe
            MusicSequenceEntry newKf = new MusicSequenceEntry(
                    targetTs,
                    MusicSequenceChannel.TYPE_CAMERA,
                    cameraChannel.getId(),
                    "",
                    "Keyframe " + (camEntryCount + 1)
            );

            // Find previous camera keyframe on the timeline
            MusicSequenceEntry prevCam = null;
            for (MusicSequenceEntry e : currentSequence.getEntries()) {
                boolean isCam = e.isUseCamera() || "CAMERA".equalsIgnoreCase(e.getActionType()) ||
                        (e.getChannelId() != null && e.getChannelId().equalsIgnoreCase(cameraChannel.getId()));
                if (isCam && e.getTimestampMs() <= targetTs) {
                    if (prevCam == null || e.getTimestampMs() >= prevCam.getTimestampMs()) {
                        prevCam = e;
                    }
                }
            }
            if (prevCam == null) {
                for (MusicSequenceEntry e : currentSequence.getEntries()) {
                    boolean isCam = e.isUseCamera() || "CAMERA".equalsIgnoreCase(e.getActionType()) ||
                            (e.getChannelId() != null && e.getChannelId().equalsIgnoreCase(cameraChannel.getId()));
                    if (isCam) {
                        prevCam = e;
                        break;
                    }
                }
            }

            newKf.setCameraX(eye.x);
            newKf.setCameraY(eye.y);
            newKf.setCameraZ(eye.z);
            newKf.setCameraYaw(yaw);
            newKf.setCameraPitch(pitch);
            newKf.setCameraRoll(roll);
            newKf.setCameraFov(fov);

            if (prevCam != null) {
                newKf.setCameraMode(prevCam.getCameraMode() != null ? prevCam.getCameraMode() : "KEYFRAME");
                newKf.setSplineMode(prevCam.getSplineMode() != null ? prevCam.getSplineMode() : "CATMULL_ROM");
                newKf.setCameraEasing(prevCam.getCameraEasing() != null ? prevCam.getCameraEasing() : "EASE_IN_OUT_CUBIC");
                newKf.setCameraInterpolate(prevCam.isCameraInterpolate());
                newKf.setCameraTarget(prevCam.getCameraTarget());
                newKf.setCameraHeightOffset(prevCam.getCameraHeightOffset());
                newKf.setCameraBackDistance(prevCam.getCameraBackDistance());
                newKf.setCameraShoulderOffset(prevCam.getCameraShoulderOffset());
                newKf.setInHandleX(prevCam.getInHandleX());
                newKf.setInHandleY(prevCam.getInHandleY());
                newKf.setInHandleZ(prevCam.getInHandleZ());
                newKf.setOutHandleX(prevCam.getOutHandleX());
                newKf.setOutHandleY(prevCam.getOutHandleY());
                newKf.setOutHandleZ(prevCam.getOutHandleZ());
                newKf.setLookAtEnabled(prevCam.isLookAtEnabled());
                newKf.setLookAtMode(prevCam.getLookAtMode());
                newKf.setLookAtX(prevCam.getLookAtX());
                newKf.setLookAtY(prevCam.getLookAtY());
                newKf.setLookAtZ(prevCam.getLookAtZ());
                newKf.setLookAtTarget(prevCam.getLookAtTarget());
                newKf.setLookAtWeight(prevCam.getLookAtWeight());
            } else {
                newKf.setCameraMode("KEYFRAME");
                newKf.setSplineMode("CATMULL_ROM");
                newKf.setCameraEasing("EASE_IN_OUT_CUBIC");
                newKf.setCameraInterpolate(true);
            }
            newKf.setDurationMs(0);
            newKf.setUseCamera(true);
            newKf.setSubAction(newKf.getCameraMode() != null ? newKf.getCameraMode() : "KEYFRAME");
            newKf.setDescription(String.format(Locale.US, "Camera (%.1f, %.1f, %.1f)",
                    newKf.getCameraX(), newKf.getCameraY(), newKf.getCameraZ()));
            newKf.setCommand(String.format(Locale.US, "camera keyframe %.1f %.1f %.1f %.0f %.0f 0 70",
                    newKf.getCameraX(), newKf.getCameraY(), newKf.getCameraZ(),
                    newKf.getCameraYaw(), newKf.getCameraPitch()));

            currentSequence.getEntries().add(newKf);
            currentSequence.sortEntriesByTimestamp();
            setSelectedEntryIndex(currentSequence.getEntries().indexOf(newKf));
            saveFeedbackMessage = String.format(Locale.ROOT, "Stamped Camera Keyframe at %.3fs", targetTs / 1000.0);
        }

        saveFeedbackTime = System.currentTimeMillis();
        saveCurrentSequenceToWorkingMap();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.4f));
        updatePreviewCamera();
    }

    public void jumpToPreviousCameraKeyframe() {
        List<MusicSequenceEntry> cameraEntries = getSortedCameraEntries();
        if (cameraEntries.isEmpty()) return;
        MusicSequenceEntry prev = null;
        for (MusicSequenceEntry e : cameraEntries) {
            if (e.getTimestampMs() < playheadMs - 10) {
                prev = e;
            }
        }
        if (prev != null) {
            this.playheadMs = prev.getTimestampMs();
            relocatePlaybackAudio(prev.getTimestampMs());
            selectedEntryIndex = currentSequence.getEntries().indexOf(prev);
            clampTimeScrollToPlayhead();
            updatePreviewCamera();
        }
    }

    public void jumpToNextCameraKeyframe() {
        List<MusicSequenceEntry> cameraEntries = getSortedCameraEntries();
        if (cameraEntries.isEmpty()) return;
        for (MusicSequenceEntry e : cameraEntries) {
            if (e.getTimestampMs() > playheadMs + 10) {
                this.playheadMs = e.getTimestampMs();
                relocatePlaybackAudio(e.getTimestampMs());
                selectedEntryIndex = currentSequence.getEntries().indexOf(e);
                clampTimeScrollToPlayhead();
                updatePreviewCamera();
                return;
            }
        }
    }

    private List<MusicSequenceEntry> getSortedCameraEntries() {
        List<MusicSequenceEntry> list = new ArrayList<>();
        if (currentSequence == null) return list;
        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        for (MusicSequenceEntry e : currentSequence.getEntries()) {
            boolean isCam = e.isUseCamera() || "CAMERA".equalsIgnoreCase(e.getActionType());
            if (!isCam) {
                int chIdx = getChannelIndex(e);
                if (chIdx >= 0 && chIdx < channels.size()) {
                    if (MusicSequenceChannel.TYPE_CAMERA.equalsIgnoreCase(channels.get(chIdx).getType())) {
                        isCam = true;
                    }
                }
            }
            if (isCam) list.add(e);
        }
        list.sort(Comparator.comparingLong(MusicSequenceEntry::getTimestampMs));
        return list;
    }

    private void clampTimeScrollToPlayhead() {
        int timelineTrackWidth = getTimelineTrackWidth();
        int timelineTrackLeft = getTrackHeaderWidth();
        double playheadScreenX = timelineTrackLeft + ((playheadMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
        if (playheadScreenX > timelineTrackLeft + (timelineTrackWidth * 0.75) || playheadScreenX < timelineTrackLeft) {
            timeScrollMs = Math.max(0.0, playheadMs - ((timelineTrackWidth * 0.35) / pixelsPerSecond) * 1000.0);
            clampTimeScroll();
        }
    }

    private void renderDiamond(GuiGraphics guiGraphics, int cx, int cy, int size, int fillColor, int borderColor) {
        for (int dy = -size; dy <= size; dy++) {
            int span = size - Math.abs(dy);
            guiGraphics.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, fillColor);
        }
        guiGraphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, borderColor);
    }

    private Entity resolvePreviewTarget(String targetStr, Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        if (targetStr == null || targetStr.isBlank() || "@p".equalsIgnoreCase(targetStr) || "player".equalsIgnoreCase(targetStr)) {
            return mc.player;
        }
        String clean = targetStr.startsWith("#") ? targetStr.substring(1) : targetStr;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getTags().contains(clean) || (e.getCustomName() != null && e.getCustomName().getString().equalsIgnoreCase(clean))) {
                return e;
            }
        }
        return mc.player;
    }

    private void updateModalWidgetsPosition() {
        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        if (modalAddChannelStep == AddChannelStep.SELECT_TYPE) {
            int panelWidth = 440;
            int panelHeight = 280;
            int left = (effWidth - panelWidth) / 2;
            int top = (effHeight - panelHeight) / 2;

            int nameY = top + 188;
            String prevName = (modalChannelNameBox != null) ? modalChannelNameBox.getValue() : "";
            this.modalChannelNameBox = new EditBox(this.font, left + 16, nameY, panelWidth - 32, 20, Component.literal("Channel Name"));
            this.modalChannelNameBox.setMaxLength(64);
            if (!modalUserCustomizedName || prevName.isBlank()) {
                this.modalChannelNameBox.setValue(MusicSequenceChannel.getDefaultNameForType(modalSelectedType, ""));
            } else {
                this.modalChannelNameBox.setValue(prevName);
            }
            this.modalChannelNameBox.setResponder(val -> {
                if (!val.isBlank() && !val.equals(MusicSequenceChannel.getDefaultNameForType(modalSelectedType, ""))) {
                    modalUserCustomizedName = true;
                }
            });
            this.modalActorNameBox = null;
            this.modalActorTagBox = null;
            this.modalEntityCatalogDropdown = null;
            this.modalExistingMobsDropdown = null;
            this.modalParentPuppetDropdown = null;
        } else if (modalAddChannelStep == AddChannelStep.PUPPET_CONFIG) {
            int panelWidth = 460;
            int panelHeight = 315;
            int left = (effWidth - panelWidth) / 2;
            int top = (effHeight - panelHeight) / 2;

            // 1. Channel Name
            String prevChName = (modalChannelNameBox != null && !modalChannelNameBox.getValue().isBlank()) ? modalChannelNameBox.getValue() : "Puppet: Void Herald";
            this.modalChannelNameBox = new EditBox(this.font, left + 16, top + 42, panelWidth - 32, 18, Component.literal("Puppet Channel Name"));
            this.modalChannelNameBox.setMaxLength(64);
            this.modalChannelNameBox.setValue(prevChName);

            // 2. Dropdowns based on mode
            if (puppetTargetMode == PuppetTargetMode.REGISTER_NEW) {
                populateEntityCatalogDropdown(left + 16, top + 115, panelWidth - 32, 20);
                this.modalExistingMobsDropdown = null;
                this.modalParentPuppetDropdown = null;
            } else if (puppetTargetMode == PuppetTargetMode.EXISTING_ACTOR) {
                populateExistingMobsDropdown(left + 16, top + 115, panelWidth - 32 - 110, 20);
                this.modalEntityCatalogDropdown = null;
                this.modalParentPuppetDropdown = null;
            } else {
                populateParentPuppetDropdown(left + 16, top + 115, panelWidth - 32, 20);
                this.modalEntityCatalogDropdown = null;
                this.modalExistingMobsDropdown = null;
            }

            // 3. Entity Name EditBox
            String prevActorName = (modalActorNameBox != null && !modalActorNameBox.getValue().isBlank()) ? modalActorNameBox.getValue() : "Void Herald";
            this.modalActorNameBox = new EditBox(this.font, left + 16, top + 153, panelWidth - 32, 18, Component.literal("Entity Name"));
            this.modalActorNameBox.setMaxLength(64);
            this.modalActorNameBox.setValue(prevActorName);

            // 4. Custom Tag EditBox (Mandatory)
            String prevTag = (modalActorTagBox != null && !modalActorTagBox.getValue().isBlank()) ? modalActorTagBox.getValue() : "puppet_void_herald";
            this.modalActorTagBox = new EditBox(this.font, left + 16, top + 189, panelWidth - 32, 18, Component.literal("Custom Tag"));
            this.modalActorTagBox.setMaxLength(64);
            this.modalActorTagBox.setValue(prevTag);
            this.modalActorTagBox.setResponder(val -> {
                if (!val.trim().isEmpty()) {
                    modalTagError = false;
                }
            });
        }
    }

    private void populateEntityCatalogDropdown(int x, int y, int width, int height) {
        this.modalEntityCatalogDropdown = new CyberpunkDropdown<>(x, y, width, height, Component.literal("Select Entity Type"));
        this.modalEntityCatalogDropdown.setAccentColor(0xFFAA55FF);
        this.modalEntityCatalogDropdown.setMaxVisibleItems(6);
        this.modalEntityCatalogDropdown.setItemHeight(22);

        List<CyberpunkDropdown.DropdownEntry<String>> catalog = new ArrayList<>();
        // 1. Pinned Void Herald
        catalog.add(new CyberpunkDropdown.DropdownEntry<>(
                "fractured_utils:void_herald",
                Component.literal("Void Herald"),
                Component.literal("fractured_utils:void_herald [Boss]"),
                0xFFAA55FF
        ));

        // 2. Discover summonable living entity types
        List<CyberpunkDropdown.DropdownEntry<String>> otherTypes = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null) continue;
            String idStr = key.toString();
            if (idStr.equals("fractured_utils:void_herald") || idStr.equals("fracturedutils:void_herald")) continue;
            if (type.canSummon() && type.getCategory() != MobCategory.MISC) {
                String name = type.getDescription().getString();
                otherTypes.add(new CyberpunkDropdown.DropdownEntry<>(
                        idStr,
                        Component.literal(name),
                        Component.literal(idStr)
                ));
            }
        }
        otherTypes.sort(Comparator.comparing(e -> e.getLabel().getString()));
        catalog.addAll(otherTypes);

        this.modalEntityCatalogDropdown.setOptions(catalog);
        this.modalEntityCatalogDropdown.selectByValue(modalSelectedEntityType);

        this.modalEntityCatalogDropdown.setOnSelect(entry -> {
            this.modalSelectedEntityType = entry.getValue();
            String entityName = entry.getLabel().getString();
            if (this.modalActorNameBox != null) {
                this.modalActorNameBox.setValue(entityName);
            }
            if (this.modalActorTagBox != null) {
                String sanitized = "puppet_" + entityName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
                this.modalActorTagBox.setValue(sanitized);
            }
            if (this.modalChannelNameBox != null) {
                this.modalChannelNameBox.setValue("Puppet: " + entityName);
            }
            this.modalTagError = false;
        });
    }

    private void populateExistingMobsDropdown(int x, int y, int width, int height) {
        this.modalExistingMobsDropdown = new CyberpunkDropdown<>(x, y, width, height, Component.literal("Select Nearby Mob"));
        this.modalExistingMobsDropdown.setAccentColor(0xFFAA55FF);
        this.modalExistingMobsDropdown.setMaxVisibleItems(5);
        this.modalExistingMobsDropdown.setItemHeight(22);

        List<CyberpunkDropdown.DropdownEntry<String>> list = new ArrayList<>();
        if (this.minecraft != null && this.minecraft.player != null && this.minecraft.level != null) {
            Vec3 pPos = this.minecraft.player.position();
            AABB box = new AABB(pPos.x - 32, pPos.y - 16, pPos.z - 32, pPos.x + 32, pPos.y + 16, pPos.z + 32);
            List<LivingEntity> nearby = this.minecraft.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this.minecraft.player);
            nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(this.minecraft.player)));

            for (LivingEntity e : nearby) {
                String uuid = e.getStringUUID();
                String name = e.getDisplayName().getString();
                ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
                String typeStr = key != null ? key.toString() : "";
                int dist = (int) Math.sqrt(e.distanceToSqr(this.minecraft.player));
                Set<String> tags = e.getTags();
                String tagStr = tags.isEmpty() ? "No tags" : String.join(", ", tags);

                list.add(new CyberpunkDropdown.DropdownEntry<>(
                        uuid,
                        Component.literal(name + " (" + dist + "m)"),
                        Component.literal(typeStr + " | " + tagStr)
                ));
            }
        }

        if (list.isEmpty()) {
            list.add(new CyberpunkDropdown.DropdownEntry<>(
                    "",
                    Component.literal("No nearby entities found"),
                    Component.literal("Aim crosshair or register new actor")
            ));
        }

        this.modalExistingMobsDropdown.setOptions(list);

        this.modalExistingMobsDropdown.setOnSelect(entry -> {
            String uuid = entry.getValue();
            if (uuid == null || uuid.isBlank()) return;
            if (this.minecraft != null && this.minecraft.level != null) {
                for (Entity e : this.minecraft.level.entitiesForRendering()) {
                    if (e.getStringUUID().equals(uuid)) {
                        applyExistingEntity(e);
                        break;
                    }
                }
            }
        });
    }

    private void populateParentPuppetDropdown(int x, int y, int width, int height) {
        this.modalParentPuppetDropdown = new CyberpunkDropdown<>(x, y, width, height, Component.literal("Select Parent Puppet Entity"));
        this.modalParentPuppetDropdown.setAccentColor(MusicSequenceChannel.COLOR_PUPPET_SUB);
        this.modalParentPuppetDropdown.setMaxVisibleItems(5);
        this.modalParentPuppetDropdown.setItemHeight(22);

        List<CyberpunkDropdown.DropdownEntry<String>> options = new ArrayList<>();
        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        for (int i = 0; i < channels.size(); i++) {
            MusicSequenceChannel ch = channels.get(i);
            if (MusicSequenceChannel.TYPE_PUPPET.equalsIgnoreCase(ch.getType()) && !ch.isSubChannel()) {
                String label = "CH " + (i + 1) + ": " + ch.getName();
                String desc = "#" + ch.getActorTag() + " (" + (!ch.getActorEntityType().isBlank() ? ch.getActorEntityType() : "Boss/Mob") + ")";
                options.add(new CyberpunkDropdown.DropdownEntry<>(ch.getId(), Component.literal(label), Component.literal(desc), ch.getColor()));
            }
        }

        if (options.isEmpty()) {
            options.add(new CyberpunkDropdown.DropdownEntry<>("", Component.literal("No parent puppet channel found"), Component.literal("Add a root puppet channel first")));
        }

        this.modalParentPuppetDropdown.setOptions(options);

        if (modalSelectedParentChannelId.isBlank() && !options.isEmpty()) {
            modalSelectedParentChannelId = options.get(0).getValue();
        }
        this.modalParentPuppetDropdown.selectByValue(modalSelectedParentChannelId);

        applySelectedParentPuppet(modalSelectedParentChannelId);

        this.modalParentPuppetDropdown.setOnSelect(entry -> {
            this.modalSelectedParentChannelId = entry.getValue();
            applySelectedParentPuppet(this.modalSelectedParentChannelId);
        });
    }

    private void applySelectedParentPuppet(String parentId) {
        if (parentId == null || parentId.isBlank()) return;
        MusicSequenceChannel parent = null;
        for (MusicSequenceChannel ch : currentSequence.getChannels()) {
            if (ch.getId().equalsIgnoreCase(parentId)) {
                parent = ch;
                break;
            }
        }
        if (parent == null) return;

        int existingSubCount = 0;
        for (MusicSequenceChannel ch : currentSequence.getChannels()) {
            if (ch.isSubChannel() && parent.getId().equalsIgnoreCase(ch.getParentChannelId())) {
                existingSubCount++;
            }
        }

        String actorName = !parent.getActorName().isBlank() ? parent.getActorName() : parent.getName();
        if (actorName.startsWith("Puppet: ")) {
            actorName = actorName.substring(8);
        }
        String defaultSubName = "↳ " + actorName + " (Track " + (existingSubCount + 2) + ")";

        if (this.modalChannelNameBox != null && (!modalUserCustomizedName || this.modalChannelNameBox.getValue().isBlank())) {
            this.modalChannelNameBox.setValue(defaultSubName);
        }
        if (this.modalActorNameBox != null) {
            this.modalActorNameBox.setValue(parent.getActorName());
        }
        if (this.modalActorTagBox != null) {
            this.modalActorTagBox.setValue(parent.getActorTag());
        }
        this.modalSelectedEntityType = parent.getActorEntityType();
        this.modalTagError = false;
        setModalStatusMessage("✓ Linking to entity #" + parent.getActorTag());
    }

    private void applyExistingEntity(Entity target) {
        if (target == null) return;
        String name = target.getDisplayName().getString();
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        String typeKey = key != null ? key.toString() : "";
        this.modalSelectedEntityType = typeKey;

        if (this.modalActorNameBox != null) {
            this.modalActorNameBox.setValue(name);
        }
        if (this.modalChannelNameBox != null) {
            this.modalChannelNameBox.setValue("Puppet: " + name);
        }

        String suggestedTag = "";
        for (String tag : target.getTags()) {
            if (tag != null && !tag.isBlank()) {
                suggestedTag = tag;
                break;
            }
        }
        if (suggestedTag.isBlank()) {
            suggestedTag = "puppet_" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        }

        if (this.modalActorTagBox != null) {
            this.modalActorTagBox.setValue(suggestedTag);
        }
        this.modalTagError = false;
        setModalStatusMessage("✓ Bound mob: " + name + " (" + typeKey + ")");
    }

    private void commitAddChannel() {
        if (modalAddChannelStep == AddChannelStep.PUPPET_CONFIG) {
            if (puppetTargetMode == PuppetTargetMode.SUB_CHANNEL) {
                MusicSequenceChannel parent = null;
                for (MusicSequenceChannel ch : currentSequence.getChannels()) {
                    if (ch.getId().equalsIgnoreCase(modalSelectedParentChannelId)) {
                        parent = ch;
                        break;
                    }
                }
                if (parent == null) {
                    setModalStatusMessage("❌ Please select a parent puppet channel to link!");
                    if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0f));
                    }
                    return;
                }

                int existingSubCount = 0;
                int lastIdx = currentSequence.getChannels().indexOf(parent);
                for (int i = 0; i < currentSequence.getChannels().size(); i++) {
                    MusicSequenceChannel c = currentSequence.getChannels().get(i);
                    if (c.isSubChannel() && c.getParentChannelId().equalsIgnoreCase(parent.getId())) {
                        existingSubCount++;
                        if (i > lastIdx) lastIdx = i;
                    }
                }

                String aName = !parent.getActorName().isBlank() ? parent.getActorName() : parent.getName();
                if (aName.startsWith("Puppet: ")) aName = aName.substring(8);
                String defaultSubName = "↳ " + aName + " (Track " + (existingSubCount + 2) + ")";

                String chName = (modalChannelNameBox != null) ? modalChannelNameBox.getValue().trim() : "";
                if (chName.isEmpty()) {
                    chName = defaultSubName;
                } else if (!chName.startsWith("↳")) {
                    chName = "↳ " + chName;
                }

                MusicSequenceChannel subChannel = new MusicSequenceChannel(
                        UUID.randomUUID().toString(),
                        MusicSequenceChannel.TYPE_PUPPET,
                        chName,
                        parent.getPuppetActor(),
                        MusicSequenceChannel.COLOR_PUPPET_SUB
                );
                subChannel.setParentChannelId(parent.getId());
                subChannel.setActorName(parent.getActorName());
                subChannel.setActorTag(parent.getActorTag());
                subChannel.setActorEntityType(parent.getActorEntityType());
                subChannel.setActorRegisteredOnly(parent.isActorRegisteredOnly());

                int insertIdx = Math.min(currentSequence.getChannels().size(), lastIdx + 1);
                currentSequence.getChannels().add(insertIdx, subChannel);

                saveCurrentSequenceToWorkingMap();
                this.activeModal = ModalType.NONE;
                this.init();
                return;
            }

            String tag = (modalActorTagBox != null) ? modalActorTagBox.getValue().trim() : "";
            if (tag.isEmpty()) {
                modalTagError = true;
                setModalStatusMessage("❌ Custom Tag is mandatory!");
                if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0f));
                }
                return;
            }

            String actorName = (modalActorNameBox != null) ? modalActorNameBox.getValue().trim() : "";
            if (actorName.isEmpty()) {
                actorName = (modalSelectedEntityType != null && !modalSelectedEntityType.isBlank()) ? modalSelectedEntityType : "Puppet Actor";
            }

            String chName = (modalChannelNameBox != null) ? modalChannelNameBox.getValue().trim() : "";
            if (chName.isEmpty()) {
                chName = "Puppet: " + actorName;
            }

            int color = MusicSequenceChannel.COLOR_PUPPET;
            MusicSequenceChannel channel = new MusicSequenceChannel(
                    UUID.randomUUID().toString(),
                    MusicSequenceChannel.TYPE_PUPPET,
                    chName,
                    "@e[tag=" + tag + ",limit=1]",
                    color
            );
            channel.setActorName(actorName);
            channel.setActorTag(tag);
            channel.setActorEntityType(modalSelectedEntityType != null ? modalSelectedEntityType : "");
            channel.setActorRegisteredOnly(puppetTargetMode == PuppetTargetMode.REGISTER_NEW);

            currentSequence.getChannels().add(channel);
            saveCurrentSequenceToWorkingMap();
            this.activeModal = ModalType.NONE;
            this.init();
            return;
        }

        String name = (modalChannelNameBox != null) ? modalChannelNameBox.getValue().trim() : "";
        if (name.isEmpty()) {
            name = MusicSequenceChannel.getDefaultNameForType(modalSelectedType, "");
        }
        int color = MusicSequenceChannel.getDefaultColorForType(modalSelectedType);
        MusicSequenceChannel channel = new MusicSequenceChannel(
                UUID.randomUUID().toString(),
                modalSelectedType,
                name,
                "",
                color
        );
        currentSequence.getChannels().add(channel);
        saveCurrentSequenceToWorkingMap();
        this.activeModal = ModalType.NONE;
        this.init();
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

    private void pickLookedEntity() {
        Entity target = getLookedAtEntity();
        if (target != null) {
            applyExistingEntity(target);
        } else {
            setModalStatusMessage("❌ No entity under crosshairs!");
        }
    }

    private void setModalStatusMessage(String msg) {
        this.modalStatusMessage = msg;
        this.modalStatusMessageTime = System.currentTimeMillis();
    }

    private void handleModalCancel() {
        if (activeModal == ModalType.ADD_CHANNEL && modalAddChannelStep == AddChannelStep.PUPPET_CONFIG) {
            modalAddChannelStep = AddChannelStep.SELECT_TYPE;
            updateModalWidgetsPosition();
            return;
        }
        this.activeModal = ModalType.NONE;
        this.pendingCloseAction = null;
    }

    private void handleModalConfirm() {
        if (activeModal == ModalType.ADD_CHANNEL) {
            commitAddChannel();
        } else if (activeModal == ModalType.CONFIRM_DELETE_CHANNEL) {
            if (modalDeleteChannelIndex >= 0) {
                deleteChannel(modalDeleteChannelIndex);
            }
            this.activeModal = ModalType.NONE;
        } else if (activeModal == ModalType.CONFIRM_UNSAVED_CHANGES) {
            saveCurrentFile();
            this.activeModal = ModalType.NONE;
            if (pendingCloseAction != null) {
                pendingCloseAction.run();
                pendingCloseAction = null;
            }
        }
    }

    private void handleModalClick(int smX, int smY, int button) {
        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        if (activeModal == ModalType.CONFIRM_UNSAVED_CHANGES) {
            int panelWidth = 380;
            int panelHeight = 165;
            int left = (effWidth - panelWidth) / 2;
            int top = (effHeight - panelHeight) / 2;

            // Close [✕]
            if (isMouseOver(smX, smY, left + panelWidth - 22, top + 4, 16, 16)) {
                playButtonSound();
                handleModalCancel();
                return;
            }

            int btnY = top + 115;
            // [SAVE & EXIT]
            if (isMouseOver(smX, smY, left + 18, btnY, 110, 24)) {
                playButtonSound();
                saveCurrentFile();
                this.activeModal = ModalType.NONE;
                if (pendingCloseAction != null) {
                    pendingCloseAction.run();
                    pendingCloseAction = null;
                }
                return;
            }

            // [DISCARD]
            if (isMouseOver(smX, smY, left + 138, btnY, 100, 24)) {
                playButtonSound();
                Map<String, String> saved = getActiveSavedMap();
                Map<String, String> working = getActiveWorkingMap();
                String orig = saved.get(currentFileName);
                if (orig != null) {
                    working.put(currentFileName, orig);
                } else {
                    working.remove(currentFileName);
                }
                loadCurrentFileSequence();
                if (isPreviewPlaying) {
                    EventAudioClientController.getInstance().stopAudio(0);
                    this.isPreviewPlaying = false;
                }
                this.activeModal = ModalType.NONE;
                if (pendingCloseAction != null) {
                    pendingCloseAction.run();
                    pendingCloseAction = null;
                }
                return;
            }

            // [CANCEL]
            if (isMouseOver(smX, smY, left + panelWidth - 18 - 90, btnY, 90, 24)) {
                playButtonSound();
                handleModalCancel();
                return;
            }
        } else if (activeModal == ModalType.CONFIRM_DELETE_CHANNEL) {
            int panelWidth = 360;
            int panelHeight = 160;
            int left = (effWidth - panelWidth) / 2;
            int top = (effHeight - panelHeight) / 2;

            // Close [✕]
            if (isMouseOver(smX, smY, left + panelWidth - 22, top + 4, 16, 16)) {
                playButtonSound();
                handleModalCancel();
                return;
            }

            int btnY = top + 115;
            // [DELETE CHANNEL]
            if (isMouseOver(smX, smY, left + 24, btnY, 140, 24)) {
                playButtonSound();
                if (modalDeleteChannelIndex >= 0) {
                    deleteChannel(modalDeleteChannelIndex);
                }
                this.activeModal = ModalType.NONE;
                return;
            }

            // [CANCEL]
            if (isMouseOver(smX, smY, left + panelWidth - 24 - 100, btnY, 100, 24)) {
                playButtonSound();
                handleModalCancel();
                return;
            }
        } else if (activeModal == ModalType.ADD_CHANNEL) {
            int panelWidth = (modalAddChannelStep == AddChannelStep.PUPPET_CONFIG) ? 460 : 440;
            int panelHeight = (modalAddChannelStep == AddChannelStep.PUPPET_CONFIG) ? 315 : 280;
            int left = (effWidth - panelWidth) / 2;
            int top = (effHeight - panelHeight) / 2;

            // Close [✕]
            if (isMouseOver(smX, smY, left + panelWidth - 22, top + 4, 16, 16)) {
                playButtonSound();
                handleModalCancel();
                return;
            }

            if (modalAddChannelStep == AddChannelStep.SELECT_TYPE) {
                int cardW = 198;
                int cardH = 38;
                int cardX1 = left + 16;
                int cardX2 = left + 226;
                int cardY1 = top + 46;
                int cardY2 = top + 88;
                int cardY3 = top + 130;

                // Card 0: COMMAND
                if (isMouseOver(smX, smY, cardX1, cardY1, cardW, cardH)) {
                    playButtonSound();
                    modalSelectedType = MusicSequenceChannel.TYPE_COMMAND;
                    if (!modalUserCustomizedName && modalChannelNameBox != null) {
                        modalChannelNameBox.setValue(MusicSequenceChannel.getDefaultNameForType(modalSelectedType, ""));
                    }
                    return;
                }
                // Card 1: DIALOG
                if (isMouseOver(smX, smY, cardX2, cardY1, cardW, cardH)) {
                    playButtonSound();
                    modalSelectedType = MusicSequenceChannel.TYPE_DIALOG;
                    if (!modalUserCustomizedName && modalChannelNameBox != null) {
                        modalChannelNameBox.setValue(MusicSequenceChannel.getDefaultNameForType(modalSelectedType, ""));
                    }
                    return;
                }
                // Card 2: SCREEN EFFECT
                if (isMouseOver(smX, smY, cardX1, cardY2, cardW, cardH)) {
                    playButtonSound();
                    modalSelectedType = MusicSequenceChannel.TYPE_SCREEN_EFFECT;
                    if (!modalUserCustomizedName && modalChannelNameBox != null) {
                        modalChannelNameBox.setValue(MusicSequenceChannel.getDefaultNameForType(modalSelectedType, ""));
                    }
                    return;
                }
                // Card 3: CAMERA
                if (isMouseOver(smX, smY, cardX2, cardY2, cardW, cardH)) {
                    playButtonSound();
                    modalSelectedType = MusicSequenceChannel.TYPE_CAMERA;
                    if (!modalUserCustomizedName && modalChannelNameBox != null) {
                        modalChannelNameBox.setValue(MusicSequenceChannel.getDefaultNameForType(modalSelectedType, ""));
                    }
                    return;
                }
                // Card 4: CHECKPOINT
                if (isMouseOver(smX, smY, cardX1, cardY3, cardW, cardH)) {
                    playButtonSound();
                    modalSelectedType = MusicSequenceChannel.TYPE_CHECKPOINT;
                    if (!modalUserCustomizedName && modalChannelNameBox != null) {
                        modalChannelNameBox.setValue(MusicSequenceChannel.getDefaultNameForType(modalSelectedType, ""));
                    }
                    return;
                }
                // Card 5: PUPPET
                if (isMouseOver(smX, smY, cardX2, cardY3, cardW, cardH)) {
                    playButtonSound();
                    modalSelectedType = MusicSequenceChannel.TYPE_PUPPET;
                    modalAddChannelStep = AddChannelStep.PUPPET_CONFIG;
                    puppetTargetMode = PuppetTargetMode.REGISTER_NEW;
                    modalSelectedEntityType = "fractured_utils:void_herald";
                    modalTagError = false;
                    updateModalWidgetsPosition();
                    return;
                }

                // EditBox
                if (modalChannelNameBox != null) {
                    boolean hit = modalChannelNameBox.mouseClicked(smX, smY, button);
                    modalChannelNameBox.setFocused(hit);
                    if (hit) return;
                }

                // Footer Buttons
                int btnY = top + 242;
                // [+ ADD CHANNEL]
                if (isMouseOver(smX, smY, left + panelWidth - 16 - 130, btnY, 130, 24)) {
                    playButtonSound();
                    commitAddChannel();
                    return;
                }
                // [CANCEL]
                if (isMouseOver(smX, smY, left + 16, btnY, 85, 24)) {
                    playButtonSound();
                    handleModalCancel();
                    return;
                }
            } else if (modalAddChannelStep == AddChannelStep.PUPPET_CONFIG) {
                // Dropdown menu clicks take highest priority if open
                if (puppetTargetMode == PuppetTargetMode.REGISTER_NEW && modalEntityCatalogDropdown != null) {
                    if (modalEntityCatalogDropdown.isOpen()) {
                        if (modalEntityCatalogDropdown.isMouseOverMenu(smX, smY)) {
                            modalEntityCatalogDropdown.mouseClicked(smX, smY, button);
                            return;
                        }
                        modalEntityCatalogDropdown.setOpen(false);
                        return;
                    }
                    if (modalEntityCatalogDropdown.isMouseOverHeader(smX, smY)) {
                        modalEntityCatalogDropdown.mouseClicked(smX, smY, button);
                        return;
                    }
                } else if (puppetTargetMode == PuppetTargetMode.EXISTING_ACTOR && modalExistingMobsDropdown != null) {
                    if (modalExistingMobsDropdown.isOpen()) {
                        if (modalExistingMobsDropdown.isMouseOverMenu(smX, smY)) {
                            modalExistingMobsDropdown.mouseClicked(smX, smY, button);
                            return;
                        }
                        modalExistingMobsDropdown.setOpen(false);
                        return;
                    }
                    if (modalExistingMobsDropdown.isMouseOverHeader(smX, smY)) {
                        modalExistingMobsDropdown.mouseClicked(smX, smY, button);
                        return;
                    }
                } else if (puppetTargetMode == PuppetTargetMode.SUB_CHANNEL && modalParentPuppetDropdown != null) {
                    if (modalParentPuppetDropdown.isOpen()) {
                        if (modalParentPuppetDropdown.isMouseOverMenu(smX, smY)) {
                            modalParentPuppetDropdown.mouseClicked(smX, smY, button);
                            return;
                        }
                        modalParentPuppetDropdown.setOpen(false);
                        return;
                    }
                    if (modalParentPuppetDropdown.isMouseOverHeader(smX, smY)) {
                        modalParentPuppetDropdown.mouseClicked(smX, smY, button);
                        return;
                    }
                }

                // Mode Tabs (3 tabs)
                int tabW = (panelWidth - 40) / 3;
                int tabY = top + 77;
                // [➕ REGISTER NEW]
                if (isMouseOver(smX, smY, left + 16, tabY, tabW, 20)) {
                    if (puppetTargetMode != PuppetTargetMode.REGISTER_NEW) {
                        playButtonSound();
                        puppetTargetMode = PuppetTargetMode.REGISTER_NEW;
                        modalTagError = false;
                        updateModalWidgetsPosition();
                    }
                    return;
                }
                // [🔍 IN-WORLD MOB]
                if (isMouseOver(smX, smY, left + 18 + tabW, tabY, tabW, 20)) {
                    if (puppetTargetMode != PuppetTargetMode.EXISTING_ACTOR) {
                        playButtonSound();
                        puppetTargetMode = PuppetTargetMode.EXISTING_ACTOR;
                        modalTagError = false;
                        updateModalWidgetsPosition();
                    }
                    return;
                }
                // [🔗 SUB-CHANNEL]
                if (isMouseOver(smX, smY, left + 20 + (tabW * 2), tabY, tabW, 20)) {
                    if (puppetTargetMode != PuppetTargetMode.SUB_CHANNEL) {
                        playButtonSound();
                        puppetTargetMode = PuppetTargetMode.SUB_CHANNEL;
                        modalTagError = false;
                        updateModalWidgetsPosition();
                    }
                    return;
                }

                // [🎯 TARGET MOB]
                if (puppetTargetMode == PuppetTargetMode.EXISTING_ACTOR) {
                    if (isMouseOver(smX, smY, left + panelWidth - 16 - 105, top + 115, 105, 20)) {
                        playButtonSound();
                        pickLookedEntity();
                        return;
                    }
                }

                // Channel Name EditBox
                if (modalChannelNameBox != null) {
                    boolean hit = modalChannelNameBox.mouseClicked(smX, smY, button);
                    modalChannelNameBox.setFocused(hit);
                    if (hit) {
                        if (modalActorNameBox != null) modalActorNameBox.setFocused(false);
                        if (modalActorTagBox != null) modalActorTagBox.setFocused(false);
                        return;
                    }
                }

                // Actor Name EditBox
                if (modalActorNameBox != null) {
                    boolean hit = modalActorNameBox.mouseClicked(smX, smY, button);
                    modalActorNameBox.setFocused(hit);
                    if (hit) {
                        if (modalChannelNameBox != null) modalChannelNameBox.setFocused(false);
                        if (modalActorTagBox != null) modalActorTagBox.setFocused(false);
                        return;
                    }
                }

                // Actor Tag EditBox
                if (modalActorTagBox != null) {
                    boolean hit = modalActorTagBox.mouseClicked(smX, smY, button);
                    modalActorTagBox.setFocused(hit);
                    if (hit) {
                        if (modalChannelNameBox != null) modalChannelNameBox.setFocused(false);
                        if (modalActorNameBox != null) modalActorNameBox.setFocused(false);
                        modalTagError = false;
                        return;
                    }
                }

                int btnY = top + 275;
                // [← BACK TO TYPES]
                if (isMouseOver(smX, smY, left + 16, btnY, 120, 24)) {
                    playButtonSound();
                    modalAddChannelStep = AddChannelStep.SELECT_TYPE;
                    updateModalWidgetsPosition();
                    return;
                }

                // [+ ADD PUPPET CHANNEL]
                if (isMouseOver(smX, smY, left + panelWidth - 16 - 165, btnY, 165, 24)) {
                    playButtonSound();
                    commitAddChannel();
                    return;
                }
            }
        }
    }

    private void playButtonSound() {
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0f));
        }
    }

    private boolean isMouseOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void drawBorderBox(GuiGraphics graphics, int x, int y, int w, int h, int borderColor, int fillColor) {
        graphics.fill(x, y, x + w, y + h, fillColor);
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);
    }

    private void drawModalButton(GuiGraphics graphics, int x, int y, int w, int h, String text, int accentColor, boolean hovered) {
        int borderColor = hovered ? accentColor : (0x99000000 | (accentColor & 0x00FFFFFF));
        int fillColor = hovered ? (0x38000000 | (accentColor & 0x00FFFFFF)) : 0xEE070F18;
        drawBorderBox(graphics, x, y, w, h, borderColor, fillColor);
        int textColor = hovered ? 0xFFFFFFFF : accentColor;
        graphics.drawCenteredString(this.font, Component.literal(text), x + (w / 2), y + (h - 8) / 2, textColor);
    }

    private void renderActiveModal(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int effWidth, int effHeight) {
        guiGraphics.fill(0, 0, effWidth, effHeight, 0xD0000000);

        if (activeModal == ModalType.CONFIRM_UNSAVED_CHANGES) {
            renderConfirmUnsavedChangesModal(guiGraphics, mouseX, mouseY, effWidth, effHeight);
        } else if (activeModal == ModalType.CONFIRM_DELETE_CHANNEL) {
            renderConfirmDeleteChannelModal(guiGraphics, mouseX, mouseY, effWidth, effHeight);
        } else if (activeModal == ModalType.ADD_CHANNEL) {
            renderAddChannelModal(guiGraphics, mouseX, mouseY, partialTick, effWidth, effHeight);
        }
    }

    private void renderConfirmUnsavedChangesModal(GuiGraphics guiGraphics, int mouseX, int mouseY, int effWidth, int effHeight) {
        int panelWidth = 380;
        int panelHeight = 165;
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        drawBorderBox(guiGraphics, left, top, panelWidth, panelHeight, 0xFFFFB300, 0xFA141006);

        // Header
        guiGraphics.fill(left, top, left + panelWidth, top + 24, 0xEE2A1E08);
        guiGraphics.fill(left, top + 23, left + panelWidth, top + 24, 0xFFFFB300);
        guiGraphics.drawString(this.font, "⚠ UNSAVED CHANGES", left + 12, top + 8, 0xFFFFB300, false);

        boolean closeHov = isMouseOver(mouseX, mouseY, left + panelWidth - 22, top + 4, 16, 16);
        guiGraphics.drawString(this.font, "✕", left + panelWidth - 18, top + 7, closeHov ? 0xFFFF3355 : 0xFF8899AA, false);

        // Message
        guiGraphics.drawString(this.font, "You have unsaved changes in sequence file:", left + 20, top + 36, 0xFFAABBCC, false);
        String display = "'" + (currentFileName != null ? currentFileName : "") + "'";
        guiGraphics.drawCenteredString(this.font, Component.literal(display), left + (panelWidth / 2), top + 54, CYAN_MAIN);
        guiGraphics.drawCenteredString(this.font, Component.literal("What would you like to do before leaving?"), left + (panelWidth / 2), top + 76, 0xFFAABBCC);

        int btnY = top + 115;
        boolean sHov = isMouseOver(mouseX, mouseY, left + 18, btnY, 110, 24);
        drawModalButton(guiGraphics, left + 18, btnY, 110, 24, "SAVE & EXIT", 0xFF00FF88, sHov);

        boolean dHov = isMouseOver(mouseX, mouseY, left + 138, btnY, 100, 24);
        drawModalButton(guiGraphics, left + 138, btnY, 100, 24, "DISCARD", 0xFFFF3355, dHov);

        boolean cHov = isMouseOver(mouseX, mouseY, left + panelWidth - 18 - 90, btnY, 90, 24);
        drawModalButton(guiGraphics, left + panelWidth - 18 - 90, btnY, 90, 24, "CANCEL", 0xFF8899AA, cHov);
    }

    private void renderConfirmDeleteChannelModal(GuiGraphics guiGraphics, int mouseX, int mouseY, int effWidth, int effHeight) {
        int panelWidth = 360;
        int panelHeight = 160;
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        drawBorderBox(guiGraphics, left, top, panelWidth, panelHeight, 0xFFFF3355, 0xFA14060A);

        // Header
        guiGraphics.fill(left, top, left + panelWidth, top + 24, 0xEE250810);
        guiGraphics.fill(left, top + 23, left + panelWidth, top + 24, 0xFFFF3355);
        guiGraphics.drawString(this.font, "DELETE TIMELINE CHANNEL", left + 12, top + 8, 0xFFFF3355, false);

        boolean closeHov = isMouseOver(mouseX, mouseY, left + panelWidth - 22, top + 4, 16, 16);
        guiGraphics.drawString(this.font, "✕", left + panelWidth - 18, top + 7, closeHov ? 0xFFFF3355 : 0xFF8899AA, false);

        // Body
        guiGraphics.drawString(this.font, "Are you sure you want to delete channel:", left + 20, top + 36, 0xFFAABBCC, false);
        List<MusicSequenceChannel> chs = currentSequence.getChannels();
        if (modalDeleteChannelIndex >= 0 && modalDeleteChannelIndex < chs.size()) {
            MusicSequenceChannel ch = chs.get(modalDeleteChannelIndex);
            String name = "CH " + (modalDeleteChannelIndex + 1) + ": " + ch.getName() + " [" + ch.getType() + "]";
            guiGraphics.drawCenteredString(this.font, Component.literal(name), left + (panelWidth / 2), top + 54, ch.getColor());

            int subCount = 0;
            if (!ch.isSubChannel()) {
                for (MusicSequenceChannel c : chs) {
                    if (c.isSubChannel() && ch.getId().equalsIgnoreCase(c.getParentChannelId())) {
                        subCount++;
                    }
                }
            }

            if (subCount > 0) {
                guiGraphics.drawCenteredString(this.font, Component.literal("⚠ Deleting this puppet also removes " + subCount + " linked sub-channel(s)!"), left + (panelWidth / 2), top + 74, 0xFFFF3355);
                guiGraphics.drawCenteredString(this.font, Component.literal("All keyframes on this channel and sub-channels will be removed!"), left + (panelWidth / 2), top + 88, 0xFFFFAA33);
            } else {
                guiGraphics.drawCenteredString(this.font, Component.literal("⚠ All keyframes on this channel will be removed!"), left + (panelWidth / 2), top + 76, 0xFFFF3355);
            }
        }

        int btnY = top + 115;
        boolean delHov = isMouseOver(mouseX, mouseY, left + 24, btnY, 140, 24);
        drawModalButton(guiGraphics, left + 24, btnY, 140, 24, "DELETE CHANNEL", 0xFFFF3355, delHov);

        boolean canHov = isMouseOver(mouseX, mouseY, left + panelWidth - 24 - 100, btnY, 100, 24);
        drawModalButton(guiGraphics, left + panelWidth - 24 - 100, btnY, 100, 24, "CANCEL", CYAN_MAIN, canHov);
    }

    private void renderAddChannelModal(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int effWidth, int effHeight) {
        if (modalAddChannelStep == AddChannelStep.SELECT_TYPE) {
            int panelWidth = 440;
            int panelHeight = 280;
            int left = (effWidth - panelWidth) / 2;
            int top = (effHeight - panelHeight) / 2;

            drawBorderBox(guiGraphics, left, top, panelWidth, panelHeight, CARD_BORDER, 0xFA070E16);

            // Header
            guiGraphics.fill(left, top, left + panelWidth, top + 24, CYAN_BG);
            guiGraphics.fill(left, top + 23, left + panelWidth, top + 24, CARD_BORDER);
            guiGraphics.drawString(this.font, "＋ ADD TIMELINE CHANNEL", left + 12, top + 8, CYAN_MAIN, false);

            boolean closeHov = isMouseOver(mouseX, mouseY, left + panelWidth - 22, top + 4, 16, 16);
            guiGraphics.drawString(this.font, "✕", left + panelWidth - 18, top + 7, closeHov ? 0xFFFF3355 : 0xFF8899AA, false);

            guiGraphics.drawString(this.font, "Select channel type to add to sequence timeline:", left + 16, top + 31, 0xFFAABBCC, false);

            int cardW = 198;
            int cardH = 38;
            int cardX1 = left + 16;
            int cardX2 = left + 226;
            int cardY1 = top + 46;
            int cardY2 = top + 88;
            int cardY3 = top + 130;

            renderTypeCard(guiGraphics, mouseX, mouseY, cardX1, cardY1, cardW, cardH, MusicSequenceChannel.TYPE_COMMAND, 0xFF00E5FF, ">_", "COMMAND", "Server & console commands");
            renderTypeCard(guiGraphics, mouseX, mouseY, cardX2, cardY1, cardW, cardH, MusicSequenceChannel.TYPE_DIALOG, 0xFFFFB300, "💬", "DIALOG", "Cutscene dialog sequences");
            renderTypeCard(guiGraphics, mouseX, mouseY, cardX1, cardY2, cardW, cardH, MusicSequenceChannel.TYPE_SCREEN_EFFECT, MusicSequenceChannel.COLOR_SCREEN_EFFECT, "⚡", "SCREEN EFFECT", "Shakes, strobe & visual fx");
            renderTypeCard(guiGraphics, mouseX, mouseY, cardX2, cardY2, cardW, cardH, MusicSequenceChannel.TYPE_CAMERA, 0xFF00FF88, "🎥", "CAMERA", "Cinematic camera paths");
            renderTypeCard(guiGraphics, mouseX, mouseY, cardX1, cardY3, cardW, cardH, MusicSequenceChannel.TYPE_CHECKPOINT, 0xFF5599FF, "🚩", "CHECKPOINT", "Player respawn points");
            renderTypeCard(guiGraphics, mouseX, mouseY, cardX2, cardY3, cardW, cardH, MusicSequenceChannel.TYPE_PUPPET, 0xFFAA55FF, "🎭", "PUPPET (ACTOR)", "Direct mob & boss actors");

            guiGraphics.drawString(this.font, "Channel Name (optional):", left + 16, top + 176, 0xFFAABBCC, false);
            if (modalChannelNameBox != null) {
                modalChannelNameBox.render(guiGraphics, mouseX, mouseY, partialTick);
            }

            int btnY = top + 242;
            boolean addHov = isMouseOver(mouseX, mouseY, left + panelWidth - 16 - 130, btnY, 130, 24);
            drawModalButton(guiGraphics, left + panelWidth - 16 - 130, btnY, 130, 24, "+ ADD CHANNEL", CYAN_MAIN, addHov);

            boolean canHov = isMouseOver(mouseX, mouseY, left + 16, btnY, 85, 24);
            drawModalButton(guiGraphics, left + 16, btnY, 85, 24, "CANCEL", 0xFFFF3355, canHov);
        } else if (modalAddChannelStep == AddChannelStep.PUPPET_CONFIG) {
            int panelWidth = 460;
            int panelHeight = 315;
            int left = (effWidth - panelWidth) / 2;
            int top = (effHeight - panelHeight) / 2;

            drawBorderBox(guiGraphics, left, top, panelWidth, panelHeight, 0xFFAA55FF, 0xFA0B0916);

            // Header
            guiGraphics.fill(left, top, left + panelWidth, top + 24, 0xEE1E0B25);
            guiGraphics.fill(left, top + 23, left + panelWidth, top + 24, 0xFFAA55FF);
            guiGraphics.drawString(this.font, "🎭 CONFIGURE PUPPET ACTOR CHANNEL", left + 12, top + 8, 0xFFAA55FF, false);

            boolean closeHov = isMouseOver(mouseX, mouseY, left + panelWidth - 22, top + 4, 16, 16);
            guiGraphics.drawString(this.font, "✕", left + panelWidth - 18, top + 7, closeHov ? 0xFFFF3355 : 0xFF8899AA, false);

            // Row 1: Channel Name
            guiGraphics.drawString(this.font, "Channel Name:", left + 16, top + 31, 0xFFAABBCC, false);
            if (modalChannelNameBox != null) {
                modalChannelNameBox.render(guiGraphics, mouseX, mouseY, partialTick);
            }

            // Row 2: Target Mode Selector Tabs (3 tabs)
            guiGraphics.drawString(this.font, "Actor Target Mode:", left + 16, top + 65, 0xFFAABBCC, false);
            int tabW = (panelWidth - 40) / 3;
            int tabY = top + 77;
            boolean regActive = (puppetTargetMode == PuppetTargetMode.REGISTER_NEW);
            boolean regHov = isMouseOver(mouseX, mouseY, left + 16, tabY, tabW, 20);
            drawModalButton(guiGraphics, left + 16, tabY, tabW, 20, "➕ REGISTER NEW", regActive ? 0xFFAA55FF : 0xFF8899AA, regHov || regActive);

            boolean existActive = (puppetTargetMode == PuppetTargetMode.EXISTING_ACTOR);
            boolean existHov = isMouseOver(mouseX, mouseY, left + 18 + tabW, tabY, tabW, 20);
            drawModalButton(guiGraphics, left + 18 + tabW, tabY, tabW, 20, "🔍 IN-WORLD MOB", existActive ? 0xFFAA55FF : 0xFF8899AA, existHov || existActive);

            boolean subActive = (puppetTargetMode == PuppetTargetMode.SUB_CHANNEL);
            boolean subHov = isMouseOver(mouseX, mouseY, left + 20 + (tabW * 2), tabY, tabW, 20);
            drawModalButton(guiGraphics, left + 20 + (tabW * 2), tabY, tabW, 20, "🔗 SUB-CHANNEL", subActive ? 0xFFBB77FF : 0xFF8899AA, subHov || subActive);

            // Row 3: Entity Type / Mob / Parent Selection
            if (puppetTargetMode == PuppetTargetMode.REGISTER_NEW) {
                guiGraphics.drawString(this.font, "Select Entity Type to Register (No World Spawn):", left + 16, top + 103, 0xFFAABBCC, false);
                if (modalEntityCatalogDropdown != null) {
                    modalEntityCatalogDropdown.render(guiGraphics, mouseX, mouseY, partialTick);
                }
            } else if (puppetTargetMode == PuppetTargetMode.EXISTING_ACTOR) {
                guiGraphics.drawString(this.font, "Choose Detected In-World Mob or Aim Crosshair:", left + 16, top + 103, 0xFFAABBCC, false);
                if (modalExistingMobsDropdown != null) {
                    modalExistingMobsDropdown.render(guiGraphics, mouseX, mouseY, partialTick);
                }
                boolean tgtHov = isMouseOver(mouseX, mouseY, left + panelWidth - 16 - 105, top + 115, 105, 20);
                drawModalButton(guiGraphics, left + panelWidth - 16 - 105, top + 115, 105, 20, "🎯 TARGET MOB", CYAN_MAIN, tgtHov);
            } else {
                guiGraphics.drawString(this.font, "Choose Existing Puppet Entity to Link As Sub-Channel:", left + 16, top + 103, 0xFFAABBCC, false);
                if (modalParentPuppetDropdown != null) {
                    modalParentPuppetDropdown.render(guiGraphics, mouseX, mouseY, partialTick);
                }
            }

            // Row 4: Entity Name
            guiGraphics.drawString(this.font, "Entity Name:", left + 16, top + 141, 0xFFAABBCC, false);
            if (modalActorNameBox != null) {
                modalActorNameBox.render(guiGraphics, mouseX, mouseY, partialTick);
            }

            // Row 5: Custom Tag (Mandatory)
            int tagLabelColor = modalTagError ? 0xFFFF3355 : 0xFFAABBCC;
            String tagLabel = modalTagError ? "Custom Tag (MANDATORY - CANNOT BE EMPTY!):" : "Custom Tag (Mandatory - all actions route to this tag):";
            guiGraphics.drawString(this.font, tagLabel, left + 16, top + 177, tagLabelColor, false);
            if (modalActorTagBox != null) {
                modalActorTagBox.render(guiGraphics, mouseX, mouseY, partialTick);
            }

            // Row 6: Explanatory / Status text
            int curTagLen = (modalActorTagBox != null) ? modalActorTagBox.getValue().trim().length() : 0;
            String tagPreview = curTagLen > 0 ? modalActorTagBox.getValue().trim() : "<tag>";
            if (modalStatusMessage != null && System.currentTimeMillis() - modalStatusMessageTime < 3500L) {
                int col = modalStatusMessage.startsWith("✓") ? 0xFF00FF88 : 0xFFFF3355;
                guiGraphics.drawString(this.font, modalStatusMessage, left + 16, top + 215, col, false);
            } else {
                if (puppetTargetMode == PuppetTargetMode.REGISTER_NEW) {
                    guiGraphics.drawString(this.font, "ℹ Registered on channel only. Actions route to @e[tag=" + tagPreview + ",limit=1]", left + 16, top + 215, 0xFF8899AA, false);
                    guiGraphics.drawString(this.font, "ℹ Spawn this entity in your world/arena with this matching tag.", left + 16, top + 228, 0xFF778899, false);
                } else if (puppetTargetMode == PuppetTargetMode.EXISTING_ACTOR) {
                    guiGraphics.drawString(this.font, "ℹ Actions on this channel route to @e[tag=" + tagPreview + ",limit=1]", left + 16, top + 215, 0xFF8899AA, false);
                    guiGraphics.drawString(this.font, "ℹ Ensure the target mob in-world has this tag applied.", left + 16, top + 228, 0xFF778899, false);
                } else {
                    guiGraphics.drawString(this.font, "ℹ Sub-channel for simultaneous actions. Linked to @e[tag=" + tagPreview + ",limit=1]", left + 16, top + 215, 0xFFBB77FF, false);
                    guiGraphics.drawString(this.font, "ℹ Shares the same in-world entity as the parent puppet channel.", left + 16, top + 228, 0xFF8899AA, false);
                }
            }

            // Row 7: Footer buttons
            int btnY = top + 275;
            boolean bHov = isMouseOver(mouseX, mouseY, left + 16, btnY, 120, 24);
            drawModalButton(guiGraphics, left + 16, btnY, 120, 24, "← BACK TO TYPES", 0xFF8899AA, bHov);

            String addBtnLabel = (puppetTargetMode == PuppetTargetMode.SUB_CHANNEL) ? "+ ADD SUB-CHANNEL" : "+ ADD PUPPET CHANNEL";
            int addBtnColor = (puppetTargetMode == PuppetTargetMode.SUB_CHANNEL) ? 0xFFBB77FF : 0xFFAA55FF;
            boolean addPHov = isMouseOver(mouseX, mouseY, left + panelWidth - 16 - 165, btnY, 165, 24);
            drawModalButton(guiGraphics, left + panelWidth - 16 - 165, btnY, 165, 24, addBtnLabel, addBtnColor, addPHov);

            // Dropdown Overlay rendered on top of all modal controls
            if (puppetTargetMode == PuppetTargetMode.REGISTER_NEW && modalEntityCatalogDropdown != null) {
                modalEntityCatalogDropdown.renderOverlay(guiGraphics, mouseX, mouseY);
            } else if (puppetTargetMode == PuppetTargetMode.EXISTING_ACTOR && modalExistingMobsDropdown != null) {
                modalExistingMobsDropdown.renderOverlay(guiGraphics, mouseX, mouseY);
            } else if (puppetTargetMode == PuppetTargetMode.SUB_CHANNEL && modalParentPuppetDropdown != null) {
                modalParentPuppetDropdown.renderOverlay(guiGraphics, mouseX, mouseY);
            }
        }
    }

    private void renderTypeCard(GuiGraphics guiGraphics, int mouseX, int mouseY, int cardX, int cardY, int cardW, int cardH, String type, int color, String icon, String title, String desc) {
        boolean hov = isMouseOver(mouseX, mouseY, cardX, cardY, cardW, cardH);
        boolean sel = modalSelectedType.equalsIgnoreCase(type);
        int cardBorder = sel ? color : (hov ? (0xAA000000 | (color & 0x00FFFFFF)) : (0x44000000 | (color & 0x00FFFFFF)));
        int cardFill = sel ? (0x35000000 | (color & 0x00FFFFFF)) : (hov ? (0x1F000000 | (color & 0x00FFFFFF)) : 0xEE09111A);
        drawBorderBox(guiGraphics, cardX, cardY, cardW, cardH, cardBorder, cardFill);

        guiGraphics.fill(cardX + 8, cardY + 8, cardX + 16, cardY + 16, color);
        if (sel) {
            guiGraphics.fill(cardX + 10, cardY + 10, cardX + 14, cardY + 14, 0xFFFFFFFF);
        }
        guiGraphics.drawString(this.font, title, cardX + 22, cardY + 8, sel ? 0xFFFFFFFF : color, false);
        guiGraphics.drawString(this.font, desc, cardX + 22, cardY + 21, sel ? 0xFFAABBCC : 0xFF778899, false);
    }

    private static boolean isLightColor(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        double luma = 0.299 * r + 0.587 * g + 0.114 * b;
        return luma > 150;
    }
}
