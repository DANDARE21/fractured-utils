# Event Orchestrator & Scripting Engine

The **Event Orchestrator** is Fractured Utils' timeline execution and choreography engine. It allows developers, mapmakers, and server administrators to orchestrate complex events, boss fights, cutscenes, and quest objectives using modular JSON sequences or Java APIs.

---

## 1. Architecture Overview

```
                      ┌───────────────────────────────────────────┐
                      │    Sequence JSON File (config/orchestrator)│
                      └─────────────────────┬─────────────────────┘
                                            │ Deserialized via Gson
                                            ▼
                      ┌───────────────────────────────────────────┐
                      │            SequenceInstance               │
                      │  • State: RUNNING, WAITING, PAUSED, etc.  │
                      │  • Target: Global (@a) or Specific Player │
                      │  • Action Cursor Index                    │
                      └─────────────────────┬─────────────────────┘
                                            │ tick(MinecraftServer)
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   Action Execution Layer                               │
│                                                                                        │
│   ┌────────────────────────┐  ┌─────────────────────────┐  ┌────────────────────────┐  │
│   │     Flow Control       │  │     World & Entities    │  │    Audio & Cutscenes   │  │
│   │  • DelayAction         │  │  • CommandAction        │  │  • PlayMusicSequence   │  │
│   │  • AwaitTriggerAction  │  │  • ExecutePuppetAction  │  │  • WaitForMusic        │  │
│   │  • WaitUntilAction     │  │  • PuppetMoveToAction   │  │  • CheckpointAction    │  │
│   │  • ForkSequenceAction  │  │  • PuppetLookAtAction   │  │  • NewObjectiveAction  │  │
│   │  • RunSequenceAction   │  │  • PuppetSuppressAction │  │  • EndObjectiveAction  │  │
│   └────────────────────────┘  └─────────────────────────┘  └────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

Sequences execute step-by-step on the server tick thread. When an action completes with `ActionResult.CONTINUE`, the orchestrator immediately evaluates the next action. If an action returns `ActionResult.WAIT` (such as `DelayAction` or `AwaitTriggerAction`), execution yields until conditions are met.

---

## 2. Sequence File Format (`JSON`)

Sequences are stored in `.minecraft/config/orchestrator_sequences/<sequence_name>.json` (or `<server_root>/config/orchestrator_sequences/`).

### Example Sequence: Boss Intro Sequence

```json
{
  "name": "boss_intro.json",
  "actions": [
    {
      "type": "checkpoint",
      "x": 120.5,
      "y": 64.0,
      "z": -350.2,
      "yaw": 0.0,
      "pitch": 0.0,
      "label": "Boss Arena Entrance",
      "targetSelector": "@a"
    },
    {
      "type": "new_objective",
      "name": "Defeat the Void Herald",
      "description": "Survive the incoming abyssal onslaught.",
      "showActiveWait": true
    },
    {
      "type": "command",
      "command": "screeneffect play cinematic_bars 5000 1000 1000 @a"
    },
    {
      "type": "delay",
      "ticks": 40
    },
    {
      "type": "play_music_sequence",
      "sequenceFile": "void_herald_theme.json"
    },
    {
      "type": "await_trigger",
      "triggerId": "door_opened"
    },
    {
      "type": "puppet_move_to",
      "target": "@e[type=fractured_utils:void_herald,limit=1]",
      "x": 120.5,
      "y": 64.0,
      "z": -330.0,
      "speed": 1.2
    },
    {
      "type": "puppet_action",
      "target": "@e[type=fractured_utils:void_herald,limit=1]",
      "actionId": "fractured_utils:leap_slam",
      "params": {
        "target": "@p",
        "windup": 25,
        "jump": 15,
        "recovery": 20
      }
    }
  ]
}
```

---

## 3. Complete Action Reference

Every action extends `OrchestratorAction` and is parsed via `ActionAdapter`.

### Flow Control Actions

| Action Type | Key Properties | Description |
| :--- | :--- | :--- |
| `delay` | `ticks` (int) | Pauses execution for the specified number of ticks ($20\text{ ticks} = 1\text{ s}$). |
| `await_trigger` | `triggerId` (string) | Suspends execution until `/orchestrator resume <triggerId>` is invoked or an operator triggers it via HUD/keybind. |
| `wait_until` | `condition` (string), `timeoutTicks` (int) | Periodically polls a condition until true or timed out. |
| `fork_sequence` | `sequenceFile` (string), `targets` (string) | Branches an asynchronous sub-sequence running concurrently with the current sequence. |
| `run_sequence` | `sequenceFile` (string), `targets` (string) | Runs another sequence synchronously as a subroutine, resuming the parent when finished. |
| `stall_parent` | *(none)* | Explicitly halts the calling parent sequence from within a child sequence. |
| `resume_parent` | *(none)* | Explicitly unblocks the calling parent sequence when a sub-task completes. |

### World & Boss Actions

| Action Type | Key Properties | Description |
| :--- | :--- | :--- |
| `command` | `command` (string) | Executes a Minecraft server console command with full permission level 4. |
| `checkpoint` | `x`, `y`, `z`, `yaw`, `pitch`, `label`, `targetSelector` | Sets the active checkpoint for players matching `targetSelector`. Overrides spawn point and sets up downed rollback. |
| `new_objective` | `name` (string), `description` (string), `showActiveWait` (bool) | Displays or updates the dynamic RPG Objective Tracker HUD. |
| `end_objective` | *(none)* | Completes or removes the active objective tracker on targeted players. |
| `puppet_action` | `target` (selector), `actionId` (ResourceLocation), `params` (map) | Orders a puppet mob with `IPuppetHandler` to execute a registered FSM attack. |
| `puppet_move_to` | `target` (selector), `x`, `y`, `z`, `speed` (float) | Overrides entity navigation and moves puppet directly to target coordinates. |
| `puppet_look_at` | `target` (selector), `lookTarget` (selector or coordinates) | Forces puppet to rotate and look towards an entity or coordinate point. |
| `puppet_stop` | `target` (selector) | Halts puppet movement, cancels active attacks, and clears navigation goals. |
| `puppet_suppress` | `target` (selector), `suppress` (bool) | Toggles Forge AI goal flag suppression (`Goal.Flag.MOVE`, `LOOK`, etc.) on/off. |

### Audio & Beat Actions

| Action Type | Key Properties | Description |
| :--- | :--- | :--- |
| `play_music_sequence` | `sequenceFile` (string) | Starts a multi-track music sequence with millisecond cues and volume fades. |
| `wait_for_music` | `channelId` (string, optional) | Waits until the designated music track/sequence has completed playback. |

---

## 4. Developer API & Java Usage

### Starting Sequences Programmatically
To trigger a sequence from another mod, event, or trigger:
```java
import net.dandare21.fracturedutils.orchestrator.OrchestratorManager;

// Start sequence globally for all players
boolean startedGlobal = OrchestratorManager.getInstance().startSequence("event_intro.json", null, 0);

// Start sequence targeted specifically to a player
ServerPlayer player = ...;
boolean startedPlayer = OrchestratorManager.getInstance().startSequence("player_trial.json", player.getScoreboardName(), 0);
```

### Resuming Triggers in Java
When an in-game event occurs (e.g., a lever pulled, a boss reaching 50% HP):
```java
// Resume any sequence waiting on trigger "altar_activated"
OrchestratorManager.getInstance().resumeTrigger(null, "altar_activated");
```

### Implementing a Custom Action
Developers can extend `OrchestratorAction`:
```java
public class CustomSpawnWaveAction extends OrchestratorAction {
    private String waveType;
    private int count;

    @Override
    public ActionResult execute(MinecraftServer server, SequenceInstance instance) {
        ServerLevel level = server.overworld();
        // Spawn custom entities...
        return ActionResult.CONTINUE; // or ActionResult.WAIT if asynchronous
    }
}
```

---

## 5. In-Game Sequencer UI (`/orchestrator ui`)

Running `/orchestrator ui` in-game opens the visual Cyberpunk sequence editor:
1. **Left Sequence Panel**: View, create, rename, and delete JSON sequences in real time.
2. **Central Action Timeline**: Displays ordered list of actions with colored icons representing action categories (delay, puppet, sound, objective).
3. **Modal Editors**: Double-clicking or clicking "Edit" opens a parameter modal for fine-tuning coordinates, timing, and targets without touching JSON files manually.
4. **Save & Reload**: Changes are immediately written to disk on the server and broadcast to clients.

---

## 6. Live Operator Monitor HUD (`OpMonitor`)

When an operator with permission level 2+ joins or runs a sequence:
- A draggable telemetry HUD appears showing:
  - Active sequence name
  - Execution state (`RUNNING`, `WAITING`, `PAUSED`)
  - Current action index / total actions
  - Elapsed execution time
  - Interactive **Resume** button
- **Quick Resume Keybind**: Pressing `\` (`key.fractured_utils.operator_resume`) triggers the current pending wait trigger without opening the console.
- **Configuring HUD Position**: Run `/config` or edit `fracturedutils-client.json` to customize position, scaling, and opacity.

---

## 7. Command Reference

All commands require OP Permission Level 2:

| Command | Usage | Description |
| :--- | :--- | :--- |
| `/orchestrator run <file> [start_index]` | `/orchestrator run boss.json 1` | Runs a sequence from step 1 for all players. |
| `/orchestrator run <file> <targets> [start_index]` | `/orchestrator run quest.json @p 0` | Runs a sequence scoped to target players. |
| `/orchestrator resume [trigger_id] [targets]` | `/orchestrator resume door_open` | Unblocks paused sequences waiting on `trigger_id`. |
| `/orchestrator pause [file] [targets]` | `/orchestrator pause boss.json` | Temporarily suspends sequence execution. |
| `/orchestrator cancel [file] [targets]` | `/orchestrator cancel boss.json` | Terminates and cleans up the active sequence. |
| `/orchestrator checkpoint restore` | `/orchestrator checkpoint restore` | Restores the active checkpoint, despawns puppets, and rewinds state. |
| `/orchestrator ui` | `/orchestrator ui` | Opens the visual sequence editor GUI. |

