# Checkpoints, Downed State & Revive Mechanics

The **Checkpoint and Downed System** provides MMO-style death prevention and team coordination mechanics for multiplayer dungeons, raids, and events. Instead of permanent player death and inventory loss, players enter a **Downed State**, can be revived by teammates, or trigger an automated **Team Wipe Rollback** to the last active checkpoint.

---

## 1. Architecture Overview

```
                      Player Takes Lethal Damage (Health <= 0)
                                    │
                       Is Player in Active Checkpoint?
                                   / \
                                  /   \
                             Yes /     \ No
                                /       \
                               ▼         ▼
┌────────────────────────────────────────┐  Vanilla Death Screen & Respawn
│              Player Enters DOWNED State │
│  • Death event cancelled               │
│  • Health clamped to 1.0 HP            │
│  • Slowness 255 & Weakness 255 applied │
│  • Nametag displays Downed Icon (\uE001│
│  • Client opens DownedSpectateScreen   │
└───────────────────┬────────────────────┘
                    │
                    ▼
          Are ALL Teammates Downed?
                    / \
               No  /   \  Yes
                  /     \
                 ▼       ▼
┌────────────────────────┐  ┌────────────────────────────────────────┐
│ Teammate Revives Player│  │            TEAM WIPE TRIGGERED          │
│ • Holds Right-Click    │  │ • TeamWipeScreen displayed (3 seconds) │
│ • 3-second progress bar│  │ • Despawns active boss puppets         │
│ • Rising audio pitch   │  │ • Stops active music sequences         │
│ • Player restored!     │  │ • Teleports team to checkpoint spawn   │
└────────────────────────┘  │ • Rewinds sequence to checkpoint index │
                            └────────────────────────────────────────┘
```

---

## 2. Checkpoint Management

Checkpoints can be created via Orchestrator sequences or Java API.

### Checkpoint Properties
- **Coordinates (`x, y, z, yaw, pitch`)**: Where players respawn upon a team wipe.
- **Label**: User-facing description (e.g. *"Phase 2: Inner Sanctum"*).
- **Target Selector (`targetSelector`)**: Defines which players are bound to this checkpoint (e.g. `@a`, `@p`, or `@a[tag=raid_party]`). Only matching players receive downed protection.
- **Sequence Index**: The step index in the Orchestrator sequence to rewind to upon a wipe.

---

## 3. The Downed State

When a participating player's health drops to zero:
1. **Death Cancellation**: `LivingHurtEvent` and `LivingDeathEvent` are canceled.
2. **Mobility Penalty**: Severe slowness and jump restriction prevents moving or jumping away.
3. **World Nametag Icon**: The player's nametag renders a glowing cross icon (`\uE001` via `fractured_utils:icons` font) visible to teammates through walls.
4. **Downed Spectate Screen (`DownedSpectateScreen`)**:
   - The downed player's view switches to a spectate overlay.
   - Shows active status of alive teammates and a countdown/waiting indicator.

---

## 4. Teammate Reviving

Teammates can rescue downed players through physical interaction:
- **How to Revive**: Walk up to the downed player and **hold Right-Click** with an empty hand or item.
- **Progress Tracking**: Requires 3.0 seconds (60 ticks) of continuous interaction.
- **Sensory Feedback**:
  - A progress ring and revive meter appears on the reviver's screen.
  - Rising audio chime plays at each interval ($0.8 \to 1.6$ pitch).
  - Glowing marker particles (`ModParticles.MARKER_PARTICLE`) spray from the downed player.
- **Completion**: The downed player is cleared of debuffs, restored to healthy status, and granted brief invulnerability.

---

## 5. Team Wipe & Automated Rollback

If every player matching the checkpoint's target selector becomes downed simultaneously:
1. **Team Wipe Screen (`TeamWipeScreen`)**:
   - All players' screens display a dark red vignette with bold text: *"TEAM WIPED"*.
   - A somber defeat sting plays; all non-master world sounds are muted.
2. **Automated Rollback Engine**:
   - **Entity Despawn**: Any boss puppets or spawned minions tagged to the sequence are cleanly discarded.
   - **Sound Stop**: Active battle music sequences are immediately halted with smooth fadeouts.
   - **Repositioning**: All players are revived, healed, and teleported back to the checkpoint coordinates with matching yaw/pitch orientation.
   - **Orchestrator Rewind**: The active Orchestrator sequence rewinds its execution cursor back to the checkpoint action and restarts from that step.

---

## 6. Developer API & Java Usage

### Setting a Checkpoint in Java
```java
import net.dandare21.fracturedutils.checkpoint.CheckpointManager;
import net.dandare21.fracturedutils.orchestrator.SequenceInstance;

// Set active checkpoint for the whole raid team
CheckpointManager.getInstance().setActiveCheckpoint(
    sequenceInstance,
    3,                  // Action index to rewind to
    120.5, 64.0, -350.2,// Spawn X, Y, Z
    180.0f, 0.0f,       // Yaw, Pitch
    "Sanctum Entrance", // Label
    "@a[tag=raid_team]" // Target selector
);
```

### Checking Player State
```java
UUID playerUUID = player.getUUID();

if (CheckpointManager.getInstance().isPlayerDowned(playerUUID)) {
    // Player is currently downed
}
```

### Manual Rollback Command
To manually trigger a checkpoint restore via console or command block:
```mcfunction
/orchestrator checkpoint restore
```

