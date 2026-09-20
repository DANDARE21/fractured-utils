package net.dandare21.fracturedutils.bossbar.network;

import net.dandare21.fracturedutils.bossbar.BossHealthBar;
import net.dandare21.fracturedutils.bossbar.client.ClientBossBarData;
import net.dandare21.fracturedutils.bossbar.client.ClientBossBarManager;
import net.dandare21.fracturedutils.bossbar.feature.PhaseMarkersFeature;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C packet to synchronize active boss healthbars to client players.
 */
public class S2CSyncBossBarsPacket {
    private final List<ClientBossBarData> bars;

    public S2CSyncBossBarsPacket(List<BossHealthBar> serverBars) {
        this.bars = new ArrayList<>();
        if (serverBars != null) {
            for (BossHealthBar sb : serverBars) {
                ClientBossBarData cb = new ClientBossBarData(sb.getId(), sb.getName());
                cb.setTargetPercent(sb.getPercent());
                cb.setCurrentHealth(sb.getCurrentHealth());
                cb.setMaxHealth(sb.getMaxHealth());
                cb.setColor(sb.getColor());
                cb.setOverlay(sb.getOverlay());
                cb.setCustomColorHex(sb.getCustomColorHex());
                cb.setStyleId(sb.getStyleId());
                cb.setCustomTexture(sb.getCustomTexture());
                cb.setBarWidth(sb.getBarWidth());
                cb.setBarHeight(sb.getBarHeight());
                cb.setTextDisplayMode(sb.getTextDisplayMode());
                cb.setShowGhostBar(sb.isShowGhostBar());
                cb.setVisible(sb.isVisible());

                if (sb.getFeatures().containsKey(PhaseMarkersFeature.ID)) {
                    PhaseMarkersFeature pm = (PhaseMarkersFeature) sb.getFeatures().get(PhaseMarkersFeature.ID);
                    cb.getPhaseThresholds().addAll(pm.getThresholds());
                }

                this.bars.add(cb);
            }
        }
    }

    public S2CSyncBossBarsPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.bars = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String id = buf.readUtf();
            Component name = buf.readComponent();
            ClientBossBarData cb = new ClientBossBarData(id, name);

            cb.setTargetPercent(buf.readFloat());
            cb.setCurrentHealth(buf.readFloat());
            cb.setMaxHealth(buf.readFloat());

            cb.setColor(buf.readEnum(BossEvent.BossBarColor.class));
            cb.setOverlay(buf.readEnum(BossEvent.BossBarOverlay.class));
            cb.setCustomColorHex(buf.readUtf());

            cb.setStyleId(buf.readUtf());
            boolean hasTexture = buf.readBoolean();
            if (hasTexture) {
                cb.setCustomTexture(buf.readResourceLocation());
            }
            cb.setBarWidth(buf.readInt());
            cb.setBarHeight(buf.readInt());

            cb.setTextDisplayMode(buf.readEnum(BossHealthBar.TextDisplayMode.class));
            cb.setShowGhostBar(buf.readBoolean());
            cb.setVisible(buf.readBoolean());

            int featureCount = buf.readInt();
            for (int f = 0; f < featureCount; f++) {
                String featId = buf.readUtf();
                if (PhaseMarkersFeature.ID.equals(featId)) {
                    PhaseMarkersFeature pm = new PhaseMarkersFeature();
                    pm.fromNetwork(buf);
                    cb.getPhaseThresholds().addAll(pm.getThresholds());
                } else {
                    // Skip or handle other features
                }
            }

            this.bars.add(cb);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.bars.size());
        for (ClientBossBarData bar : this.bars) {
            buf.writeUtf(bar.getId());
            buf.writeComponent(bar.getName());
            buf.writeFloat(bar.getTargetPercent());
            buf.writeFloat(bar.getCurrentHealth());
            buf.writeFloat(bar.getMaxHealth());

            buf.writeEnum(bar.getColor());
            buf.writeEnum(bar.getOverlay());
            buf.writeUtf(bar.getCustomColorHex() != null ? bar.getCustomColorHex() : "");

            buf.writeUtf(bar.getStyleId() != null ? bar.getStyleId() : "default");
            buf.writeBoolean(bar.getCustomTexture() != null);
            if (bar.getCustomTexture() != null) {
                buf.writeResourceLocation(bar.getCustomTexture());
            }
            buf.writeInt(bar.getBarWidth());
            buf.writeInt(bar.getBarHeight());

            buf.writeEnum(bar.getTextDisplayMode());
            buf.writeBoolean(bar.isShowGhostBar());
            buf.writeBoolean(bar.isVisible());

            // Features count
            int featureCount = bar.getPhaseThresholds().isEmpty() ? 0 : 1;
            buf.writeInt(featureCount);
            if (!bar.getPhaseThresholds().isEmpty()) {
                buf.writeUtf(PhaseMarkersFeature.ID);
                PhaseMarkersFeature pm = new PhaseMarkersFeature();
                for (float t : bar.getPhaseThresholds()) {
                    pm.addThreshold(t);
                }
                pm.toNetwork(buf);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ClientBossBarManager.getInstance().updateBossBars(this.bars);
        });
        ctx.setPacketHandled(true);
    }
}
