# Client Usage, In-Game GUIs & Configuration Guide

This guide covers all client-side features in Fractured Utils, including keybindings, in-game graphical user interfaces (GUIs), the Operator Monitor HUD, and client/server configuration files.

---

## 1. Keybindings Reference

Fractured Utils registers four configurable keybindings under **Options > Controls > Key Binds > Fractured Utils**:

| Action | Default Key | Registry Key | Function |
| :--- | :--- | :--- | :--- |
| **Waiting Room** | `J` | `key.fractured_utils.waiting_room` | Hold for 1.5 seconds (or click if OP) to join/open the pre-event waiting room lobby. |
| **Advance Dialog** | `E` | `key.fractured_utils.dialog_advance` | Advances to the next RPG dialog line or instantly finishes typewriter text. |
| **Skip Cutscene** | `Space` | `key.fractured_utils.skip_cutscene` | Hold to skip an active video cutscene when skip is enabled by the server. |
| **Operator Resume** | `\` | `key.fractured_utils.operator_resume` | Instantly unblocks the current sequence wait trigger without typing commands. |

---

## 2. In-Game Graphical User Interfaces (GUIs)

### 1. Event Orchestrator Editor (`/orchestrator ui`)
The Orchestrator Editor allows visual choreography of event sequences:
- **Left Panel**: View, create, rename, and delete JSON sequences stored in `config/orchestrator_sequences/`.
- **Center Action List**: Displays the step-by-step action sequence. Each card shows the action category, target, and timing.
- **Action Modal Editor**: Click "Add Action" or double-click any existing action to open its dedicated parameter dialog:
  - Command input boxes with auto-completion.
  - Coordinate pickers (with "Set to Current Position" shortcuts).
  - Delay sliders (in ticks).
  - Target selector pickers (`@a`, `@p`, `@e[...]`).
- **Drag & Reorder**: Reorder actions by clicking the up/down arrows.

---

### 2. Music Sequencer Editor (`/musicsequence ui`)
The Music Sequencer DAW (Digital Audio Workstation) provides beat-accurate event placement:
- **Audio Waveform Canvas**: Real-time rendering of audio amplitude waveforms loaded from the active OGG track.
- **Scrubbing Playhead**: Click and drag across the timeline to scrub forward/backward.
- **Multi-Track Channels**: Manage independent tracks for background music, sound effects, voice lines, camera cuts, and puppet attack triggers.
- **Cue Markers**: Place timestamped cues with millisecond precision to align boss slams, lighting changes, or screen shakes with musical drops.

---

### 3. Camera Setup Screen (`CameraSetupScreen`)
Used to choreograph 3D cinematic camera shots:
- **Embedded 3D Viewport**: An interactive preview window directly in the GUI displaying the camera lens perspective.
- **Keyframe Controls**: Adjust Camera Position $(X, Y, Z)$, Rotation (Yaw, Pitch, Roll), and Field-of-View ($FOV$).
- **Spline & Easing Selectors**: Choose between `CATMULL_ROM`, `BEZIER`, `LINEAR`, or `STEP`, paired with 11 easing curves (Quad, Cubic, Sine, Expo, Elastic, Bounce).
- **Target Tracking**: Bind the camera lens to track an entity (`@p`, `@e[type=boss]`) with variable look-at weighting.

---

### 4. Live Operator Monitor HUD (`OpMonitor`)
When an operator with permission level 2+ connects to a server running a sequence, a draggable telemetry HUD appears:
- **Displays**: Active sequence name, execution status (`RUNNING`, `WAITING`, `PAUSED`), current step, and elapsed time.
- **Quick Action Button**: Click the on-screen "Resume" button or press the `\` key to trigger waiting steps.
- **Customization (`OpMonitorConfigScreen`)**:
  - Drag the overlay anywhere on your screen.
  - Adjust opacity ($0.1$ to $1.0$).
  - Adjust scaling ($0.5\times$ to $2.0\times$).

---

### 5. Waiting Room Lobby (`WaitingRoomScreen`)
- Displays event title and synchronized countdown clock.
- **Player Roster**: Shows connected participants with checkmarks indicating ready status.
- **Ready Toggle**: Click "Ready" to signal preparedness to the event host.
- **Integrated Chat**: Send and receive lobby messages without closing the UI.

---

### 6. Downed Spectator Screen (`DownedSpectateScreen`)
- When downed during an active raid/encounter, the player's view enters a spectate screen.
- Teammate statuses are displayed while awaiting a teammate to perform a revive interaction.

---

## 3. Configuration Files Reference

### Client Configuration: `config/fracturedutils-client.json`

```json
{
  "videoVolumePercent": 100,
  "opMonitorEnabled": true,
  "opMonitorX": -1,
  "opMonitorY": -1,
  "opMonitorOpacity": 0.6,
  "opMonitorScale": 1.0
}
```

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `videoVolumePercent` | int | `100` | Audio volume multiplier ($0$ to $100$) for video cutscenes. |
| `opMonitorEnabled` | boolean | `true` | Toggles rendering of the Operator Monitor telemetry HUD. |
| `opMonitorX`, `Y` | int | `-1` | Saved screen coordinate coordinates ($-1$ uses default top-left anchoring). |
| `opMonitorOpacity` | float | `0.6` | Background opacity ($0.1$ to $1.0$) of the Operator HUD. |
| `opMonitorScale` | float | `1.0` | UI scaling factor ($0.5$ to $2.0$) for the Operator HUD. |

---

### Server Configuration: `config/fracturedutils-server.json`

```json
{
  "keepInventoryNoXp": false,
  "enableDebugBoss": true,
  "teamWipeScreenDurationSeconds": 3,
  "eventAudioPort": 8085,
  "eventAudioExternalUrl": "",
  "eventAudioRequirePack": true,
  "eventAudioNamespace": "fracturedutils"
}
```

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `keepInventoryNoXp` | boolean | `false` | When enabled, players retain inventory upon death but reset experience points to 0. |
| `enableDebugBoss` | boolean | `true` | Allows spawning and debugging the Void Herald boss puppet entity. |
| `teamWipeScreenDurationSeconds` | int | `3` | Number of seconds the "TEAM WIPED" screen is shown before rollback. |
| `eventAudioPort` | int | `8085` | Local TCP port for the embedded dynamic HTTP music streaming server. |
| `eventAudioExternalUrl` | string | `""` | Public WAN URL or reverse-proxy domain for streaming audio across the internet. |
| `eventAudioRequirePack` | boolean | `true` | If true, clients are automatically prompted to accept the virtual event audio pack. |
| `eventAudioNamespace` | string | `"fracturedutils"` | Resource namespace prefix for dynamically discovered event sound tracks. |

