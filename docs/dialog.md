# RPG Dialog System

The **RPG Dialog System** brings story-driven, Undertale-style narrative presentation to Minecraft. It features DataPack-driven sequences, typewriter text reveals, natural punctuation pacing, in-line BBCode tags with mid-text effects, customizable voice blips, soft player input locking, a nine-sliced customizable RPG dialog frame, and cinematic camera focus with dynamic entity tracking.

---

## 1. Architecture Overview

```
                      ┌───────────────────────────────────────────┐
                      │    DataPack (data/<namespace>/dialog_...)  │
                      └─────────────────────┬─────────────────────┘
                                            │ /reload (AddReloadListenerEvent)
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               DialogSequenceInstance                                   │
│  • ResourceLocation Identifier & Codec<DialogSequence>                                │
│  • Dynamic Entity Resolution (entity tags, selectors, bone offsets)                    │
│  • Soft Input Lock (Slowness 255 + JumpBoost 128 - preserves physics & gravity)        │
│  • Client Line Dispatch & Player Consensus Management                                  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ S2CDialogDisplayPacket
                                    ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 Client Presentation                                    │
│                                                                                        │
│  ┌─────────────────────────────┐                    ┌───────────────────────────────┐  │
│  │       DialogHudOverlay      │                    │     ClientDialogInputLock     │  │
│  │  • 9-Slice DialogStyle      │                    │  • Intercepts WASD & Jump     │  │
│  │  • Punctuation Pacing       │                    │  • Turns player to face NPC   │  │
│  │  • In-line Rich Tags        │                    │  • Forces 3rd Person Model    │  │
│  │  • Mid-text Events/Shakes   │                    │  • Released on Sequence End   │  │
│  └─────────────────────────────┘                    └───────────────────────────────┘  │
│                                 Advance Key (`E`)                                      │
│               Two-Stage Advance: 1. Fast-Forward -> 2. Next Line                       │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Sequence File Format (`JSON`)

Dialog sequences are stored in DataPacks under `data/<namespace>/dialog_sequences/<name>.json` (with fallback support for `config/dialog_sequences/<name>.json`).

### Example: `data/fractured_utils/dialog_sequences/herald_confrontation.json`
```json
{
  "style": "fractured_utils:cyberpunk",
  "lines": [
    {
      "speaker": "§5Void Herald",
      "text": "Did you truly believe your light could penetrate this depth? {shake_screen:3.0:20}",
      "delayTicks": 60,
      "charSpeedTicks": 1,
      "letterSound": "fractured_utils:dialog.blip_monster",
      "letterSoundPitchMin": 0.6,
      "letterSoundPitchMax": 0.9,
      "waitForInput": true,
      "cameraTarget": {
        "type": "entity_tag",
        "tag": "herald_boss",
        "bone": "head",
        "offset": [0.0, 0.5, 2.5],
        "lookAtTarget": true
      },
      "interpolationTicks": 20,
      "easing": "EASE_IN_OUT_CUBIC"
    },
    {
      "speaker": "§6Archivist",
      "text": "Hold fast! {pause:15} Do not listen to its {shake}deceits{/shake}!",
      "delayTicks": 40,
      "charSpeedTicks": 1,
      "letterSound": "fractured_utils:dialog.blip_high",
      "letterSoundPitchMin": 1.1,
      "letterSoundPitchMax": 1.3,
      "waitForInput": true,
      "speakerSelector": "@e[type=minecraft:villager,limit=1]",
      "interpolationTicks": 15
    }
  ]
}
```

---

## 3. Property Reference (`DialogLine`)

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `speaker` | string | `""` | Character name displayed above the dialog box. Supports `§` color codes. |
| `text` | string | `""` | Dialogue text to reveal. Supports rich inline BBCode tags (`{pause}`, `{shake}`, etc.). |
| `delayTicks` | int | `40` | Duration to display the completed line if `waitForInput` is false ($20\text{ ticks} = 1\text{ s}$). |
| `sound` | string | `""` | One-shot sound event played once at the start of the line. |
| `volume` | float | `1.0` | Sound volume multiplier. |
| `pitch` | float | `1.0` | Sound pitch multiplier. |
| `charSpeedTicks` | int | `1` | Ticks per letter revealed ($1 = 20\text{ chars/sec}$, $2 = 10\text{ chars/sec}$, $0 = \text{instant}$). |
| `letterSound` | string | `""` | Sound event played per character blip. |
| `letterSoundPitchMin` | float | `0.8` | Lower pitch bound for randomized letter blips. |
| `letterSoundPitchMax` | float | `1.2` | Upper pitch bound for randomized letter blips. |
| `waitForInput` | boolean | `true` | If `true`, halts until the player presses the Dialog Advance key (`E`). |
| `cameraTarget` | object | `null` | Dynamic camera tracking configuration (see below). |
| `speakerSelector` | string | `null` | Shorthand entity selector (e.g. `@e[tag=boss,limit=1]`) to target as speaker. |
| `interpolationTicks` | int | `20` | Transition duration in ticks for smooth easing into the camera view. |
| `easing` | string | `"EASE_IN_OUT_CUBIC"` | Easing curve (`LINEAR`, `EASE_IN_OUT_CUBIC`, `EASE_IN_OUT_QUAD`, `EASE_OUT_SINE`, etc.). |
| `style` | string | `null` | Style ID override for this line (e.g. `"fractured_utils:retro_undertale"`). |
| `useCamera` | boolean | `false` | Legacy static camera override. |
| `cameraX`, `Y`, `Z` | double | `0.0` | Legacy static world coordinates. |
| `cameraYaw`, `Pitch`| float | `0.0` | Legacy camera orientation angles. |
| `cameraFov` | double | `70.0` | Custom field-of-view angle (zoom). |

### `cameraTarget` Options
- `type`: `"entity_tag"`, `"selector"`, or `"fixed"`.
- `tag`: Scoreboard tag to search for (e.g. `"herald_boss"`).
- `selector`: Target selector string (e.g. `"@e[type=fractured:void_herald,limit=1]"`).
- `bone`: Anchor base height (`"eyes"`, `"head"`, `"chest"`, `"feet"`).
- `offset`: `[x, y, z]` vector offset relative to anchor.
- `lookAtTarget`: `true` to auto-orient pitch/yaw to look at the target entity.

---

## 4. In-line Rich Tags & Narrative Pacing

The typewriter engine includes natural punctuation pacing and custom BBCode tags:

### Natural Punctuation Pacing
- `,` and `;` $\rightarrow$ automatically pauses typing for **+4 ticks**.
- `.`, `!`, `?` $\rightarrow$ automatically pauses typing for **+10 ticks** (ellipses `...` only pause once at the end).

### In-line BBCode Tags
| Tag | Example | Description |
| :--- | :--- | :--- |
| `{pause:N}` | `{pause:15}` | Pauses typewriter for $N$ ticks before typing continues. |
| `{shake}...{/shake}` | `{shake}danger{/shake}` | Shakes letters violently in-place. |
| `{wave}...{/wave}` | `{wave}flowing{/wave}` | Renders characters with a smooth vertical sine wave. |
| `{rainbow}...{/rainbow}` | `{rainbow}fabulous{/rainbow}` | Cycles characters through chromatic rainbow hues. |
| `{glitch}...{/glitch}` | `{glitch}corrupted{/glitch}` | Sporadically offsets and glitches letter colors. |
| `{pulse}...{/pulse}` | `{pulse}charging{/pulse}` | Smoothly pulses letter luminosity. |
| `{color:#HEX}` | `{color:#FF5555}red text{/color}` | Changes text color to any hex or named color. |
| `{shake_screen:intensity:duration}` | `{shake_screen:3.0:20}` | Triggers a camera screen shake when this word is reached. |
| `{sound:id:vol:pitch}` | `{sound:minecraft:entity.lightning_bolt.thunder:1.0:1.2}` | Plays a one-shot sound right on this word. |

---

## 5. Dialog Box Styles & 9-Slicing

Dialog frames support custom textures and nine-slicing via `NineSliceRenderer`.

### Built-in Styles
- `fractured_utils:cyberpunk` (Default): Sleek cyan double border with corner accent notches.
- `fractured_utils:retro_undertale`: Crisp black box with pure white double border.
- `fractured_utils:fantasy_parchment`: Medieval parchment tones with gold accents.
- `fractured_utils:minimal_dark`: Semi-transparent dark glass panel.

Custom styles can be created in DataPacks under `data/<namespace>/dialog_styles/<name>.json`:
```json
{
  "id": "mymod:custom_wood",
  "texture": "mymod:textures/gui/dialog/wood_frame.png",
  "nineSlice": {
    "textureWidth": 64,
    "textureHeight": 64,
    "sliceLeft": 10,
    "sliceTop": 10,
    "sliceRight": 10,
    "sliceBottom": 10
  },
  "defaultTextColor": -1
}
```

---

## 6. Controls & Two-Stage Advance

- **Advance Key**: Press `E` (rebindable in *Options > Controls > Key Binds > Fractured Utils > Advance Dialog*).
- **Two-Stage Advance Flow**:
  1. **First Press**: If text is still typing or paused, instantly reveals all remaining text and triggers inline events.
  2. **Second Press**: When line is fully displayed, advances to the next dialogue line or finishes sequence.

---

## 7. Developer API & Commands

### Commands (Permission Level 2)
| Command | Usage | Description |
| :--- | :--- | :--- |
| `run` | `/dialog run <sequence_id> [targets]` | Starts dialogue by ResourceLocation with autocomplete. |
| `skip` / `next` | `/dialog skip` | Fast-forwards typewriter or advances active line. |
| `stop` | `/dialog stop` | Halts all active dialogs for all players. |
| `list` | `/dialog list` | Lists all loaded dialogue sequence IDs. |
| `ui` | `/dialog ui` | Opens the visual sequence editor. |

### Java API
```java
import net.dandare21.fracturedutils.dialog.DialogManager;
import net.minecraft.resources.ResourceLocation;

// Start dialog by ResourceLocation
DialogManager.getInstance().startSequence(new ResourceLocation("fractured_utils", "herald_confrontation"), targets);
```
