# RPG Dialog System

The **RPG Dialog System** brings story-driven, Undertale-style narrative presentation to Minecraft. It features typewriter text reveals, customizable letter voice blips, player input locking, a retro RPG dialog box overlay, and automated cinematic camera focus.

---

## 1. Architecture Overview

```
                      ┌───────────────────────────────────────────┐
                      │   Dialog JSON (config/dialog_sequences/)  │
                      └─────────────────────┬─────────────────────┘
                                            │ DialogManager.startSequence()
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               DialogSequenceInstance                                   │
│  • Current DialogLine                                                                  │
│  • Typewriter Char Progress & Tick Counter                                             │
│  • Letter Blip Sound Dispatcher                                                        │
│  • Camera Focus Coordinates (cameraX/Y/Z, yaw, pitch, FOV)                             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ S2CDialogDisplayPacket
                                    ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 Client Presentation                                    │
│                                                                                        │
│  ┌─────────────────────────────┐                    ┌───────────────────────────────┐  │
│  │       DialogHudOverlay      │                    │     ClientDialogInputLock     │  │
│  │  • Speaker Nametag          │                    │  • Intercepts WASD & Jump     │  │
│  │  • Character-by-character   │                    │  • Blocks Left/Right Click    │  │
│  │  • Advance Icon (Blinking)  │                    │  • Released on Sequence End   │  │
│  └─────────────────────────────┘                    └───────────────────────────────┘  │
│                                 Advance Key (`E`)                                      │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Sequence File Format (`JSON`)

Dialog sequences are stored in `.minecraft/config/dialog_sequences/<name>.json` (or server `config/dialog_sequences/`).

### Example: `herald_confrontation.json`
```json
{
  "name": "herald_confrontation.json",
  "lines": [
    {
      "speaker": "§5Void Herald",
      "text": "Did you truly believe your light could penetrate this depth?",
      "delayTicks": 60,
      "charSpeedTicks": 1,
      "letterSound": "fractured_utils:dialog.blip_monster",
      "letterSoundPitchMin": 0.6,
      "letterSoundPitchMax": 0.9,
      "waitForInput": true,
      "useCamera": true,
      "cameraX": 120.5,
      "cameraY": 66.2,
      "cameraZ": -328.0,
      "cameraYaw": 180.0,
      "cameraPitch": 10.0,
      "cameraFov": 65.0
    },
    {
      "speaker": "§6Archivist",
      "text": "Hold fast! Do not listen to its deceits!",
      "delayTicks": 40,
      "charSpeedTicks": 1,
      "letterSound": "fractured_utils:dialog.blip_high",
      "letterSoundPitchMin": 1.1,
      "letterSoundPitchMax": 1.3,
      "waitForInput": true,
      "useCamera": false
    }
  ]
}
```

---

## 3. Property Reference (`DialogLine`)

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `speaker` | string | `""` | Character name displayed above the dialog box. Supports `§` color codes. |
| `text` | string | `""` | The dialogue string to reveal. Supports linebreaks (`\n`) and formatting. |
| `delayTicks` | int | `40` | Duration to display the completed line if `waitForInput` is false ($20\text{ ticks} = 1\text{ s}$). |
| `sound` | string | `""` | One-shot sound event played once at the start of the line (e.g. voiceover line). |
| `volume` | float | `1.0` | Sound volume multiplier. |
| `pitch` | float | `1.0` | Sound pitch multiplier. |
| `charSpeedTicks` | int | `1` | Ticks per letter revealed ($1 = 20\text{ chars/sec}$, $2 = 10\text{ chars/sec}$). $0$ displays instantly. |
| `letterSound` | string | `""` | Sound event played per character blip. See sound list below. |
| `letterSoundPitchMin` | float | `0.8` | Lower pitch bound for randomized letter blips. |
| `letterSoundPitchMax` | float | `1.2` | Upper pitch bound for randomized letter blips. |
| `waitForInput` | boolean | `true` | If `true`, halts until the player presses the Dialog Advance key (`E`). |
| `useCamera` | boolean | `false` | If `true`, overrides the player's camera position and angle during this line. |
| `cameraX`, `Y`, `Z` | double | `0.0` | World coordinates for the camera position. |
| `cameraYaw`, `Pitch`| float | `0.0` | Camera orientation angles in degrees. |
| `cameraFov` | double | `70.0` | Custom field-of-view angle (zoom). |

---

## 4. Built-in Voice Blip Sounds

Fractured Utils includes 8 built-in letter blip sounds for unique vocal personalities:

| Sound Identifier | Recommended Character Archetype |
| :--- | :--- |
| `fractured_utils:dialog.blip_default` | Neutral narrator, standard NPC dialogue. |
| `fractured_utils:dialog.blip_sans` | Low, relaxed, comedic character voices. |
| `fractured_utils:dialog.blip_papyrus` | Loud, eccentric, or energetic character voices. |
| `fractured_utils:dialog.blip_monster` | Deep, ominous demons, bosses, or beasts. |
| `fractured_utils:dialog.blip_robot` | Synthesizers, androids, terminals, and AI units. |
| `fractured_utils:dialog.blip_typing` | Mechanical typewriter, teletype, or terminal consoles. |
| `fractured_utils:dialog.blip_high` | Fairies, young characters, or lighthearted companions. |
| `fractured_utils:dialog.blip_low` | Solemn, elder, or armored characters. |

---

## 5. Player Input Locking & Cinematic Camera

When a dialog begins:
- **Movement Lock**: Player movement vectors are zeroed (`player.setDeltaMovement(Vec3.ZERO)`), and attack/interact events are canceled (`PlayerInteractEvent`, `LivingAttackEvent`).
- **Focus Camera**: If `useCamera` is enabled on the line, the client switches to `CustomCameraManager`, smoothly positioning the camera at the speaker's face.
- **Safety**: While locked in dialog, players take no environmental or mob damage. If the player disconnects, dialog state cleans up cleanly.

---

## 6. Developer API & Java Usage

### Triggering Dialog in Java
```java
import net.dandare21.fracturedutils.dialog.DialogManager;
import net.minecraft.server.level.ServerPlayer;
import java.util.Collections;

// Start dialog for all players on the server
DialogManager.getInstance().startSequence("herald_confrontation.json", null);

// Start dialog specifically for one player
ServerPlayer player = ...;
DialogManager.getInstance().startSequence("herald_confrontation.json", Collections.singletonList(player));
```

### Skipping and Halting Dialogs
```java
// Advance past the current active line
DialogManager.getInstance().skipCurrentLine(server);

// Stop and clear all running dialogs immediately
DialogManager.getInstance().stopAllSequences(server);
```

---

## 7. Command Reference

All commands require OP Permission Level 2:

| Command | Usage | Description |
| :--- | :--- | :--- |
| `run` | `/dialog run <file_name> [targets]` | Starts a dialog sequence for all or targeted players. |
| `skip` | `/dialog skip` | Immediately completes current text typewriter or advances to next line. |
| `next` | `/dialog next` | Alias for `/dialog skip`. |
| `stop` | `/dialog stop` | Halts and dismisses all active dialogs for all players. |
| `list` | `/dialog list` | Lists all JSON dialog sequence files located in `config/dialog_sequences`. |
| `ui` | `/dialog ui` | Opens the in-game dialog editor screen. |

### Client Controls
- **Advance Key**: Press `E` (rebindable in *Options > Controls > Key Binds > Fractured Utils > Advance Dialog*).

