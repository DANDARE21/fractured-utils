package net.dandare21.fracturedutils.npc.pipeline;

import net.dandare21.fracturedutils.npc.entity.CustomNpcEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class NpcTriggerEvents {

    public static void onDialogEnd(MinecraftServer server, ResourceLocation sequenceId, List<ServerPlayer> players) {
        if (server == null || players == null || players.isEmpty()) return;
        String filter = sequenceId != null ? sequenceId.toString() : "";

        for (ServerPlayer player : players) {
            if (player == null) continue;
            // Find nearby NPCs within 32 blocks of player
            List<CustomNpcEntity> npcs = player.serverLevel().getEntitiesOfClass(
                    CustomNpcEntity.class,
                    player.getBoundingBox().inflate(32.0)
            );
            for (CustomNpcEntity npc : npcs) {
                npc.fireTrigger(NpcTriggerType.ON_DIALOG_END, filter, player);
            }
        }
    }

    public static void onMidDialogEvent(ServerPlayer player, String eventName, String payload) {
        if (player == null) return;
        List<CustomNpcEntity> npcs = player.serverLevel().getEntitiesOfClass(
                CustomNpcEntity.class,
                player.getBoundingBox().inflate(32.0)
        );
        for (CustomNpcEntity npc : npcs) {
            npc.fireTrigger(NpcTriggerType.ON_MID_DIALOG_EVENT, eventName, player);
        }
    }
}
