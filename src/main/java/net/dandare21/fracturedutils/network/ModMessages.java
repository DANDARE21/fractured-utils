package net.dandare21.fracturedutils.network;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.network.packet.C2SClientReadyPacket;
import net.dandare21.fracturedutils.network.packet.C2SCutsceneEndPacket;
import net.dandare21.fracturedutils.network.packet.C2SDownloadCompletePacket;
import net.dandare21.fracturedutils.network.packet.JoinWaitingRoomC2SPacket;
import net.dandare21.fracturedutils.network.packet.OpenWaitingRoomScreenS2CPacket;
import net.dandare21.fracturedutils.network.packet.S2CDownloadVideoPacket;
import net.dandare21.fracturedutils.network.packet.S2CPrepareVideoPacket;
import net.dandare21.fracturedutils.network.packet.S2CStartPlaybackPacket;
import net.dandare21.fracturedutils.network.packet.SyncWaitingRoomStateS2CPacket;
import net.dandare21.fracturedutils.network.packet.ToggleReadyC2SPacket;
import net.dandare21.fracturedutils.network.packet.C2SRequestOpenOrchestratorUiPacket;
import net.dandare21.fracturedutils.network.packet.S2CSendSequenceDataPacket;
import net.dandare21.fracturedutils.network.packet.C2SSaveSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SDeleteSequencePacket;
import net.dandare21.fracturedutils.network.packet.S2CSyncOperatorActionsPacket;
import net.dandare21.fracturedutils.network.packet.C2SSubmitOperatorResumePacket;
import net.dandare21.fracturedutils.network.packet.C2SStartSequencePacket;
import net.dandare21.fracturedutils.network.packet.S2CSyncSequenceTelemetryPacket;
import net.dandare21.fracturedutils.network.packet.S2CSyncPingsPacket;
import net.dandare21.fracturedutils.network.packet.S2CSyncDownedPacket;
import net.dandare21.fracturedutils.network.packet.S2CTeamWipePacket;
import net.dandare21.fracturedutils.network.packet.C2SRequestOpenDialogUiPacket;
import net.dandare21.fracturedutils.network.packet.S2CSendDialogSequenceDataPacket;
import net.dandare21.fracturedutils.network.packet.C2SSaveDialogSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SDeleteDialogSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SStartDialogSequencePacket;
import net.dandare21.fracturedutils.network.packet.S2CDialogDisplayPacket;
import net.dandare21.fracturedutils.network.packet.C2SDialogAdvancePacket;
import net.dandare21.fracturedutils.network.packet.S2CDialogReadinessPacket;
import net.dandare21.fracturedutils.network.packet.S2CDialogClearPacket;
import net.dandare21.fracturedutils.network.packet.S2CSyncObjectivePacket;
import net.dandare21.fracturedutils.network.packet.S2CPlayEventAudioPacket;
import net.dandare21.fracturedutils.network.packet.S2CStopEventAudioPacket;
import net.dandare21.fracturedutils.network.packet.C2SResourcePackStatusPacket;
import net.dandare21.fracturedutils.network.packet.S2CAudioPackSyncPacket;
import net.dandare21.fracturedutils.network.packet.S2CAudioSyncTimePacket;
import net.dandare21.fracturedutils.network.packet.C2SRequestOpenMusicSequenceUiPacket;
import net.dandare21.fracturedutils.network.packet.S2CSendMusicSequenceDataPacket;
import net.dandare21.fracturedutils.network.packet.C2SSaveMusicSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SDeleteMusicSequencePacket;
import net.dandare21.fracturedutils.network.packet.C2SStartMusicSequencePacket;
import net.dandare21.fracturedutils.network.packet.ClientboundPuppetAnimPacket;
import net.dandare21.fracturedutils.network.packet.ClientboundAttackIndicatorPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
    private static SimpleChannel INSTANCE;
    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(FracturedUtils.MOD_ID + ":main"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(SyncWaitingRoomStateS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncWaitingRoomStateS2CPacket::new)
                .encoder(SyncWaitingRoomStateS2CPacket::encode)
                .consumerMainThread(SyncWaitingRoomStateS2CPacket::handle)
                .add();

        net.messageBuilder(OpenWaitingRoomScreenS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenWaitingRoomScreenS2CPacket::new)
                .encoder(OpenWaitingRoomScreenS2CPacket::encode)
                .consumerMainThread(OpenWaitingRoomScreenS2CPacket::handle)
                .add();

        net.messageBuilder(JoinWaitingRoomC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(JoinWaitingRoomC2SPacket::new)
                .encoder(JoinWaitingRoomC2SPacket::encode)
                .consumerMainThread(JoinWaitingRoomC2SPacket::handle)
                .add();

        net.messageBuilder(ToggleReadyC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ToggleReadyC2SPacket::new)
                .encoder(ToggleReadyC2SPacket::encode)
                .consumerMainThread(ToggleReadyC2SPacket::handle)
                .add();

        net.messageBuilder(S2CPrepareVideoPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CPrepareVideoPacket::new)
                .encoder(S2CPrepareVideoPacket::encode)
                .consumerMainThread(S2CPrepareVideoPacket::handle)
                .add();

        net.messageBuilder(C2SClientReadyPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SClientReadyPacket::new)
                .encoder(C2SClientReadyPacket::encode)
                .consumerMainThread(C2SClientReadyPacket::handle)
                .add();

        net.messageBuilder(S2CStartPlaybackPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CStartPlaybackPacket::new)
                .encoder(S2CStartPlaybackPacket::encode)
                .consumerMainThread(S2CStartPlaybackPacket::handle)
                .add();

        net.messageBuilder(C2SCutsceneEndPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SCutsceneEndPacket::new)
                .encoder(C2SCutsceneEndPacket::encode)
                .consumerMainThread(C2SCutsceneEndPacket::handle)
                .add();

        net.messageBuilder(S2CDownloadVideoPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CDownloadVideoPacket::new)
                .encoder(S2CDownloadVideoPacket::encode)
                .consumerMainThread(S2CDownloadVideoPacket::handle)
                .add();

        net.messageBuilder(C2SDownloadCompletePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SDownloadCompletePacket::new)
                .encoder(C2SDownloadCompletePacket::encode)
                .consumerMainThread(C2SDownloadCompletePacket::handle)
                .add();

        net.messageBuilder(C2SRequestOpenOrchestratorUiPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SRequestOpenOrchestratorUiPacket::new)
                .encoder(C2SRequestOpenOrchestratorUiPacket::encode)
                .consumerMainThread(C2SRequestOpenOrchestratorUiPacket::handle)
                .add();

        net.messageBuilder(S2CSendSequenceDataPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CSendSequenceDataPacket::new)
                .encoder(S2CSendSequenceDataPacket::encode)
                .consumerMainThread(S2CSendSequenceDataPacket::handle)
                .add();

        net.messageBuilder(C2SSaveSequencePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SSaveSequencePacket::new)
                .encoder(C2SSaveSequencePacket::encode)
                .consumerMainThread(C2SSaveSequencePacket::handle)
                .add();

        net.messageBuilder(C2SDeleteSequencePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SDeleteSequencePacket::new)
                .encoder(C2SDeleteSequencePacket::encode)
                .consumerMainThread(C2SDeleteSequencePacket::handle)
                .add();

        net.messageBuilder(S2CSyncOperatorActionsPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CSyncOperatorActionsPacket::new)
                .encoder(S2CSyncOperatorActionsPacket::encode)
                .consumerMainThread(S2CSyncOperatorActionsPacket::handle)
                .add();

        net.messageBuilder(C2SSubmitOperatorResumePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SSubmitOperatorResumePacket::new)
                .encoder(C2SSubmitOperatorResumePacket::encode)
                .consumerMainThread(C2SSubmitOperatorResumePacket::handle)
                .add();

        net.messageBuilder(S2CSyncSequenceTelemetryPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CSyncSequenceTelemetryPacket::new)
                .encoder(S2CSyncSequenceTelemetryPacket::encode)
                .consumerMainThread(S2CSyncSequenceTelemetryPacket::handle)
                .add();
        net.messageBuilder(C2SStartSequencePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SStartSequencePacket::new)
                .encoder(C2SStartSequencePacket::encode)
                .consumerMainThread(C2SStartSequencePacket::handle)
                .add();

        net.messageBuilder(S2CSyncPingsPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CSyncPingsPacket::new)
                .encoder(S2CSyncPingsPacket::encode)
                .consumerMainThread(S2CSyncPingsPacket::handle)
                .add();

        net.messageBuilder(S2CSyncDownedPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CSyncDownedPacket::new)
                .encoder(S2CSyncDownedPacket::encode)
                .consumerMainThread(S2CSyncDownedPacket::handle)
                .add();

        net.messageBuilder(S2CTeamWipePacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CTeamWipePacket::new)
                .encoder(S2CTeamWipePacket::encode)
                .consumerMainThread(S2CTeamWipePacket::handle)
                .add();

        net.messageBuilder(C2SRequestOpenDialogUiPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SRequestOpenDialogUiPacket::new)
                .encoder(C2SRequestOpenDialogUiPacket::encode)
                .consumerMainThread(C2SRequestOpenDialogUiPacket::handle)
                .add();

        net.messageBuilder(S2CSendDialogSequenceDataPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CSendDialogSequenceDataPacket::new)
                .encoder(S2CSendDialogSequenceDataPacket::encode)
                .consumerMainThread(S2CSendDialogSequenceDataPacket::handle)
                .add();

        net.messageBuilder(C2SSaveDialogSequencePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SSaveDialogSequencePacket::new)
                .encoder(C2SSaveDialogSequencePacket::encode)
                .consumerMainThread(C2SSaveDialogSequencePacket::handle)
                .add();

        net.messageBuilder(C2SDeleteDialogSequencePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SDeleteDialogSequencePacket::new)
                .encoder(C2SDeleteDialogSequencePacket::encode)
                .consumerMainThread(C2SDeleteDialogSequencePacket::handle)
                .add();

        net.messageBuilder(C2SStartDialogSequencePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SStartDialogSequencePacket::new)
                .encoder(C2SStartDialogSequencePacket::encode)
                .consumerMainThread(C2SStartDialogSequencePacket::handle)
                .add();

        net.messageBuilder(S2CDialogDisplayPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CDialogDisplayPacket::new)
                .encoder(S2CDialogDisplayPacket::encode)
                .consumerMainThread(S2CDialogDisplayPacket::handle)
                .add();

        net.messageBuilder(C2SDialogAdvancePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SDialogAdvancePacket::new)
                .encoder(C2SDialogAdvancePacket::encode)
                .consumerMainThread(C2SDialogAdvancePacket::handle)
                .add();

        net.messageBuilder(S2CDialogReadinessPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CDialogReadinessPacket::new)
                .encoder(S2CDialogReadinessPacket::encode)
                .consumerMainThread(S2CDialogReadinessPacket::handle)
                .add();

        net.messageBuilder(S2CDialogClearPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CDialogClearPacket::new)
                .encoder(S2CDialogClearPacket::encode)
                .consumerMainThread(S2CDialogClearPacket::handle)
                .add();

        net.messageBuilder(S2CSyncObjectivePacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CSyncObjectivePacket::new)
                .encoder(S2CSyncObjectivePacket::encode)
                .consumerMainThread(S2CSyncObjectivePacket::handle)
                .add();

        net.messageBuilder(S2CPlayEventAudioPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CPlayEventAudioPacket::new)
                .encoder(S2CPlayEventAudioPacket::encode)
                .consumerMainThread(S2CPlayEventAudioPacket::handle)
                .add();

        net.messageBuilder(S2CStopEventAudioPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CStopEventAudioPacket::new)
                .encoder(S2CStopEventAudioPacket::encode)
                .consumerMainThread(S2CStopEventAudioPacket::handle)
                .add();

        net.messageBuilder(C2SResourcePackStatusPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SResourcePackStatusPacket::new)
                .encoder(C2SResourcePackStatusPacket::encode)
                .consumerMainThread(C2SResourcePackStatusPacket::handle)
                .add();

        net.messageBuilder(S2CAudioPackSyncPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CAudioPackSyncPacket::new)
                .encoder(S2CAudioPackSyncPacket::encode)
                .consumerMainThread(S2CAudioPackSyncPacket::handle)
                .add();

        net.messageBuilder(S2CAudioSyncTimePacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CAudioSyncTimePacket::new)
                .encoder(S2CAudioSyncTimePacket::encode)
                .consumerMainThread(S2CAudioSyncTimePacket::handle)
                .add();

        net.messageBuilder(C2SRequestOpenMusicSequenceUiPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SRequestOpenMusicSequenceUiPacket::new)
                .encoder(C2SRequestOpenMusicSequenceUiPacket::encode)
                .consumerMainThread(C2SRequestOpenMusicSequenceUiPacket::handle)
                .add();

        net.messageBuilder(S2CSendMusicSequenceDataPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CSendMusicSequenceDataPacket::new)
                .encoder(S2CSendMusicSequenceDataPacket::encode)
                .consumerMainThread(S2CSendMusicSequenceDataPacket::handle)
                .add();

        net.messageBuilder(C2SSaveMusicSequencePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SSaveMusicSequencePacket::new)
                .encoder(C2SSaveMusicSequencePacket::encode)
                .consumerMainThread(C2SSaveMusicSequencePacket::handle)
                .add();

        net.messageBuilder(C2SDeleteMusicSequencePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SDeleteMusicSequencePacket::new)
                .encoder(C2SDeleteMusicSequencePacket::encode)
                .consumerMainThread(C2SDeleteMusicSequencePacket::handle)
                .add();

        net.messageBuilder(C2SStartMusicSequencePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SStartMusicSequencePacket::new)
                .encoder(C2SStartMusicSequencePacket::encode)
                .consumerMainThread(C2SStartMusicSequencePacket::handle)
                .add();

        net.messageBuilder(ClientboundPuppetAnimPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ClientboundPuppetAnimPacket::new)
                .encoder(ClientboundPuppetAnimPacket::encode)
                .consumerMainThread(ClientboundPuppetAnimPacket::handle)
                .add();

        net.messageBuilder(ClientboundAttackIndicatorPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ClientboundAttackIndicatorPacket::new)
                .encoder(ClientboundAttackIndicatorPacket::encode)
                .consumerMainThread(ClientboundAttackIndicatorPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToPlayers(MSG message, java.util.List<ServerPlayer> players) {
        if (players != null) {
            for (ServerPlayer player : players) {
                if (player != null) {
                    sendToPlayer(message, player);
                }
            }
        }
    }

    public static <MSG> void sendToAllPlayers(MSG message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }

    public static <MSG> void sendToAllPlayers(MSG message, MinecraftServer server) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }

    public static <MSG> void sendToTrackingEntityAndSelf(MSG message, net.minecraft.world.entity.Entity entity) {
        if (entity != null) {
            INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), message);
        }
    }

    public static <MSG> void sendToNear(MSG message, net.minecraft.server.level.ServerLevel level, double x, double y, double z, double radius) {
        if (level != null) {
            INSTANCE.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, radius, level.dimension())), message);
        }
    }

    public static <MSG> void sendToDimension(MSG message, net.minecraft.server.level.ServerLevel level) {
        if (level != null) {
            INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), message);
        }
    }
}
