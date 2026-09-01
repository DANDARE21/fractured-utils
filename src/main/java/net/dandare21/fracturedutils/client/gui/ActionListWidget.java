package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.orchestrator.action.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ActionListWidget extends ObjectSelectionList<ActionListWidget.ActionEntry> {
    private final OrchestratorScreen screen;
    private final int topPos;
    private final int bottomPos;

    private int draggingIndex = -1;
    private double dragMouseX = 0;
    private double dragMouseY = 0;
    private int targetDropIndex = -1;

    public ActionListWidget(OrchestratorScreen screen, Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
        super(minecraft, width, height, top, bottom, itemHeight);
        this.screen = screen;
        this.topPos = top;
        this.bottomPos = bottom;
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
    }

    public void updateEntries(List<OrchestratorAction> actions) {
        this.clearEntries();
        for (int i = 0; i < actions.size(); i++) {
            this.addEntry(new ActionEntry(screen, actions.get(i), i, actions.size()));
        }
    }

    public int getTopPos() {
        return topPos;
    }

    public int getItemHeight() {
        return itemHeight;
    }

    public int getDraggingIndex() {
        return draggingIndex;
    }

    @Override
    public int getRowWidth() {
        return this.width - 24;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getRowLeft() + this.getRowWidth() + 6;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int rowLeft = getRowLeft();
            int rowWidth = getRowWidth();
            int listTop = this.topPos;
            int listBottom = this.bottomPos;

            if (mouseX >= rowLeft && mouseX <= rowLeft + rowWidth && mouseY >= listTop && mouseY <= listBottom) {
                int relativeY = (int) (mouseY - listTop + this.getScrollAmount());
                int index = relativeY / this.itemHeight;

                if (index >= 0 && index < this.children().size()) {
                    ActionEntry entry = this.children().get(index);
                    if (entry.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                    // Start drag-and-drop
                    this.draggingIndex = index;
                    this.dragMouseX = mouseX;
                    this.dragMouseY = mouseY;
                    this.targetDropIndex = index;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingIndex != -1) {
            this.dragMouseX = mouseX;
            this.dragMouseY = mouseY;

            int listTop = this.topPos;
            int relativeY = (int) (mouseY - listTop + this.getScrollAmount());
            int row = (relativeY + this.itemHeight / 2) / this.itemHeight;
            this.targetDropIndex = Math.max(0, Math.min(this.children().size(), row));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingIndex != -1) {
            if (targetDropIndex != -1 && targetDropIndex != draggingIndex && targetDropIndex != draggingIndex + 1) {
                screen.reorderAction(draggingIndex, targetDropIndex);
            }
            this.draggingIndex = -1;
            this.targetDropIndex = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int listLeft = this.getRowLeft();
        int listWidth = this.getRowWidth();
        int listTop = this.topPos;
        int listBottom = this.bottomPos;
        int listHeight = listBottom - listTop;

        double scale = screen.getLayoutScale();

        // Enable scaled scissor region so item contents adapt to screen dimensions on all resolutions
        graphics.enableScissor(
            (int) Math.round(listLeft * scale),
            (int) Math.round(listTop * scale),
            (int) Math.round((listLeft + listWidth + 12) * scale),
            (int) Math.round(listBottom * scale)
        );

        int count = this.children().size();
        int scroll = (int) this.getScrollAmount();

        for (int i = 0; i < count; i++) {
            int itemTop = listTop + (i * this.itemHeight) - scroll;
            int itemBottom = itemTop + this.itemHeight;

            if (itemBottom >= listTop && itemTop <= listBottom) {
                ActionEntry entry = this.children().get(i);
                boolean isHovered = (mouseX >= listLeft && mouseX <= listLeft + listWidth && mouseY >= itemTop && mouseY < itemBottom && mouseY >= listTop && mouseY <= listBottom);
                entry.render(graphics, i, itemTop, listLeft, listWidth, this.itemHeight, mouseX, mouseY, isHovered, partialTick);
            }
        }

        graphics.disableScissor();

        // 1. Draw Drag-and-Drop Placement Line
        if (draggingIndex != -1 && targetDropIndex >= 0 && targetDropIndex <= this.children().size()) {
            int lineLeft = getRowLeft();
            int lineRight = lineLeft + getRowWidth();
            int lineY = (int) (listTop + targetDropIndex * this.itemHeight - scroll);

            if (lineY >= topPos - 2 && lineY <= bottomPos + 2) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 350);

                // Main horizontal cyan placement indicator line
                graphics.fill(lineLeft, lineY - 2, lineRight, lineY + 2, 0xFF00E5FF);
                graphics.fill(lineLeft, lineY - 1, lineRight, lineY + 1, 0xFFFFFFFF);

                // Side indicator tabs
                graphics.fill(lineLeft - 4, lineY - 4, lineLeft, lineY + 4, 0xFF00E5FF);
                graphics.fill(lineLeft - 2, lineY - 2, lineLeft, lineY + 2, 0xFFFFFFFF);

                graphics.fill(lineRight, lineY - 4, lineRight + 4, lineY + 4, 0xFF00E5FF);
                graphics.fill(lineRight, lineY - 2, lineRight + 2, lineY + 2, 0xFFFFFFFF);

                graphics.pose().popPose();
            }
        }

        // 2. Render Floating Preview Card while Dragging
        if (draggingIndex >= 0 && draggingIndex < children().size()) {
            ActionEntry draggedEntry = children().get(draggingIndex);
            OrchestratorAction action = draggedEntry.getAction();

            int cardW = getRowWidth();
            int cardH = this.itemHeight - 2;
            int cardX = (int) dragMouseX - cardW / 2;
            int cardY = (int) dragMouseY - cardH / 2;

            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);

            // Floating Card Fill & Glowing Cyan Border
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xEE082535);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 1, 0xFF00E5FF);
            graphics.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0xFF00E5FF);
            graphics.fill(cardX, cardY, cardX + 1, cardY + cardH, 0xFF00E5FF);
            graphics.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, 0xFF00E5FF);

            Font font = Minecraft.getInstance().font;
            String text = "≡ [" + (draggingIndex + 1) + "] " + action.getType().toUpperCase();
            graphics.drawString(font, text, cardX + 8, cardY + 4, 0xFF00E5FF, false);

            graphics.pose().popPose();
        }

        // 3. Cyberpunk Scrollbar Rendering for Action Timeline
        if (this.getMaxScroll() > 0) {
            int scrollbarX = this.getScrollbarPosition();
            int scrollbarY = this.topPos;
            int scrollbarW = 6;
            int scrollbarH = listHeight;

            // Track Background & Borders
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0x77050B10);
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + 1, scrollbarY + scrollbarH, 0xAA00E5FF);
            graphics.fill(scrollbarX + scrollbarW - 1, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0xAA00E5FF);

            int totalContentH = count * this.itemHeight;
            int thumbH = Math.max(16, (int) ((float) scrollbarH / totalContentH * scrollbarH));
            int thumbY = scrollbarY + (int) ((float) scroll / this.getMaxScroll() * (scrollbarH - thumbH));

            // Bright Cyan Thumb
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFF00E5FF);
        }
    }

    public static class ActionEntry extends ObjectSelectionList.Entry<ActionEntry> {
        private final OrchestratorScreen screen;
        private final OrchestratorAction action;
        private final int index;
        private final int totalCount;

        public ActionEntry(OrchestratorScreen screen, OrchestratorAction action, int index, int totalCount) {
            this.screen = screen;
            this.action = action;
            this.index = index;
            this.totalCount = totalCount;
        }

        public OrchestratorAction getAction() {
            return action;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            Font font = Minecraft.getInstance().font;

            boolean isBeingDragged = (screen.getActionListWidget() != null && screen.getActionListWidget().getDraggingIndex() == index);

            boolean isInvalidCommand = false;
            if (action instanceof CommandAction ca) {
                isInvalidCommand = !CommandAction.isValidCommand(Minecraft.getInstance(), ca.getRun());
            }

            net.dandare21.fracturedutils.network.packet.S2CSyncSequenceTelemetryPacket.SequenceTelemetryData telemetry = screen.getActiveTelemetry();
            boolean isExecuted = false;
            boolean isCurrentAction = false;
            if (telemetry != null) {
                int curIdx = telemetry.getCurrentIndex();
                if (index < curIdx) {
                    isExecuted = true;
                } else if (index == curIdx) {
                    isCurrentAction = true;
                }
            }

            int accentColor;
            int bg;
            int borderColor;

            if (isBeingDragged) {
                accentColor = 0x6600E5FF;
                bg = 0x4408121B;
                borderColor = 0x5500E5FF;
            } else if (isCurrentAction) {
                accentColor = 0xFF00FF55;
                bg = isHovered ? 0xEE0D4026 : 0xDD0A2E1C;
                borderColor = 0xFF00FF55;
            } else if (isExecuted) {
                accentColor = 0xFF33CC66;
                bg = isHovered ? 0xEE0A281A : 0xBB081C12;
                borderColor = 0xAA33CC66;
            } else if (isInvalidCommand) {
                accentColor = 0xFFFF3355;
                bg = isHovered ? 0xEE300A10 : 0xBB1B080C;
                borderColor = isHovered ? 0xFFFF5555 : 0xFFFF3355;
            } else {
                accentColor = getActionColor(action.getType());
                bg = isHovered ? 0xEE0A2030 : 0xBB08121B;
                borderColor = isHovered ? 0xFF00E5FF : 0xAA00E5FF;
            }

            // Render Card Fill & Border
            graphics.fill(left, top, left + width, top + height - 2, bg);
            graphics.fill(left, top, left + width, top + 1, borderColor);
            graphics.fill(left, top + height - 3, left + width, top + height - 2, borderColor);
            graphics.fill(left, top, left + 1, top + height - 2, borderColor);
            graphics.fill(left + width - 1, top, left + width, top + height - 2, borderColor);

            // Left Accent Pill Indicator
            graphics.fill(left + 2, top + 2, left + 5, top + height - 4, accentColor);

            // Drag Handle Icon ≡
            graphics.drawString(font, Component.literal("≡"), left + 8, top + (height - 8) / 2, isHovered ? 0xFF00E5FF : 0x8800E5FF, false);

            // Header Text (Action Index + Type Name)
            String typeText = "[" + (index + 1) + "] " + action.getType().toUpperCase();
            if (isCurrentAction) {
                typeText += "  ▶ CURRENT ACTION";
            } else if (isExecuted) {
                typeText += "  ✓ EXECUTED";
            } else if (isInvalidCommand) {
                typeText += " ⚠ INVALID SYNTAX";
            }

            int maxTextW = width - 74;
            if (font.width(typeText) > maxTextW) {
                typeText = font.plainSubstrByWidth(typeText, Math.max(10, maxTextW - 6)) + "..";
            }
            graphics.drawString(font, Component.literal(typeText), left + 22, top + 4, accentColor, false);

            // Detail Subtext
            String details = getDetailsText(action);
            if (!details.isEmpty()) {
                if (font.width(details) > maxTextW) {
                    details = font.plainSubstrByWidth(details, Math.max(10, maxTextW - 6)) + "..";
                }
                graphics.drawString(font, Component.literal(details), left + 22, top + 17, 0xFFAABBCC, false);
            }

            // Render Mini Buttons: Play (▶), Edit (⚙), Delete (✖)
            int btnY = top + 5;
            int btnH = 18;
            int btnW = 20;

            int playX = left + width - 68;
            int editX = left + width - 46;
            int deleteX = left + width - 24;

            renderCyberpunkMiniBtn(graphics, "▶", playX, btnY, btnW, btnH, 0xFF55FF55, mouseX, mouseY);
            renderCyberpunkMiniBtn(graphics, "⚙", editX, btnY, btnW, btnH, 0xFF00E5FF, mouseX, mouseY);
            renderCyberpunkMiniBtn(graphics, "✖", deleteX, btnY, btnW, btnH, 0xFFFF3355, mouseX, mouseY);

            // Render Tooltips for Mini Buttons / Drag Handle on Hover
            if (mouseY >= btnY && mouseY < btnY + btnH) {
                if (mouseX >= playX && mouseX < playX + btnW) {
                    graphics.renderTooltip(font, Component.literal("Start sequence from Action #" + (index + 1)), mouseX, mouseY);
                } else if (mouseX >= editX && mouseX < editX + btnW) {
                    graphics.renderTooltip(font, Component.literal("Edit action"), mouseX, mouseY);
                } else if (mouseX >= deleteX && mouseX < deleteX + btnW) {
                    graphics.renderTooltip(font, Component.literal("Delete action"), mouseX, mouseY);
                } else if (isHovered) {
                    graphics.renderTooltip(font, Component.literal("Drag to reorder"), mouseX, mouseY);
                }
            } else if (isHovered && mouseX >= left && mouseX <= left + width) {
                graphics.renderTooltip(font, Component.literal("Drag to reorder"), mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && screen.getActionListWidget() != null) {
                int listTop = screen.getActionListWidget().getTopPos();
                int rowTop = listTop + (this.index * screen.getActionListWidget().getItemHeight()) - (int) screen.getActionListWidget().getScrollAmount();
                int btnY = rowTop + 5;
                int btnH = 18;
                int btnW = 20;

                int rowLeft = screen.getActionListWidget().getRowLeft();
                int width = screen.getActionListWidget().getRowWidth();

                int playX = rowLeft + width - 68;
                int editX = rowLeft + width - 46;
                int deleteX = rowLeft + width - 24;

                if (mouseY >= btnY && mouseY < btnY + btnH) {
                    if (mouseX >= playX && mouseX < playX + btnW) {
                        screen.runSequenceFromAction(this.index);
                        return true;
                    } else if (mouseX >= editX && mouseX < editX + btnW) {
                        screen.openEditModal(this.index);
                        return true;
                    } else if (mouseX >= deleteX && mouseX < deleteX + btnW) {
                        screen.deleteAction(this.index);
                        return true;
                    }
                }
            }
            return false;
        }

        private void renderCyberpunkMiniBtn(GuiGraphics graphics, String text, int x, int y, int w, int h, int baseColor, int mouseX, int mouseY) {
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            int borderColor = hovered ? 0xFFFFFFFF : baseColor;
            int bg = hovered ? 0xEE082535 : 0xEE060C12;
            int textColor = hovered ? 0xFFFFFFFF : baseColor;

            graphics.fill(x, y, x + w, y + h, bg);
            graphics.fill(x, y, x + w, y + 1, borderColor);
            graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
            graphics.fill(x, y, x + 1, y + h, borderColor);
            graphics.fill(x + w - 1, y, x + w, y + h, borderColor);

            if (hovered) {
                graphics.fill(x + 1, y + 1, x + 3, y + 2, borderColor);
                graphics.fill(x + 1, y + 1, x + 2, y + 3, borderColor);
            }

            Font font = Minecraft.getInstance().font;
            graphics.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, textColor);
        }

        private int getActionColor(String type) {
            return switch (type) {
                case "checkpoint" -> 0xFFFF2255;
                case "new_objective" -> 0xFF00E5FF;
                case "end_objective" -> 0xFFFF5555;
                case "play_music_sequence", "music_sequence" -> 0xFFAA55FF;
                case "wait_until", "delay", "await_trigger" -> 0xFFFFAA00;
                case "fork_sequence" -> 0xFF55FF55;
                case "run_sequence" -> 0xFF00E5FF;
                case "stall_parent" -> 0xFFFF3355;
                case "resume_parent" -> 0xFF55FF55;
                default -> 0xFF00E5FF;
            };
        }

        private String getDetailsText(OrchestratorAction action) {
            if (action instanceof CommandAction ca) {
                String r = ca.getRun();
                return r.length() > 36 ? r.substring(0, 33) + "..." : r;
            } else if (action instanceof net.dandare21.fracturedutils.orchestrator.action.CheckpointAction cp) {
                return String.format(java.util.Locale.ROOT, "🚩 Checkpoint %s (%.1f, %.1f, %.1f)", cp.getTargetSelector(), cp.getX(), cp.getY(), cp.getZ());
            } else if (action instanceof WaitUntilAction wua) {
                String mode = wua.getWaitType().toLowerCase();
                if (mode.equals("delay")) {
                    return "Delay " + wua.getTicks() + " ticks (" + String.format("%.1fs", wua.getTicks() / 20.0f) + ")";
                } else if (mode.equals("operator_action")) {
                    String lbl = wua.getLabel();
                    return "Operator Action: " + (lbl.isEmpty() ? wua.getTriggerId() : lbl);
                } else if (mode.equals("proximity") || mode.equals("marker") || mode.equals("player_proximity") || mode.equals("area")) {
                    String reqText = wua.isRequireAllPlayers() ? "ALL players" : "ANY player";
                    String markerVisText = wua.isOpsOnlyVisibility() ? "Marker: Ops" : "Marker: All";
                    String areaText = wua.isShowRadiusArea() ? ("Area On (" + (wua.isAreaOpsOnlyVisibility() ? "Ops" : "All") + ")") : "Area Off";
                    return String.format(java.util.Locale.ROOT, "Marker (%.1f, %.1f, %.1f) r=%.1fm [%s, %s, %s]", wua.getX(), wua.getY(), wua.getZ(), wua.getRadius(), reqText, markerVisText, areaText);
                } else if (mode.equals("video") || mode.equals("video_end") || mode.equals("cutscene") || mode.equals("cinematic")) {
                    return "Wait for active video to end";
                } else if (mode.equals("dialog") || mode.equals("dialog_end") || mode.equals("dialogs_end") || mode.equals("dialog_sequence") || mode.equals("dialog_finish")) {
                    if (wua.getTriggerId() != null && !wua.getTriggerId().isBlank()) {
                        return "Wait for dialog \"" + wua.getTriggerId() + "\" to end";
                    }
                    return "Wait for active dialog to end";
                } else if (mode.equals("waiting_room") || mode.equals("waiting_room_end") || mode.equals("waitingroom")) {
                    return "Wait for active waiting room to end";
                } else if (mode.equals("waiting_room_ready") || mode.equals("waiting_room_all_ready") || mode.equals("waitingroom_ready")) {
                    return "Wait until all players in waiting room are ready";
                } else if (mode.equals("downloads") || mode.equals("downloads_end") || mode.equals("cutscene_downloads") || mode.equals("video_downloads")) {
                    return "Wait for cutscene downloads to complete";
                } else {
                    return "Trigger Signal: \"" + wua.getTriggerId() + "\"";
                }
            } else if (action instanceof DelayAction da) {
                return "Stall for " + da.getTicks() + " server ticks (" + String.format("%.1fs", da.getTicks() / 20.0f) + ")";
            } else if (action instanceof AwaitTriggerAction ata) {
                return "Await trigger signal: \"" + ata.getTriggerId() + "\"";
            } else if (action instanceof ForkSequenceAction fsa) {
                return "Fork non-blocking subsequence: " + fsa.getFile();
            } else if (action instanceof RunSequenceAction rsa) {
                return "Run blocking subsequence: " + rsa.getFile();
            } else if (action instanceof StallParentAction) {
                return "Pause parent sequence node";
            } else if (action instanceof ResumeParentAction) {
                return "Wake up parent sequence node";
            } else if (action instanceof NewObjectiveAction noa) {
                String waitTag = noa.isShowActiveWait() ? " [Wait On]" : "";
                return "📋 New Objective: \"" + noa.getName() + "\"" + waitTag;
            } else if (action instanceof EndObjectiveAction) {
                return "🏁 End Active Objective";
            } else if (action instanceof PlayMusicSequenceAction pmsa) {
                String modeTag = pmsa.isAwaitCompletion() ? " [Wait Finish]" : " [Async]";
                return "🎵 Play Music Sequence: \"" + pmsa.getSequenceFile() + "\"" + modeTag;
            }
            return "";
        }

        @Override
        public Component getNarration() {
            return Component.literal("Action " + (index + 1));
        }
    }
}
