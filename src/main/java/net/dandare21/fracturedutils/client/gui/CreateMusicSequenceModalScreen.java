package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.sound.event.AudioTrackDiscovery;
import net.dandare21.fracturedutils.sound.event.EventAudioClientController;
import net.dandare21.fracturedutils.sound.sequence.MusicSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;

public class CreateMusicSequenceModalScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int CARD_BORDER = 0xAA00E5FF;
    private static final int RED_CANCEL = 0xFFFF3355;
    private static final int GREEN_PLAY = 0xFF00FF88;
    private static final int YELLOW_SELECTED = 0xFFFFD700;

    private final Screen parentScreen;
    private final Consumer<MusicSequence> onSequenceCreated;
    private final List<String> allTracks = new ArrayList<>();

    private EditBox searchBox;
    private String searchQuery = "";

    private EditBox fileNameEditBox;
    private EditBox bpmEditBox;
    private CyberpunkCheckbox loopCheckbox;

    private String selectedTrack = "";
    private String previewingTrack = null;
    private boolean userEditedName = false;

    private double scrollAmount = 0;
    private boolean isDraggingScrollbar = false;
    private double dragOffsetY = 0;

    private static class AudioTrackRow {
        String trackId;
        String displayTitle;
        int y;
        CyberpunkButton selectBtn;
        CyberpunkButton previewBtn;
    }

    private final List<AudioTrackRow> trackRows = new ArrayList<>();

    public CreateMusicSequenceModalScreen(Screen parentScreen, List<String> availableTracks, Consumer<MusicSequence> onSequenceCreated) {
        super(Component.literal("Select Song Track for Sequence"));
        this.parentScreen = parentScreen;
        this.onSequenceCreated = onSequenceCreated;

        Set<String> trackSet = new LinkedHashSet<>();
        if (availableTracks != null) {
            trackSet.addAll(availableTracks);
        }
        trackSet.addAll(AudioTrackDiscovery.getAllAvailableTracks());

        this.allTracks.addAll(trackSet);
        if (!this.allTracks.isEmpty()) {
            this.selectedTrack = this.allTracks.get(0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double getLayoutScale() {
        int targetW = 560;
        int targetH = 380;
        if (this.width <= 0 || this.height <= 0) return 1.0;
        double scaleX = (double) this.width / targetW;
        double scaleY = (double) this.height / targetH;
        return Math.min(1.0, Math.min(scaleX, scaleY));
    }

    private int getListTop(int top) {
        return top + 64;
    }

    private int getListBottom(int top, int panelHeight) {
        return top + panelHeight - 92;
    }

    private int getRowHeight() {
        return 34;
    }

    private List<AudioTrackRow> getFilteredRows() {
        if (searchQuery == null || searchQuery.isBlank()) {
            return trackRows;
        }
        List<AudioTrackRow> filtered = new ArrayList<>();
        for (AudioTrackRow row : trackRows) {
            boolean titleMatch = row.displayTitle.toLowerCase(Locale.ROOT).contains(searchQuery);
            boolean idMatch = row.trackId.toLowerCase(Locale.ROOT).contains(searchQuery);
            if (titleMatch || idMatch) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.trackRows.clear();

        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        int panelWidth = Math.min(effWidth - 24, 540);
        int panelHeight = Math.min(effHeight - 20, 360);
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        // 1. Search Box
        this.searchBox = new EditBox(this.font, left + 16, top + 36, panelWidth - 32, 20, Component.literal("Search Audio"));
        this.searchBox.setHint(Component.literal("Search songs and sound IDs..."));
        this.searchBox.setValue(searchQuery);
        this.searchBox.setResponder(text -> {
            this.searchQuery = text.trim().toLowerCase(Locale.ROOT);
            this.scrollAmount = 0;
            updateRowPositions(left, top, panelWidth, panelHeight);
        });
        this.addRenderableWidget(this.searchBox);

        // 2. Build Audio Track Rows
        for (String track : allTracks) {
            AudioTrackRow row = new AudioTrackRow();
            row.trackId = track;
            row.displayTitle = AudioTrackDiscovery.formatTrackLabel(track);

            // Select button
            boolean isSelected = track.equalsIgnoreCase(selectedTrack);
            row.selectBtn = new CyberpunkButton(0, 0, 75, 20,
                    Component.literal(isSelected ? "✓ SELECTED" : "SELECT"),
                    b -> selectTrack(track),
                    isSelected ? YELLOW_SELECTED : CYAN_MAIN,
                    isSelected
            );
            row.selectBtn.setTooltip(Tooltip.create(Component.literal("Choose " + row.displayTitle + " for sequence")));

            // Preview button
            boolean isThisPreviewing = track.equalsIgnoreCase(previewingTrack);
            row.previewBtn = new CyberpunkButton(0, 0, 50, 20,
                    Component.literal(isThisPreviewing ? "⏸ STOP" : "▶ PLAY"),
                    b -> togglePreviewTrack(track),
                    isThisPreviewing ? 0xFFFFD700 : GREEN_PLAY,
                    false
            );
            row.previewBtn.setTooltip(Tooltip.create(Component.literal("Listen to " + row.displayTitle)));

            this.addWidget(row.selectBtn);
            this.addWidget(row.previewBtn);
            this.trackRows.add(row);
        }

        // 3. Bottom Configuration Controls
        int configY = getListBottom(top, panelHeight) + 8;

        // Sequence File Name
        this.fileNameEditBox = new EditBox(this.font, left + 110, configY, panelWidth - 126, 20, Component.literal("Sequence Name"));
        this.fileNameEditBox.setMaxLength(64);
        String initialName = deriveDefaultSequenceName(selectedTrack);
        this.fileNameEditBox.setValue(initialName);
        this.fileNameEditBox.setResponder(text -> {
            if (!text.trim().equals(initialName)) {
                this.userEditedName = true;
            }
        });
        this.addRenderableWidget(this.fileNameEditBox);

        // BPM & Looping & Action Buttons Row
        int actionRowY = configY + 26;

        this.bpmEditBox = new EditBox(this.font, left + 45, actionRowY, 45, 20, Component.literal("BPM"));
        this.bpmEditBox.setMaxLength(3);
        this.bpmEditBox.setValue("120");
        this.bpmEditBox.setTooltip(Tooltip.create(Component.literal("Song Tempo in BPM (Beats Per Minute)")));
        this.addRenderableWidget(this.bpmEditBox);

        this.loopCheckbox = new CyberpunkCheckbox(
                left + 98, actionRowY, 80, 20,
                Component.literal("Loop Song"),
                false,
                null
        );
        this.addRenderableWidget(this.loopCheckbox);

        CyberpunkButton cancelBtn = new CyberpunkButton(
                left + panelWidth - 250, actionRowY, 80, 20,
                Component.literal("CANCEL"),
                b -> this.onClose(),
                RED_CANCEL,
                false
        );
        this.addRenderableWidget(cancelBtn);

        CyberpunkButton createBtn = new CyberpunkButton(
                left + panelWidth - 165, actionRowY, 150, 20,
                Component.literal("CREATE SEQUENCE ⚡"),
                b -> confirmAndCreate(),
                CYAN_MAIN,
                false
        );
        createBtn.setTooltip(Tooltip.create(Component.literal("Create sequence and open timeline editor")));
        this.addRenderableWidget(createBtn);

        updateRowPositions(left, top, panelWidth, panelHeight);
    }

    private void selectTrack(String track) {
        this.selectedTrack = track;
        if (!userEditedName && fileNameEditBox != null) {
            fileNameEditBox.setValue(deriveDefaultSequenceName(track));
        }
        this.init();
    }

    private void togglePreviewTrack(String track) {
        if (track == null || track.isBlank()) return;

        if (track.equalsIgnoreCase(previewingTrack)) {
            stopPreviewAudio();
            this.init();
            return;
        }

        stopPreviewAudio();
        this.previewingTrack = track;

        EventAudioClientController.getInstance().playAudio(
                track,
                net.dandare21.fracturedutils.sound.ModSoundSources.EVENT_MUSIC,
                1.0f,
                1.0f,
                0,
                0L,
                true,
                net.dandare21.fracturedutils.network.packet.S2CPlayEventAudioPacket.PlaybackMode.FIRE_AND_FORGET,
                false,
                2000
        );
        this.init();
    }

    private void stopPreviewAudio() {
        if (previewingTrack != null) {
            EventAudioClientController.getInstance().stopAudio(0);
            this.previewingTrack = null;
        }
    }

    private String deriveDefaultSequenceName(String track) {
        if (track == null || track.trim().isEmpty()) {
            return "sequence.json";
        }
        String clean = track.trim();
        if (clean.startsWith("event.")) clean = clean.substring(6);
        if (clean.contains(":")) clean = clean.substring(clean.indexOf(':') + 1);
        if (clean.startsWith("music_disc.")) clean = clean.substring("music_disc.".length());
        if (clean.startsWith("music.")) clean = clean.substring("music.".length());

        clean = clean.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        if (clean.isEmpty()) clean = "sequence";
        return clean + "_sequence.json";
    }

    private void confirmAndCreate() {
        String fileName = fileNameEditBox != null ? fileNameEditBox.getValue().trim() : "";
        if (fileName.isEmpty()) {
            fileName = deriveDefaultSequenceName(selectedTrack);
        }
        if (!fileName.endsWith(".json")) {
            fileName += ".json";
        }
        fileName = fileName.replaceAll("[^a-zA-Z0-9._\\-]", "_");

        int bpm = 120;
        if (bpmEditBox != null) {
            try {
                int parsed = Integer.parseInt(bpmEditBox.getValue().trim());
                if (parsed >= 20 && parsed <= 300) {
                    bpm = parsed;
                }
            } catch (Exception ignored) {}
        }

        boolean isLooping = loopCheckbox != null && loopCheckbox.isChecked();

        stopPreviewAudio();

        MusicSequence newSequence = new MusicSequence(
                fileName,
                selectedTrack != null ? selectedTrack.trim() : "",
                isLooping,
                1.0f,
                1.0f,
                new ArrayList<>()
        );
        newSequence.setBpm(bpm);

        if (onSequenceCreated != null) {
            onSequenceCreated.accept(newSequence);
        }
    }

    @Override
    public void onClose() {
        stopPreviewAudio();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
    }

    private void updateRowPositions(int left, int top, int panelWidth, int panelHeight) {
        int listTop = getListTop(top);
        int listBottom = getListBottom(top, panelHeight);
        int rowH = getRowHeight();

        List<AudioTrackRow> visibleRows = getFilteredRows();
        int totalHeight = visibleRows.size() * (rowH + 3);
        int viewHeight = listBottom - listTop;
        double maxScroll = Math.max(0, totalHeight - viewHeight);
        this.scrollAmount = Math.max(0, Math.min(maxScroll, this.scrollAmount));

        for (AudioTrackRow row : trackRows) {
            row.selectBtn.visible = false;
            row.previewBtn.visible = false;
        }

        int startY = (int) (listTop - scrollAmount);
        for (int i = 0; i < visibleRows.size(); i++) {
            AudioTrackRow row = visibleRows.get(i);
            int currentY = startY + i * (rowH + 3);
            row.y = currentY;

            boolean inView = currentY + rowH >= listTop && currentY <= listBottom;

            int btnY = currentY + (rowH - 20) / 2;
            int rightX = left + panelWidth - 24;

            row.selectBtn.setX(rightX - 75);
            row.selectBtn.setY(btnY);
            row.selectBtn.visible = inView;

            row.previewBtn.setX(rightX - 132);
            row.previewBtn.setY(btnY);
            row.previewBtn.visible = inView;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        int panelWidth = Math.min(effWidth - 24, 540);
        int panelHeight = Math.min(effHeight - 20, 360);
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        int listTop = getListTop(top);
        int listBottom = getListBottom(top, panelHeight);
        int rowH = getRowHeight();

        List<AudioTrackRow> visibleRows = getFilteredRows();
        int totalHeight = visibleRows.size() * (rowH + 3);
        int viewHeight = listBottom - listTop;
        double maxScroll = Math.max(0, totalHeight - viewHeight);

        this.scrollAmount = Math.max(0, Math.min(maxScroll, this.scrollAmount - (delta * 24)));
        updateRowPositions(left, top, panelWidth, panelHeight);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        int panelWidth = Math.min(effWidth - 24, 540);
        int panelHeight = Math.min(effHeight - 20, 360);
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        double scaledMouseX = scale < 1.0 ? mouseX / scale : mouseX;
        double scaledMouseY = scale < 1.0 ? mouseY / scale : mouseY;

        // Check scrollbar drag
        int listTop = getListTop(top);
        int listBottom = getListBottom(top, panelHeight);
        int scrollbarX = left + panelWidth - 20;

        List<AudioTrackRow> visibleRows = getFilteredRows();
        int totalHeight = visibleRows.size() * (getRowHeight() + 3);
        int viewHeight = listBottom - listTop;

        if (totalHeight > viewHeight && scaledMouseX >= scrollbarX - 2 && scaledMouseX <= scrollbarX + 8 && scaledMouseY >= listTop && scaledMouseY <= listBottom) {
            this.isDraggingScrollbar = true;
            this.dragOffsetY = scaledMouseY;
            return true;
        }

        // Clicking on an audio card row selects it
        if (scaledMouseX >= left + 16 && scaledMouseX <= left + panelWidth - 160 && scaledMouseY >= listTop && scaledMouseY <= listBottom) {
            for (AudioTrackRow row : visibleRows) {
                if (scaledMouseY >= row.y && scaledMouseY <= row.y + getRowHeight()) {
                    selectTrack(row.trackId);
                    return true;
                }
            }
        }

        return super.mouseClicked(scaledMouseX, scaledMouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double scale = getLayoutScale();
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        int panelWidth = Math.min(effWidth - 24, 540);
        int panelHeight = Math.min(effHeight - 20, 360);
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        int listTop = getListTop(top);
        int listBottom = getListBottom(top, panelHeight);

        List<AudioTrackRow> visibleRows = getFilteredRows();
        int totalHeight = visibleRows.size() * (getRowHeight() + 3);
        int viewHeight = listBottom - listTop;
        double maxScroll = Math.max(0, totalHeight - viewHeight);

        if (isDraggingScrollbar && maxScroll > 0) {
            double scaledMouseY = scale < 1.0 ? mouseY / scale : mouseY;
            double deltaY = scaledMouseY - dragOffsetY;
            this.dragOffsetY = scaledMouseY;

            double scrollRatio = maxScroll / (viewHeight - 20);
            this.scrollAmount = Math.max(0, Math.min(maxScroll, this.scrollAmount + (deltaY * scrollRatio)));
            updateRowPositions(left, top, panelWidth, panelHeight);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
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

        graphics.fill(0, 0, effWidth, effHeight, 0xCC000000);

        int panelWidth = Math.min(effWidth - 24, 540);
        int panelHeight = Math.min(effHeight - 20, 360);
        int left = (effWidth - panelWidth) / 2;
        int top = (effHeight - panelHeight) / 2;

        drawBorderBox(graphics, left, top, panelWidth, panelHeight, CARD_BORDER, CYAN_BG);

        // Header Banner
        graphics.fill(left, top, left + panelWidth, top + 30, 0xEE09141D);
        graphics.fill(left, top + 29, left + panelWidth, top + 30, CYAN_MAIN);
        graphics.drawString(this.font, "♫ SELECT AUDIO TRACK FOR NEW SEQUENCE", left + 18, top + 7, CYAN_MAIN, false);
        graphics.drawString(this.font, "Choose an audio track fetched from our audio system to choreograph", left + 18, top + 18, 0xFFAABBCC, false);

        // Track count badge
        String countBadge = allTracks.size() + " audio track(s) fetched";
        graphics.drawString(this.font, countBadge, left + panelWidth - this.font.width(countBadge) - 18, top + 11, 0xFF00FF88, false);

        // Frame around track list
        int listTop = getListTop(top);
        int listBottom = getListBottom(top, panelHeight);
        int rowH = getRowHeight();

        drawBorderBox(graphics, left + 16, listTop - 1, panelWidth - 32, (listBottom - listTop) + 2, 0x5500E5FF, 0xEE060E14);

        // Scissor clip for audio cards list
        graphics.enableScissor(
                (int) ((left + 16) * scale),
                (int) (listTop * scale),
                (int) ((left + panelWidth - 16) * scale),
                (int) (listBottom * scale)
        );

        List<AudioTrackRow> visibleRows = getFilteredRows();

        if (visibleRows.isEmpty()) {
            graphics.drawCenteredString(this.font, "No audio tracks matching search.", left + panelWidth / 2, listTop + 40, 0xFFAABBCC);
        } else {
            for (AudioTrackRow row : visibleRows) {
                if (row.y + rowH < listTop || row.y > listBottom) continue;

                boolean isSelected = row.trackId.equalsIgnoreCase(selectedTrack);
                boolean isHovered = scaledMouseX >= left + 18 && scaledMouseX <= left + panelWidth - 18 && scaledMouseY >= row.y && scaledMouseY <= row.y + rowH;

                int bgColor = isSelected ? 0xEE0D2638 : (isHovered ? 0xEE0A1D2B : 0xEE07121A);
                int borderColor = isSelected ? YELLOW_SELECTED : (isHovered ? CYAN_MAIN : 0x4400E5FF);

                drawBorderBox(graphics, left + 18, row.y, panelWidth - 36, rowH, borderColor, bgColor);

                // Left accent bar
                graphics.fill(left + 19, row.y + 1, left + 23, row.y + rowH - 1, isSelected ? YELLOW_SELECTED : CYAN_MAIN);

                // Music Note icon
                graphics.drawString(this.font, "🎵", left + 28, row.y + 7, isSelected ? YELLOW_SELECTED : CYAN_MAIN, false);

                // Track Title (bold-styled)
                graphics.drawString(this.font, row.displayTitle, left + 44, row.y + 6, isSelected ? 0xFFFFFFFF : 0xFFE0E0E0, false);

                // Track ID
                graphics.drawString(this.font, row.trackId, left + 44, row.y + 18, isSelected ? 0xFFAADDEE : 0xFF7799AA, false);

                // Render Action Buttons
                row.previewBtn.render(graphics, scaledMouseX, scaledMouseY, partialTick);
                row.selectBtn.render(graphics, scaledMouseX, scaledMouseY, partialTick);
            }
        }

        graphics.disableScissor();

        // Scrollbar
        int totalHeight = visibleRows.size() * (rowH + 3);
        int viewHeight = listBottom - listTop;
        if (totalHeight > viewHeight) {
            int sbX = left + panelWidth - 22;
            int sbY = listTop;
            int sbH = viewHeight;

            graphics.fill(sbX, sbY, sbX + 4, sbY + sbH, 0x77050B10);
            graphics.fill(sbX, sbY, sbX + 1, sbY + sbH, 0x5500E5FF);

            int thumbH = Math.max(14, (int) ((float) sbH / totalHeight * sbH));
            double maxScroll = totalHeight - viewHeight;
            int thumbY = sbY + (int) ((float) scrollAmount / maxScroll * (sbH - thumbH));

            graphics.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, isDraggingScrollbar ? 0xFFFFFFFF : CYAN_MAIN);
        }

        // Bottom Configuration Labels
        int configY = listBottom + 8;
        graphics.drawString(this.font, "Sequence Name:", left + 18, configY + 6, 0xFFAABBCC, false);
        graphics.drawString(this.font, "BPM:", left + 18, configY + 32, 0xFFAABBCC, false);

        super.render(graphics, scaledMouseX, scaledMouseY, partialTick);
        graphics.pose().popPose();
    }
}
