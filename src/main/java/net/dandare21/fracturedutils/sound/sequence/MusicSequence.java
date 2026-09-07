package net.dandare21.fracturedutils.sound.sequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MusicSequence {
    private String sequenceName;
    private String songTrack;
    private boolean looping;
    private float volume;
    private float pitch;
    private int bpm;
    private long startMs;
    private long endMs;
    private List<MusicSequenceEntry> entries;

    public MusicSequence() {
        this.sequenceName = "new_music_sequence";
        this.songTrack = "";
        this.looping = false;
        this.volume = 1.0f;
        this.pitch = 1.0f;
        this.bpm = 120;
        this.startMs = 0L;
        this.endMs = 0L;
        this.entries = new ArrayList<>();
    }

    public MusicSequence(String sequenceName, String songTrack, boolean looping, float volume, float pitch, List<MusicSequenceEntry> entries) {
        this(sequenceName, songTrack, looping, volume, pitch, 120, 0L, 0L, entries);
    }

    public MusicSequence(String sequenceName, String songTrack, boolean looping, float volume, float pitch, int bpm, List<MusicSequenceEntry> entries) {
        this(sequenceName, songTrack, looping, volume, pitch, bpm, 0L, 0L, entries);
    }

    public MusicSequence(String sequenceName, String songTrack, boolean looping, float volume, float pitch, int bpm, long startMs, long endMs, List<MusicSequenceEntry> entries) {
        this.sequenceName = sequenceName != null ? sequenceName : "new_music_sequence";
        this.songTrack = songTrack != null ? songTrack : "";
        this.looping = looping;
        this.volume = volume > 0 ? volume : 1.0f;
        this.pitch = pitch > 0 ? pitch : 1.0f;
        this.bpm = bpm > 0 ? bpm : 120;
        this.startMs = Math.max(0L, startMs);
        this.endMs = Math.max(0L, endMs);
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public String getSequenceName() {
        return sequenceName;
    }

    public void setSequenceName(String sequenceName) {
        this.sequenceName = sequenceName != null ? sequenceName : "new_music_sequence";
    }

    public String getFileName() {
        return sequenceName;
    }

    public void setFileName(String fileName) {
        setSequenceName(fileName);
    }

    public String getSongTrack() {
        return songTrack;
    }

    public void setSongTrack(String songTrack) {
        this.songTrack = songTrack != null ? songTrack : "";
    }

    public boolean isLooping() {
        return looping;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(2.0f, volume));
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = Math.max(0.1f, Math.min(2.0f, pitch));
    }

    public int getBpm() {
        return bpm <= 0 ? 120 : bpm;
    }

    public void setBpm(int bpm) {
        this.bpm = Math.max(20, Math.min(300, bpm));
    }

    public long getStartMs() {
        return Math.max(0L, startMs);
    }

    public void setStartMs(long startMs) {
        this.startMs = Math.max(0L, startMs);
    }

    public long getEndMs() {
        return Math.max(0L, endMs);
    }

    public void setEndMs(long endMs) {
        this.endMs = Math.max(0L, endMs);
    }

    public List<MusicSequenceEntry> getEntries() {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        return entries;
    }

    public void setEntries(List<MusicSequenceEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public void sortEntriesByTimestamp() {
        if (entries != null) {
            entries.sort(Comparator.comparingLong(MusicSequenceEntry::getTimestampMs));
        }
    }

    public MusicSequence copy() {
        List<MusicSequenceEntry> copiedEntries = new ArrayList<>();
        if (this.entries != null) {
            for (MusicSequenceEntry e : this.entries) {
                copiedEntries.add(e.copy());
            }
        }
        return new MusicSequence(this.sequenceName, this.songTrack, this.looping, this.volume, this.pitch, this.bpm, this.startMs, this.endMs, copiedEntries);
    }
}
