package net.dandare21.fracturedutils.npc.network;

import net.dandare21.fracturedutils.npc.NpcDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2COpenNpcStudioPacket {
    private final int targetEntityId;
    private final NpcDefinition definition;

    public S2COpenNpcStudioPacket(int targetEntityId, NpcDefinition definition) {
        this.targetEntityId = targetEntityId;
        this.definition = definition;
    }

    public S2COpenNpcStudioPacket(FriendlyByteBuf buf) {
        this.targetEntityId = buf.readVarInt();
        this.definition = NpcDefinition.decode(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
        definition.encode(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft.getInstance().setScreen(
                        new net.dandare21.fracturedutils.npc.client.gui.NpcStudioScreen(targetEntityId, definition)
                );
            });
        });
        ctx.setPacketHandled(true);
    }
}
