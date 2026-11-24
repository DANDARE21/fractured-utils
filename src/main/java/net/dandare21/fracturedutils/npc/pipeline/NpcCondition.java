package net.dandare21.fracturedutils.npc.pipeline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.Optional;

public record NpcCondition(
        String type,
        Optional<String> tag,
        Optional<String> objective,
        Optional<String> operator,
        Optional<Integer> scoreValue,
        Optional<String> item,
        Optional<Integer> count,
        boolean negated
) {
    public static final Codec<NpcCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("type", "player_tag").forGetter(NpcCondition::type),
            Codec.STRING.optionalFieldOf("tag").forGetter(NpcCondition::tag),
            Codec.STRING.optionalFieldOf("objective").forGetter(NpcCondition::objective),
            Codec.STRING.optionalFieldOf("operator").forGetter(NpcCondition::operator),
            Codec.INT.optionalFieldOf("scoreValue").forGetter(NpcCondition::scoreValue),
            Codec.STRING.optionalFieldOf("item").forGetter(NpcCondition::item),
            Codec.INT.optionalFieldOf("count").forGetter(NpcCondition::count),
            Codec.BOOL.optionalFieldOf("negated", false).forGetter(NpcCondition::negated)
    ).apply(instance, NpcCondition::new));

    public boolean test(ServerPlayer player) {
        if (player == null) return false;
        boolean result = false;
        String t = type.toLowerCase();
        switch (t) {
            case "player_tag" -> {
                String reqTag = tag.orElse("");
                result = !reqTag.isEmpty() && player.getTags().contains(reqTag);
            }
            case "scoreboard" -> {
                String objName = objective.orElse("");
                int targetVal = scoreValue.orElse(0);
                String op = operator.orElse(">=");
                Scoreboard sb = player.getScoreboard();
                Objective obj = sb.getObjective(objName);
                if (obj != null && sb.hasPlayerScore(player.getScoreboardName(), obj)) {
                    int actualVal = sb.getOrCreatePlayerScore(player.getScoreboardName(), obj).getScore();
                    result = switch (op) {
                        case "==" -> actualVal == targetVal;
                        case ">=" -> actualVal >= targetVal;
                        case "<=" -> actualVal <= targetVal;
                        case ">" -> actualVal > targetVal;
                        case "<" -> actualVal < targetVal;
                        case "!=" -> actualVal != targetVal;
                        default -> actualVal >= targetVal;
                    };
                }
            }
            case "held_item" -> {
                String reqItem = item.orElse("");
                int reqCount = count.orElse(1);
                var held = player.getMainHandItem();
                String heldId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(held.getItem()).toString();
                result = heldId.equals(reqItem) && held.getCount() >= reqCount;
            }
            default -> result = true;
        }
        return negated != result;
    }
}
