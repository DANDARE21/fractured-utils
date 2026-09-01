package net.dandare21.fracturedutils.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dandare21.fracturedutils.sound.event.ClientAudioConfig;
import net.dandare21.fracturedutils.sound.event.ClientAudioPackManager;
import net.dandare21.fracturedutils.sound.event.EventAudioClientController;
import net.dandare21.fracturedutils.sound.event.EventAudioManager;
import net.dandare21.fracturedutils.sound.sequence.MusicSequence;
import net.dandare21.fracturedutils.sound.sequence.MusicSequenceEntry;
import net.dandare21.fracturedutils.sound.sequence.MusicSequenceManager;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.C2SDeleteMusicSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SSaveMusicSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SStartMusicSequencePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class MusicSequenceScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int CARD_BORDER = 0xAA00E5FF;
    private static final int PLAYHEAD_COLOR = 0xFFFF3355;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, String> savedServerSequenceFiles;
    private final Map<String, String> workingServerSequenceFiles;
    private final Map<String, String> savedClientSequenceFiles;
    private final Map<String, String> workingClientSequenceFiles;
    private final List<String> availableTracks;
    private boolean isClientMode = false;

    private String currentFileName;
    private MusicSequence currentSequence = new MusicSequence();

    private CyberpunkDropdown<String> fileDropdown;
    private CyberpunkDropdown<String> trackDropdown;
    private CyberpunkCheckbox loopingCheckbox;
    private EditBox newFileEditBox;
    private EditBox bpmEditBox;

    // Timeline Zoom & Scroll Controls
    private double pixelsPerSecond = 50.0; // horizontal zoom (pixels per second)
    private double timeScrollMs = 0.0; // timeline horizontal scroll offset in milliseconds
    private double playheadMs = 0.0; // active preview playhead position in milliseconds
    private boolean isPreviewPlaying = false;
    private long lastPreviewTickTime = 0;

    // Timeline Drag, Pan & Selection
    private int draggedEntryIndex = -1;
    private boolean isDraggingPlayhead = false;
    private boolean isDraggingStartMarker = false;
    private boolean isDraggingEndMarker = false;
    private boolean isDraggingTimelineScroll = false;
    private boolean isPanningTimeline = false;
    private double lastDragMouseX = 0;
    private double lastPanMouseX = 0;
    private boolean autoFollowPlayhead = true;
    private int selectedEntryIndex = -1;

    // Channel Layout Definitions
    private static final String[] ACTION_CHANNELS = new String[]{
            "COMMAND", "DIALOG", "OBJECTIVE", "CAMERA", "SOUND_EFFECT"
    };

    private static final int[] CHANNEL_COLORS = new int[]{
            0xFF00E5FF, 0xFFFFD700, 0xFF00FF88, 0xFFFF0055, 0xFFAA55FF
    };

    private String saveFeedbackMessage = null;
    private long saveFeedbackTime = 0;

    public MusicSequenceScreen(Map<String, String> serverSequenceFiles, List<String> availableTracks) {
        super(Component.literal("Timeline Music Sequence Orchestrator"));
        this.savedServerSequenceFiles = serverSequenceFiles != null ? new HashMap<>(serverSequenceFiles) : new HashMap<>();
        this.workingServerSequenceFiles = new HashMap<>(this.savedServerSequenceFiles);
        this.savedClientSequenceFiles = loadLocalClientSequences();
        this.workingClientSequenceFiles = new HashMap<>(this.savedClientSequenceFiles);

        // Populate available songs from resourcepack, SoundManager & server suggestions
        Set<String> trackSet = new LinkedHashSet<>();
        if (availableTracks != null) {
            trackSet.addAll(availableTracks);
        }
        trackSet.addAll(ClientAudioPackManager.getInstance().getAvailableTracks());
        trackSet.addAll(EventAudioManager.getInstance().getAvailableTrackSuggestions());

        try {
            var soundManager = Minecraft.getInstance().getSoundManager();
            if (soundManager != null) {
                for (net.minecraft.resources.ResourceLocation loc : soundManager.getAvailableSounds()) {
                    if (loc != null && (loc.getPath().contains("event") || loc.getNamespace().equals("fracturedutils"))) {
                        trackSet.add(loc.getPath());
                        trackSet.add(loc.toString());
                        if (loc.getPath().startsWith("event.")) {
                            trackSet.add(loc.getPath().substring(6));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

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
                    this.currentSequence.sortEntriesByTimestamp();
                    return;
                }
            } catch (Exception ignored) {}
        }
        this.currentSequence = new MusicSequence(currentFileName, "", false, 1.0f, 1.0f, new ArrayList<>());
    }

    private void saveCurrentSequenceToWorkingMap() {
        if (currentFileName != null && currentSequence != null) {
            currentSequence.sortEntriesByTimestamp();
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

        // New File Input & Add Button
        this.newFileEditBox = new EditBox(this.font, leftX + 155, topY, 80, 20, Component.literal("New file"));
        this.newFileEditBox.setMaxLength(64);
        this.newFileEditBox.setHint(Component.literal("new_sequence"));
        this.addRenderableWidget(this.newFileEditBox);

        CyberpunkButton addFileBtn = new CyberpunkButton(leftX + 240, topY, 25, 20, Component.literal("+"), b -> createNewSequenceFile(), CYAN_MAIN, false);
        addFileBtn.setTooltip(Tooltip.create(Component.literal("Create new sequence file")));
        this.addRenderableWidget(addFileBtn);

        // 2. Resourcepack Song Selector Dropdown
        Set<String> dynamicTracks = new LinkedHashSet<>(availableTracks);
        dynamicTracks.addAll(ClientAudioPackManager.getInstance().getAvailableTracks());
        dynamicTracks.addAll(EventAudioManager.getInstance().getAvailableTrackSuggestions());
        if (currentSequence.getSongTrack() != null && !currentSequence.getSongTrack().isEmpty()) {
            dynamicTracks.add(currentSequence.getSongTrack());
        }

        List<CyberpunkDropdown.DropdownEntry<String>> songEntries = new ArrayList<>();
        songEntries.add(new CyberpunkDropdown.DropdownEntry<>("", Component.literal("[None / No Song]"), Component.literal("Sequence runs timed actions without audio track")));

        for (String track : dynamicTracks) {
            String label = track;
            if (label.startsWith("event.")) label = label.substring(6);
            songEntries.add(new CyberpunkDropdown.DropdownEntry<>(track, Component.literal(label), Component.literal(track)));
        }

        this.trackDropdown = new CyberpunkDropdown<>(leftX + 270, topY, 175, 20, Component.literal("Song Track"));
        this.trackDropdown.setOptions(songEntries);
        this.trackDropdown.selectByValue(currentSequence.getSongTrack());
        this.trackDropdown.setOnSelect(selected -> {
            currentSequence.setSongTrack(selected.getValue());
            saveCurrentSequenceToWorkingMap();
        });
        this.addRenderableWidget(this.trackDropdown);

        // BPM Input Box
        this.bpmEditBox = new EditBox(this.font, leftX + 450, topY, 45, 20, Component.literal("BPM"));
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
                leftX + 500, topY, 75, 20,
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

        CyberpunkButton playPreviewBtn = new CyberpunkButton(leftX, toolbarY, 100, 20,
                Component.literal(isPreviewPlaying ? "⏸ PAUSE" : "▶ PREVIEW"),
                b -> togglePreviewPlayback(),
                isPreviewPlaying ? 0xFFFFD700 : 0xFF00FF88,
                false
        );
        this.addRenderableWidget(playPreviewBtn);

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
        CyberpunkButton closeBtn = new CyberpunkButton(leftX, footerY, 100, 22, Component.literal("CLOSE"), b -> this.onClose(), 0xFF8899AA, false);

        this.addRenderableWidget(saveBtn);
        this.addRenderableWidget(runBtn);
        this.addRenderableWidget(stopBtn);
        this.addRenderableWidget(closeBtn);
    }

    private void togglePreviewPlayback() {
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
        }
        this.init();
    }

    @Override
    public void onClose() {
        if (isPreviewPlaying) {
            EventAudioClientController.getInstance().stopAudio(0);
        }
        super.onClose();
    }

    private void adjustZoom(double factor) {
        this.pixelsPerSecond = Math.max(10.0, Math.min(300.0, this.pixelsPerSecond * factor));
    }

    private void selectSequenceFile(String fileName) {
        saveCurrentSequenceToWorkingMap();
        this.currentFileName = fileName;
        loadCurrentFileSequence();
        this.playheadMs = 0;
        this.timeScrollMs = 0;
        this.init();
    }

    private void createNewSequenceFile() {
        String name = this.newFileEditBox.getValue().trim();
        if (name.isEmpty()) return;
        if (!name.endsWith(".json")) {
            name += ".json";
        }
        saveCurrentSequenceToWorkingMap();
        this.currentFileName = name;
        this.currentSequence = new MusicSequence(currentFileName, "", false, 1.0f, 1.0f, new ArrayList<>());
        getActiveSequenceMap().put(currentFileName, GSON.toJson(currentSequence));
        this.newFileEditBox.setValue("");
        this.init();
    }

    public void openEditEntryModal(int index) {
        long defaultTs = (index >= 0 && index < currentSequence.getEntries().size()) ? currentSequence.getEntries().get(index).getTimestampMs() : (long) playheadMs;
        openEditEntryModal(index, defaultTs);
    }

    public void openEditEntryModal(int index, long defaultTimestampMs) {
        saveCurrentSequenceToWorkingMap();
        MusicSequenceEntry targetEntry = (index >= 0 && index < currentSequence.getEntries().size()) ? currentSequence.getEntries().get(index) : new MusicSequenceEntry(defaultTimestampMs, "COMMAND", "", "");
        this.minecraft.setScreen(new EditMusicEntryModalScreen(this, targetEntry, updated -> {
            if (index >= 0 && index < currentSequence.getEntries().size()) {
                currentSequence.getEntries().set(index, updated);
            } else {
                currentSequence.getEntries().add(updated);
            }
            currentSequence.sortEntriesByTimestamp();
            saveCurrentSequenceToWorkingMap();
            this.init();
        }));
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
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

        int timelineLeft = 14;
        int timelineWidth = this.width - 28;

        if (mouseX >= timelineLeft && mouseX <= timelineLeft + timelineWidth) {
            if (hasControlDown()) {
                // Modern DAW: Ctrl + Mouse Wheel = Mouse-Centered Zoom
                double oldPixelsPerSecond = this.pixelsPerSecond;
                double zoomFactor = delta > 0 ? 1.25 : 0.8;
                double newPixelsPerSecond = Math.max(10.0, Math.min(400.0, oldPixelsPerSecond * zoomFactor));

                double mouseTimeMs = timeScrollMs + ((mouseX - timelineLeft) / oldPixelsPerSecond) * 1000.0;
                this.pixelsPerSecond = newPixelsPerSecond;
                this.timeScrollMs = Math.max(0.0, mouseTimeMs - ((mouseX - timelineLeft) / newPixelsPerSecond) * 1000.0);
                return true;
            } else {
                // Horizontal Timeline Panning Scroll
                double scrollSpeed = 500.0 / (pixelsPerSecond / 50.0);
                timeScrollMs = Math.max(0.0, timeScrollMs - (delta * scrollSpeed));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double scale = getLayoutScale();
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
        int timelineTop = 90;
        int rulerHeight = 24;
        int waveformHeight = 45;
        int channelHeight = 28;
        int totalHeight = rulerHeight + waveformHeight + (ACTION_CHANNELS.length * channelHeight);

        if (mouseX >= timelineLeft && mouseX <= timelineLeft + timelineWidth) {
            int canvasY = timelineTop + rulerHeight;
            int totalTrackAreaHeight = waveformHeight + (ACTION_CHANNELS.length * channelHeight);

            // Middle Click (button 2) or Right Click on empty canvas -> Start Timeline Panning
            if (button == 2 || (button == 1 && mouseY >= timelineTop && mouseY <= timelineTop + totalHeight)) {
                boolean hitKeyframe = false;
                if (button == 1 && mouseY >= canvasY + waveformHeight) {
                    // Check if right-clicking a keyframe
                    List<MusicSequenceEntry> entries = currentSequence.getEntries();
                    for (int i = 0; i < entries.size(); i++) {
                        MusicSequenceEntry entry = entries.get(i);
                        int channelIndex = getChannelIndex(entry.getActionType());
                        int entryTrackY = canvasY + waveformHeight + (channelIndex * channelHeight);
                        double entryX = timelineLeft + ((entry.getTimestampMs() - timeScrollMs) / 1000.0) * pixelsPerSecond;
                        if (Math.abs(mouseX - entryX) <= 8 && mouseY >= entryTrackY && mouseY <= entryTrackY + channelHeight) {
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

            // Check click on Ruler -> START Marker, END Marker, or Seek Playhead
            if (mouseY >= timelineTop && mouseY <= timelineTop + rulerHeight) {
                double startX = timelineLeft + ((currentSequence.getStartMs() - timeScrollMs) / 1000.0) * pixelsPerSecond;
                double endX = timelineLeft + ((getEffectiveEndMs() - timeScrollMs) / 1000.0) * pixelsPerSecond;

                if (Math.abs(mouseX - startX) <= 14) {
                    this.isDraggingStartMarker = true;
                    return true;
                }
                if (Math.abs(mouseX - endX) <= 14) {
                    this.isDraggingEndMarker = true;
                    return true;
                }

                double relX = mouseX - timelineLeft;
                this.playheadMs = Math.max(0.0, timeScrollMs + (relX / pixelsPerSecond) * 1000.0);
                this.isDraggingPlayhead = true;
                return true;
            }

            // Check click on Keyframe Nodes or Track Canvas
            if (mouseY >= canvasY && mouseY <= canvasY + totalTrackAreaHeight) {
                double relX = mouseX - timelineLeft;
                long clickedMs = snapTimestamp((long) Math.max(0.0, timeScrollMs + (relX / pixelsPerSecond) * 1000.0), hasAltDown());

                // Check keyframe node hits
                List<MusicSequenceEntry> entries = currentSequence.getEntries();
                for (int i = 0; i < entries.size(); i++) {
                    MusicSequenceEntry entry = entries.get(i);
                    int channelIndex = getChannelIndex(entry.getActionType());
                    int entryTrackY = canvasY + waveformHeight + (channelIndex * channelHeight);
                    double entryX = timelineLeft + ((entry.getTimestampMs() - timeScrollMs) / 1000.0) * pixelsPerSecond;

                    if (Math.abs(mouseX - entryX) <= 8 && mouseY >= entryTrackY && mouseY <= entryTrackY + channelHeight) {
                        this.selectedEntryIndex = i;
                        if (button == 0) {
                            // Start dragging keyframe
                            this.draggedEntryIndex = i;
                            this.lastDragMouseX = mouseX;
                            return true;
                        } else if (button == 1) {
                            // Right-click -> Edit Keyframe
                            openEditEntryModal(i, entry.getTimestampMs());
                            return true;
                        }
                    }
                }

                // Click on empty space in action channel -> Create new keyframe at snapped timestamp
                if (button == 0 && mouseY >= canvasY + waveformHeight) {
                    int clickedChannelIndex = (int) ((mouseY - (canvasY + waveformHeight)) / channelHeight);
                    if (clickedChannelIndex >= 0 && clickedChannelIndex < ACTION_CHANNELS.length) {
                        String actionType = ACTION_CHANNELS[clickedChannelIndex];
                        MusicSequenceEntry newEntry = new MusicSequenceEntry(clickedMs, actionType, "", "");
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
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
            dragX /= scale;
            dragY /= scale;
        }

        int timelineLeft = 14;

        if (isPanningTimeline) {
            double deltaX = mouseX - lastPanMouseX;
            timeScrollMs = Math.max(0.0, timeScrollMs - (deltaX / pixelsPerSecond) * 1000.0);
            lastPanMouseX = mouseX;
            return true;
        }

        if (isDraggingStartMarker) {
            double relX = mouseX - timelineLeft;
            long rawMs = (long) Math.max(0.0, timeScrollMs + (relX / pixelsPerSecond) * 1000.0);
            long snappedMs = snapTimestamp(rawMs, hasAltDown());
            long maxAllowed = getEffectiveEndMs() - 50L;
            long clampedStart = Math.max(0L, Math.min(maxAllowed, snappedMs));
            currentSequence.setStartMs(clampedStart);
            saveCurrentSequenceToWorkingMap();
            return true;
        }

        if (isDraggingEndMarker) {
            double relX = mouseX - timelineLeft;
            long rawMs = (long) Math.max(0.0, timeScrollMs + (relX / pixelsPerSecond) * 1000.0);
            long snappedMs = snapTimestamp(rawMs, hasAltDown());
            long minAllowed = currentSequence.getStartMs() + 50L;
            long clampedEnd = Math.max(minAllowed, snappedMs);
            currentSequence.setEndMs(clampedEnd);
            saveCurrentSequenceToWorkingMap();
            return true;
        }

        if (draggedEntryIndex >= 0 && draggedEntryIndex < currentSequence.getEntries().size()) {
            double relX = mouseX - timelineLeft;
            long rawTs = (long) Math.max(0.0, timeScrollMs + (relX / pixelsPerSecond) * 1000.0);
            long snappedTs = snapTimestamp(rawTs, hasAltDown());
            currentSequence.getEntries().get(draggedEntryIndex).setTimestampMs(snappedTs);
            saveCurrentSequenceToWorkingMap();
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isPanningTimeline = false;
        isDraggingStartMarker = false;
        isDraggingEndMarker = false;
        if (draggedEntryIndex != -1) {
            currentSequence.sortEntriesByTimestamp();
            saveCurrentSequenceToWorkingMap();
            draggedEntryIndex = -1;
        }
        isDraggingPlayhead = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (newFileEditBox != null && newFileEditBox.isFocused()) return super.keyPressed(keyCode, scanCode, modifiers);
        if (bpmEditBox != null && bpmEditBox.isFocused()) return super.keyPressed(keyCode, scanCode, modifiers);

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
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_END) {
            if (!currentSequence.getEntries().isEmpty()) {
                currentSequence.sortEntriesByTimestamp();
                long maxTs = currentSequence.getEntries().get(currentSequence.getEntries().size() - 1).getTimestampMs();
                this.playheadMs = maxTs;
                int timelineWidth = this.width - 28;
                this.timeScrollMs = Math.max(0.0, maxTs - (timelineWidth * 0.5 / pixelsPerSecond) * 1000.0);
            }
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            double stepMs = hasShiftDown() ? (60000.0 / currentSequence.getBpm()) : 50.0;
            this.playheadMs = Math.max(0.0, playheadMs - stepMs);
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            double stepMs = hasShiftDown() ? (60000.0 / currentSequence.getBpm()) : 50.0;
            this.playheadMs = Math.max(0.0, playheadMs + stepMs);
            return true;
        }

        if (hasControlDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
            saveCurrentFile();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int getChannelIndex(String actionType) {
        if (actionType == null) return 0;
        for (int i = 0; i < ACTION_CHANNELS.length; i++) {
            if (ACTION_CHANNELS[i].equalsIgnoreCase(actionType)) return i;
        }
        return 0;
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

        // Update preview playhead position if playing & Auto-Follow timeline
        if (isPreviewPlaying) {
            long now = System.currentTimeMillis();
            long dt = now - lastPreviewTickTime;
            lastPreviewTickTime = now;
            playheadMs += dt;

            if (autoFollowPlayhead) {
                int timelineLeft = 14;
                int timelineWidth = effWidth - 28;
                double playheadScreenX = timelineLeft + ((playheadMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
                if (playheadScreenX > timelineLeft + (timelineWidth * 0.75)) {
                    timeScrollMs = Math.max(0.0, playheadMs - ((timelineWidth * 0.35) / pixelsPerSecond) * 1000.0);
                } else if (playheadScreenX < timelineLeft) {
                    timeScrollMs = Math.max(0.0, playheadMs - ((timelineWidth * 0.1) / pixelsPerSecond) * 1000.0);
                }
            }
        }

        int timelineLeft = 14;
        int timelineWidth = effWidth - 28;
        int timelineTop = 90;
        int rulerHeight = 24;
        int waveformHeight = 45;
        int channelHeight = 28;

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

        int statusX = effWidth - 240;
        if (selectedEntryIndex >= 0 && selectedEntryIndex < currentSequence.getEntries().size()) {
            MusicSequenceEntry sel = currentSequence.getEntries().get(selectedEntryIndex);
            long sec = sel.getTimestampMs() / 1000;
            long ms = sel.getTimestampMs() % 1000;
            String selStr = String.format("SEL: CH%d (%s) @ %02d:%02d.%03d [DEL to delete]",
                    getChannelIndex(sel.getActionType()) + 1, sel.getActionType(), sec / 60, sec % 60, ms);
            if (this.font.width(selStr) > 230) {
                selStr = this.font.plainSubstrByWidth(selStr, 224) + "..";
            }
            guiGraphics.drawString(this.font, selStr, statusX, 9, CYAN_MAIN, false);
        } else if (hasUnsavedChanges()) {
            guiGraphics.drawString(this.font, "* UNSAVED CHANGES", statusX + 60, 9, 0xFFFF3366, false);
        } else if (saveFeedbackMessage != null && System.currentTimeMillis() - saveFeedbackTime < 3000L) {
            String fb = saveFeedbackMessage;
            if (this.font.width(fb) > 230) {
                fb = this.font.plainSubstrByWidth(fb, 224) + "..";
            }
            guiGraphics.drawString(this.font, fb, statusX, 9, 0xFF00FF88, false);
        }

        // Timeline Outer Canvas Box
        int totalHeight = rulerHeight + waveformHeight + (ACTION_CHANNELS.length * channelHeight);
        guiGraphics.fill(timelineLeft, timelineTop, timelineLeft + timelineWidth, timelineTop + totalHeight, CYAN_BG);
        guiGraphics.fill(timelineLeft, timelineTop, timelineLeft + timelineWidth, timelineTop + 1, CARD_BORDER);
        guiGraphics.fill(timelineLeft, timelineTop + totalHeight - 1, timelineLeft + timelineWidth, timelineTop + totalHeight, CARD_BORDER);
        guiGraphics.fill(timelineLeft, timelineTop, timelineLeft + 1, timelineTop + totalHeight, CARD_BORDER);
        guiGraphics.fill(timelineLeft + timelineWidth - 1, timelineTop, timelineLeft + timelineWidth, timelineTop + totalHeight, CARD_BORDER);

        // 1. Time Ruler (Top Axis)
        guiGraphics.fill(timelineLeft, timelineTop, timelineLeft + timelineWidth, timelineTop + rulerHeight, 0xEE081622);
        guiGraphics.fill(timelineLeft, timelineTop + rulerHeight - 1, timelineLeft + timelineWidth, timelineTop + rulerHeight, 0xAA00E5FF);

        double secondsVisible = timelineWidth / pixelsPerSecond;
        int stepSec = secondsVisible > 30 ? 5 : 1;

        for (int sec = 0; sec <= (int) secondsVisible + 10; sec += stepSec) {
            double tickMs = (sec * 1000.0);
            double tickX = timelineLeft + ((tickMs - timeScrollMs) / 1000.0) * pixelsPerSecond;

            if (tickX >= timelineLeft && tickX <= timelineLeft + timelineWidth) {
                int tickY2 = timelineTop + rulerHeight - (sec % 5 == 0 ? 12 : 6);
                guiGraphics.fill((int) tickX, tickY2, (int) tickX + 1, timelineTop + rulerHeight, 0xAA00E5FF);

                if (sec % 5 == 0 || stepSec == 1) {
                    String timeLabel = String.format("%02d:%02d", sec / 60, sec % 60);
                    guiGraphics.drawString(this.font, timeLabel, (int) tickX - 12, timelineTop + 4, 0xFFAABBCC, false);
                }
            }
        }

        // 2. Channel 0: Audio & Waveform Channel
        int waveTopY = timelineTop + rulerHeight;
        guiGraphics.fill(timelineLeft, waveTopY, timelineLeft + timelineWidth, waveTopY + waveformHeight, 0xEE05101A);
        guiGraphics.fill(timelineLeft, waveTopY + waveformHeight - 1, timelineLeft + timelineWidth, waveTopY + waveformHeight, 0x6600E5FF);

        // Render Waveform Visualization & Header Tag
        MusicWaveformRenderer.renderWaveform(guiGraphics, currentSequence.getSongTrack(), timelineLeft, waveTopY, timelineWidth, waveformHeight, timeScrollMs, pixelsPerSecond);

        // 3. Action Channels (Channels 1 - 5)
        int currentTrackY = waveTopY + waveformHeight;

        for (int c = 0; c < ACTION_CHANNELS.length; c++) {
            String chName = ACTION_CHANNELS[c];
            int chColor = CHANNEL_COLORS[c];

            int rowBg = (c % 2 == 0) ? 0xEE091624 : 0xEE060F1A;
            guiGraphics.fill(timelineLeft, currentTrackY, timelineLeft + timelineWidth, currentTrackY + channelHeight, rowBg);
            guiGraphics.fill(timelineLeft, currentTrackY + channelHeight - 1, timelineLeft + timelineWidth, currentTrackY + channelHeight, 0x4400E5FF);

            // Channel Header Label
            guiGraphics.drawString(this.font, "CH " + (c + 1) + ": " + chName, timelineLeft + 8, currentTrackY + 8, chColor, false);

            currentTrackY += channelHeight;
        }

        // 3.5 Rekordbox-Style Beat Grid & Red Bar Lines (50ms game ticks & red bar starts)
        int bpm = currentSequence.getBpm();
        double beatMs = 60000.0 / (double) Math.max(20, bpm);
        double gameTickMs = 50.0; // 1 Minecraft tick = 50ms

        int gridStartY = waveTopY;
        int gridTotalHeight = waveformHeight + (ACTION_CHANNELS.length * channelHeight);

        // A. Render 50ms Game Tick Grid Lines (Possible action snap positions)
        double firstTick = Math.floor(timeScrollMs / gameTickMs);
        double lastTick = Math.ceil((timeScrollMs + (timelineWidth / pixelsPerSecond) * 1000.0) / gameTickMs);

        for (double t = firstTick; t <= lastTick; t++) {
            double tMs = t * gameTickMs;
            double tickX = timelineLeft + ((tMs - timeScrollMs) / 1000.0) * pixelsPerSecond;

            if (tickX >= timelineLeft && tickX <= timelineLeft + timelineWidth) {
                guiGraphics.fill((int) tickX, gridStartY + waveformHeight, (int) tickX + 1, gridStartY + gridTotalHeight, 0x1500E5FF);
            }
        }

        // B. Render Beat Lines & Rekordbox Red Bar Lines
        double firstBeat = Math.floor(timeScrollMs / beatMs);
        double lastBeat = Math.ceil((timeScrollMs + (timelineWidth / pixelsPerSecond) * 1000.0) / beatMs);

        for (double b = firstBeat; b <= lastBeat; b++) {
            double bMs = b * beatMs;
            double beatX = timelineLeft + ((bMs - timeScrollMs) / 1000.0) * pixelsPerSecond;

            if (beatX >= timelineLeft && beatX <= timelineLeft + timelineWidth) {
                long beatIndex = Math.round(b);
                boolean isBarStart = (beatIndex % 4 == 0);

                if (isBarStart) {
                    // Rekordbox Red Bar Line at start of every 4-beat bar
                    guiGraphics.fill((int) beatX - 1, gridStartY, (int) beatX + 1, gridStartY + gridTotalHeight, 0xFFFF0055);

                    // Red Bar Badge Number on Ruler
                    long barNumber = (beatIndex / 4) + 1;
                    if (barNumber > 0) {
                        guiGraphics.drawString(this.font, "B" + barNumber, (int) beatX + 3, gridStartY - 10, 0xFFFF0055, false);
                    }
                } else {
                    // Sub-beat line
                    guiGraphics.fill((int) beatX, gridStartY, (int) beatX + 1, gridStartY + gridTotalHeight, 0x3300E5FF);
                }
            }
        }

        // 3.6 Sequence START & END Draggable Markers & Out-Of-Bounds Dimming
        long startMs = currentSequence.getStartMs();
        long endMs = getEffectiveEndMs();

        // DAW Preview Playback Loop/Pause between startMs and endMs
        if (isPreviewPlaying) {
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
                }
            }
        }

        double startX = timelineLeft + ((startMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
        double endX = timelineLeft + ((endMs - timeScrollMs) / 1000.0) * pixelsPerSecond;

        // A. Dimmed Region BEFORE START
        if (startX > timelineLeft) {
            int dimW = (int) Math.min((double) timelineWidth, startX - timelineLeft);
            if (dimW > 0) {
                guiGraphics.fill(timelineLeft, timelineTop, timelineLeft + dimW, timelineTop + totalHeight, 0xCC050A10);
                guiGraphics.drawString(this.font, "BEFORE START", timelineLeft + 6, timelineTop + 6, 0xAA888888, false);
            }
        }

        // B. Dimmed Region AFTER END
        if (endX < timelineLeft + timelineWidth) {
            int dimX = (int) Math.max((double) timelineLeft, endX);
            int dimW = (timelineLeft + timelineWidth) - dimX;
            if (dimW > 0) {
                guiGraphics.fill(dimX, timelineTop, dimX + dimW, timelineTop + totalHeight, 0xCC050A10);
                guiGraphics.drawString(this.font, "AFTER END", dimX + 6, timelineTop + 6, 0xAA888888, false);
            }
        }

        // C. START Line & Flag Badge
        if (startX >= timelineLeft && startX <= timelineLeft + timelineWidth) {
            int sx = (int) startX;
            guiGraphics.fill(sx - 1, timelineTop, sx + 2, timelineTop + totalHeight, 0xFF00FF88);
            guiGraphics.fill(sx - 14, timelineTop, sx + 14, timelineTop + 14, 0xEE00FF88);
            guiGraphics.drawCenteredString(this.font, "IN", sx, timelineTop + 3, 0xFF000000);
        }

        // D. END Line & Flag Badge
        if (endX >= timelineLeft && endX <= timelineLeft + timelineWidth) {
            int ex = (int) endX;
            guiGraphics.fill(ex - 1, timelineTop, ex + 2, timelineTop + totalHeight, 0xFFFF3366);
            guiGraphics.fill(ex - 16, timelineTop, ex + 16, timelineTop + 14, 0xEEFF3366);
            guiGraphics.drawCenteredString(this.font, "OUT", ex, timelineTop + 3, 0xFFFFFFFF);
        }

        // 4. Action Keyframe Nodes
        List<MusicSequenceEntry> entries = currentSequence.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            MusicSequenceEntry entry = entries.get(i);
            int channelIndex = getChannelIndex(entry.getActionType());
            int chColor = CHANNEL_COLORS[channelIndex];

            int entryTrackY = waveTopY + waveformHeight + (channelIndex * channelHeight);
            double entryX = timelineLeft + ((entry.getTimestampMs() - timeScrollMs) / 1000.0) * pixelsPerSecond;

            if (entryX >= timelineLeft && entryX <= timelineLeft + timelineWidth) {
                int kx = (int) entryX;
                int ky = entryTrackY + (channelHeight / 2);

                boolean isHovered = (scaledMouseX >= kx - 8 && scaledMouseX <= kx + 8 && scaledMouseY >= entryTrackY && scaledMouseY <= entryTrackY + channelHeight);
                boolean isSelected = (i == selectedEntryIndex);

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

        // 5. Render Vertical Red Playhead Scrubber Line
        double playheadX = timelineLeft + ((playheadMs - timeScrollMs) / 1000.0) * pixelsPerSecond;
        if (playheadX >= timelineLeft && playheadX <= timelineLeft + timelineWidth) {
            int px = (int) playheadX;
            guiGraphics.fill(px - 1, timelineTop, px + 2, timelineTop + totalHeight, PLAYHEAD_COLOR);

            // Playhead Handle Cap
            guiGraphics.fill(px - 5, timelineTop, px + 6, timelineTop + 10, PLAYHEAD_COLOR);
            guiGraphics.fill(px - 4, timelineTop + 1, px + 5, timelineTop + 9, 0xFFFFFFFF);
        }

        super.render(guiGraphics, scaledMouseX, scaledMouseY, partialTick);

        if (this.fileDropdown != null) {
            this.fileDropdown.renderOverlay(guiGraphics, scaledMouseX, scaledMouseY);
        }
        if (this.trackDropdown != null) {
            this.trackDropdown.renderOverlay(guiGraphics, scaledMouseX, scaledMouseY);
        }

        guiGraphics.pose().popPose();
    }
}
