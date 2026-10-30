# Event Audio & Multi-Track Music Sequencer

Fractured Utils features a comprehensive sound subsystem built specifically for synchronized live events, boss encounters, and scripted music tracks. It combines **zero-download HTTP dynamic audio streaming** with an **in-game visual multi-track waveform sequencer**.

---

## 1. Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Dynamic HTTP Audio Server                       │
│  • EventMusicHttpServer (Embedded server, default port 8085)           │
│  • EventMusicPackBuilder: Assembles virtual resource pack in memory    │
│  • SHA-1 validation & automatic client pack delivery                   │
│  • Sound Categories: 'eventmusic' and 'eventambience'                  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    Multi-Track Music Sequencer Engine                  │
│                                                                        │
│  Timeline (Milliseconds):                                              │
│  00:00.000 ──────────► 00:15.500 ──────────► 00:32.120 ────────► End   │
│  [Track 1: OGG Audio]                                                  │
│  [Track 2: Commands]   [Spawn Boss]          [Trigger Shockwave]       │
│  [Track 3: Camera]     [Catmull-Rom Pan]     [Over-The-Shoulder]       │
│  [Track 4: Effects]    [Cinematic Bars]      [Impact Frame]            │
│  [Track 5: Dialogue]                         [Herald: "Fall!"]         │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Dynamic HTTP Audio Streaming

### The Problem
Traditional resource packs require massive multi-megabyte downloads every time a new boss theme or music track is added, forcing clients to reload all textures, models, and sounds.

### The Solution
Fractured Utils runs an embedded HTTP streaming server (`EventMusicHttpServer`).
- Place `.ogg` tracks into `config/event_music/`.
- Run `/eventmusic reload`.
- The server automatically builds an in-memory virtual resource pack and serves audio buffers directly to connecting clients.
- Clients receive tracks on the fly without restarting their client or reloading game models.

### Audio Categories
Fractured Utils adds two dedicated client audio categories:
1. **Event Music (`eventmusic`)**: Boss battle tracks, cinematic orchestral themes.
2. **Event Ambience (`eventambience`)**: Environmental background loops, wind, weather, arena droning.

Both categories have dedicated in-game volume sliders in **Options > Music & Sounds** (`ClientSoundOptionsHandler`).

---

## 3. Multi-Track Music Sequencer

The **Music Sequencer** (`MusicSequenceManager`) synchronizes game events to the millisecond timeline of an audio track.

### Sequence File Format (`JSON`)
Sequences are stored in `config/music_sequences/<name>.json`.

```json
{
  "sequenceName": "void_awakening.json",
  "songTrack": "fractured_utils:void_herald_theme",
  "looping": false,
  "volume": 1.0,
  "pitch": 1.0,
  "bpm": 140,
  "startMs": 0,
  "endMs": 185000,
  "entries": [
    {
      "timestampMs": 5200,
      "actionType": "COMMAND",
      "command": "screeneffect play cinematic_bars 15000 1000 1000 @a",
      "description": "Intro letterboxing"
    },
    {
      "timestampMs": 12850,
      "actionType": "CAMERA",
      "cameraMode": "STATIC",
      "cameraX": 100.5,
      "cameraY": 65.0,
      "cameraZ": -200.5,
      "cameraYaw": 90.0,
      "cameraPitch": 15.0,
      "splineMode": "CATMULL_ROM",
      "cameraEasing": "EASE_IN_OUT_CUBIC",
      "description": "Camera pan to altar"
    },
    {
      "timestampMs": 24000,
      "actionType": "SCREEN_EFFECT",
      "screenEffectId": "fractured_utils:impact_frame",
      "durationMs": 350,
      "screenEffectColor": -65536,
      "description": "Bass drop impact flash"
    },
    {
      "timestampMs": 24000,
      "actionType": "PUPPET_ACTION",
      "command": "leap_slam",
      "windupMs": 600,
      "jumpMs": 400,
      "recoveryMs": 500,
      "description": "Boss slam on the beat"
    }
  ]
}
```

---

## 4. OpenAL Direct Audio Manipulation

Fractured Utils includes custom low-level audio utilities interfacing directly with LWJGL / OpenAL:

### `OpenALSeeker`
Allows the server to jump or scrub to any millisecond offset in an OGG file without latency:
```java
// Fast-forward or resume track at exactly 45.2 seconds
OpenALSeeker.seekToMillisecond(soundInstance, 45200);
```

### `SmoothFadeSoundInstance`
Avoids abrupt audio cuts by applying smooth mathematical volume interpolation curves during track start, transitions, and stops:
```java
// Fade out over 2500ms
EventAudioManager.getInstance().stopAudio(server, null, 2500);
```

---

## 5. In-Game Sequencer UI (`/musicsequence ui`)

Opening the in-game editor via `/musicsequence ui` provides a Digital Audio Workstation (DAW) experience inside Minecraft:
- **Audio Waveform Visualization**: Visual peaks and valleys rendered directly onto the timeline (`MusicWaveformRenderer`).
- **Playhead Scrubbing**: Drag the playhead cursor to audition timing and sound cues in real time.
- **Entry Markers**: Colored marker tags denoting commands, camera transitions, screen effects, and puppet strikes.
- **Modal Property Editors**: Fine-tune cue timestamps and action properties with instant preview.

---

## 6. Developer API & Java Usage

### Playing Event Audio in Java
```java
import net.dandare21.fracturedutils.sound.event.EventAudioManager;
import net.dandare21.fracturedutils.sound.ModSoundSources;
import net.dandare21.fracturedutils.network.packet.S2CPlayEventAudioPacket.PlaybackMode;

// Play soundtrack with a 2-second fade-in
EventAudioManager.getInstance().playAudio(
    server,
    "fractured_utils:boss_theme",
    ModSoundSources.EVENT_MUSIC,
    null,                    // Target players (null = all players)
    1.0f,                    // Volume
    1.0f,                    // Pitch
    2000,                    // Fade duration in milliseconds
    PlaybackMode.SERVER_CONTROLLED,
    true,                    // Looping
    500                      // Client sync threshold (ms)
);
```

### Starting a Music Sequence in Java
```java
import net.dandare21.fracturedutils.sound.sequence.MusicSequenceManager;

// Trigger music sequence for all players
MusicSequenceManager.getInstance().startSequence("void_awakening.json", null);
```

---

## 7. Command Reference

All commands require OP Permission Level 2:

### Event Audio Commands (`/eventmusic`)

| Command | Usage | Description |
| :--- | :--- | :--- |
| `play` | `/eventmusic play <track> [category] [mode] [loop] [vol] [pitch] [fade] [sync]` | Plays an audio track across server/clients. |
| `stop` | `/eventmusic stop [fadeDurationMs]` | Smoothly fades out and stops all event audio. |
| `reload` | `/eventmusic reload` | Re-scans `config/event_music/` and updates virtual packs. |
| `status` | `/eventmusic status [player]` | Shows current playing track, elapsed time, and sync stats. |
| `require` | `/eventmusic require <true\|false>` | Toggles whether clients are required to download the pack. |

### Music Sequencer Commands (`/musicsequence`)

| Command | Usage | Description |
| :--- | :--- | :--- |
| `run` | `/musicsequence run <file_name> [targets]` | Runs a synchronized music sequence JSON file. |
| `stop` | `/musicsequence stop` | Stops all active music sequences immediately. |
| `list` | `/musicsequence list` | Lists all sequence files in `config/music_sequences/`. |
| `ui` | `/musicsequence ui` | Opens the interactive visual waveform sequencer UI. |

