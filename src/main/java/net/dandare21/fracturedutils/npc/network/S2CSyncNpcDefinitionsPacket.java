package net.dandare21.fracturedutils.npc.network;

import net.dandare21.fracturedutils.npc.NpcDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CSyncNpcDefinitionsPacket {
    private final List<NpcDefinition> definitions;

    public S2CSyncNpcDefinitionsPacket(List<NpcDefinition> definitions) {
        this.definitions = definitions != null ? definitions : List.of();
    }

    public S2CSyncNpcDefinitionsPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.definitions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.definitions.add(NpcDefinition.decode(buf));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(definitions.size());
        for (NpcDefinition def : definitions) {
            def.encode(buf);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.dandare21.fracturedutils.npc.client.ClientNpcData.updateDefinitions(definitions);
            });
        });
        ctx.setPacketHandled(true);
    }
}
