# Cinematics & 3D Keyframe Camera System

Fractured Utils provides two cinematic tools: **Remote Video Cutscenes** for pre-rendered cinematic videos and an advanced **3D Keyframe Camera System** with Catmull-Rom splines, Bezier curves, and mathematical easing functions.

---

## 1. Remote Video Cutscene System

The video cutscene system allows servers to stream and synchronize pre-rendered video cutscenes (e.g. MP4 / WEBM) to all players or targeted groups.

### Network Preparation & Handshake Flow
```
Server                                              Client(s)
  │                                                     │
  ├────── S2CPrepareVideoPacket(url, allowSkip) ───────►│ (Background download)
  │                                                     │
  │◄───── C2SDownloadCompletePacket(hash) ──────────────┤
  │                                                     │
(All clients ready)                                     │
  │                                                     │
  ├────── S2CStartPlaybackPacket(timestamp) ───────────►│ (Opens CinematicScreen)
  │                                                     │
  │◄───── C2SCutsceneEndPacket / Skip ──────────────────┤ (Holds Space to Skip)
```

### Key Features
- **Client Video Cache (`ClientVideoCache`)**: Videos are cached to disk so subsequent replays play instantly without redownloading.
- **Audio Suppression**: World and ambient sounds are automatically muted during video playback (`PlaySoundEvent` cancellation).
- **Hold-to-Skip**: Players can hold the `Space` key (`key.fractured_utils.skip_cutscene`) to skip if `allowSkip` is enabled.
- **Auto-Cleanup**: With `deleteAfter = true`, the cached video file is automatically deleted from disk once playback finishes.

### Video Commands

| Command | Usage | Description |
| :--- | :--- | :--- |
| `/playcinematic` | `/playcinematic [targets] <url> [allowSkip] [deleteAfter]` | Downloads and synchronously plays a video cutscene. |
| `/downloadcinematic` | `/downloadcinematic [targets] <name> <url>` | Pre-caches a video in the background before an event. |

```mcfunction
# Play a skip-enabled cutscene for all players and auto-delete cache after
/playcinematic @a https://example.com/cutscenes/intro.mp4 true true
```

---

## 2. 3D Keyframe Spline Camera System

The 3D Camera system overrides the player's view with choreographed camera paths, mathematical splines, and customizable easing functions.

### Camera Shot Types (`CameraShot`)
- **`STATIC`**: Fixed camera position and orientation in world space.
- **`FOLLOW`**: Camera follows an entity's position while maintaining relative angles.
- **`OVER_THE_SHOULDER`**: Third-person cinematic action view with configurable shoulder offsets (`heightOffset`, `backDistance`, `shoulderOffset`).
- **`CLEAR`**: Smoothly releases camera control back to the player.

---

## 3. Splines & Interpolation Types (`SplineMath`)

| Spline Mode | Mathematical Implementation | Use Case |
| :--- | :--- | :--- |
| `CATMULL_ROM` | Centripetal Catmull-Rom cubic spline passing through all control points without overshoot. | Smooth cinematic fly-throughs through multiple rooms or scenery. |
| `BEZIER` | Cubic Bezier curve using incoming (`inHandleX/Y/Z`) and outgoing (`outHandleX/Y/Z`) velocity tangent vectors. | Precise camera curves, arcs, and dramatic sweeping angles. |
| `LINEAR` | Direct linear point-to-point interpolation. | Mechanical pans, rapid transitions, or tracking shots. |
| `STEP` | Discrete instantaneous step (zero interpolation). | Hard cinematic camera cuts between scenes. |

---

## 4. Easing Functions Reference (`CameraEasing`)

Fractured Utils implements 11 easing curves, each with `EASE_IN`, `EASE_OUT`, and `EASE_IN_OUT` variations:

```
Linear        Quadratic      Cubic          Quartic        Quintic
──────►       ───/──►        ──/───►        ─/────►        /─────►

Sine          Exponential    Circular       Back           Bounce
───~──►       ────/─►        ───)──►        ──~─/─►        _/\_/\►
```

- **`LINEAR`**: Constant velocity.
- **`QUAD`**, **`CUBIC`**, **`QUART`**, **`QUINT`**: Progressive polynomial acceleration and deceleration.
- **`SINE`**: Gentle, natural sinusoidal acceleration.
- **`EXPO`**: High-contrast, sharp cinematic zooms.
- **`CIRC`**: Circular arc easing.
- **`BACK`**: Pulls slightly backward before surging forward (anticipation).
- **`ELASTIC`**: Rubber-band overshoot and settle.
- **`BOUNCE`**: Decaying physics bounce on arrival.

---

## 5. Decoupled Look-At Targeting (Inverse Kinematics)

Camera orientation (Yaw/Pitch) can be decoupled from the camera's position path:
- **`COORDINATE` Target**: Forces the camera lens to continuously track a static coordinate `(x, y, z)` while the camera moves along a spline.
- **`ENTITY` Target**: Forces the camera lens to lock onto an entity (e.g. `@p` or `@e[type=boss]`).
- **`lookAtWeight`**: Blend factor ($0.0 \le w \le 1.0$) between the keyframe's fixed rotation and the dynamic look-at target.

---

## 6. Developer API & Java Usage

### Triggering a Camera Path in Java
```java
import net.dandare21.fracturedutils.client.camera.CameraSequenceTrack;
import net.dandare21.fracturedutils.client.camera.CameraKeyframe;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.S2CPlayCameraTrackPacket;

// Build camera track
CameraSequenceTrack track = new CameraSequenceTrack("boss_intro_pan");

// Keyframe 1: Wide establishing shot
track.addKeyframe(new CameraKeyframe.Builder()
    .pos(120.0, 75.0, -300.0)
    .lookAt("@e[type=fractured_utils:void_herald,limit=1]")
    .fov(85.0)
    .duration(3000) // 3 seconds
    .spline("CATMULL_ROM")
    .easing("EASE_IN_OUT_CUBIC")
    .build()
);

// Keyframe 2: Close-up zoom
track.addKeyframe(new CameraKeyframe.Builder()
    .pos(120.0, 66.0, -325.0)
    .lookAt("@e[type=fractured_utils:void_herald,limit=1]")
    .fov(50.0)
    .duration(2000)
    .spline("CATMULL_ROM")
    .easing("EASE_OUT_EXPO")
    .build()
);

// Send to player
ModMessages.sendToPlayer(new S2CPlayCameraTrackPacket(track), player);
```

---

## 7. In-Game Tools: Setup Screen & Path Visualizer

1. **Camera Setup Screen (`CameraSetupScreen`)**:
   - Access from the visual Sequencer or UI to set camera positions, preview angles, and tweak tangent handles interactively.
2. **In-World Camera Path Renderer (`CameraPathRenderer`)**:
   - While in creative/editor mode, the mod renders glowing 3D trajectory spline curves, keyframe nodes, and camera forward vectors directly in the game world.
3. **Embedded 3D Viewport (`SequencerViewportManager`)**:
   - The Sequencer UI embeds an interactive 3D camera preview viewport directly inside the GUI canvas.

