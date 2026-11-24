package net.dandare21.fracturedutils.npc.pipeline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.dandare21.fracturedutils.npc.entity.CustomNpcEntity;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record NpcRule(
        NpcTriggerType trigger,
        Optional<String> eventFilter,
        List<NpcCondition> conditions,
        List<NpcAction> actions
) {
    public static final Codec<NpcRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("trigger", "on_right_click")
                    .xmap(NpcTriggerType::fromString, t -> t.name().toLowerCase(Locale.ROOT))
                    .forGetter(NpcRule::trigger),
            Codec.STRING.optionalFieldOf("eventFilter").forGetter(NpcRule::eventFilter),
            NpcCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(NpcRule::conditions),
            NpcAction.CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(NpcRule::actions)
    ).apply(instance, NpcRule::new));

    public boolean matches(NpcTriggerType firedTrigger, String firedFilter, ServerPlayer player) {
        if (this.trigger != firedTrigger) return false;
        if (eventFilter.isPresent() && !eventFilter.get().isEmpty()) {
            if (firedFilter == null || !eventFilter.get().equalsIgnoreCase(firedFilter.trim())) {
                return false;
            }
        }
        for (NpcCondition condition : conditions) {
            if (!condition.test(player)) {
                return false;
            }
        }
        return true;
    }

    public void execute(CustomNpcEntity npc, ServerPlayer player) {
        for (NpcAction action : actions) {
            action.execute(npc, player);
        }
    }
}
