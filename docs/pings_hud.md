# Tactical HUD Pings & World Markers

The **HUD Ping System** (also known as *Global Markers*) provides Apex Legends / RPG style 3D tactical markers projected onto player screens. Pings show labels, custom colors, live distance calculations, and an **off-screen edge clamping algorithm** that tracks objectives even when looking away.

---

## 1. Architecture Overview

```
                      ┌───────────────────────────────────────────┐
                      │    PingManager (Server Registry & Sync)   │
                      │  • HudPing(name, label, x, y, z, color)   │
                      └─────────────────────┬─────────────────────┘
                                            │ S2CSyncPingsPacket
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   ClientPingRenderer                                   │
│                                                                                        │
│   ┌───────────────────────────────┐           ┌─────────────────────────────────────┐  │
│   │     3D ──► 2D Screen Matrix   │           │     Off-Screen Edge Clamping        │  │
│   │  • ModelView & Projection     │           │  • Angle vector from screen center  │  │
│   │  • Distance readout (e.g. 42m)│           │  • Clamped to screen boundary box   │  │
│   │  • Scaled by distance         │           │  • Edge directional arrow pointer   │  │
│   └───────────────────────────────┘           └─────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Key Features

### Raycast Auto-Placement
When targeting an in-world block or structure, players do not need to look up coordinates. Running `/ping add <name>` automatically casts a ray up to 120 blocks along the crosshair and places the marker exactly on the targeted surface.

### Real-Time Distance Readout
Markers display dynamic metric distance readouts beneath their labels:
$$\text{Distance} = \sqrt{(x - x_p)^2 + (y - y_p)^2 + (z - z_p)^2}$$
- Values are formatted as `42m` or `1.2km`.
- Markers smoothly scale so distant objectives remain legible without occluding foreground gameplay.

### Off-Screen Edge Clamping
When a player turns away from a ping, traditional markers disappear off-screen. Fractured Utils calculates the vector from the center of the viewport to the target and **clamps the marker along the screen border**:
- Markers slide around the edges of the monitor indicating the direction to turn.
- A small directional notch/arrow indicates whether to look left, right, up, or behind.

### Dimension-Aware
Pings are tied to specific dimensions (Overworld, Nether, End, or custom mod dimensions) and will only render when the player is in the matching dimension.

---

## 3. Developer API & Java Usage

### Adding a Ping Programmatically
```java
import net.dandare21.fracturedutils.ping.HudPing;
import net.dandare21.fracturedutils.ping.PingManager;

// Create marker with cyan color (#00E5FF)
HudPing ping = new HudPing(
    "altar_objective",     // Unique name
    "Abyssal Altar",       // Display label
    120.5, 64.0, -350.2,   // Coordinates
    "minecraft:overworld", // Dimension
    0xFF00E5FF,            // Hex ARGB color
    "default",             // Icon style
    "Server"               // Creator name
);

// Add to server manager (automatically syncs to all clients)
PingManager.getInstance().addPing(server, ping);
```

### Removing Pings
```java
// Remove single ping by name
PingManager.getInstance().removePing(server, "altar_objective");

// Clear all active pings
PingManager.getInstance().clearPings(server);
```

---

## 4. Command Reference

All commands require OP Permission Level 2:

| Command | Usage | Description |
| :--- | :--- | :--- |
| `add` (Raycast) | `/ping add <name> [label]` | Raycasts crosshair (up to 120 blocks) to place a marker. |
| `add` (Explicit)| `/ping add <name> <pos> [label] [colorHex]` | Places a marker at explicit world coordinates. |
| `remove` | `/ping remove <name>` | Deletes the specified marker. |
| `clear` | `/ping clear` | Removes all world markers across all dimensions. |
| `list` | `/ping list` | Lists all active pings with coordinates and dimensions. |

*Command Aliases: `/gmarker`, `/hudmarker`, `/fu_ping`*

### Examples
```mcfunction
# Ping where you are currently looking with a custom label
/ping add boss_gate "Dungeon Entrance"

# Ping specific coordinates in gold (#FFD700)
/ping add treasure 540.5 32.0 1200.5 "Hidden Vault" #FFD700

# Remove the marker
/ping remove boss_gate
```

