# Cinematic Screen Effects

Fractured Utils includes a post-processing and overlay screen effect pipeline. These effects provide visual feedback for heavy impacts, boss transitions, cinematic moments, and trauma feedback using custom GLSL shaders and matrix transformations.

---

## 1. Architecture Overview

```
                      ┌───────────────────────────────────────────┐
                      │    ScreenEffectManager (Server & Client)  │
                      └─────────────────────┬─────────────────────┘
                                            │ S2CPlayScreenEffectPacket
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              ClientScreenEffectHandler                                 │
│  • RenderGuiOverlayEvent.Pre Hook (Layered over game buffer before HUD)                │
│                                                                                        │
│   ┌───────────────────────────┐                     ┌───────────────────────────────┐  │
│   │    MangaDrawImpactRenderer│                     │       ScreenShakeEffect       │  │
│   │  • GLSL Post-Shader       │                     │  • Perlin noise / trauma      │  │
│   │  • Radiating speed lines  │                     │  • Viewport X, Y, Roll jitter │  │
│   │  • Duotone color palette  │                     │  • Exponential decay          │  │
│   └───────────────────────────┘                     └───────────────────────────────┘  │
│   ┌───────────────────────────┐                     ┌───────────────────────────────┐  │
│   │    CinematicBarsEffect    │                     │      InvertColorsEffect       │  │
│   │  • Anamorphic letterbox   │                     │  • Fullscreen RGB inversion   │  │
│   │  • Smooth ease transitions│                     │  • Flash & decay pulse        │  │
│   └───────────────────────────┘                     └───────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Effect Catalog

### 1. Manga Draw Impact Frame (`impact_frame`)
- **Visual**: High-contrast anime/manga impact flash with dynamic radiating speed lines and duotone color fills.
- **Technology**: Driven by custom shaders `impact.fsh` and `impact_draw.fsh` with `MangaDrawImpactRenderer`.
- **Customization**:
  - `durationMs`: Total duration (typically 150ms – 400ms for punchy impacts).
  - `primaryColor`: Color of speedlines / background (e.g. `0xFFFFFFFF` white or `0xFFFF0055` crimson).
  - `secondaryColor`: Contrast color (e.g. `0xFF000000` deep black).
  - `lineCount`: Density of radiating speed lines.

### 2. Screen Shake (`shake`)
- **Visual**: Directional viewport jitter simulating violent explosions, boss footfalls, or heavy hits.
- **Technology**: Mathematical trauma decay. Instead of linear shake, trauma decays quadratically:
  $$\text{Offset} = \text{Trauma}^2 \cdot \text{Noise}(t)$$
  This creates intense initial vibration that settles naturally into subtle tremors.
- **Customization**:
  - `durationMs`: Duration of the shake.
  - `intensity`: Maximum displacement amplitude in pixels.

### 3. Cinematic Letterbox Bars (`cinematic_bars`)
- **Visual**: Anamorphic widescreen cinema bars that slide down from the top and up from the bottom of the screen.
- **Customization**:
  - `durationMs`: Total duration the letterbox stays active.
  - `transitionInMs`: Slide-in ease duration.
  - `transitionOutMs`: Slide-out ease duration.
  - `barHeightRatio`: Fraction of screen height occupied by bars (default $0.125 = 12.5\%$).
  - `color`: Hex color of bars (default `0xFF000000` black).

### 4. Invert Colors (`invert`)
- **Visual**: Reverses screen RGB values ($1.0 - \text{RGB}$) for an eerie, dimension-shifting negative flash.
- **Customization**: `durationMs`.

### 5. Strobe & Hue Shift (`strobe`, `hue_shift`)
- **Visual**: Rapid rhythmic brightness pulsing or smooth cyclical color wheel rotation.

---

## 3. Developer API & Java Usage

### Triggering Screen Effects in Java
```java
import net.dandare21.fracturedutils.screeneffect.ScreenEffectManager;
import net.dandare21.fracturedutils.screeneffect.ScreenEffects;

// Play Anime Impact Frame on targeted players
ScreenEffectManager.playEffect(
    targetedPlayers,
    ScreenEffects.impactFrame(
        250,        // Duration (ms)
        0xFFFFFFFF, // Primary color (white)
        0xFF000000, // Secondary color (black)
        35          // Number of speed lines
    )
);

// Play heavy Screen Shake
ScreenEffectManager.playEffect(
    player,
    ScreenEffects.shake(600, 3.5f) // 600ms, intensity 3.5
);

// Trigger Cinematic Bars with smooth 500ms transitions
ScreenEffectManager.playEffect(
    server.getPlayerList().getPlayers(),
    ScreenEffects.cinematicBars(5000, 500, 500, 0.125f, 0xFF000000)
);

// Stop all active screen effects
ScreenEffectManager.stopAllEffects(player);
```

---

## 4. Command Reference

All commands require OP Permission Level 2:

| Command | Usage | Description |
| :--- | :--- | :--- |
| `play impact_frame` | `/screeneffect play impact_frame [durationMs] [primaryColor] [secondaryColor] [targets]` | Triggers manga speedline impact flash. |
| `play shake` | `/screeneffect play shake [durationMs] [intensity]` | Triggers trauma-based camera shake. |
| `play invert` | `/screeneffect play invert [durationMs]` | Triggers fullscreen color inversion. |
| `play cinematic_bars`| `/screeneffect play cinematic_bars [durationMs] [inMs] [outMs] [targets]` | Triggers widescreen cinematic letterboxing. |
| `stop` | `/screeneffect stop [targets]` | Halts and clears all active screen effects immediately. |

### Color Argument Syntax
Colors can be specified as hexadecimal strings (e.g. `0xFFFFFFFF`, `#FF0055`, `0x00E5FF`).

### Example In-Game Commands:
```mcfunction
# 200ms anime speedline impact flash
/screeneffect play impact_frame 200 0xFFFFFFFF 0xFF000000 @a

# Dramatic cinematic bars for 8 seconds with 1s fade in/out
/screeneffect play cinematic_bars 8000 1000 1000 @a

# Violent screen shake for 400ms
/screeneffect play shake 400 4.0
```

