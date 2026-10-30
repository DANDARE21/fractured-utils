# Modular Boss Health Bar System

The **Boss Health Bar System** provides customizable, multi-entity health bar rendering on client screens. It replaces Minecraft's basic single-entity boss bar with support for multi-entity pooling (e.g. duo/trio bosses, multi-part monsters), custom textures, phase notches, damage ghost bars, and an extensible style rendering engine.

---

## 1. Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│                        BossHealthBar (Server)                          │
│  • Linked Entity UUIDs (Multi-Entity Pooling: Σ Current HP / Σ Max HP) │
│  • Style: VANILLA, FRACTURED, CUSTOM_TEXTURE                           │
│  • Features: PhaseMarkersFeature (Notches: 0.75, 0.50, 0.25)           │
│  • Ghost Bar: Trailing damage decay animation                          │
│  • Text Display: NONE, VALUE, PERCENT, BOTH                            │
│  • Visibility, Autohide, Autodelete, Radius & Player Scoping           │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ S2CSyncBossBarsPacket
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      ClientBossBarManager (Client)                     │
│                                                                        │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │                     BossBarStyleRegistry                       │   │
│   │                                                                │   │
│   │  • VanillaBossBarStyleRenderer        (Vanilla overlays)       │   │
│   │  • FracturedBossBarStyleRenderer      (Stylized gradient)      │   │
│   │  • CustomTextureBossBarStyleRenderer  (Custom ResourceLocation)│   │
│   │  • Developer Custom Renderers         (IBossBarStyleRenderer)  │   │
│   └────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Key Features

### Multi-Entity Health Pooling (`/bosshealthbar link`)
Link any number of entities to a single boss bar. The health bar automatically aggregates their health:
$$\text{Bar Percent} = \frac{\sum_{i=1}^{n} \text{entity}_i.\text{getHealth()}}{\sum_{i=1}^{n} \text{entity}_i.\text{getMaxHealth()}}$$
- Ideal for twin bosses (e.g., Ornstein & Smough style encounters), multi-part bosses (head + hands), or wave defense encounters.
- If one entity dies or despawns, the bar smoothly recalculates.
- Automatic cleanup when all linked entities die (if `autodelete` is enabled).

### Phase Markers & Notches
Add visible tick marks indicating phase shifts or health threshold boundaries:
- Added via `/bosshealthbar phase <id> add <0.0-1.0>`.
- Renders glowing notches along the bar so players know exactly when a boss will transition or change mechanics.

### Ghost Bar (Damage Lag Effect)
When a boss takes damage, a secondary trailing bar lingers and smoothly glides downward:
- Gives high-impact visual weight to player damage combos.
- Enabled via `/bosshealthbar set <id> ghost_bar true`.

### Flexible Text Displays
Configure what text appears over the bar:
- `none`: Clean bar without numerical text.
- `value`: Current and max HP (e.g., `1,450 / 2,000`).
- `percent`: Percentage remaining (e.g., `72.5%`).
- `both`: Value and percentage combined.

---

## 3. Developer API & Custom Style Renderers

### Programmatic Creation & Control
```java
import net.dandare21.fracturedutils.bossbar.BossHealthBar;
import net.dandare21.fracturedutils.bossbar.BossHealthBarManager;
import net.minecraft.network.chat.Component;

// Create and register a custom boss bar
BossHealthBar bar = BossHealthBarManager.getInstance().createBossBar(
    "void_sovereign",
    Component.literal("§d§lVoid Sovereign")
);

// Configure styling and behavior
bar.setStyle("FRACTURED");
bar.setGhostBarEnabled(true);
bar.setTextDisplayMode(BossHealthBar.TextDisplayMode.BOTH);
bar.setRange(64.0); // Only visible within 64 blocks

// Link living entities
bar.linkEntity(bossEntity);
bar.linkEntity(leftHandEntity);
bar.linkEntity(rightHandEntity);

// Add phase marker notches at 66% and 33%
bar.addPhaseMarker(0.66f);
bar.addPhaseMarker(0.33f);
```

### Implementing a Custom Style Renderer
Developers can register their own renderers using `IBossBarStyleRenderer`:
```java
import net.dandare21.fracturedutils.bossbar.client.style.IBossBarStyleRenderer;
import net.dandare21.fracturedutils.bossbar.client.style.BossBarStyleRegistry;

public class CyberpunkBossBarStyleRenderer implements IBossBarStyleRenderer {

    @Override
    public void render(GuiGraphics graphics, ClientBossBarData bar, int x, int y, int width, int height, float partialTick) {
        // Custom background draw
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xCC111111);

        // Render ghost bar layer
        float ghostPercent = bar.getSmoothGhostPercent();
        int ghostWidth = (int) (width * ghostPercent);
        graphics.fill(x, y, x + ghostWidth, y + height, 0xFFFF5555);

        // Render primary health bar layer
        float currentPercent = bar.getSmoothHealthPercent();
        int fillWidth = (int) (width * currentPercent);
        graphics.fillGradient(x, y, x + fillWidth, y + height, 0xFF00E5FF, 0xFF0077FF);

        // Render phase marker ticks
        for (float notch : bar.getPhaseMarkers()) {
            int notchX = x + (int) (width * notch);
            graphics.fill(notchX - 1, y - 1, notchX + 1, y + height + 1, 0xFFFFFFFF);
        }
    }
}

// Registration in client setup
BossBarStyleRegistry.register("CYBERPUNK", new CyberpunkBossBarStyleRenderer());
```

---

## 4. Command Reference

All commands require OP Permission Level 2:

| Command | Usage | Description |
| :--- | :--- | :--- |
| `create` | `/bosshealthbar create <id> <name>` | Creates a new bar with the given display name. |
| `remove` | `/bosshealthbar remove <id>` | Deletes the specified boss health bar. |
| `list` | `/bosshealthbar list` | Lists all active boss bars on the server. |
| `show` / `hide` | `/bosshealthbar show <id>` | Toggles visibility without deleting the bar. |
| `link` | `/bosshealthbar link <id> <entities>` | Links one or more entities to pool their health. |
| `unlink` | `/bosshealthbar unlink <id> [entities]` | Unlinks entities from the specified bar. |
| `set style` | `/bosshealthbar set <id> style <VANILLA\|FRACTURED\|CUSTOM_TEXTURE>` | Changes the rendering style. |
| `set texture` | `/bosshealthbar set <id> texture <resource_location>` | Sets the custom bar texture for `CUSTOM_TEXTURE`. |
| `set dimensions` | `/bosshealthbar set <id> dimensions <width> <height>` | Overrides width and height in pixels. |
| `set ghost_bar` | `/bosshealthbar set <id> ghost_bar <true\|false>` | Toggles trailing damage decay bar. |
| `set text_display`| `/bosshealthbar set <id> text_display <none\|value\|percent\|both>` | Configures HP text layout. |
| `set autohide` | `/bosshealthbar set <id> autohide <true\|false>` | Automatically hides when linked entities despawn. |
| `set autodelete`| `/bosshealthbar set <id> autodelete <true\|false>` | Automatically deletes when linked entities die. |
| `set range` | `/bosshealthbar set <id> range <radius>` | Scopes visibility to players within $N$ blocks. |
| `phase add` | `/bosshealthbar phase <id> add <0.0-1.0>` | Adds a phase marker notch at that percent. |
| `phase clear` | `/bosshealthbar phase <id> clear` | Clears all phase marker notches. |
| `info` | `/bosshealthbar info <id>` | Displays full diagnostics and linked entities. |

