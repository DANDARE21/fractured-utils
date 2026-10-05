package net.dandare21.fracturedutils.network.packet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.dandare21.fracturedutils.client.camera.CameraSequenceTrack;
import net.dandare21.fracturedutils.client.camera.ClientCameraHandler;
import net.dandare21.fracturedutils.sound.sequence.MusicSequenceEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CPlayCameraTrackPacket {
    private static final Gson GSON = new Gson();
    private static final Type ENTRY_LIST_TYPE = new TypeToken<ArrayList<MusicSequenceEntry>>() {}.getType();

    private final String sequenceName;
    private final long startTimeMs;
    private final boolean looping;
    private final long startMs;
    private final long endMs;
    private final String cameraEntriesJson;

    public S2CPlayCameraTrackPacket(String sequenceName, long startTimeMs, boolean looping, long startMs, long endMs, List<MusicSequenceEntry> cameraEntries) {
        this.sequenceName = sequenceName != null ? sequenceName : "";
        this.startTimeMs = startTimeMs;
        this.looping = looping;
        this.startMs = startMs;
        this.endMs = endMs;
        this.cameraEntriesJson = GSON.toJson(cameraEntries != null ? cameraEntries : new ArrayList<>());
    }

    public S2CPlayCameraTrackPacket(FriendlyByteBuf buf) {
        this.sequenceName = buf.readUtf(128);
        this.startTimeMs = buf.readLong();
        this.looping = buf.readBoolean();
        this.startMs = buf.readLong();
        this.endMs = buf.readLong();
        this.cameraEntriesJson = buf.readUtf(262144);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.sequenceName, 128);
        buf.writeLong(this.startTimeMs);
        buf.writeBoolean(this.looping);
        buf.writeLong(this.startMs);
        buf.writeLong(this.endMs);
        buf.writeUtf(this.cameraEntriesJson, 262144);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                List<MusicSequenceEntry> entries = GSON.fromJson(this.cameraEntriesJson, ENTRY_LIST_TYPE);
                if (entries != null && !entries.isEmpty()) {
                    CameraSequenceTrack track = CameraSequenceTrack.compile(entries, this.looping, this.startMs, this.endMs);
                    ClientCameraHandler.playCameraTrack(track, this.startTimeMs);
                } else {
                    ClientCameraHandler.clearCameraOverride();
                }
            } catch (Exception ignored) {
                ClientCameraHandler.clearCameraOverride();
            }
        }));
        ctx.setPacketHandled(true);
    }

    public String getSequenceName() {
        return sequenceName;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public boolean isLooping() {
        return looping;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public String getCameraEntriesJson() {
        return cameraEntriesJson;
    }
}
