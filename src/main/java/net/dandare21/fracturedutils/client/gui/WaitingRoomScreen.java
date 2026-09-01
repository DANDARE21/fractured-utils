package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.client.ClientWaitingRoomData;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.ToggleReadyC2SPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class WaitingRoomScreen extends Screen {

    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xFF05090C;
    private static final int CARD_BORDER = 0xAA00E5FF;
    private static final int RED_NOT_READY = 0xFFFF3355;
    private static final int GRAY_CONNECTING = 0xFF556677;

    private double scrollAmount = 0;
    private boolean isDraggingScrollbar = false;
    private double dragClickOffsetY = 0;

    private EditBox chatInput;
    private double chatScrollAmount = 0;
    private boolean isDraggingChatScrollbar = false;
    private double dragClickChatOffsetY = 0;
    private int lastChatLineCount = 0;
    private long lastTickedSecond = -1;

    public WaitingRoomScreen() {
        super(Component.translatable("gui.fracturedutils.waiting_room.title"));
    }

    private boolean isOp() {
        return this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(2);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return isOp();
    }

    @Override
    public void onClose() {
        if (isOp() || !ClientWaitingRoomData.isActive()) {
            super.onClose();
        }
    }

    private double getLayoutScale() {
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

        int leftPanelLeft = 12;
        int leftPanelWidth = (int) (effWidth * 0.71) - 12;
        int rightPanelLeft = leftPanelLeft + leftPanelWidth + 12;
        int rightPanelWidth = effWidth - rightPanelLeft - 12;

        int mainTop = 42;
        int mainHeight = effHeight - mainTop - 12;
        int bottomY = mainTop + mainHeight - 60;

        int buttonWidth = 130;
        int buttonHeight = 34;
        int buttonX = leftPanelLeft + leftPanelWidth - 15 - buttonWidth;
        int buttonY = bottomY + 13;

        // Custom Theme Ready / Cancel Button
        this.addRenderableWidget(new CyberpunkReadyButton(
                buttonX, buttonY, buttonWidth, buttonHeight,
                button -> ModMessages.sendToServer(new ToggleReadyC2SPacket())
        ));

        // Upper-Right Admin "X" Close Icon Button
        if (isOp()) {
            int closeW = 20;
            int closeH = 20;
            int closeX = effWidth - closeW - 8;
            int closeY = 8;

            this.addRenderableWidget(new CyberpunkCloseButton(
                    closeX, closeY, closeW, closeH,
                    button -> super.onClose()
            ));
        }

        // Chat Input Field & Send Button
        int inputY = mainTop + mainHeight - 34;
        int chatBoxWidth = rightPanelWidth - 20;
        int editBoxX = rightPanelLeft + 18;
        int editBoxY = inputY + 5;
        int editBoxWidth = chatBoxWidth - 32;
        int editBoxHeight = 16;

        this.chatInput = new EditBox(this.font, editBoxX, editBoxY, editBoxWidth, editBoxHeight, Component.literal("Chat Input"));
        this.chatInput.setMaxLength(256);
        this.chatInput.setBordered(false);
        this.chatInput.setTextColor(0xFFFFFFFF);
        this.chatInput.setHint(Component.translatable("gui.fracturedutils.waiting_room.chat_hint").withStyle(ChatFormatting.GRAY));
        this.addRenderableWidget(this.chatInput);

        // '➤' Send Button
        this.addRenderableWidget(new CyberpunkSendButton(
                rightPanelLeft + 10 + chatBoxWidth - 24, inputY + 2, 20, 20,
                button -> sendChatMessage()
        ));
    }

    private void sendChatMessage() {
        if (this.chatInput == null) return;
        String text = this.chatInput.getValue().trim();
        if (!text.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
            if (text.startsWith("/")) {
                this.minecraft.player.connection.sendUnsignedCommand(text.substring(1));
            } else {
                this.minecraft.player.connection.sendChat(text);
            }
        }
        this.chatInput.setValue("");
        this.chatInput.setFocused(false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // ENTER or NUMPAD ENTER
            if (this.chatInput != null && this.chatInput.isFocused()) {
                sendChatMessage();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!ClientWaitingRoomData.isActive()) {
            this.onClose();
            return;
        }

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

        // Deep Black Background
        guiGraphics.fill(0, 0, effWidth, effHeight, CYAN_BG);

        // Cyberpunk Wireframe Grid Overlay with Diagonal Scroll
        drawGridOverlay(guiGraphics, effWidth, effHeight);

        // Top Header Bar
        drawTopHeader(guiGraphics, effWidth);

        // Panel Layout Dimensions
        int leftPanelLeft = 12;
        int leftPanelWidth = (int) (effWidth * 0.71) - 12;
        int rightPanelLeft = leftPanelLeft + leftPanelWidth + 12;
        int rightPanelWidth = effWidth - rightPanelLeft - 12;

        int mainTop = 42;
        int mainHeight = effHeight - mainTop - 12;

        // Render Panels
        drawLeftPanel(guiGraphics, leftPanelLeft, mainTop, leftPanelWidth, mainHeight, scale);
        drawRightPanel(guiGraphics, rightPanelLeft, mainTop, rightPanelWidth, mainHeight, scale);

        super.render(guiGraphics, scaledMouseX, scaledMouseY, partialTick);
        guiGraphics.pose().popPose();
    }

    private void drawGridOverlay(GuiGraphics guiGraphics, int effW, int effH) {
        int gridSize = 32;
        int gridColor = 0x1200E5FF;

        long time = System.currentTimeMillis();
        int offsetX = (int) ((time / 40) % gridSize);
        int offsetY = (int) ((time / 40) % gridSize);

        for (int x = -gridSize + offsetX; x < effW + gridSize; x += gridSize) {
            guiGraphics.fill(x, 0, x + 1, effH, gridColor);
        }
        for (int y = -gridSize + offsetY; y < effH + gridSize; y += gridSize) {
            guiGraphics.fill(0, y, effW, y + 1, gridColor);
        }
    }

    private void drawTopHeader(GuiGraphics guiGraphics, int effW) {
        // Top Header Container
        guiGraphics.fill(0, 0, effW, 36, 0xEE060C12);
        guiGraphics.fill(0, 35, effW, 36, CYAN_MAIN);

        // Event Title (Top Left)
        String eventTitle = ClientWaitingRoomData.getRoomTitle().toUpperCase();
        guiGraphics.fill(12, 10, 24, 26, CYAN_MAIN);
        guiGraphics.drawString(this.font, Component.literal(eventTitle).withStyle(ChatFormatting.BOLD), 30, 14, CYAN_MAIN, false);

        // Timer Box & Ticking Sound
        boolean isCountdown = ClientWaitingRoomData.isCountingDown();
        long remaining = isCountdown ? ClientWaitingRoomData.getCountdownRemainingSeconds() : 0;

        if (isCountdown && remaining != this.lastTickedSecond) {
            this.lastTickedSecond = remaining;
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.6f, 0.5f));
            }
        }

        String timeStr;
        int timerColor;
        if (isCountdown) {
            timeStr = String.format("%02d:%02d", remaining / 60, remaining % 60);
            timerColor = RED_NOT_READY;
        } else {
            long elapsedSeconds = ClientWaitingRoomData.getElapsedSeconds();
            timeStr = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
            timerColor = CYAN_MAIN;
        }

        int timerBoxWidth = 90;
        int timerBoxX = (effW - timerBoxWidth) / 2;
        drawBorderBox(guiGraphics, timerBoxX, 6, timerBoxWidth, 24, timerColor, 0xFF050B10);
        guiGraphics.drawCenteredString(this.font, Component.literal(timeStr).withStyle(ChatFormatting.BOLD), timerBoxX + (timerBoxWidth / 2), 14, timerColor);
    }

    private void drawLeftPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, double scale) {
        // More transparent panel background so diagonal grid is clearly visible
        drawBorderBox(guiGraphics, x, y, width, height, CYAN_MAIN, 0x7708121B);

        // Fetch Online Connected Players from Tab List
        List<PlayerInfo> connectedPlayers = new ArrayList<>();
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            Collection<PlayerInfo> online = this.minecraft.getConnection().getOnlinePlayers();
            if (online != null) {
                connectedPlayers.addAll(online);
            }
        }

        int totalConnected = connectedPlayers.size();
        List<UUID> joinedUUIDs = ClientWaitingRoomData.getPlayerUUIDs();
        List<Boolean> readyStates = ClientWaitingRoomData.getPlayerReadyStates();
        List<Integer> finishedList = ClientWaitingRoomData.getPlayerFinishedDownloads();
        List<Integer> remainingList = ClientWaitingRoomData.getPlayerRemainingDownloads();
        int joinedCount = joinedUUIDs.size();

        // Header: PLAYERS JOINED ([Joined]/[Total Connected])
        guiGraphics.drawString(this.font,
                Component.translatable("gui.fracturedutils.waiting_room.players_joined", joinedCount, totalConnected).withStyle(ChatFormatting.BOLD),
                x + 15, y + 12, CYAN_MAIN, false);

        // WAITING / STARTING Badge
        boolean isCountdown = ClientWaitingRoomData.isCountingDown();
        Component badgeText = isCountdown
                ? Component.translatable("gui.fracturedutils.waiting_room.starting")
                : Component.translatable("gui.fracturedutils.waiting_room.waiting");
        int badgeColor = isCountdown ? RED_NOT_READY : CYAN_MAIN;
        int badgeWidth = this.font.width(badgeText.copy().withStyle(ChatFormatting.BOLD)) + 12;

        int badgeX = x + width - badgeWidth - 15;
        guiGraphics.fill(badgeX, y + 10, badgeX + badgeWidth, y + 24, badgeColor);
        guiGraphics.drawCenteredString(this.font, badgeText.copy().withStyle(ChatFormatting.BOLD), badgeX + (badgeWidth / 2), y + 13, 0xFFFFFFFF);

        // Separator line
        guiGraphics.fill(x + 12, y + 30, x + width - 12, y + 31, 0x4400E5FF);

        // Player Cards Area Grid
        int gridTop = y + 38;
        int gridBottom = y + height - 70;
        int gridHeight = gridBottom - gridTop;
        int scrollbarSpace = 16;
        int gridWidth = width - 24 - scrollbarSpace;

        int cols = Math.min(4, Math.max(1, gridWidth / 140));
        int cardGapX = 8;
        int cardGapY = 8;

        int cardW = (gridWidth - (cardGapX * (cols - 1))) / cols;
        int cardH = 34; // Fixed clean height per card

        int totalRows = (int) Math.ceil((double) totalConnected / cols);
        int contentHeight = totalRows > 0 ? (totalRows * cardH + Math.max(0, totalRows - 1) * cardGapY) : 0;
        double maxScroll = Math.max(0, contentHeight - gridHeight);

        this.scrollAmount = Math.max(0, Math.min(this.scrollAmount, maxScroll));

        if (connectedPlayers.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("Connecting to server...").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                    x + (width / 2), gridTop + 20, 0xFF888888);
        } else {
            // Enable Scissor for clipping scrolled elements
            guiGraphics.enableScissor((int) ((x + 12) * scale), (int) (gridTop * scale), (int) ((x + 12 + gridWidth) * scale), (int) (gridBottom * scale));

            for (int i = 0; i < connectedPlayers.size(); i++) {
                PlayerInfo playerInfo = connectedPlayers.get(i);
                int col = i % cols;
                int row = i / cols;

                int cardX = x + 12 + (col * (cardW + cardGapX));
                int cardY = gridTop + (row * (cardH + cardGapY)) - (int) this.scrollAmount;

                // Render card if it's visible within vertical bounds
                if (cardY + cardH >= gridTop && cardY <= gridBottom) {
                    UUID uuid = playerInfo.getProfile().getId();
                    String name = playerInfo.getProfile().getName();
                    ResourceLocation skinLoc = playerInfo.getSkinLocation();

                    int joinedIdx = joinedUUIDs.indexOf(uuid);
                    boolean hasJoined = joinedIdx >= 0;
                    boolean isReady = hasJoined && joinedIdx < readyStates.size() && readyStates.get(joinedIdx);
                    int finished = (joinedIdx >= 0 && joinedIdx < finishedList.size()) ? finishedList.get(joinedIdx) : 0;
                    int remaining = (joinedIdx >= 0 && joinedIdx < remainingList.size()) ? remainingList.get(joinedIdx) : 0;

                    drawConnectedPlayerCard(guiGraphics, cardX, cardY, cardW, cardH, name, skinLoc, hasJoined, isReady, finished, remaining);
                }
            }

            guiGraphics.disableScissor();

            // Render Scrollbar if content overflows
            if (maxScroll > 0) {
                int scrollbarX = x + width - 20;
                int scrollbarY = gridTop;
                int scrollbarW = 6;
                int scrollbarH = gridHeight;

                // Scrollbar background track
                guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0x55050B10);
                guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 1, scrollbarY + scrollbarH, 0x4400E5FF);

                int thumbH = Math.max(16, (int) ((float) gridHeight / contentHeight * gridHeight));
                int thumbY = scrollbarY + (int) ((float) this.scrollAmount / maxScroll * (gridHeight - thumbH));

                int thumbColor = this.isDraggingScrollbar ? 0xFF00FFFF : CYAN_MAIN;
                guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, thumbColor);
            }
        }

        // Bottom Banner & Notice
        int bottomY = y + height - 60;
        guiGraphics.fill(x + 12, bottomY, x + width - 12, bottomY + 1, 0x4400E5FF);

        int buttonW = 130;
        int maxNoticeWidth = width - 27 - buttonW - 12;

        String noticeText = "EVENT STARTS AUTOMATICALLY WHEN PLAYERS JOIN OR TIMER HITS 0. ENSURE YOUR MODS ARE UPDATED.";
        List<FormattedCharSequence> splitLines = this.font.split(Component.literal(noticeText), Math.max(80, maxNoticeWidth));

        int textY = bottomY + 12;
        for (FormattedCharSequence line : splitLines) {
            guiGraphics.drawString(this.font, line, x + 15, textY, 0xFF00A0B0, false);
            textY += 12;
            if (textY + 10 > y + height) break;
        }
    }

    private int getConnectedPlayerCount() {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            Collection<PlayerInfo> online = this.minecraft.getConnection().getOnlinePlayers();
            if (online != null) {
                return online.size();
            }
        }
        return 0;
    }

    private double getMaxScroll() {
        int leftPanelLeft = 12;
        int leftPanelWidth = (int) (this.width * 0.71) - 12;
        int mainTop = 42;
        int mainHeight = this.height - mainTop - 12;

        int gridTop = mainTop + 38;
        int gridBottom = mainTop + mainHeight - 70;
        int gridHeight = gridBottom - gridTop;
        int gridWidth = leftPanelWidth - 24 - 16;

        int cols = Math.min(4, Math.max(1, gridWidth / 140));
        int cardH = 34;
        int cardGapY = 8;

        int totalConnected = getConnectedPlayerCount();
        int totalRows = (int) Math.ceil((double) totalConnected / cols);
        int contentHeight = totalRows > 0 ? (totalRows * cardH + Math.max(0, totalRows - 1) * cardGapY) : 0;

        return Math.max(0, contentHeight - gridHeight);
    }

    private double getMaxChatScroll() {
        int leftPanelLeft = 12;
        int leftPanelWidth = (int) (this.width * 0.71) - 12;
        int rightPanelLeft = leftPanelLeft + leftPanelWidth + 12;
        int rightPanelWidth = this.width - rightPanelLeft - 12;

        int mainTop = 42;
        int mainHeight = this.height - mainTop - 12;
        int chatBoxTop = mainTop + 36;
        int chatBoxBottom = mainTop + mainHeight - 42;
        int chatBoxWidth = rightPanelWidth - 20;
        int chatBoxHeight = chatBoxBottom - chatBoxTop;

        List<Component> rawMessages = ClientWaitingRoomData.getChatMessages();
        List<FormattedCharSequence> allWrappedLines = new ArrayList<>();
        int maxTextW = chatBoxWidth - 20;

        for (Component msg : rawMessages) {
            List<FormattedCharSequence> split = this.font.split(msg, Math.max(50, maxTextW));
            allWrappedLines.addAll(split);
        }

        int lineH = 10;
        int totalContentH = allWrappedLines.size() * lineH;
        return Math.max(0, totalContentH - (chatBoxHeight - 12));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        double scale = getLayoutScale();
        if (scale < 1.0) {
            mouseX /= scale;
            mouseY /= scale;
        }
        int effWidth = (int) (this.width / scale);
        int effHeight = (int) (this.height / scale);

        int leftPanelLeft = 12;
        int leftPanelWidth = (int) (effWidth * 0.71) - 12;
        int rightPanelLeft = leftPanelLeft + leftPanelWidth + 12;
        int rightPanelWidth = effWidth - rightPanelLeft - 12;

        int mainTop = 42;
        int mainHeight = effHeight - mainTop - 12;
        int gridTop = mainTop + 38;
        int gridBottom = mainTop + mainHeight - 70;

        int chatBoxTop = mainTop + 36;
        int chatBoxBottom = mainTop + mainHeight - 42;
        int chatBoxWidth = rightPanelWidth - 20;

        if (mouseX >= leftPanelLeft && mouseX <= leftPanelLeft + leftPanelWidth && mouseY >= gridTop && mouseY <= gridBottom) {
            double maxScroll = getMaxScroll();
            if (maxScroll > 0) {
                this.scrollAmount = Math.max(0, Math.min(this.scrollAmount - amount * 16, maxScroll));
                return true;
            }
        } else if (mouseX >= rightPanelLeft + 10 && mouseX <= rightPanelLeft + 10 + chatBoxWidth && mouseY >= chatBoxTop && mouseY <= chatBoxBottom) {
            double maxChatScroll = getMaxChatScroll();
            if (maxChatScroll > 0) {
                this.chatScrollAmount = Math.max(0, Math.min(this.chatScrollAmount - amount * 16, maxChatScroll));
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
            int effWidth = (int) (this.width / scale);
            int effHeight = (int) (this.height / scale);

            int leftPanelLeft = 12;
            int leftPanelWidth = (int) (effWidth * 0.71) - 12;
            int mainTop = 42;
            int mainHeight = effHeight - mainTop - 12;
            int gridTop = mainTop + 38;
            int gridBottom = mainTop + mainHeight - 70;
            int gridHeight = gridBottom - gridTop;

            int scrollbarX = leftPanelLeft + leftPanelWidth - 20;

            double maxScroll = getMaxScroll();
            if (maxScroll > 0 && mouseX >= scrollbarX - 4 && mouseX <= scrollbarX + 10 && mouseY >= gridTop && mouseY <= gridBottom) {
                this.isDraggingScrollbar = true;

                int gridWidth = leftPanelWidth - 24 - 16;
                int cols = Math.min(4, Math.max(1, gridWidth / 140));
                int cardH = 34;
                int cardGapY = 8;
                int totalConnected = getConnectedPlayerCount();
                int totalRows = (int) Math.ceil((double) totalConnected / cols);
                int contentHeight = totalRows > 0 ? (totalRows * cardH + Math.max(0, totalRows - 1) * cardGapY) : 0;

                int thumbH = Math.max(16, (int) ((float) gridHeight / contentHeight * gridHeight));
                int thumbY = gridTop + (int) ((float) this.scrollAmount / maxScroll * (gridHeight - thumbH));

                if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                    this.dragClickOffsetY = mouseY - thumbY;
                } else {
                    double targetRatio = (mouseY - gridTop - (thumbH / 2.0)) / (gridHeight - thumbH);
                    this.scrollAmount = Math.max(0, Math.min(targetRatio * maxScroll, maxScroll));
                    this.dragClickOffsetY = thumbH / 2.0;
                }
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
        if (this.isDraggingScrollbar && button == 0) {
            int effWidth = (int) (this.width / scale);
            int effHeight = (int) (this.height / scale);

            int leftPanelLeft = 12;
            int leftPanelWidth = (int) (effWidth * 0.71) - 12;
            int mainTop = 42;
            int mainHeight = effHeight - mainTop - 12;
            int gridTop = mainTop + 38;
            int gridBottom = mainTop + mainHeight - 70;
            int gridHeight = gridBottom - gridTop;

            double maxScroll = getMaxScroll();
            if (maxScroll > 0) {
                int gridWidth = leftPanelWidth - 24 - 16;
                int cols = Math.min(4, Math.max(1, gridWidth / 140));
                int cardH = 34;
                int cardGapY = 8;
                int totalConnected = getConnectedPlayerCount();
                int totalRows = (int) Math.ceil((double) totalConnected / cols);
                int contentHeight = totalRows > 0 ? (totalRows * cardH + Math.max(0, totalRows - 1) * cardGapY) : 0;

                int thumbH = Math.max(16, (int) ((float) gridHeight / contentHeight * gridHeight));
                double newThumbY = mouseY - this.dragClickOffsetY - gridTop;
                double targetRatio = newThumbY / (gridHeight - thumbH);
                this.scrollAmount = Math.max(0, Math.min(targetRatio * maxScroll, maxScroll));
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDraggingScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void drawConnectedPlayerCard(GuiGraphics guiGraphics, int x, int y, int w, int h, String name, ResourceLocation skinLoc, boolean hasJoined, boolean isReady, int finishedDownloads, int remainingDownloads) {
        int borderColor;
        int fillColor;
        int nameColor;
        String statusText;
        int statusColor;

        if (!hasJoined) {
            // Dim style (Connected but not joined waiting room)
            borderColor = 0x44556677;
            fillColor = 0x88060C12;
            nameColor = 0xFF8899A6;
            statusText = "■ CONNECTING";
            statusColor = GRAY_CONNECTING;
        } else if (isReady) {
            // Brighter style - READY
            borderColor = CYAN_MAIN;
            fillColor = 0x990B1924;
            nameColor = 0xFFFFFFFF;
            statusText = "■ READY";
            statusColor = CYAN_MAIN;
        } else {
            // Brighter style - NOT READY
            borderColor = CARD_BORDER;
            fillColor = 0x99160C12;
            nameColor = 0xFFFFFFFF;
            statusText = "■ NOT READY";
            statusColor = RED_NOT_READY;
        }

        drawBorderBox(guiGraphics, x, y, w, h, borderColor, fillColor);

        // Player Head Avatar from Tab List Skin Location
        int avatarSize = 22;
        int avatarX = x + 6;
        int avatarY = y + (h - avatarSize) / 2;

        drawBorderBox(guiGraphics, avatarX - 1, avatarY - 1, avatarSize + 2, avatarSize + 2, borderColor, 0xFF000000);

        if (skinLoc != null) {
            PlayerFaceRenderer.draw(guiGraphics, skinLoc, avatarX, avatarY, avatarSize);
        } else {
            guiGraphics.drawCenteredString(this.font, "👤", avatarX + (avatarSize / 2), avatarY + (avatarSize / 2) - 4, borderColor);
        }

        // Render Download Counter Badge on Card Right Side ([Finished Downloads]/[Total Downloads])
        int counterW = 0;
        if (finishedDownloads > 0 || remainingDownloads > 0) {
            int totalDownloads = finishedDownloads + remainingDownloads;
            String counterText = "[" + finishedDownloads + "/" + totalDownloads + "]";
            int counterColor = (remainingDownloads > 0) ? 0xFFFFD700 : 0xFF55FF55; // Gold if downloading, Green if completed
            counterW = this.font.width(counterText) + 4;
            int counterX = x + w - counterW - 2;
            int counterY = y + (h / 2) - 4;
            guiGraphics.drawString(this.font, Component.literal(counterText).withStyle(ChatFormatting.BOLD), counterX, counterY, counterColor, false);
        }

        // Name & Status Text Placement
        int textX = avatarX + avatarSize + 8;
        int maxTextW = w - (textX - x) - counterW - 6;

        int nameY = y + (h / 2) - 9;
        int statusY = y + (h / 2) + 2;

        // Truncate name if necessary to fit inside panel
        String displayName = name;
        if (this.font.width(displayName) > maxTextW) {
            displayName = this.font.plainSubstrByWidth(displayName, Math.max(10, maxTextW - 8)) + "..";
        }

        guiGraphics.drawString(this.font, Component.literal(displayName).withStyle(ChatFormatting.BOLD), textX, nameY, nameColor, false);

        // Truncate status text if necessary to fit inside panel
        String displayStatus = statusText;
        if (this.font.width(displayStatus) > maxTextW) {
            displayStatus = this.font.plainSubstrByWidth(displayStatus, Math.max(10, maxTextW - 6));
        }

        guiGraphics.drawString(this.font, displayStatus, textX, statusY, statusColor, false);
    }

    private void drawRightPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, double scale) {
        // Semi-transparent panel background so diagonal grid shows through
        drawBorderBox(guiGraphics, x, y, width, height, CYAN_MAIN, 0x7708121B);

        // Header: CHAT
        guiGraphics.drawString(this.font,
                Component.translatable("gui.fracturedutils.waiting_room.chat_title").withStyle(ChatFormatting.BOLD),
                x + 12, y + 12, CYAN_MAIN, false);

        guiGraphics.fill(x + 10, y + 28, x + width - 10, y + 29, 0x4400E5FF);

        // Chat Box Area
        int chatBoxTop = y + 36;
        int chatBoxBottom = y + height - 42;
        int chatBoxWidth = width - 20;
        int chatBoxHeight = chatBoxBottom - chatBoxTop;

        // Semi-transparent chat container fill
        drawBorderBox(guiGraphics, x + 10, chatBoxTop, chatBoxWidth, chatBoxHeight, 0x4400E5FF, 0xAA050B10);

        List<Component> rawMessages = ClientWaitingRoomData.getChatMessages();
        List<FormattedCharSequence> allWrappedLines = new ArrayList<>();
        int maxTextW = chatBoxWidth - 20;

        for (Component msg : rawMessages) {
            List<FormattedCharSequence> split = this.font.split(msg, Math.max(50, maxTextW));
            allWrappedLines.addAll(split);
        }

        int lineH = 10;
        int totalContentH = allWrappedLines.size() * lineH;
        double maxChatScroll = Math.max(0, totalContentH - (chatBoxHeight - 12));

        boolean wasAtBottom = (this.chatScrollAmount >= (maxChatScroll - 16)) || (this.chatScrollAmount == 0 && maxChatScroll > 0 && this.lastChatLineCount == 0);
        if (allWrappedLines.size() > this.lastChatLineCount) {
            if (wasAtBottom) {
                this.chatScrollAmount = maxChatScroll;
            }
            this.lastChatLineCount = allWrappedLines.size();
        }

        this.chatScrollAmount = Math.max(0, Math.min(this.chatScrollAmount, maxChatScroll));

        if (allWrappedLines.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("No chat messages yet...").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                    x + 10 + (chatBoxWidth / 2), chatBoxTop + 15, 0xFF778899);
        } else {
            guiGraphics.enableScissor((int) ((x + 12) * scale), (int) ((chatBoxTop + 4) * scale), (int) ((x + 10 + chatBoxWidth - 14) * scale), (int) ((chatBoxBottom - 4) * scale));

            int startY = chatBoxTop + 6;
            for (int i = 0; i < allWrappedLines.size(); i++) {
                int lineY = startY + (i * lineH) - (int) this.chatScrollAmount;
                if (lineY + lineH >= chatBoxTop && lineY <= chatBoxBottom) {
                    guiGraphics.drawString(this.font, allWrappedLines.get(i), x + 18, lineY, 0xFFFFFFFF, false);
                }
            }

            guiGraphics.disableScissor();

            // Scrollbar for Chat if overflowed
            if (maxChatScroll > 0) {
                int scrollbarX = x + 10 + chatBoxWidth - 10;
                int scrollbarY = chatBoxTop + 4;
                int scrollbarW = 5;
                int scrollbarH = chatBoxHeight - 8;

                guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0x55050B10);

                int thumbH = Math.max(12, (int) ((float) scrollbarH / totalContentH * scrollbarH));
                int thumbY = scrollbarY + (int) ((float) this.chatScrollAmount / maxChatScroll * (scrollbarH - thumbH));

                int thumbColor = this.isDraggingChatScrollbar ? 0xFF00FFFF : CYAN_MAIN;
                guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, thumbColor);
            }
        }

        // Bottom Chat Input Box container
        int inputY = y + height - 34;
        int inputHeight = 24;
        drawBorderBox(guiGraphics, x + 10, inputY, chatBoxWidth, inputHeight, 0x6600E5FF, 0xAA050B10);
    }

    private void drawBorderBox(GuiGraphics guiGraphics, int x, int y, int w, int h, int borderColor, int fillColor) {
        guiGraphics.fill(x, y, x + w, y + h, fillColor);
        guiGraphics.fill(x, y, x + w, y + 1, borderColor);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        guiGraphics.fill(x, y, x + 1, y + h, borderColor);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor);
    }

    public static class CyberpunkReadyButton extends Button {
        public CyberpunkReadyButton(int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean isCountdown = ClientWaitingRoomData.isCountingDown();
            this.active = !isCountdown;

            Minecraft mc = Minecraft.getInstance();
            UUID selfUUID = mc.player != null ? mc.player.getUUID() : null;
            boolean isReady = ClientWaitingRoomData.isSelfReady(selfUUID);
            boolean isHovered = this.active && this.isHoveredOrFocused();

            Component buttonText;
            int borderColor;
            int fillColor;
            int textColor;

            if (!this.active) {
                // Disabled / Locked state during countdown
                buttonText = isReady
                        ? Component.translatable("gui.fracturedutils.waiting_room.cancel")
                        : Component.translatable("gui.fracturedutils.waiting_room.ready_up");
                borderColor = 0xAA445566;
                fillColor = 0xEE0A0F14;
                textColor = 0xFF556677;
            } else if (isReady) {
                // CANCEL state
                buttonText = Component.translatable("gui.fracturedutils.waiting_room.cancel");
                borderColor = isHovered ? 0xFFFF5577 : RED_NOT_READY;
                fillColor = isHovered ? 0xEE330C15 : 0xEE1E080F;
                textColor = isHovered ? 0xFFFFFFFF : 0xFFFFCCDD;
            } else {
                // READY UP! state
                buttonText = Component.translatable("gui.fracturedutils.waiting_room.ready_up");
                borderColor = isHovered ? 0xFF55FFFF : CYAN_MAIN;
                fillColor = isHovered ? 0xEE082535 : 0xEE081622;
                textColor = isHovered ? 0xFFFFFFFF : CYAN_MAIN;
            }

            // Fill background
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, fillColor);

            // Cyberpunk border lines
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, borderColor);
            guiGraphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, borderColor);
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, borderColor);
            guiGraphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, borderColor);

            // Hover corner notch accents
            if (isHovered) {
                guiGraphics.fill(this.getX() + 2, this.getY() + 2, this.getX() + 6, this.getY() + 3, borderColor);
                guiGraphics.fill(this.getX() + 2, this.getY() + 2, this.getX() + 3, this.getY() + 6, borderColor);

                guiGraphics.fill(this.getX() + this.width - 6, this.getY() + this.height - 3, this.getX() + this.width - 2, this.getY() + this.height - 2, borderColor);
                guiGraphics.fill(this.getX() + this.width - 3, this.getY() + this.height - 6, this.getX() + this.width - 2, this.getY() + this.height - 2, borderColor);
            }

            // Render Text
            guiGraphics.drawCenteredString(mc.font, buttonText.copy().withStyle(ChatFormatting.BOLD), this.getX() + (this.width / 2), this.getY() + (this.height - 8) / 2, textColor);
        }
    }

    public static class CyberpunkCloseButton extends Button {
        public CyberpunkCloseButton(int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height, Component.literal("✕"), onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean isHovered = this.isHoveredOrFocused();

            int borderColor = isHovered ? 0xFFFF3355 : 0xAA00E5FF;
            int fillColor = isHovered ? 0xEE330C15 : 0xEE060C12;
            int textColor = isHovered ? 0xFFFF5577 : CYAN_MAIN;

            // Fill background & border
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, fillColor);
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, borderColor);
            guiGraphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, borderColor);
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, borderColor);
            guiGraphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, borderColor);

            // Render "✕" icon centered
            Font font = Minecraft.getInstance().font;
            guiGraphics.drawCenteredString(font, Component.literal("✕").withStyle(ChatFormatting.BOLD), this.getX() + (this.width / 2), this.getY() + (this.height - 8) / 2, textColor);
        }
    }

    public static class CyberpunkSendButton extends Button {
        public CyberpunkSendButton(int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height, Component.literal("➤"), onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean isHovered = this.isHoveredOrFocused();
            int color = isHovered ? 0xFFFFFFFF : CYAN_MAIN;
            Font font = Minecraft.getInstance().font;
            guiGraphics.drawCenteredString(font, Component.literal("➤"), this.getX() + (this.width / 2), this.getY() + (this.height - 8) / 2, color);
        }
    }
}
