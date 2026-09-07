package net.dandare21.fracturedutils.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dandare21.fracturedutils.sound.sequence.MusicSequence;
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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class MusicSequenceSelectScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int CARD_BORDER = 0xAA00E5FF;
    private static final int RED_DELETE = 0xFFFF3355;
    private static final int GREEN_RUN = 0xFF00FF88;
    private static final int YELLOW_RENAME = 0xFFFFD700;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, String> savedServerSequenceFiles;
    private final Map<String, String> workingServerSequenceFiles;
    private final Map<String, String> savedClientSequenceFiles;
    private final Map<String, String> workingClientSequenceFiles;
    private final List<String> availableTracks;
    private boolean isClientMode = false;

    private EditBox searchBox;
    private String searchQuery = "";

    private double scrollAmount = 0;
    private boolean isDraggingScrollbar = false;
    private double dragOffsetY = 0;

    private static class SequenceCardRow {
        String fileName;
        String songTrack;
        int entryCount;
        int bpm;
        boolean looping;
        int y;
        CyberpunkButton editBtn;
        CyberpunkButton runBtn;
        CyberpunkButton renameBtn;
        CyberpunkButton deleteBtn;
    }

    private final List<SequenceCardRow> cardRows = new ArrayList<>();

    public MusicSequenceSelectScreen(Map<String, String> serverSequenceFiles, List<String> availableTracks) {
        super(Component.literal("Music Sequence Hub"));
        this.savedServerSequenceFiles = serverSequenceFiles != null ? new HashMap<>(serverSequenceFiles) : new HashMap<>();
        this.workingServerSequenceFiles = new HashMap<>(this.savedServerSequenceFiles);
        this.savedClientSequenceFiles = loadLocalClientSequences();
        this.workingClientSequenceFiles = new HashMap<>(this.savedClientSequenceFiles);
        this.availableTracks = availableTracks != null ? new ArrayList<>(availableTracks) : new ArrayList<>();
        for (String track : net.dandare21.fracturedutils.sound.event.AudioTrackDiscovery.getAllAvailableTracks()) {
            if (!this.availableTracks.contains(track)) {
                this.availableTracks.add(track);
            }
        }

        if (this.savedServerSequenceFiles != null && !this.savedServerSequenceFiles.isEmpty()) {
            this.isClientMode = false;
        } else {
            boolean isMultiplayer = Minecraft.getInstance().getCurrentServer() != null || !Minecraft.getInstance().isSingleplayer();
            this.isClientMode = !isMultiplayer;
        }
    }

    public MusicSequenceSelectScreen(MusicSequenceScreen editorScreen) {
        this(editorScreen.getWorkingServerSequenceFiles(), editorScreen.getAvailableTracks());
        this.isClientMode = editorScreen.isClientMode();
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double getLayoutScale() {
        int targetW = 680;
        int targetH = 400;
        if (this.width <= 0 || this.height <= 0) return 1.0;
        double scaleX = (double) this.width / targetW;
        double scaleY = (double) this.height / targetH;
        return Math.min(1.0, Math.min(scaleX, scaleY));
    }

    private Map<String, String> getActiveSavedMap() {
        return isClientMode ? savedClientSequenceFiles : savedServerSequenceFiles;
    }

    private Map<String, String> getActiveWorkingMap() {
        return isClientMode ? workingClientSequenceFiles : workingServerSequenceFiles;
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

    private void saveLocalClientSequence(String fileName, String jsonContent) {
        File dir = MusicSequenceManager.getInstance().getDirectory();
        File file = new File(dir, sanitizeFileName(fileName));
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(jsonContent);
        } catch (IOException ignored) {}
    }

    private void deleteLocalClientSequence(String fileName) {
        File dir = MusicSequenceManager.getInstance().getDirectory();
        File file = new File(dir, sanitizeFileName(fileName));
        if (file.exists()) {
            file.delete();
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "sequence.json";
        String clean = fileName.trim();
        if (!clean.endsWith(".json")) clean += ".json";
        return clean.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.cardRows.clear();

        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        int leftX = 24;
        int topY = 40;
        int panelWidth = effWidth - 48;

        // 1. Search Box
        this.searchBox = new EditBox(this.font, leftX, topY, 180, 20, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search sequences / tracks..."));
        this.searchBox.setValue(searchQuery);
        this.searchBox.setResponder(text -> {
            this.searchQuery = text.trim().toLowerCase(Locale.ROOT);
            this.scrollAmount = 0;
            updateRowPositions();
        });
        this.addRenderableWidget(this.searchBox);

        // 2. Storage Mode Toggle (if multiplayer)
        boolean isMultiplayer = Minecraft.getInstance().getCurrentServer() != null || !Minecraft.getInstance().isSingleplayer();
        if (isMultiplayer) {
            CyberpunkButton modeBtn = new CyberpunkButton(leftX + 190, topY, 125, 20,
                    Component.literal(isClientMode ? "LOCAL STORAGE" : "SERVER STORAGE"),
                    b -> {
                        this.isClientMode = !this.isClientMode;
                        this.scrollAmount = 0;
                        this.init();
                    },
                    isClientMode ? YELLOW_RENAME : CYAN_MAIN,
                    false
            );
            modeBtn.setTooltip(Tooltip.create(Component.literal("Toggle between editing server-stored or client-local sequences")));
            this.addRenderableWidget(modeBtn);
        }

        // 3. New Music Sequence Button (Triggers song request first)
        CyberpunkButton newSequenceBtn = new CyberpunkButton(
                leftX + panelWidth - 160, topY, 160, 20,
                Component.literal("+ NEW MUSIC SEQUENCE"),
                b -> openCreateModal(),
                CYAN_MAIN,
                false
        );
        newSequenceBtn.setTooltip(Tooltip.create(Component.literal("Create a new music sequence, choosing the song track first")));
        this.addRenderableWidget(newSequenceBtn);

        // 4. Populate and build Sequence Card Rows
        Map<String, String> activeMap = getActiveWorkingMap();
        List<String> sortedFiles = new ArrayList<>(activeMap.keySet());
        Collections.sort(sortedFiles);

        for (String fileName : sortedFiles) {
            String json = activeMap.get(fileName);
            MusicSequence seq = null;
            if (json != null) {
                try {
                    seq = GSON.fromJson(json, MusicSequence.class);
                } catch (Exception ignored) {}
            }
            if (seq == null) {
                seq = new MusicSequence(fileName, "", false, 1.0f, 1.0f, new ArrayList<>());
            }

            SequenceCardRow row = new SequenceCardRow();
            row.fileName = fileName;
            row.songTrack = seq.getSongTrack();
            row.entryCount = seq.getEntries() != null ? seq.getEntries().size() : 0;
            row.bpm = seq.getBpm();
            row.looping = seq.isLooping();

            // Edit button
            row.editBtn = new CyberpunkButton(0, 0, 56, 20, Component.literal("EDIT ✏"), b -> openEditor(fileName), CYAN_MAIN, false);
            row.editBtn.setTooltip(Tooltip.create(Component.literal("Open timeline editor for " + fileName)));

            // Run button
            row.runBtn = new CyberpunkButton(0, 0, 50, 20, Component.literal("RUN ▶"), b -> runSequence(fileName), GREEN_RUN, false);
            row.runBtn.setTooltip(Tooltip.create(Component.literal("Execute and test playback of " + fileName)));

            // Rename button
            row.renameBtn = new CyberpunkButton(0, 0, 60, 20, Component.literal("RENAME"), b -> openRenameModal(fileName), YELLOW_RENAME, false);
            row.renameBtn.setTooltip(Tooltip.create(Component.literal("Rename sequence file " + fileName)));

            // Delete button
            row.deleteBtn = new CyberpunkButton(0, 0, 60, 20, Component.literal("DELETE"), b -> openDeleteModal(fileName), RED_DELETE, false);
            row.deleteBtn.setTooltip(Tooltip.create(Component.literal("Delete sequence file " + fileName)));

            this.addWidget(row.editBtn);
            this.addWidget(row.runBtn);
            this.addWidget(row.renameBtn);
            this.addWidget(row.deleteBtn);

            this.cardRows.add(row);
        }

        // 5. Close Button in Footer
        int footerY = effHeight - 28;
        CyberpunkButton closeBtn = new CyberpunkButton(leftX, footerY, 90, 20, Component.literal("CLOSE"), b -> this.onClose(), 0xFF8899AA, false);
        this.addRenderableWidget(closeBtn);

        updateRowPositions();
    }

    private void openCreateModal() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new CreateMusicSequenceModalScreen(this, availableTracks, newSequence -> {
                String fileName = sanitizeFileName(newSequence.getFileName());
                newSequence.setFileName(fileName);
                String json = GSON.toJson(newSequence);

                getActiveWorkingMap().put(fileName, json);
                if (isClientMode) {
                    saveLocalClientSequence(fileName, json);
                    getActiveSavedMap().put(fileName, json);
                } else {
                    ModMessages.sendToServer(new C2SSaveMusicSequencePacket(fileName, json));
                    getActiveSavedMap().put(fileName, json);
                }

                // Immediately open timeline editor for the newly created sequence!
                openEditor(fileName);
            }));
        }
    }

    private void openEditor(String fileName) {
        if (this.minecraft != null) {
            MusicSequenceScreen screen = new MusicSequenceScreen(this.workingServerSequenceFiles, this.availableTracks);
            screen.setParentSelectScreen(this);
            screen.selectSequenceFileDirectly(fileName, this.isClientMode);
            this.minecraft.setScreen(screen);
        }
    }

    private void runSequence(String fileName) {
        if (isClientMode) {
            String json = getActiveWorkingMap().get(fileName);
            if (json != null) {
                try {
                    MusicSequence seq = GSON.fromJson(json, MusicSequence.class);
                    if (seq != null && seq.getSongTrack() != null && !seq.getSongTrack().isBlank()) {
                        net.dandare21.fracturedutils.sound.event.EventAudioClientController.getInstance().playAudio(
                                seq.getSongTrack(),
                                net.dandare21.fracturedutils.sound.ModSoundSources.EVENT_MUSIC,
                                seq.getVolume(),
                                seq.getPitch(),
                                0,
                                0L,
                                true,
                                net.dandare21.fracturedutils.network.packet.S2CPlayEventAudioPacket.PlaybackMode.FIRE_AND_FORGET,
                                seq.isLooping(),
                                2000
                        );
                    }
                } catch (Exception ignored) {}
            }
        } else {
            ModMessages.sendToServer(new C2SStartMusicSequencePacket(fileName));
        }
    }

    private void openRenameModal(String oldName) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new RenameMusicSequenceModalScreen(this, oldName, newName -> {
                String sanitized = sanitizeFileName(newName);
                if (!sanitized.equalsIgnoreCase(oldName)) {
                    String content = getActiveWorkingMap().remove(oldName);
                    String savedContent = getActiveSavedMap().remove(oldName);
                    if (content == null) content = savedContent != null ? savedContent : "{}";

                    getActiveWorkingMap().put(sanitized, content);
                    getActiveSavedMap().put(sanitized, content);

                    if (isClientMode) {
                        deleteLocalClientSequence(oldName);
                        saveLocalClientSequence(sanitized, content);
                    } else {
                        ModMessages.sendToServer(new C2SDeleteMusicSequencePacket(oldName));
                        ModMessages.sendToServer(new C2SSaveMusicSequencePacket(sanitized, content));
                    }
                    this.init();
                }
            }));
        }
    }

    private void openDeleteModal(String fileName) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ConfirmDeleteMusicSequenceModalScreen(this, fileName, () -> {
                getActiveWorkingMap().remove(fileName);
                getActiveSavedMap().remove(fileName);

                if (isClientMode) {
                    deleteLocalClientSequence(fileName);
                } else {
                    ModMessages.sendToServer(new C2SDeleteMusicSequencePacket(fileName));
                }
                this.init();
            }));
        }
    }

    private List<SequenceCardRow> getFilteredRows() {
        if (searchQuery == null || searchQuery.isBlank()) {
            return cardRows;
        }
        List<SequenceCardRow> filtered = new ArrayList<>();
        for (SequenceCardRow row : cardRows) {
            boolean nameMatch = row.fileName.toLowerCase(Locale.ROOT).contains(searchQuery);
            boolean songMatch = row.songTrack != null && row.songTrack.toLowerCase(Locale.ROOT).contains(searchQuery);
            if (nameMatch || songMatch) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private int getListTop() {
        return 68;
    }

    private int getListBottom(int effHeight) {
        return effHeight - 34;
    }

    private int getRowHeight() {
        return 48;
    }

    private void updateRowPositions() {
        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        int leftX = 24;
        int listTop = getListTop();
        int listBottom = getListBottom(effHeight);
        int cardWidth = effWidth - 48;
        int rowH = getRowHeight();

        List<SequenceCardRow> visibleRows = getFilteredRows();
        int totalHeight = visibleRows.size() * (rowH + 4);
        int viewHeight = listBottom - listTop;
        double maxScroll = Math.max(0, totalHeight - viewHeight);
        this.scrollAmount = Math.max(0, Math.min(maxScroll, this.scrollAmount));

        // Hide all rows by default
        for (SequenceCardRow row : cardRows) {
            row.editBtn.visible = false;
            row.runBtn.visible = false;
            row.renameBtn.visible = false;
            row.deleteBtn.visible = false;
        }

        int startY = (int) (listTop - scrollAmount);
        for (int i = 0; i < visibleRows.size(); i++) {
            SequenceCardRow row = visibleRows.get(i);
            int currentY = startY + i * (rowH + 4);
            row.y = currentY;

            boolean inView = currentY + rowH >= listTop && currentY <= listBottom;

            int btnY = currentY + 14;
            int rightX = leftX + cardWidth - 10;

            row.deleteBtn.setX(rightX - 60);
            row.deleteBtn.setY(btnY);
            row.deleteBtn.visible = inView;

            row.renameBtn.setX(rightX - 124);
            row.renameBtn.setY(btnY);
            row.renameBtn.visible = inView;

            row.runBtn.setX(rightX - 178);
            row.runBtn.setY(btnY);
            row.runBtn.visible = inView;

            row.editBtn.setX(rightX - 238);
            row.editBtn.setY(btnY);
            row.editBtn.visible = inView;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double scale = getLayoutScale();
        int effHeight = (int) (this.height / scale);

        int listTop = getListTop();
        int listBottom = getListBottom(effHeight);
        int rowH = getRowHeight();

        List<SequenceCardRow> visibleRows = getFilteredRows();
        int totalHeight = visibleRows.size() * (rowH + 4);
        int viewHeight = listBottom - listTop;
        double maxScroll = Math.max(0, totalHeight - viewHeight);

        this.scrollAmount = Math.max(0, Math.min(maxScroll, this.scrollAmount - delta * 24.0));
        updateRowPositions();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }

        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);
        int listTop = getListTop();
        int listBottom = getListBottom(effHeight);
        int scrollbarX = effWidth - 20;

        if (button == 0 && mouseX >= scrollbarX - 4 && mouseX <= scrollbarX + 8 && mouseY >= listTop && mouseY <= listBottom) {
            this.isDraggingScrollbar = true;
            this.dragOffsetY = mouseY;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar) {
            double scale = getLayoutScale();
            if (scale < 1.0) {
                mouseY /= scale;
            }
            int effHeight = (int) (this.height / scale);
            int listTop = getListTop();
            int listBottom = getListBottom(effHeight);
            int viewHeight = listBottom - listTop;

            List<SequenceCardRow> visibleRows = getFilteredRows();
            int totalHeight = visibleRows.size() * (getRowHeight() + 4);
            double maxScroll = Math.max(0, totalHeight - viewHeight);

            double delta = (mouseY - dragOffsetY) / viewHeight * totalHeight;
            this.scrollAmount = Math.max(0, Math.min(maxScroll, this.scrollAmount + delta));
            this.dragOffsetY = mouseY;
            updateRowPositions();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void drawBorderBox(GuiGraphics graphics, int x, int y, int w, int h, int borderColor, int fillColor) {
        graphics.fill(x, y, x + w, y + h, fillColor);
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        graphics.fill(x, y + 1, x + 1, y + h, borderColor);
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

        // Background & Cyberpunk Accent Lines
        graphics.fill(0, 0, effWidth, effHeight, CYAN_BG);
        graphics.fill(0, 0, effWidth, 32, 0xEE09141D);
        graphics.fill(0, 31, effWidth, 32, CYAN_MAIN);

        // Title Header
        graphics.drawString(this.font, "♫ MUSIC SEQUENCE HUB", 24, 8, CYAN_MAIN, false);
        graphics.drawString(this.font, "Select a music sequence to edit or create a new audio-driven choreography", 24, 20, 0xFFAABBCC, false);

        int leftX = 24;
        int listTop = getListTop();
        int listBottom = getListBottom(effHeight);
        int cardWidth = effWidth - 48;
        int rowH = getRowHeight();

        // Enable scissor clipping for scrollable area
        graphics.enableScissor(
                (int) (leftX * scale),
                (int) (listTop * scale),
                (int) ((leftX + cardWidth) * scale),
                (int) (listBottom * scale)
        );

        List<SequenceCardRow> visibleRows = getFilteredRows();

        if (visibleRows.isEmpty()) {
            int emptyY = listTop + 40;
            drawBorderBox(graphics, leftX + 40, emptyY, cardWidth - 80, 70, 0x5500E5FF, 0xDD09141D);
            graphics.drawCenteredString(this.font, Component.literal("No music sequences found matching your filter."), leftX + cardWidth / 2, emptyY + 18, 0xFFAABBCC);
            graphics.drawCenteredString(this.font, Component.literal("Click [+ NEW MUSIC SEQUENCE] in the top right to create one!"), leftX + cardWidth / 2, emptyY + 36, CYAN_MAIN);
        } else {
            for (SequenceCardRow row : visibleRows) {
                if (row.y + rowH < listTop || row.y > listBottom) continue;

                boolean isHovered = scaledMouseX >= leftX && scaledMouseX <= leftX + cardWidth && scaledMouseY >= row.y && scaledMouseY <= row.y + rowH;
                int bgColor = isHovered ? 0xEE0D1B26 : 0xEE08121B;
                int borderColor = isHovered ? CYAN_MAIN : 0x6600E5FF;

                drawBorderBox(graphics, leftX, row.y, cardWidth, rowH, borderColor, bgColor);

                // Left accent tag
                graphics.fill(leftX + 1, row.y + 1, leftX + 4, row.y + rowH - 1, CYAN_MAIN);

                // File Name
                graphics.drawString(this.font, "▶  " + row.fileName, leftX + 12, row.y + 8, 0xFFFFFFFF, false);

                // Subtitle details
                String songTitle = (row.songTrack != null && !row.songTrack.isBlank()) ? net.dandare21.fracturedutils.sound.event.AudioTrackDiscovery.formatTrackLabel(row.songTrack) : "[No Audio Track]";
                String songLabel = (row.songTrack != null && !row.songTrack.isBlank()) ? "🎵 " + songTitle + " (" + row.songTrack + ")" : "🔇 " + songTitle;
                String details = String.format(Locale.US, "%s  |  ⏱ %d Keyframe(s)  |  %d BPM  |  %s",
                        songLabel, row.entryCount, row.bpm, row.looping ? "🔁 Looping" : "One-shot");
                graphics.drawString(this.font, details, leftX + 12, row.y + 24, 0xFFAABBCC, false);

                // Render Action Buttons
                row.editBtn.render(graphics, scaledMouseX, scaledMouseY, partialTick);
                row.runBtn.render(graphics, scaledMouseX, scaledMouseY, partialTick);
                row.renameBtn.render(graphics, scaledMouseX, scaledMouseY, partialTick);
                row.deleteBtn.render(graphics, scaledMouseX, scaledMouseY, partialTick);
            }
        }

        graphics.disableScissor();

        // Render Scrollbar
        int totalHeight = visibleRows.size() * (rowH + 4);
        int viewHeight = listBottom - listTop;
        if (totalHeight > viewHeight && totalHeight > 0) {
            int scrollbarX = effWidth - 18;
            graphics.fill(scrollbarX, listTop, scrollbarX + 4, listBottom, 0x4400E5FF);

            int thumbHeight = Math.max(16, (int) ((double) viewHeight / totalHeight * viewHeight));
            double maxScroll = totalHeight - viewHeight;
            int thumbY = (int) (listTop + (scrollAmount / maxScroll) * (viewHeight - thumbHeight));
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, CYAN_MAIN);
        }

        // Footer Bar
        int footerY = effHeight - 30;
        graphics.fill(0, footerY - 4, effWidth, effHeight, 0xEE09141D);
        graphics.fill(0, footerY - 4, effWidth, footerY - 3, 0x4400E5FF);

        String footerStatus = String.format(Locale.US, "Showing %d of %d sequence(s)  |  Storage: %s",
                visibleRows.size(), cardRows.size(), isClientMode ? "Local Client Config" : "Server Storage");
        graphics.drawString(this.font, footerStatus, leftX + 105, footerY + 6, 0xFFAABBCC, false);

        super.render(graphics, scaledMouseX, scaledMouseY, partialTick);
        graphics.pose().popPose();
    }
}
