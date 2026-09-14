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
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.C2SDeleteMusicSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SSaveMusicSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SSpawnPuppetPacket;
import net.dandare21.fracturedutils.network.packet.C2SStartMusicSequencePacket;
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
        EXISTING_ACTOR
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

    // Channel Layout & Premiere-Style Track Header Definitions
    public static final int TRACK_HEADER_WIDTH = 135;
    private double channelScrollY = 0.0;

    private String saveFeedbackMessage = null;
    private long saveFeedbackTime = 0;

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

    private void saveCurrentSequenceToWorkingMap() {
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

    private double getLayoutScale() {
        int targetW = 740;
        int targetH = 380;
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

        int topY = 36;
        int leftX = 14;

        // 1. Sequence File Selector Dropdown
        List<CyberpunkDropdown.DropdownEntry<String>> fileEntries = new ArrayList<>();
        List<String> sortedFiles = new ArrayList<>(getActiveSequenceMap().keySet());
        Collections.sort(sortedFiles);
        for (String f : sortedFiles) {
            fileEntries.add(new CyberpunkDropdown.DropdownEntry<>(f, Component.literal(f)));
        }

        this.fileDropdown = new CyberpunkDropdown<>(leftX, topY, 150, 20, Component.literal("Sequence File"));
        this.fileDropdown.setOptions(fileEntries);
        this.fileDropdown.selectByValue(currentFileName);
        this.fileDropdown.setOnSelect(entry -> selectSequenceFile(entry.getValue()));
        this.addRenderableWidget(this.fileDropdown);

        CyberpunkButton addFileBtn = new CyberpunkButton(leftX + 155, topY, 55, 20, Component.literal("+ NEW"), b -> openCreateSequenceModal(), CYAN_MAIN, false);
        addFileBtn.setTooltip(Tooltip.create(Component.literal("Create a new music sequence (select song first)")));
        this.addRenderableWidget(addFileBtn);

        // 2. Resourcepack Song Selector Dropdown
        Set<String> dynamicTracks = new LinkedHashSet<>(availableTracks);
        dynamicTracks.addAll(net.dandare21.fracturedutils.sound.event.AudioTrackDiscovery.getAllAvailableTracks());
        if (currentSequence.getSongTrack() != null && !currentSequence.getSongTrack().isEmpty()) {
            dynamicTracks.add(currentSequence.getSongTrack());
        }

        List<CyberpunkDropdown.DropdownEntry<String>> songEntries = new ArrayList<>();
        songEntries.add(new CyberpunkDropdown.DropdownEntry<>("", Component.literal("[None / No Song]"), Component.literal("Sequence runs timed actions without audio track")));

        for (String track : dynamicTracks) {
            String label = net.dandare21.fracturedutils.sound.event.AudioTrackDiscovery.formatTrackLabel(track);
            songEntries.add(new CyberpunkDropdown.DropdownEntry<>(track, Component.literal(label), Component.literal(track)));
        }

        this.trackDropdown = new CyberpunkDropdown<>(leftX + 215, topY, 190, 20, Component.literal("Song Track"));
        this.trackDropdown.setMaxVisibleItems(8);
        this.trackDropdown.setOptions(songEntries);
        this.trackDropdown.selectByValue(currentSequence.getSongTrack());
        this.trackDropdown.setOnSelect(selected -> {
            currentSequence.setSongTrack(selected.getValue());
            saveCurrentSequenceToWorkingMap();
        });
        this.addRenderableWidget(this.trackDropdown);

        // BPM Input Box
        this.bpmEditBox = new EditBox(this.font, leftX + 410, topY, 45, 20, Component.literal("BPM"));
        this.bpmEditBox.setMaxLength(3);
        this.bpmEditBox.setValue(String.valueOf(currentSequence.getBpm()));
        this.bpmEditBox.setHint(Component.literal("120"));
        this.bpmEditBox.setTooltip(Tooltip.create(Component.literal("Song Tempo in BPM (Beats Per Minute)")));
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

        // Looping Checkbox
        this.loopingCheckbox = new CyberpunkCheckbox(
                leftX + 460, topY, 75, 20,
                Component.literal("Loop Song"),
                currentSequence.isLooping(),
                checked -> {
                    currentSequence.setLooping(checked);
                    saveCurrentSequenceToWorkingMap();
                }
        );
        this.addRenderableWidget(this.loopingCheckbox);

        // Storage Mode Toggle (if in multiplayer)
        boolean isMultiplayer = Minecraft.getInstance().getCurrentServer() != null || !Minecraft.getInstance().isSingleplayer();
        if (isMultiplayer) {
            CyberpunkButton modeBtn = new CyberpunkButton(effWidth - 160, topY, 145, 20,
                    Component.literal(isClientMode ? "LOCAL STORAGE" : "SERVER STORAGE"),
                    b -> {
                        saveCurrentSequenceToWorkingMap();
                        this.isClientMode = !this.isClientMode;
                        Map<String, String> activeMap = getActiveSequenceMap();
                        if (!activeMap.containsKey(currentFileName)) {
                            this.currentFileName = activeMap.isEmpty() ? "new_music_sequence.json" : activeMap.keySet().iterator().next();
                        }
                        loadCurrentFileSequence();
                        this.init();
                    },
                    isClientMode ? 0xFFFFD700 : CYAN_MAIN,
                    false
            );
            this.addRenderableWidget(modeBtn);
        }

        // 3. Timeline Control Toolbar (Play/Pause, Zoom, + Keyframe, Time position)
        int toolbarY = topY + 26;

        this.playPreviewBtn = new CyberpunkButton(leftX, toolbarY, 100, 20,
                Component.literal(isPreviewPlaying ? "⏸ PAUSE" : "▶ PREVIEW"),
                b -> togglePreviewPlayback(),
                isPreviewPlaying ? 0xFFFFD700 : 0xFF00FF88,
                false
        );
        this.addRenderableWidget(this.playPreviewBtn);

        CyberpunkButton zoomInBtn = new CyberpunkButton(leftX + 110, toolbarY, 65, 20, Component.literal("ZOOM +"), b -> adjustZoom(1.25));
        CyberpunkButton zoomOutBtn = new CyberpunkButton(leftX + 180, toolbarY, 65, 20, Component.literal("ZOOM -"), b -> adjustZoom(0.8));
        this.addRenderableWidget(zoomInBtn);
        this.addRenderableWidget(zoomOutBtn);

        CyberpunkButton addKeyframeBtn = new CyberpunkButton(leftX + 255, toolbarY, 110, 20, Component.literal("+ KEYFRAME"), b -> openEditEntryModal(-1, (long) playheadMs), CYAN_MAIN, false);
        this.addRenderableWidget(addKeyframeBtn);

        CyberpunkButton sortBtn = new CyberpunkButton(leftX + 375, toolbarY, 85, 20, Component.literal("SORT TIME"), b -> sortEntries(), 0xFFFFD700, false);
        this.addRenderableWidget(sortBtn);

        CyberpunkButton autoFollowBtn = new CyberpunkButton(leftX + 465, toolbarY, 110, 20,
                Component.literal(autoFollowPlayhead ? "FOLLOW: ON" : "FOLLOW: OFF"),
                b -> {
                    this.autoFollowPlayhead = !this.autoFollowPlayhead;
                    this.init();
                },
                autoFollowPlayhead ? 0xFF00FF88 : 0xFF8899AA,
                false
        );
        autoFollowBtn.setTooltip(Tooltip.create(Component.literal("Auto-scroll timeline to follow playhead during playback")));
        this.addRenderableWidget(autoFollowBtn);

        // 4. Footer Action Buttons
        int footerY = effHeight - 30;

        CyberpunkButton saveBtn = new CyberpunkButton(effWidth - 115, footerY, 100, 22, Component.literal("SAVE FILE"), b -> saveCurrentFile(), CYAN_MAIN, false);
        CyberpunkButton runBtn = new CyberpunkButton(effWidth - 225, footerY, 100, 22, Component.literal("▶ RUN SERVER"), b -> startSequencePlayback(currentFileName), 0xFF00FF88, false);
        CyberpunkButton stopBtn = new CyberpunkButton(effWidth - 325, footerY, 90, 22, Component.literal("⏹ STOP MUSIC"), b -> MusicSequenceManager.getInstance().stopAllSequences(Minecraft.getInstance().getSingleplayerServer()), 0xFFFF3366, false);
        
        CyberpunkButton hubBtn = new CyberpunkButton(leftX, footerY, 125, 22, Component.literal("← ALL SEQUENCES"), b -> returnToSelectScreen(), CYAN_MAIN, false);
        hubBtn.setTooltip(Tooltip.create(Component.literal("Return to Music Sequence Hub selection menu")));
        CyberpunkButton closeBtn = new CyberpunkButton(leftX + 130, footerY, 70, 22, Component.literal("CLOSE"), b -> this.onClose(), 0xFF8899AA, false);

        this.addRenderableWidget(saveBtn);
        this.addRenderableWidget(runBtn);
        this.addRenderableWidget(stopBtn);
        this.addRenderableWidget(hubBtn);
        this.addRenderableWidget(closeBtn);

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
            if (this.playheadMs >= endMs) {
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
        if (isPreviewPlaying) {
            EventAudioClientController.getInstance().stopAudio(0);
            this.isPreviewPlaying = false;
        }
        if (isPreviewCameraActive) {
            CustomCameraManager.clearCustomCamera();
            this.isPreviewCameraActive = false;
        }
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

    public double getPlayheadMs() {
        return this.playheadMs;
    }

    public void openEditEntryModal(int index) {
        long defaultTs = (index >= 0 && index < currentSequence.getEntries().size()) ? currentSequence.getEntries().get(index).getTimestampMs() : (long) playheadMs;
        openEditEntryModal(index, defaultTs);
    }

    public void openEditEntryModal(int index, long defaultTimestampMs) {
        saveCurrentSequenceToWorkingMap();
        MusicSequenceEntry targetEntry = (index >= 0 && index < currentSequence.getEntries().size()) ? currentSequence.getEntries().get(index) : new MusicSequenceEntry(defaultTimestampMs, "COMMAND", "", "");
        int chIdx = getChannelIndex(targetEntry);
        MusicSequenceChannel channel = (chIdx >= 0 && chIdx < currentSequence.getChannels().size()) ? currentSequence.getChannels().get(chIdx) : null;
        Runnable deleteAction = index >= 0 ? () -> {
            int existingIdx = currentSequence.getEntries().indexOf(targetEntry);
            if (existingIdx >= 0) {
                deleteEntry(existingIdx);
            } else if (index < currentSequence.getEntries().size()) {
                deleteEntry(index);
            }
        } : null;

        this.minecraft.setScreen(new EditMusicEntryModalScreen(this, targetEntry, channel, updated -> {
            int existingIdx = currentSequence.getEntries().indexOf(targetEntry);
            if (existingIdx >= 0) {
                currentSequence.getEntries().set(existingIdx, updated);
            } else if (index >= 0 && index < currentSequence.getEntries().size()) {
                currentSequence.getEntries().set(index, updated);
            } else {
                currentSequence.getEntries().add(updated);
            }
            currentSequence.sortEntriesByTimestamp();
            saveCurrentSequenceToWorkingMap();
            this.init();
        }, deleteAction));
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
            saveCurrentSequenceToWorkingMap();
            this.init();
        }
    }

    public void duplicateSelectedEntry() {
        if (selectedEntryIndex >= 0 && selectedEntryIndex < currentSequence.getEntries().size()) {
            duplicateEntry(selectedEntryIndex);
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
            playButtonSound();
            this.init();
        }
    }

    public void deleteChannel(int index) {
        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        if (index >= 0 && index < channels.size()) {
            MusicSequenceChannel removed = channels.remove(index);
            currentSequence.getEntries().removeIf(e -> removed.getId().equalsIgnoreCase(e.getChannelId()));
            saveCurrentSequenceToWorkingMap();
            this.init();
        }
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

    private long snapTimestamp(long rawMs, boolean bypassSnap) {
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
        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        return effWidth - 28;
    }

    private int getTimelineTrackWidth() {
        return Math.max(100, getTimelineWidth() - TRACK_HEADER_WIDTH);
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

        double scale = getLayoutScale();
        int timelineLeft = 14;
        int trackHeaderLeft = timelineLeft;
        int timelineTrackLeft = timelineLeft + TRACK_HEADER_WIDTH;
        int timelineTrackWidth = getTimelineTrackWidth();
        double scaledMouseX = scale < 1.0 ? mouseX / scale : mouseX;

        // Vertical Track Scrolling over Left Track Headers
        if (scaledMouseX >= trackHeaderLeft && scaledMouseX < timelineTrackLeft) {
            channelScrollY = Math.max(0.0, channelScrollY - (delta * 24.0));
            return true;
        }

        if (scaledMouseX >= timelineTrackLeft && scaledMouseX <= timelineTrackLeft + timelineTrackWidth) {
            if (hasControlDown()) {
                // Modern DAW: Ctrl + Mouse Wheel = Mouse-Centered Zoom
                double oldPixelsPerSecond = this.pixelsPerSecond;
                double zoomFactor = delta > 0 ? 1.25 : 0.8;
                double newPixelsPerSecond = Math.max(10.0, Math.min(400.0, oldPixelsPerSecond * zoomFactor));

                double mouseTimeMs = timeScrollMs + ((scaledMouseX - timelineTrackLeft) / oldPixelsPerSecond) * 1000.0;
                this.pixelsPerSecond = newPixelsPerSecond;
                this.timeScrollMs = Math.max(0.0, mouseTimeMs - ((scaledMouseX - timelineTrackLeft) / newPixelsPerSecond) * 1000.0);
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
        double scale = getLayoutScale();
        int smX = (int) (mouseX / scale);
        int smY = (int) (mouseY / scale);

        if (activeModal != ModalType.NONE) {
            handleModalClick(smX, smY, button);
            return true;
        }

        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }

        if (fileDropdown != null && fileDropdown.isOpen() && fileDropdown.isMouseOverMenu(mouseX, mouseY)) {
            return fileDropdown.mouseClicked(mouseX, mouseY, button);
        }
        if (trackDropdown != null && trackDropdown.isOpen() && trackDropdown.isMouseOverMenu(mouseX, mouseY)) {
            return trackDropdown.mouseClicked(mouseX, mouseY, button);
        }

        int timelineLeft = 14;
        int effWidth = (int) (this.width / scale);
        int timelineWidth = effWidth - 28;
        int trackHeaderLeft = timelineLeft;
        int timelineTrackLeft = timelineLeft + TRACK_HEADER_WIDTH;
        int timelineTrackWidth = getTimelineTrackWidth();
        int timelineTop = 90;
        int rulerHeight = 24;
        int waveformHeight = 45;
        int channelHeight = 28;

        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        int channelCount = channels.size();
        int contentHeight = rulerHeight + waveformHeight + (channelCount * channelHeight) + 32;
        int maxTimelineHeight = Math.max(160, (int)(this.height / scale) - 30 - timelineTop - 8);
        int totalHeight = Math.min(maxTimelineHeight, contentHeight);
        int canvasY = timelineTop + rulerHeight;
        int trackAreaY = canvasY + waveformHeight;

        // 0. Check Click on Header Inspector [⧉ DUP] or [🗑 DEL] Button
        if (selectedEntryIndex >= 0 && selectedEntryIndex < currentSequence.getEntries().size()) {
            int statusX = effWidth - 250;
            int dupBtnX = statusX + 125;
            if (mouseX >= dupBtnX && mouseX <= dupBtnX + 45 && mouseY >= 6 && mouseY <= 20) {
                duplicateSelectedEntry();
                return true;
            }
            int delBtnX = statusX + 175;
            if (mouseX >= delBtnX && mouseX <= delBtnX + 45 && mouseY >= 6 && mouseY <= 20) {
                deleteEntry(selectedEntryIndex);
                selectedEntryIndex = -1;
                return true;
            }
        }

        // 1. Check Click on Left Track Header Column (Premiere Preset Space)
        if (mouseX >= trackHeaderLeft && mouseX < timelineTrackLeft) {
            // Check delete button [✕] for each channel row
            for (int c = 0; c < channels.size(); c++) {
                int trackY = trackAreaY + (c * channelHeight) - (int) channelScrollY;
                if (trackY >= trackAreaY && trackY + channelHeight <= timelineTop + totalHeight) {
                    if (mouseX >= timelineTrackLeft - 22 && mouseX <= timelineTrackLeft - 6 && mouseY >= trackY + 5 && mouseY <= trackY + 23) {
                        this.modalDeleteChannelIndex = c;
                        this.activeModal = ModalType.CONFIRM_DELETE_CHANNEL;
                        return true;
                    }
                }
            }

            // Check click on "+ ADD CHANNEL" button below all channels
            int addBtnY = trackAreaY + (channelCount * channelHeight) + 4 - (int) channelScrollY;
            if (addBtnY >= trackAreaY && addBtnY + 22 <= timelineTop + totalHeight + 10) {
                if (mouseX >= trackHeaderLeft + 4 && mouseX <= timelineTrackLeft - 4 && mouseY >= addBtnY && mouseY <= addBtnY + 22) {
                    openAddChannelModal();
                    return true;
                }
            }
        }

        // 2. Check Click on Right Timeline Tracks Canvas
        if (mouseX >= timelineTrackLeft && mouseX <= timelineLeft + timelineWidth) {
            // Middle Click (button 2) or Right Click on empty canvas -> Start Timeline Panning
            if (button == 2 || (button == 1 && mouseY >= timelineTop && mouseY <= timelineTop + totalHeight)) {
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
            if (Math.abs(mouseX - playheadX) <= 8 && mouseY >= timelineTop && mouseY <= timelineTop + totalHeight) {
                this.isDraggingPlayhead = true;
                return true;
            }

            // Click on Ruler -> START Marker, END Marker, or Seek Playhead
            if (mouseY >= timelineTop && mouseY <= timelineTop + rulerHeight) {
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
            if (mouseY >= canvasY && mouseY < trackAreaY) {
                double relX = mouseX - timelineTrackLeft;
                double clickedTimeMs = timeScrollMs + (relX / pixelsPerSecond) * 1000.0;
                this.playheadMs = Math.max(0.0, Math.min(getEffectiveEndMs(), clickedTimeMs));
                this.isDraggingPlayhead = true;
                relocatePlaybackAudio((long) this.playheadMs);
                return true;
            }

            // Check Click on Keyframe Nodes, Duration Bars, or Track Canvas
            if (mouseY >= trackAreaY && mouseY <= timelineTop + totalHeight) {
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

                    // Check clicking on right edge stretch/resize handle (all duration actions EXCEPT puppet)
                    if (button == 0 && !isPuppet && totalW > 0) {
                        double rightEdgeX = entryX + totalW;
                        if (Math.abs(mouseX - rightEdgeX) <= 6 && mouseY >= entryTrackY && mouseY <= entryTrackY + channelHeight) {
                            this.isStretchingDuration = true;
                            this.stretchingEntry = entry;
                            this.stretchingEntryIndex = i;
                            this.selectedEntryIndex = i;
                            return true;
                        }
                    }

                    boolean hit = (totalW > 0)
                            ? (mouseX >= entryX - 4 && mouseX <= entryX + totalW + 4 && mouseY >= entryTrackY && mouseY <= entryTrackY + channelHeight)
                            : (Math.abs(mouseX - entryX) <= 8 && mouseY >= entryTrackY && mouseY <= entryTrackY + channelHeight);

                    if (hit) {
                        this.selectedEntryIndex = i;
                        if (hasShiftDown() && (button == 0 || button == 1)) {
                            deleteEntry(i);
                            this.selectedEntryIndex = -1;
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
                            if (!hasSpawn) {
                                newEntry.setSubAction("SPAWN");
                                String aTag = channel.getActorTag().isBlank() ? channel.getName().toLowerCase(Locale.ROOT).replace(" ", "_") : channel.getActorTag();
                                String eType = channel.getEntityTypeId().isBlank() ? "fractured_utils:void_herald" : channel.getEntityTypeId();
                                String aName = channel.getActorName().isBlank() ? channel.getName() : channel.getActorName();
                                String px = "~", py = "~", pz = "~";
                                if (minecraft != null && minecraft.player != null) {
                                    px = String.format(Locale.ROOT, "%.2f", minecraft.player.getX());
                                    py = String.format(Locale.ROOT, "%.2f", minecraft.player.getY());
                                    pz = String.format(Locale.ROOT, "%.2f", minecraft.player.getZ());
                                }
                                newEntry.setCommand(String.format(Locale.ROOT, "summon %s %s %s %s {Tags:[\"%s\",\"puppet_actor\"],CustomName:'{\"text\":\"%s\"}',NoAI:0b,PersistenceRequired:1b}",
                                        eType, px, py, pz, aTag, aName));
                                newEntry.setDescription("Spawn " + aName + " @ " + px + " " + py + " " + pz);
                            } else {
                                String aTag = channel.getActorTag().isBlank() ? channel.getName().toLowerCase(Locale.ROOT).replace(" ", "_") : channel.getActorTag();
                                newEntry.setSubAction("EXECUTE_ACTION");
                                newEntry.setCommand(String.format(Locale.ROOT, "puppet_action tag:%s action:fractured_utils:leap_slam target:@p windup:500 jump:1500 duration:800 recovery:600", aTag));
                                newEntry.setWindupMs(500);
                                newEntry.setJumpMs(1500);
                                newEntry.setDurationMs(800);
                                newEntry.setRecoveryMs(600);
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
                            newEntry.setDescription("Narrator: " + line.getText());
                            newEntry.setSubAction("SINGLE_DIALOG");
                        } else if (channel.getType().equalsIgnoreCase("CAMERA")) {
                            newEntry.setActionType(MusicSequenceChannel.TYPE_CAMERA);
                            newEntry.setSubAction("STATIC");
                            newEntry.setDurationMs(3000);
                            newEntry.setUseCamera(true);
                            if (minecraft != null && minecraft.player != null) {
                                Vec3 eyePos = minecraft.player.getEyePosition();
                                newEntry.setCameraX(eyePos.x);
                                newEntry.setCameraY(eyePos.y);
                                newEntry.setCameraZ(eyePos.z);
                                newEntry.setCameraYaw(minecraft.player.getYRot());
                                newEntry.setCameraPitch(minecraft.player.getXRot());
                                newEntry.setCameraFov(70.0);
                            }
                            newEntry.setCameraMode("STATIC");
                            newEntry.setCameraInterpolate(true);
                            newEntry.setDescription(String.format(Locale.US, "Camera (%.1f, %.1f, %.1f)",
                                    newEntry.getCameraX(), newEntry.getCameraY(), newEntry.getCameraZ()));
                            newEntry.setCommand(String.format(Locale.US, "camera static %.1f %.1f %.1f %.0f %.0f 70",
                                    newEntry.getCameraX(), newEntry.getCameraY(), newEntry.getCameraZ(),
                                    newEntry.getCameraYaw(), newEntry.getCameraPitch()));
                        }
                        currentSequence.getEntries().add(newEntry);
                        currentSequence.sortEntriesByTimestamp();
                        saveCurrentSequenceToWorkingMap();
                        int newIndex = currentSequence.getEntries().indexOf(newEntry);
                        this.selectedEntryIndex = newIndex;
                        openEditEntryModal(newIndex, clickedMs);
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

        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
            dragX /= scale;
            dragY /= scale;
        }

        int timelineTrackLeft = 14 + TRACK_HEADER_WIDTH;

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
            } else {
                stretchingEntry.setDurationMs((int) newDurationMs);
            }
            return true;
        }

        if (draggedEntry != null) {
            double relX = mouseX - timelineTrackLeft;
            long rawTs = (long) Math.max(0.0, timeScrollMs + (relX / pixelsPerSecond) * 1000.0);
            long snappedTs = snapTimestamp(rawTs, hasAltDown());
            draggedEntry.setTimestampMs(snappedTs);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeModal != ModalType.NONE) {
            return true;
        }

        isPanningTimeline = false;
        isDraggingStartMarker = false;
        isDraggingEndMarker = false;
        if (isStretchingDuration) {
            isStretchingDuration = false;
            stretchingEntry = null;
            stretchingEntryIndex = -1;
            saveCurrentSequenceToWorkingMap();
        }
        if (draggedEntry != null) {
            currentSequence.sortEntriesByTimestamp();
            if (selectedEntryIndex >= 0) {
                selectedEntryIndex = currentSequence.getEntries().indexOf(draggedEntry);
            }
            saveCurrentSequenceToWorkingMap();
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

        if (bpmEditBox != null && bpmEditBox.isFocused()) return super.keyPressed(keyCode, scanCode, modifiers);

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        if (hasControlDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_D) {
            duplicateSelectedEntry();
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            togglePreviewPlayback();
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (selectedEntryIndex >= 0 && selectedEntryIndex < currentSequence.getEntries().size()) {
                deleteEntry(selectedEntryIndex);
                selectedEntryIndex = -1;
                return true;
            }
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

        if (hasControlDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
            saveCurrentFile();
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
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
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

        this.renderBackground(guiGraphics);

        int timelineLeft = 14;
        int timelineWidth = effWidth - 28;
        int trackHeaderLeft = timelineLeft;
        int timelineTrackLeft = timelineLeft + TRACK_HEADER_WIDTH;
        int timelineTrackWidth = getTimelineTrackWidth();
        int timelineTop = 90;
        int rulerHeight = 24;
        int waveformHeight = 45;
        int channelHeight = 28;

        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        int channelCount = channels.size();
        int contentHeight = rulerHeight + waveformHeight + (channelCount * channelHeight) + 32;
        int maxTimelineHeight = Math.max(160, effHeight - 30 - timelineTop - 8);
        int totalHeight = Math.min(maxTimelineHeight, contentHeight);

        // Clamp channel vertical scroll
        int maxScrollY = Math.max(0, contentHeight - totalHeight);
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

        if (isPreviewPlaying || isDraggingPlayhead) {
            updatePreviewCamera();
        }

        // Header Title Bar
        guiGraphics.fill(0, 0, effWidth, 30, CYAN_BG);
        guiGraphics.fill(0, 29, effWidth, 30, CYAN_MAIN);
        guiGraphics.drawString(this.font, "TIMELINE MUSIC SEQUENCE ORCHESTRATOR", 16, 9, CYAN_MAIN, false);

        // Playhead Time Counter & Inspector Status
        long curSec = (long) (playheadMs / 1000.0);
        long curMs = (long) (playheadMs % 1000.0);
        String playheadTimeStr = String.format("%02d:%02d.%03d", curSec / 60, curSec % 60, curMs);
        int headerPlayheadX = Math.max(260, effWidth / 2 - 50);
        guiGraphics.drawString(this.font, "PLAYHEAD: " + playheadTimeStr, headerPlayheadX, 9, 0xFFFFD700, false);

        int statusX = effWidth - 250;
        if (selectedEntryIndex >= 0 && selectedEntryIndex < currentSequence.getEntries().size()) {
            MusicSequenceEntry sel = currentSequence.getEntries().get(selectedEntryIndex);
            long sec = sel.getTimestampMs() / 1000;
            long ms = sel.getTimestampMs() % 1000;
            int chIdx = getChannelIndex(sel);
            String chName = (chIdx < channels.size()) ? channels.get(chIdx).getName() : sel.getActionType();
            long slamTotalMs = sel.getTimestampMs() + sel.getWindupMs() + sel.getJumpMs();
            long sSec = slamTotalMs / 1000;
            long sMs = slamTotalMs % 1000;
            String selStr = (sel.getWindupMs() + sel.getJumpMs() > 0)
                    ? String.format("CH%d (%s) @ %02d:%02d.%03d [SLAM %02d:%02d.%03d]", chIdx + 1, chName, sec / 60, sec % 60, ms, sSec / 60, sSec % 60, sMs)
                    : String.format("CH%d (%s) @ %02d:%02d.%03d", chIdx + 1, chName, sec / 60, sec % 60, ms);
            if (this.font.width(selStr) > 230) {
                selStr = this.font.plainSubstrByWidth(selStr, 224) + "..";
            }
            guiGraphics.drawString(this.font, selStr, statusX - 85, 9, CYAN_MAIN, false);

            int dupBtnX = statusX + 125;
            boolean isHoverDup = (scaledMouseX >= dupBtnX && scaledMouseX <= dupBtnX + 45 && scaledMouseY >= 6 && scaledMouseY <= 20);
            guiGraphics.fill(dupBtnX, 6, dupBtnX + 45, 20, isHoverDup ? 0xFF00E5FF : 0x8800E5FF);
            guiGraphics.drawCenteredString(this.font, "⧉ DUP", dupBtnX + 22, 9, isHoverDup ? 0xFF000000 : 0xFFFFFFFF);

            int delBtnX = statusX + 175;
            boolean isHoverDel = (scaledMouseX >= delBtnX && scaledMouseX <= delBtnX + 45 && scaledMouseY >= 6 && scaledMouseY <= 20);
            guiGraphics.fill(delBtnX, 6, delBtnX + 45, 20, isHoverDel ? 0xFFFF3355 : 0x88FF3355);
            guiGraphics.drawCenteredString(this.font, "🗑 DEL", delBtnX + 22, 9, 0xFFFFFFFF);
        } else if (hasUnsavedChanges()) {
            guiGraphics.drawString(this.font, "* UNSAVED CHANGES", statusX + 60, 9, 0xFFFF3366, false);
        } else if (saveFeedbackMessage != null && System.currentTimeMillis() - saveFeedbackTime < 3000L) {
            String fb = saveFeedbackMessage;
            if (this.font.width(fb) > 240) {
                fb = this.font.plainSubstrByWidth(fb, 234) + "..";
            }
            guiGraphics.drawString(this.font, fb, statusX, 9, 0xFF00FF88, false);
        }

        // Timeline Outer Box
        guiGraphics.fill(timelineLeft, timelineTop, timelineLeft + timelineWidth, timelineTop + totalHeight, CYAN_BG);
        guiGraphics.fill(timelineLeft, timelineTop, timelineLeft + timelineWidth, timelineTop + 1, CARD_BORDER);
        guiGraphics.fill(timelineLeft, timelineTop + totalHeight - 1, timelineLeft + timelineWidth, timelineTop + totalHeight, CARD_BORDER);
        guiGraphics.fill(timelineLeft, timelineTop, timelineLeft + 1, timelineTop + totalHeight, CARD_BORDER);
        guiGraphics.fill(timelineLeft + timelineWidth - 1, timelineTop, timelineLeft + timelineWidth, timelineTop + totalHeight, CARD_BORDER);

        int waveTopY = timelineTop + rulerHeight;
        int trackAreaY = waveTopY + waveformHeight;

        // ==========================================
        // 1. LEFT COLUMN: PREMIERE-STYLE TRACK HEADERS
        // ==========================================

        // A. Header block for Ruler row ("TRACKS")
        guiGraphics.fill(trackHeaderLeft, timelineTop, timelineTrackLeft, timelineTop + rulerHeight, 0xEE081622);
        guiGraphics.fill(trackHeaderLeft, timelineTop + rulerHeight - 1, timelineTrackLeft, timelineTop + rulerHeight, 0xAA00E5FF);
        guiGraphics.drawString(this.font, "CHANNELS", trackHeaderLeft + 10, timelineTop + 8, CYAN_MAIN, false);

        // B. Header block for Waveform row ("🎵 AUDIO")
        guiGraphics.fill(trackHeaderLeft, waveTopY, timelineTrackLeft, waveTopY + waveformHeight, 0xEE05101A);
        guiGraphics.fill(trackHeaderLeft, waveTopY, trackHeaderLeft + 4, waveTopY + waveformHeight, CYAN_MAIN);
        guiGraphics.fill(trackHeaderLeft, waveTopY + waveformHeight - 1, timelineTrackLeft, waveTopY + waveformHeight, 0x6600E5FF);
        guiGraphics.drawString(this.font, "🎵 AUDIO / SONG", trackHeaderLeft + 8, waveTopY + 8, CYAN_MAIN, false);

        String trackName = currentSequence.getSongTrack();
        if (trackName != null && !trackName.isBlank()) {
            String shortSong = net.dandare21.fracturedutils.sound.event.AudioTrackDiscovery.formatTrackLabel(trackName);
            if (this.font.width(shortSong) > TRACK_HEADER_WIDTH - 16) {
                shortSong = this.font.plainSubstrByWidth(shortSong, TRACK_HEADER_WIDTH - 22) + "..";
            }
            guiGraphics.drawString(this.font, shortSong, trackHeaderLeft + 8, waveTopY + 24, 0xFFAABBCC, false);
        } else {
            guiGraphics.drawString(this.font, "[No Audio Track]", trackHeaderLeft + 8, waveTopY + 24, 0xFF778899, false);
        }

        // C. Scissored Channel Track Headers
        guiGraphics.enableScissor(
                (int) (trackHeaderLeft * scale),
                (int) (trackAreaY * scale),
                (int) (timelineTrackLeft * scale),
                (int) ((timelineTop + totalHeight) * scale)
        );

        for (int c = 0; c < channels.size(); c++) {
            MusicSequenceChannel ch = channels.get(c);
            int trackY = trackAreaY + (c * channelHeight) - (int) channelScrollY;

            int rowBg = (c % 2 == 0) ? 0xEE0A1828 : 0xEE071220;
            guiGraphics.fill(trackHeaderLeft, trackY, timelineTrackLeft, trackY + channelHeight, rowBg);
            guiGraphics.fill(trackHeaderLeft, trackY + channelHeight - 1, timelineTrackLeft, trackY + channelHeight, 0x4400E5FF);

            // Left 4px accent stripe with channel color
            guiGraphics.fill(trackHeaderLeft, trackY, trackHeaderLeft + 4, trackY + channelHeight, ch.getColor());

            // Channel Title
            String title = "CH " + (c + 1) + ": " + ch.getName();
            if (this.font.width(title) > TRACK_HEADER_WIDTH - 28) {
                title = this.font.plainSubstrByWidth(title, TRACK_HEADER_WIDTH - 34) + "..";
            }
            guiGraphics.drawString(this.font, title, trackHeaderLeft + 8, trackY + 5, ch.getColor(), false);

            // Channel Sub-label (Actor / Type)
            String sub = ch.getType();
            if (ch.getType().equalsIgnoreCase("PUPPET")) {
                if (ch.getActorTag() != null && !ch.getActorTag().isBlank()) {
                    sub = "🎭 #" + ch.getActorTag();
                } else if (!ch.getPuppetActor().isBlank()) {
                    sub = "🎭 " + ch.getPuppetActor();
                }
            }
            if (this.font.width(sub) > TRACK_HEADER_WIDTH - 28) {
                sub = this.font.plainSubstrByWidth(sub, TRACK_HEADER_WIDTH - 34) + "..";
            }
            guiGraphics.drawString(this.font, sub, trackHeaderLeft + 8, trackY + 16, 0xFF8899AA, false);

            // Delete Channel Button [✕]
            boolean isHoverDel = (scaledMouseX >= timelineTrackLeft - 22 && scaledMouseX <= timelineTrackLeft - 6 && scaledMouseY >= trackY + 5 && scaledMouseY <= trackY + 23);
            guiGraphics.fill(timelineTrackLeft - 22, trackY + 5, timelineTrackLeft - 6, trackY + 21, isHoverDel ? 0x88FF3355 : 0x22334455);
            guiGraphics.drawCenteredString(this.font, "✕", timelineTrackLeft - 14, trackY + 9, isHoverDel ? 0xFFFFFFFF : 0xFFAABBCC);
        }

        // D. "+ ADD CHANNEL" Button below all channels
        int addBtnY = trackAreaY + (channelCount * channelHeight) + 4 - (int) channelScrollY;
        boolean isHoverAdd = (scaledMouseX >= trackHeaderLeft + 4 && scaledMouseX <= timelineTrackLeft - 4 && scaledMouseY >= addBtnY && scaledMouseY <= addBtnY + 22);

        guiGraphics.fill(trackHeaderLeft + 4, addBtnY, timelineTrackLeft - 4, addBtnY + 22, isHoverAdd ? 0xDD00E5FF : 0x2200E5FF);
        guiGraphics.fill(trackHeaderLeft + 4, addBtnY, timelineTrackLeft - 4, addBtnY + 1, CARD_BORDER);
        guiGraphics.fill(trackHeaderLeft + 4, addBtnY + 21, timelineTrackLeft - 4, addBtnY + 22, CARD_BORDER);
        guiGraphics.fill(trackHeaderLeft + 4, addBtnY, trackHeaderLeft + 5, addBtnY + 22, CARD_BORDER);
        guiGraphics.fill(timelineTrackLeft - 5, addBtnY, timelineTrackLeft - 4, addBtnY + 22, CARD_BORDER);
        guiGraphics.drawCenteredString(this.font, "+ ADD CHANNEL", trackHeaderLeft + (TRACK_HEADER_WIDTH / 2), addBtnY + 7, isHoverAdd ? 0xFF000000 : CYAN_MAIN);

        guiGraphics.disableScissor();

        // ==========================================
        // 2. RIGHT COLUMN: TIMELINE TRACKS & GRID
        // ==========================================

        // Scissor Timeline Track Area so zoom/scroll never bleeds into headers
        guiGraphics.enableScissor(
                (int) (timelineTrackLeft * scale),
                (int) (timelineTop * scale),
                (int) ((timelineLeft + timelineWidth) * scale),
                (int) ((timelineTop + totalHeight) * scale)
        );

        // A. Time Ruler (Top Axis)
        guiGraphics.fill(timelineTrackLeft, timelineTop, timelineLeft + timelineWidth, timelineTop + rulerHeight, 0xEE081622);
        guiGraphics.fill(timelineTrackLeft, timelineTop + rulerHeight - 1, timelineLeft + timelineWidth, timelineTop + rulerHeight, 0xAA00E5FF);

        double secondsVisible = timelineTrackWidth / pixelsPerSecond;
        int stepSec = secondsVisible > 30 ? 5 : 1;

        for (int sec = 0; sec <= (int) secondsVisible + 20; sec += stepSec) {
            double tickMs = (sec * 1000.0);
            double tickX = timelineTrackLeft + ((tickMs - timeScrollMs) / 1000.0) * pixelsPerSecond;

            if (tickX >= timelineTrackLeft && tickX <= timelineLeft + timelineWidth) {
                int tickY2 = timelineTop + rulerHeight - (sec % 5 == 0 ? 12 : 6);
                guiGraphics.fill((int) tickX, tickY2, (int) tickX + 1, timelineTop + rulerHeight, 0xAA00E5FF);

                if (sec % 5 == 0 || stepSec == 1) {
                    String timeLabel = String.format("%02d:%02d", sec / 60, sec % 60);
                    guiGraphics.drawString(this.font, timeLabel, (int) tickX - 12, timelineTop + 4, 0xFFAABBCC, false);
                }
            }
        }

        // B. Audio & Waveform Visualization Track
        guiGraphics.fill(timelineTrackLeft, waveTopY, timelineLeft + timelineWidth, waveTopY + waveformHeight, 0xEE05101A);
        guiGraphics.fill(timelineTrackLeft, waveTopY + waveformHeight - 1, timelineLeft + timelineWidth, waveTopY + waveformHeight, 0x6600E5FF);

        MusicWaveformRenderer.renderWaveform(guiGraphics, currentSequence.getSongTrack(), timelineTrackLeft, waveTopY, timelineTrackWidth, waveformHeight, timeScrollMs, pixelsPerSecond);

        // C. Channel Tracks Backgrounds
        for (int c = 0; c < channels.size(); c++) {
            int trackY = trackAreaY + (c * channelHeight) - (int) channelScrollY;
            int rowBg = (c % 2 == 0) ? 0xEE091624 : 0xEE060F1A;
            guiGraphics.fill(timelineTrackLeft, trackY, timelineLeft + timelineWidth, trackY + channelHeight, rowBg);
            guiGraphics.fill(timelineTrackLeft, trackY + channelHeight - 1, timelineLeft + timelineWidth, trackY + channelHeight, 0x3300E5FF);
        }

        // D. Rekordbox-Style Beat Grid & Red Bar Lines
        int bpm = currentSequence.getBpm();
        double beatMs = 60000.0 / (double) Math.max(20, bpm);
        double gameTickMs = 50.0;
        int gridTotalHeight = totalHeight - rulerHeight;

        // 50ms Game Tick Grid Lines
        double firstTick = Math.floor(timeScrollMs / gameTickMs);
        double lastTick = Math.ceil((timeScrollMs + (timelineTrackWidth / pixelsPerSecond) * 1000.0) / gameTickMs);

        for (double t = firstTick; t <= lastTick; t++) {
            double tMs = t * gameTickMs;
            double tickX = timelineTrackLeft + ((tMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
            if (tickX >= timelineTrackLeft && tickX <= timelineLeft + timelineWidth) {
                guiGraphics.fill((int) tickX, waveTopY + waveformHeight, (int) tickX + 1, timelineTop + totalHeight, 0x1500E5FF);
            }
        }

        // Beat Lines & Rekordbox Red Bar Lines
        double firstBeat = Math.floor(timeScrollMs / beatMs);
        double lastBeat = Math.ceil((timeScrollMs + (timelineTrackWidth / pixelsPerSecond) * 1000.0) / beatMs);

        for (double b = firstBeat; b <= lastBeat; b++) {
            double bMs = b * beatMs;
            double beatX = timelineTrackLeft + ((bMs - timeScrollMs) / 1000.0) * pixelsPerSecond;

            if (beatX >= timelineTrackLeft && beatX <= timelineLeft + timelineWidth) {
                long beatIndex = Math.round(b);
                boolean isBarStart = (beatIndex % 4 == 0);

                if (isBarStart) {
                    guiGraphics.fill((int) beatX - 1, waveTopY, (int) beatX + 1, timelineTop + totalHeight, 0xFFFF0055);
                    long barNumber = (beatIndex / 4) + 1;
                    if (barNumber > 0) {
                        guiGraphics.drawString(this.font, "B" + barNumber, (int) beatX + 3, waveTopY - 10, 0xFFFF0055, false);
                    }
                } else {
                    guiGraphics.fill((int) beatX, waveTopY, (int) beatX + 1, timelineTop + totalHeight, 0x3300E5FF);
                }
            }
        }

        // E. Sequence START & END Draggable Markers & Out-Of-Bounds Dimming
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
                        playPreviewBtn.setMessage(Component.literal("▶ PREVIEW"));
                        playPreviewBtn.setAccentColor(0xFF00FF88);
                    }
                }
            }
        }

        double startX = timelineTrackLeft + ((startMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
        double endX = timelineTrackLeft + ((endMs - timeScrollMs) / 1000.0) * pixelsPerSecond;

        // Dimmed Region BEFORE START
        if (startX > timelineTrackLeft) {
            int dimW = (int) Math.min((double) timelineTrackWidth, startX - timelineTrackLeft);
            if (dimW > 0) {
                guiGraphics.fill(timelineTrackLeft, timelineTop, timelineTrackLeft + dimW, timelineTop + totalHeight, 0xCC050A10);
                guiGraphics.drawString(this.font, "BEFORE START", timelineTrackLeft + 6, timelineTop + 6, 0xAA888888, false);
            }
        }

        // Dimmed Region AFTER END
        if (endX < timelineLeft + timelineWidth) {
            int dimX = (int) Math.max((double) timelineTrackLeft, endX);
            int dimW = (timelineLeft + timelineWidth) - dimX;
            if (dimW > 0) {
                guiGraphics.fill(dimX, timelineTop, dimX + dimW, timelineTop + totalHeight, 0xCC050A10);
                guiGraphics.drawString(this.font, "AFTER END", dimX + 6, timelineTop + 6, 0xAA888888, false);
            }
        }

        // START Line & Flag Badge
        if (startX >= timelineTrackLeft && startX <= timelineLeft + timelineWidth) {
            int sx = (int) startX;
            guiGraphics.fill(sx - 1, timelineTop, sx + 2, timelineTop + totalHeight, 0xFF00FF88);
            guiGraphics.fill(sx - 14, timelineTop, sx + 14, timelineTop + 14, 0xEE00FF88);
            guiGraphics.drawCenteredString(this.font, "IN", sx, timelineTop + 3, 0xFF000000);
        }

        // END Line & Flag Badge
        if (endX >= timelineTrackLeft && endX <= timelineLeft + timelineWidth) {
            int ex = (int) endX;
            guiGraphics.fill(ex - 1, timelineTop, ex + 2, timelineTop + totalHeight, 0xFFFF3366);
            guiGraphics.fill(ex - 16, timelineTop, ex + 16, timelineTop + 14, 0xEEFF3366);
            guiGraphics.drawCenteredString(this.font, "OUT", ex, timelineTop + 3, 0xFFFFFFFF);
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

            if (entryTrackY >= trackAreaY - 10 && entryTrackY <= timelineTop + totalHeight) {
                boolean isSelected = (i == selectedEntryIndex);

                if (totalDurationMs > 0) {
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
                    } else if ("CAMERA".equalsIgnoreCase(entry.getActionType()) || (channelIndex < channels.size() && "CAMERA".equalsIgnoreCase(channels.get(channelIndex).getType()))) {
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
                                int auraColor = (now / 300) % 2 == 0 ? 0xFFFFFFFF : MusicSequenceChannel.COLOR_CAMERA;
                                guiGraphics.fill(bx - 3, barY - 2, bx + bw + 3, barY + barHeight + 2, auraColor);
                            }

                            // Dark Magenta/Rose Background
                            guiGraphics.fill(bx, barY, bx + bw, barY + barHeight, 0xEE160810);

                            // Left Rose Accent Notch
                            guiGraphics.fill(bx, barY, bx + 3, barY + barHeight, MusicSequenceChannel.COLOR_CAMERA);

                            // Camera bar fill
                            guiGraphics.fill(bx + 3, barY + 1, bx + bw - 1, barY + barHeight - 1, isHovered ? 0x44FF0055 : 0x22FF0055);

                            // Bar Outer Border
                            int borderColor = isHovered ? 0xFFFFFFFF : (isSelected ? 0xFFFFFFFF : 0x88FF0055);
                            guiGraphics.fill(bx, barY, bx + bw, barY + 1, borderColor);
                            guiGraphics.fill(bx, barY + barHeight - 1, bx + bw, barY + barHeight, borderColor);
                            guiGraphics.fill(bx + bw - 1, barY, bx + bw, barY + barHeight, borderColor);

                            // Right Edge Stretch Handle (Resize Grip)
                            boolean isStretchHovered = (isHovered && Math.abs(scaledMouseX - (bx + bw)) <= 6);
                            int handleColor = isStretchHovered ? 0xFFFFFFFF : MusicSequenceChannel.COLOR_CAMERA;
                            guiGraphics.fill(bx + bw - 3, barY + 3, bx + bw - 1, barY + barHeight - 3, handleColor);
                            if (isStretchHovered) {
                                guiGraphics.fill(bx + bw - 4, barY + 1, bx + bw, barY + barHeight - 1, 0x55FFFFFF);
                            }

                            // Camera Keyframe Icon / Node at Start
                            int barCenterY = barY + (barHeight / 2);
                            guiGraphics.fill(bx - 3, barCenterY - 3, bx + 3, barCenterY + 3, MusicSequenceChannel.COLOR_CAMERA);
                            guiGraphics.fill(bx - 2, barCenterY - 2, bx + 2, barCenterY + 2, 0xFF000000);
                            guiGraphics.fill(bx - 1, barCenterY - 1, bx + 1, barCenterY + 1, 0xFFFFFFFF);

                            // Text preview inside bar
                            String mode = entry.getCameraMode();
                            String displayLabel = "🎥 " + (entry.getDescription().isEmpty() ? ("Camera: " + mode) : entry.getDescription());
                            int textW = this.font.width(displayLabel);

                            int textAvailW = bw - 10;
                            if (textAvailW > 20) {
                                if (textW > textAvailW) {
                                    displayLabel = this.font.plainSubstrByWidth(displayLabel, textAvailW - 8) + "..";
                                }
                                guiGraphics.drawString(this.font, displayLabel, bx + 6, barY + 3, 0xFFFF7799, false);
                            }

                            // Duration label badge after bar
                            long durMs = entry.getTotalDurationMs();
                            String durBadge = String.format(Locale.ROOT, "%.1fs (%dms)", durMs / 1000.0, durMs);
                            guiGraphics.drawString(this.font, durBadge, bx + bw + 4, barY + 3, 0xFFAABBCC, false);
                        }
                    } else if ("SCREEN_EFFECT".equalsIgnoreCase(entry.getActionType()) || "SCREEN_EFFECTS".equalsIgnoreCase(entry.getActionType()) || (channelIndex < channels.size() && MusicSequenceChannel.TYPE_SCREEN_EFFECT.equalsIgnoreCase(channels.get(channelIndex).getType()))) {
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
                                int auraColor = (now / 300) % 2 == 0 ? 0xFFFFFFFF : MusicSequenceChannel.COLOR_SCREEN_EFFECT;
                                guiGraphics.fill(bx - 3, barY - 2, bx + bw + 3, barY + barHeight + 2, auraColor);
                            }

                            // Dark Violet/Magenta Background
                            guiGraphics.fill(bx, barY, bx + bw, barY + barHeight, 0xEE160515);

                            // Left Accent Notch
                            guiGraphics.fill(bx, barY, bx + 3, barY + barHeight, MusicSequenceChannel.COLOR_SCREEN_EFFECT);

                            // Screen Effect bar fill
                            guiGraphics.fill(bx + 3, barY + 1, bx + bw - 1, barY + barHeight - 1, isHovered ? 0x44FF00CC : 0x22FF00CC);

                            // Bar Outer Border
                            int borderColor = isHovered ? 0xFFFFFFFF : (isSelected ? 0xFFFFFFFF : 0x88FF00CC);
                            guiGraphics.fill(bx, barY, bx + bw, barY + 1, borderColor);
                            guiGraphics.fill(bx, barY + barHeight - 1, bx + bw, barY + barHeight, borderColor);
                            guiGraphics.fill(bx + bw - 1, barY, bx + bw, barY + barHeight, borderColor);

                            // Right Edge Stretch Handle (Resize Grip)
                            boolean isStretchHovered = (isHovered && Math.abs(scaledMouseX - (bx + bw)) <= 6);
                            int handleColor = isStretchHovered ? 0xFFFFFFFF : MusicSequenceChannel.COLOR_SCREEN_EFFECT;
                            guiGraphics.fill(bx + bw - 3, barY + 3, bx + bw - 1, barY + barHeight - 3, handleColor);
                            if (isStretchHovered) {
                                guiGraphics.fill(bx + bw - 4, barY + 1, bx + bw, barY + barHeight - 1, 0x55FFFFFF);
                            }

                            // Screen Effect Keyframe Icon / Node at Start
                            int barCenterY = barY + (barHeight / 2);
                            guiGraphics.fill(bx - 3, barCenterY - 3, bx + 3, barCenterY + 3, MusicSequenceChannel.COLOR_SCREEN_EFFECT);
                            guiGraphics.fill(bx - 2, barCenterY - 2, bx + 2, barCenterY + 2, 0xFF000000);
                            guiGraphics.fill(bx - 1, barCenterY - 1, bx + 1, barCenterY + 1, 0xFFFFFFFF);

                            // Text preview inside bar
                            String sub = entry.getSubAction().isEmpty() ? "SHAKE" : entry.getSubAction();
                            String displayLabel = "⚡ " + (entry.getDescription().isEmpty() ? ("FX: " + sub) : entry.getDescription());
                            int textW = this.font.width(displayLabel);

                            int textAvailW = bw - 10;
                            if (textAvailW > 20) {
                                if (textW > textAvailW) {
                                    displayLabel = this.font.plainSubstrByWidth(displayLabel, textAvailW - 8) + "..";
                                }
                                guiGraphics.drawString(this.font, displayLabel, bx + 6, barY + 3, 0xFFFF77EE, false);
                            }

                            // Duration label badge after bar
                            long durMs = entry.getTotalDurationMs();
                            String durBadge = String.format(Locale.ROOT, "%.1fs (%dms)", durMs / 1000.0, durMs);
                            guiGraphics.drawString(this.font, durBadge, bx + bw + 4, barY + 3, 0xFFAABBCC, false);
                        }
                    } else {
                        // Render multi-color segmented duration bar
                        double windupW = (entry.getWindupMs() / 1000.0) * pixelsPerSecond;
                        double jumpW = (entry.getJumpMs() / 1000.0) * pixelsPerSecond;
                        double durationW = (entry.getDurationMs() / 1000.0) * pixelsPerSecond;
                        double recoveryW = (entry.getRecoveryMs() / 1000.0) * pixelsPerSecond;
                        double totalW = Math.max(8.0, windupW + jumpW + durationW + recoveryW);

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

                        int ww = (int) Math.round(windupW);
                        int jw = (int) Math.round(jumpW);
                        int dw = (int) Math.round(durationW);

                        int curX = bx;

                        // 1. Indication / Ground Telegraph Bar (where the attack is indicated)
                        if (ww > 0) {
                            int wEnd = curX + ww;
                            guiGraphics.fill(curX, barY + 1, wEnd, barY + barHeight - 1, 0xDDFF9900);

                            // Left start notch for indication
                            guiGraphics.fill(curX, barY, curX + 2, barY + barHeight, 0xFFFFCC00);

                            if (wEnd - curX >= 48) {
                                String txt = "INDICATOR (" + entry.getWindupMs() + "ms)";
                                guiGraphics.drawCenteredString(this.font, txt, curX + (wEnd - curX) / 2, barY + 3, 0xFF000000);
                            } else if (wEnd - curX >= 22) {
                                guiGraphics.drawCenteredString(this.font, entry.getWindupMs() + "ms", curX + (wEnd - curX) / 2, barY + 3, 0xFF000000);
                            }
                            curX = wEnd;
                        }

                        // 2. Jump Bar (Ascent & Descent through the air)
                        if (jw > 0) {
                            int jEnd = curX + jw;
                            guiGraphics.fill(curX, barY + 1, jEnd, barY + barHeight - 1, 0xDD4A69BD);

                            // Divider line between indicator and jump
                            guiGraphics.fill(curX, barY, curX + 1, barY + barHeight, 0xFF6C88C9);

                            if (jEnd - curX >= 40) {
                                String txt = "JUMP (" + entry.getJumpMs() + "ms)";
                                guiGraphics.drawCenteredString(this.font, txt, curX + (jEnd - curX) / 2, barY + 3, 0xFFFFFFFF);
                            } else if (jEnd - curX >= 20) {
                                guiGraphics.drawCenteredString(this.font, entry.getJumpMs() + "ms", curX + (jEnd - curX) / 2, barY + 3, 0xFFFFFFFF);
                            }
                            curX = jEnd;
                        }

                        // 3. The Attack Execution Point (Exactly when entity slams the ground!)
                        int execX = curX;

                        // 4. Active Slam Attack Duration Bar (Ground tremor / impact fissure duration)
                        if (dw > 0) {
                            int dEnd = Math.min(bx + bw, curX + dw);
                            guiGraphics.fill(curX, barY + 1, dEnd, barY + barHeight - 1, 0xDDAA55FF);
                            if (dEnd - curX >= 44) {
                                String txt = "ATTACK (" + entry.getDurationMs() + "ms)";
                                guiGraphics.drawCenteredString(this.font, txt, curX + (dEnd - curX) / 2, barY + 3, 0xFFFFFFFF);
                            } else if (dEnd - curX >= 20) {
                                guiGraphics.drawCenteredString(this.font, entry.getDurationMs() + "ms", curX + (dEnd - curX) / 2, barY + 3, 0xFFFFFFFF);
                            }
                            curX = dEnd;
                        }

                        // 5. Attack End / Transition Marker to Recovery
                        int impactX = curX;
                        if (dw > 0 && entry.getRecoveryMs() > 0) {
                            guiGraphics.fill(impactX - 1, barY - 1, impactX + 1, barY + barHeight + 1, 0xFFFF3355);
                        }

                        // 6. Recovery Bar (Stun / recovery state)
                        if (curX < bx + bw) {
                            guiGraphics.fill(curX, barY + 1, bx + bw, barY + barHeight - 1, 0xDD00E5FF);
                            if (bx + bw - curX >= 48) {
                                String txt = "RECOVERY (" + entry.getRecoveryMs() + "ms)";
                                guiGraphics.drawCenteredString(this.font, txt, curX + (bx + bw - curX) / 2, barY + 3, 0xFF000000);
                            } else if (bx + bw - curX >= 20) {
                                guiGraphics.drawCenteredString(this.font, entry.getRecoveryMs() + "ms", curX + (bx + bw - curX) / 2, barY + 3, 0xFF000000);
                            }
                        }

                        // Bar Outer Border
                        int borderColor = isHovered ? 0xFFFFFFFF : (isSelected ? 0xFFFFFFFF : CARD_BORDER);
                        guiGraphics.fill(bx, barY, bx + bw, barY + 1, borderColor);
                        guiGraphics.fill(bx, barY + barHeight - 1, bx + bw, barY + barHeight, borderColor);
                        guiGraphics.fill(bx + bw - 1, barY, bx + bw, barY + barHeight, borderColor);

                        // 7. KEYFRAME SQUARE RIGHT WHEN ATTACK IS EXECUTED (TOUCHDOWN SLAM)
                        int barCenterY = barY + (barHeight / 2);
                        // Vertical guideline tick
                        guiGraphics.fill(execX - 1, barY - 3, execX + 1, barY + barHeight + 3, 0xFFFFFFFF);
                        // Black drop-shadow border
                        guiGraphics.fill(execX - 5, barCenterY - 5, execX + 5, barCenterY + 5, 0xFF000000);
                        // Vibrant Execution Square (White border + Neon Red/Magenta core + White dot)
                        int sqBorder = isSelected ? 0xFFFFFFFF : 0xFFFFD700;
                        guiGraphics.fill(execX - 4, barCenterY - 4, execX + 4, barCenterY + 4, sqBorder);
                        guiGraphics.fill(execX - 3, barCenterY - 3, execX + 3, barCenterY + 3, 0xFFFF0055);
                        guiGraphics.fill(execX - 1, barCenterY - 1, execX + 1, barCenterY + 1, 0xFFFFFFFF);

                        // Slam impact timestamp badge
                        if (ww > 0 || jw > 0) {
                            long slamTimeMs = entry.getTimestampMs() + entry.getWindupMs() + entry.getJumpMs();
                            long sSec = slamTimeMs / 1000;
                            long sRem = slamTimeMs % 1000;
                            String slamTxt = String.format("%02d:%02d.%03d", sSec / 60, sSec % 60, sRem);
                            int slamTxtW = this.font.width(slamTxt);
                            int badgeX = execX - (slamTxtW / 2);
                            if (badgeX >= timelineTrackLeft && badgeX + slamTxtW <= timelineLeft + timelineWidth) {
                                guiGraphics.fill(badgeX - 2, barY - 11, badgeX + slamTxtW + 2, barY - 1, 0xEE060C12);
                                guiGraphics.drawString(this.font, slamTxt, badgeX, barY - 10, isSelected ? 0xFFFF0055 : 0xFFFFCC00, false);
                            }
                        }

                        // Start notch at bx if windup > 0
                        if (ww > 0) {
                            guiGraphics.fill(bx - 1, barY - 2, bx + 1, barY + barHeight + 2, 0xFFFFCC00);
                        }

                        // Label Badge after bar
                        String actionName = entry.getSubAction().isEmpty() ? entry.getActionType() : entry.getSubAction();
                        if (entry.getDescription() != null && !entry.getDescription().isBlank()) {
                            actionName = entry.getDescription();
                        }
                        String label = actionName + " (" + totalDurationMs + "ms)";
                        if (this.font.width(label) > 140) {
                            label = this.font.plainSubstrByWidth(label, 134) + "..";
                        }
                        guiGraphics.drawString(this.font, label, bx + bw + 6, barY + 3, isSelected ? 0xFFFFFFFF : chColor, false);
                    }
                    }
                } else {
                    // Instant single keyframe node
                    if (entryX >= timelineTrackLeft && entryX <= timelineLeft + timelineWidth) {
                        int kx = (int) entryX;
                        int ky = entryTrackY + (channelHeight / 2);

                        boolean isHovered = (scaledMouseX >= kx - 8 && scaledMouseX <= kx + 8 && scaledMouseY >= entryTrackY && scaledMouseY <= entryTrackY + channelHeight);
                        int nodeColor = (isHovered || isSelected) ? 0xFFFFFFFF : chColor;

                        if (isSelected) {
                            long now = System.currentTimeMillis();
                            int auraColor = (now / 300) % 2 == 0 ? 0xFFFFFFFF : CYAN_MAIN;
                            guiGraphics.fill(kx - 8, ky - 8, kx + 8, ky + 8, auraColor);
                        }

                        guiGraphics.fill(kx - 6, ky - 6, kx + 6, ky + 6, 0xEE060C12);
                        guiGraphics.fill(kx - 5, ky - 5, kx + 5, ky + 5, nodeColor);
                        guiGraphics.fill(kx - 3, ky - 3, kx + 3, ky + 3, 0xFF000000);

                        // Keyframe timestamp badge
                        long sec = entry.getTimestampMs() / 1000;
                        long msRem = entry.getTimestampMs() % 1000;
                        String timeBadge = String.format("%02d:%02d.%01d", sec / 60, sec % 60, msRem / 100);
                        guiGraphics.drawString(this.font, timeBadge, kx + 8, ky - 4, nodeColor, false);
                    }
                }
            }
        }

        // G. Render Vertical Red Playhead Scrubber Line
        double playheadX = timelineTrackLeft + ((playheadMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
        if (playheadX >= timelineTrackLeft && playheadX <= timelineLeft + timelineWidth) {
            int px = (int) playheadX;
            guiGraphics.fill(px - 1, timelineTop, px + 2, timelineTop + totalHeight, PLAYHEAD_COLOR);

            // Playhead Handle Cap
            guiGraphics.fill(px - 5, timelineTop, px + 6, timelineTop + 10, PLAYHEAD_COLOR);
            guiGraphics.fill(px - 4, timelineTop + 1, px + 5, timelineTop + 9, 0xFFFFFFFF);
        }

        guiGraphics.disableScissor();

        // Vertical divider line separating Left Track Headers and Timeline Tracks
        guiGraphics.fill(timelineTrackLeft - 1, timelineTop, timelineTrackLeft, timelineTop + totalHeight, CARD_BORDER);

        int widgetMouseX = (activeModal != ModalType.NONE) ? -1000 : scaledMouseX;
        int widgetMouseY = (activeModal != ModalType.NONE) ? -1000 : scaledMouseY;
        super.render(guiGraphics, widgetMouseX, widgetMouseY, partialTick);

        if (activeModal == ModalType.NONE) {
            if (this.fileDropdown != null) {
                this.fileDropdown.renderOverlay(guiGraphics, scaledMouseX, scaledMouseY);
            }
            if (this.trackDropdown != null) {
                this.trackDropdown.renderOverlay(guiGraphics, scaledMouseX, scaledMouseY);
            }
        }

        if (activeModal != ModalType.NONE) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 800.0f);
            renderActiveModal(guiGraphics, scaledMouseX, scaledMouseY, partialTick, effWidth, effHeight);
            guiGraphics.pose().popPose();
        }

        guiGraphics.pose().popPose();
    }

    private void updatePreviewCamera() {
        if (currentSequence == null) return;
        List<MusicSequenceChannel> channels = currentSequence.getChannels();
        List<MusicSequenceEntry> cameraEntries = new ArrayList<>();

        for (MusicSequenceEntry e : currentSequence.getEntries()) {
            boolean isCamera = e.isUseCamera();
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
            if (isPreviewCameraActive) {
                CustomCameraManager.clearCustomCamera();
                isPreviewCameraActive = false;
            }
            return;
        }

        // Find the active entry at playheadMs
        MusicSequenceEntry activeEntry = null;
        MusicSequenceEntry nextEntry = null;
        for (int i = 0; i < cameraEntries.size(); i++) {
            MusicSequenceEntry e = cameraEntries.get(i);
            long start = e.getTimestampMs();
            long dur = Math.max(50L, e.getTotalDurationMs());
            long end = start + dur;
            if (playheadMs >= start && playheadMs < end) {
                activeEntry = e;
                if (i + 1 < cameraEntries.size()) {
                    nextEntry = cameraEntries.get(i + 1);
                }
                break;
            }
        }

        if (activeEntry == null) {
            if (isPreviewCameraActive) {
                CustomCameraManager.clearCustomCamera();
                isPreviewCameraActive = false;
            }
            return;
        }

        isPreviewCameraActive = true;
        String mode = activeEntry.getCameraMode();
        if ("CLEAR".equalsIgnoreCase(mode)) {
            CustomCameraManager.clearCustomCamera();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if ("FOLLOW".equalsIgnoreCase(mode)) {
            Entity target = resolvePreviewTarget(activeEntry.getCameraTarget(), mc);
            CustomCameraManager.setTargetEntity(target != null ? target : mc.player, activeEntry.getCameraHeightOffset(), activeEntry.getCameraPitch());
            CustomCameraManager.setCustomFov(activeEntry.getCameraFov());
        } else if ("OVER_THE_SHOULDER".equalsIgnoreCase(mode)) {
            Entity target = resolvePreviewTarget(activeEntry.getCameraTarget(), mc);
            CustomCameraManager.setOverTheShoulderTarget(target != null ? target : mc.player, activeEntry.getCameraBackDistance(), activeEntry.getCameraShoulderOffset(), activeEntry.getCameraHeightOffset(), activeEntry.getCameraPitch());
            CustomCameraManager.setCustomFov(activeEntry.getCameraFov());
        } else {
            // STATIC mode (with interpolation to next entry if enabled)
            double x = activeEntry.getCameraX();
            double y = activeEntry.getCameraY();
            double z = activeEntry.getCameraZ();
            float yaw = activeEntry.getCameraYaw();
            float pitch = activeEntry.getCameraPitch();
            float roll = activeEntry.getCameraRoll();
            double fov = activeEntry.getCameraFov();

            if (activeEntry.isCameraInterpolate() && nextEntry != null && !"CLEAR".equalsIgnoreCase(nextEntry.getCameraMode())) {
                long segStart = activeEntry.getTimestampMs();
                long segEnd = nextEntry.getTimestampMs();
                if (segEnd > segStart) {
                    double t = Math.max(0.0, Math.min(1.0, (playheadMs - segStart) / (double) (segEnd - segStart)));
                    x = x + (nextEntry.getCameraX() - x) * t;
                    y = y + (nextEntry.getCameraY() - y) * t;
                    z = z + (nextEntry.getCameraZ() - z) * t;

                    float yawDiff = ((nextEntry.getCameraYaw() - yaw) % 360.0f + 540.0f) % 360.0f - 180.0f;
                    yaw = yaw + yawDiff * (float) t;
                    pitch = pitch + (nextEntry.getCameraPitch() - pitch) * (float) t;
                    roll = roll + (nextEntry.getCameraRoll() - roll) * (float) t;
                    fov = fov + (nextEntry.getCameraFov() - fov) * t;
                }
            }

            CustomCameraManager.setCustomCamera(new Vec3(x, y, z), yaw, pitch, roll, true);
            CustomCameraManager.setCustomFov(fov);
        }
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
            } else {
                populateExistingMobsDropdown(left + 16, top + 115, panelWidth - 32 - 110, 20);
                this.modalEntityCatalogDropdown = null;
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
                }

                // Mode Tabs
                int tabW = (panelWidth - 36) / 2;
                int tabY = top + 77;
                // [➕ REGISTER NEW ACTOR]
                if (isMouseOver(smX, smY, left + 16, tabY, tabW, 20)) {
                    if (puppetTargetMode != PuppetTargetMode.REGISTER_NEW) {
                        playButtonSound();
                        puppetTargetMode = PuppetTargetMode.REGISTER_NEW;
                        modalTagError = false;
                        updateModalWidgetsPosition();
                    }
                    return;
                }
                // [🔍 SELECT EXISTING ACTOR]
                if (isMouseOver(smX, smY, left + 20 + tabW, tabY, tabW, 20)) {
                    if (puppetTargetMode != PuppetTargetMode.EXISTING_ACTOR) {
                        playButtonSound();
                        puppetTargetMode = PuppetTargetMode.EXISTING_ACTOR;
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
        }

        guiGraphics.drawCenteredString(this.font, Component.literal("⚠ All keyframes on this channel will be removed!"), left + (panelWidth / 2), top + 76, 0xFFFF3355);

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

            // Row 2: Target Mode Selector Tabs
            guiGraphics.drawString(this.font, "Actor Target Mode:", left + 16, top + 65, 0xFFAABBCC, false);
            int tabW = (panelWidth - 36) / 2;
            int tabY = top + 77;
            boolean regActive = (puppetTargetMode == PuppetTargetMode.REGISTER_NEW);
            boolean regHov = isMouseOver(mouseX, mouseY, left + 16, tabY, tabW, 20);
            drawModalButton(guiGraphics, left + 16, tabY, tabW, 20, "➕ REGISTER NEW ACTOR", regActive ? 0xFFAA55FF : 0xFF8899AA, regHov || regActive);

            boolean existActive = (puppetTargetMode == PuppetTargetMode.EXISTING_ACTOR);
            boolean existHov = isMouseOver(mouseX, mouseY, left + 20 + tabW, tabY, tabW, 20);
            drawModalButton(guiGraphics, left + 20 + tabW, tabY, tabW, 20, "🔍 SELECT EXISTING ACTOR", existActive ? 0xFFAA55FF : 0xFF8899AA, existHov || existActive);

            // Row 3: Entity Type / Mob Selection
            if (puppetTargetMode == PuppetTargetMode.REGISTER_NEW) {
                guiGraphics.drawString(this.font, "Select Entity Type to Register (No World Spawn):", left + 16, top + 103, 0xFFAABBCC, false);
                if (modalEntityCatalogDropdown != null) {
                    modalEntityCatalogDropdown.render(guiGraphics, mouseX, mouseY, partialTick);
                }
            } else {
                guiGraphics.drawString(this.font, "Choose Detected In-World Mob or Aim Crosshair:", left + 16, top + 103, 0xFFAABBCC, false);
                if (modalExistingMobsDropdown != null) {
                    modalExistingMobsDropdown.render(guiGraphics, mouseX, mouseY, partialTick);
                }
                boolean tgtHov = isMouseOver(mouseX, mouseY, left + panelWidth - 16 - 105, top + 115, 105, 20);
                drawModalButton(guiGraphics, left + panelWidth - 16 - 105, top + 115, 105, 20, "🎯 TARGET MOB", CYAN_MAIN, tgtHov);
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
                } else {
                    guiGraphics.drawString(this.font, "ℹ Actions on this channel route to @e[tag=" + tagPreview + ",limit=1]", left + 16, top + 215, 0xFF8899AA, false);
                    guiGraphics.drawString(this.font, "ℹ Ensure the target mob in-world has this tag applied.", left + 16, top + 228, 0xFF778899, false);
                }
            }

            // Row 7: Footer buttons
            int btnY = top + 275;
            boolean bHov = isMouseOver(mouseX, mouseY, left + 16, btnY, 120, 24);
            drawModalButton(guiGraphics, left + 16, btnY, 120, 24, "← BACK TO TYPES", 0xFF8899AA, bHov);

            boolean addPHov = isMouseOver(mouseX, mouseY, left + panelWidth - 16 - 165, btnY, 165, 24);
            drawModalButton(guiGraphics, left + panelWidth - 16 - 165, btnY, 165, 24, "+ ADD PUPPET CHANNEL", 0xFFAA55FF, addPHov);

            // Dropdown Overlay rendered on top of all modal controls
            if (puppetTargetMode == PuppetTargetMode.REGISTER_NEW && modalEntityCatalogDropdown != null) {
                modalEntityCatalogDropdown.renderOverlay(guiGraphics, mouseX, mouseY);
            } else if (puppetTargetMode == PuppetTargetMode.EXISTING_ACTOR && modalExistingMobsDropdown != null) {
                modalExistingMobsDropdown.renderOverlay(guiGraphics, mouseX, mouseY);
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
}
