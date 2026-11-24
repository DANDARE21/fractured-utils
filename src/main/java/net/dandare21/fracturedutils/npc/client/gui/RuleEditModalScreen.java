package net.dandare21.fracturedutils.npc.client.gui;

import net.dandare21.fracturedutils.client.gui.CyberpunkButton;
import net.dandare21.fracturedutils.dialog.DialogManager;
import net.dandare21.fracturedutils.npc.pipeline.NpcAction;
import net.dandare21.fracturedutils.npc.pipeline.NpcCondition;
import net.dandare21.fracturedutils.npc.pipeline.NpcRule;
import net.dandare21.fracturedutils.npc.pipeline.NpcTriggerType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class RuleEditModalScreen extends Screen {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int CYAN_BG = 0xF705090C;
    private static final int CARD_BORDER = 0xAA00E5FF;
    private static final int GOLD = 0xFFFFD700;

    private final Screen parent;
    private final NpcRule originalRule;
    private final Consumer<NpcRule> onSave;

    private NpcTriggerType trigger;
    private EditBox eventFilterBox;
    private String actionType = "run_command";
    private EditBox actionParamBox;
    private String conditionType = "none";
    private EditBox conditionParamBox;

    public RuleEditModalScreen(Screen parent, NpcRule originalRule, Consumer<NpcRule> onSave) {
        super(Component.literal("Rule Editor"));
        this.parent = parent;
        this.originalRule = originalRule;
        this.onSave = onSave;

        if (originalRule != null) {
            this.trigger = originalRule.trigger();
        } else {
            this.trigger = NpcTriggerType.ON_RIGHT_CLICK;
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int boxW = 380;
        int boxH = 250;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;

        int rowY = boxY + 36;

        // WHEN Trigger Toggle Button
        this.addRenderableWidget(new CyberpunkButton(boxX + 15, rowY, 180, 20,
                Component.literal("WHEN: " + trigger.name()), b -> {
            trigger = switch (trigger) {
                case ON_RIGHT_CLICK -> NpcTriggerType.ON_DIALOG_END;
                case ON_DIALOG_END -> NpcTriggerType.ON_MID_DIALOG_EVENT;
                case ON_MID_DIALOG_EVENT -> NpcTriggerType.ON_PROXIMITY;
                case ON_PROXIMITY -> NpcTriggerType.ON_DAMAGED;
                case ON_DAMAGED -> NpcTriggerType.ON_RIGHT_CLICK;
            };
            b.setMessage(Component.literal("WHEN: " + trigger.name()));
        }, CYAN_MAIN, false));

        // Optional Event Filter
        eventFilterBox = new EditBox(this.font, boxX + 205, rowY + 1, 160, 18, Component.literal("Filter"));
        eventFilterBox.setMaxLength(256);
        if (originalRule != null && originalRule.eventFilter().isPresent()) {
            eventFilterBox.setValue(originalRule.eventFilter().get());
        }
        this.addRenderableWidget(eventFilterBox);

        // IF Condition Type
        rowY += 40;
        this.addRenderableWidget(new CyberpunkButton(boxX + 15, rowY, 180, 20,
                Component.literal("IF: " + conditionType.toUpperCase()), b -> {
            conditionType = switch (conditionType) {
                case "none" -> "player_tag";
                case "player_tag" -> "held_item";
                case "held_item" -> "none";
                default -> "none";
            };
            b.setMessage(Component.literal("IF: " + conditionType.toUpperCase()));
        }, GOLD, false));

        conditionParamBox = new EditBox(this.font, boxX + 205, rowY + 1, 160, 18, Component.literal("Condition Value"));
        conditionParamBox.setMaxLength(256);
        if (originalRule != null && !originalRule.conditions().isEmpty()) {
            var c = originalRule.conditions().get(0);
            conditionType = c.type();
            conditionParamBox.setValue(c.tag().orElse(c.item().orElse("")));
        }
        this.addRenderableWidget(conditionParamBox);

        // THEN Action Type
        rowY += 40;
        this.addRenderableWidget(new CyberpunkButton(boxX + 15, rowY, 180, 20,
                Component.literal("THEN: " + actionType.toUpperCase()), b -> {
            actionType = switch (actionType) {
                case "run_command" -> "start_dialog";
                case "start_dialog" -> "play_animation";
                case "play_animation" -> "look_at";
                case "look_at" -> "give_item";
                case "give_item" -> "despawn";
                case "despawn" -> "run_command";
                default -> "run_command";
            };
            b.setMessage(Component.literal("THEN: " + actionType.toUpperCase()));
        }, CYAN_MAIN, false));

        actionParamBox = new EditBox(this.font, boxX + 205, rowY + 1, 160, 18, Component.literal("Action Value"));
        actionParamBox.setMaxLength(512);
        if (originalRule != null && !originalRule.actions().isEmpty()) {
            var a = originalRule.actions().get(0);
            actionType = a.type();
            actionParamBox.setValue(a.command().orElse(a.dialogId().orElse(a.animation().orElse(""))));
        } else if (actionParamBox.getValue().isEmpty()) {
            actionParamBox.setValue("say Hello %player%!");
        }
        this.addRenderableWidget(actionParamBox);

        // Browse Dialog button if action is start_dialog
        int browseY = rowY + 28;
        this.addRenderableWidget(new CyberpunkButton(boxX + 205, browseY, 160, 20,
                Component.literal("📁 Pick Sequence"), b -> {
            List<String> seqs = new ArrayList<>();
            for (var id : DialogManager.getInstance().getSequenceIds()) {
                seqs.add(id.toString());
            }
            if (seqs.isEmpty()) seqs.add("fractured_utils:default_dialog");
            if (this.minecraft != null) {
                this.minecraft.setScreen(new AssetPickerModalScreen(this, "Select Dialog Sequence", seqs, chosen -> {
                    actionType = "start_dialog";
                    actionParamBox.setValue(chosen);
                }));
            }
        }, GOLD, false));

        // Save & Cancel Buttons
        int btnY = boxY + boxH - 26;
        this.addRenderableWidget(new CyberpunkButton(boxX + 15, btnY, 160, 20,
                Component.literal("✓ Save Rule"), b -> saveAndClose(), CYAN_MAIN, false));

        this.addRenderableWidget(new CyberpunkButton(boxX + boxW - 175, btnY, 160, 20,
                Component.literal("Cancel"), b -> this.onClose(), 0xFFFF5555, false));
    }

    private void saveAndClose() {
        Optional<String> filter = (!eventFilterBox.getValue().isBlank())
                ? Optional.of(eventFilterBox.getValue().trim()) : Optional.empty();

        List<NpcCondition> conditions = new ArrayList<>();
        if (!"none".equalsIgnoreCase(conditionType) && !conditionParamBox.getValue().isBlank()) {
            String val = conditionParamBox.getValue().trim();
            if ("player_tag".equalsIgnoreCase(conditionType)) {
                conditions.add(new NpcCondition("player_tag", Optional.of(val), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false));
            } else if ("held_item".equalsIgnoreCase(conditionType)) {
                conditions.add(new NpcCondition("held_item", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(val), Optional.of(1), false));
            }
        }

        List<NpcAction> actions = new ArrayList<>();
        String param = actionParamBox.getValue().trim();
        switch (actionType) {
            case "start_dialog" -> actions.add(new NpcAction("start_dialog", Optional.of(param), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            case "play_animation" -> actions.add(new NpcAction("play_animation", Optional.empty(), Optional.of(param), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            case "look_at" -> actions.add(new NpcAction("look_at", Optional.empty(), Optional.empty(), Optional.of(param.isEmpty() ? "player" : param), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            case "give_item" -> actions.add(new NpcAction("give_item", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(param), Optional.of(1), Optional.empty()));
            case "despawn" -> actions.add(new NpcAction("despawn", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            default -> actions.add(new NpcAction("run_command", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(param)));
        }

        NpcRule rule = new NpcRule(trigger, filter, conditions, actions);
        onSave.accept(rule);
        this.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        int boxW = 380;
        int boxH = 250;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;

        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, CYAN_BG);
        guiGraphics.renderOutline(boxX, boxY, boxW, boxH, CARD_BORDER);

        guiGraphics.drawString(this.font, "✦ EDIT INTERACTION RULE", boxX + 15, boxY + 12, CYAN_MAIN, false);
        guiGraphics.drawString(this.font, "Filter (optional):", boxX + 205, boxY + 24, 0xFF888888, false);
        guiGraphics.drawString(this.font, "Condition Value:", boxX + 205, boxY + 64, 0xFF888888, false);
        guiGraphics.drawString(this.font, "Action Value:", boxX + 205, boxY + 104, 0xFF888888, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
