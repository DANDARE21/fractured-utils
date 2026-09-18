# Developer Guide: Boss Puppet Framework v2.0 (Forge 1.20.1)

The **Boss Puppet Framework v2.0** is an enterprise-grade AI orchestration and attack dispatch system for Forge 1.20.1. It replaces legacy intrusive interface inheritance (`IPuppetEntity`), loosely typed `CompoundTag` parameters, and leaky AI goals with **Forge Capabilities**, **DFU Codecs**, **Global Registries**, **Goal Flag Suppression**, and **S2C GeckoLib Animation Synchronization**.

---

## 1. Architecture Overview

```
                      ┌─────────────────────────────────────────┐
                      │    External Sequences / Orchestrator    │
                      │               (JSON File)               │
                      └────────────────────┬────────────────────┘
                                           │ DFU Codec<T>
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │      PuppetActionType<T> Registry       │
                      │          (ModPuppetActions)             │
                      └────────────────────┬────────────────────┘
                                           │ dispatch(type, params)
                                           ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               IPuppetHandler Capability                                │
│                                                                                        │
│  ┌─────────────────────────────────┐        ┌───────────────────────────────────────┐  │
│  │     Robust AI Suppression       │        │       Finite State Machine (FSM)      │  │
│  │  GoalSelector.setControlFlag    │        │         PuppetActionInstance<T>       │  │
│  │  • Flag.MOVE, LOOK, JUMP (false)│        │                                       │  │
│  │  • Flag.TARGET (false)          │        │       WINDUP ───────► ACTIVE          │  │
│  │  • getNavigation().stop()       │        │         ▲                │            │  │
│  │  • setTarget(null)              │        │         │                ▼            │  │
│  │  • Clean restoreAi() on exit    │        │       IDLE  ◄──────  RECOVERY         │  │
│  └─────────────────────────────────┘        └───────────────────┬───────────────────┘  │
└─────────────────────────────────────────────────────────────────┼──────────────────────┘
                                                                  │ onPhaseTransition()
                                                                  ▼
                                              ┌───────────────────────────────────────┐
                                              │      ClientboundPuppetAnimPacket      │
                                              │     (S2C GeckoLib Trigger Packet)     │
                                              └───────────────────┬───────────────────┘
                                                                  │
                                                                  ▼
                                              ┌───────────────────────────────────────┐
                                              │          GeoEntity (GeckoLib)         │
                                              │  geoEntity.triggerAnim(ctrl, anim)    │
                                              └───────────────────────────────────────┘
```

---

## 2. Core Components

| Component | Class / Interface | Responsibility |
| :--- | :--- | :--- |
| **Capability** | [`IPuppetHandler`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/capability/IPuppetHandler.java) | Attached to any `Mob` via `AttachCapabilitiesEvent<Entity>`. Controls FSM lifecycle and goal flag suppression. |
| **Provider** | [`PuppetCapabilityProvider`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/capability/PuppetCapabilityProvider.java) | Exposes thread-safe `LazyOptional<IPuppetHandler>`. |
| **Targeting** | [`ActionTarget`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/target/ActionTarget.java) | Polymorphic target union (`Vec3`, `UUID`, or command selector string) parsed via DFU Codecs. |
| **Action Definition** | [`PuppetActionType<T>`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/fsm/PuppetActionType.java) | Global immutable action blueprint backed by Mojang `Codec<T>`. |
| **Action Parameter** | [`ActionParameter<T>`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/fsm/ActionParameter.java) | Strongly typed parameter descriptor (booleans, numbers, strings, options) detected by the Sequencer. |
| **Action FSM** | [`PuppetActionInstance<T>`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/fsm/PuppetActionInstance.java) | Running action finite state machine (`WINDUP`, `ACTIVE`, `RECOVERY`, `IDLE`). |
| **Registry** | [`ModPuppetActions`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/registry/ModPuppetActions.java) | Global central registry mapping `ResourceLocation` to `PuppetActionType<?>`. |
| **Anim Sync Packet** | [`ClientboundPuppetAnimPacket`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/network/packet/ClientboundPuppetAnimPacket.java) | S2C packet triggering GeckoLib animations on tracking clients. |

---

## 3. Polymorphic Targeting (`ActionTarget`)

The `ActionTarget` type supports three target sources with a unified DFU Codec:

1. **Static Coordinates (`PositionTarget`)**: Fixed world position `[x, y, z]` or `{"type": "position", "x": 0, "y": 64, "z": 0}`.
2. **Explicit Entity (`EntityTarget`)**: UUID string representing a specific entity.
3. **Command Selector (`SelectorTarget`)**: Minecraft target selector (e.g. `@p`, `@e[tag=boss_target,limit=1]`), evaluated at execution time against `ServerLevel`.

### Java Usage

```java
// From static position
ActionTarget posTarget = ActionTarget.fromPos(new Vec3(100, 64, 200));

// From Entity UUID
ActionTarget entityTarget = ActionTarget.fromEntity(player.getUUID());

// From Selector
ActionTarget selectorTarget = ActionTarget.fromSelector("@p");

// Evaluation
Vec3 position = target.getPosition(serverLevel);
Optional<LivingEntity> entity = target.getEntity(serverLevel);
```

---

## 4. Robust AI Goal Suppression

Instead of relying on priority 0 goal hijacking, v2.0 directly manipulates `GoalSelector#setControlFlag`:

```java
// Locking AI control
mob.goalSelector.setControlFlag(Goal.Flag.MOVE, false);
mob.goalSelector.setControlFlag(Goal.Flag.LOOK, false);
mob.goalSelector.setControlFlag(Goal.Flag.JUMP, false);
mob.targetSelector.setControlFlag(Goal.Flag.TARGET, false);

mob.getNavigation().stop();
mob.setTarget(null);

// Restoring AI control on completion
mob.goalSelector.setControlFlag(Goal.Flag.MOVE, true);
mob.goalSelector.setControlFlag(Goal.Flag.LOOK, true);
mob.goalSelector.setControlFlag(Goal.Flag.JUMP, true);
mob.targetSelector.setControlFlag(Goal.Flag.TARGET, true);
```

---

## 5. Reference Attacks: Void Leap Slam & Abyssal Barrage

### Action A: Void Leap Slam (`fractured_utils:leap_slam`)

* **Record**: `LeapSlamParams(ActionTarget target, double slamRadius, float damage, int windupTicks, int recoveryTicks)`
* **Lifecycle**:
  * **`WINDUP`**: Faces mob toward target, displays circular ground telegraph particles (`ParticleTypes.CRIT` ring), and sends `"charge"` GeckoLib animation packet.
  * **`ACTIVE`**: Launches mob along a calculated parabolic arc toward the target vector, sends `"leap"` animation packet, detects ground impact, spawns `EXPLOSION_EMITTER` particles, deals AoE damage in `slamRadius`, and applies radial knockback.
  * **`RECOVERY`**: Roots mob in place, sends `"stunned"` animation packet for `recoveryTicks`, then transitions to `IDLE` and restores AI control.

### Action B: Abyssal Barrage (`fractured_utils:abyssal_barrage`)

* **Record**: `AbyssalBarrageParams(int channelTicks, int waveInterval, double projectileSpeed)`
* **Lifecycle**:
  * **`ACTIVE`**: Roots mob in place, sends `"barrage"` animation packet, and every `waveInterval` ticks fires a ring of 8 `WitherSkull` projectiles outward with rotating angular offsets.
  * Completes after `channelTicks` and cleanly restores autonomous AI.

---

## 6. External Sequence JSON Schemas

External sequences and orchestration files can trigger attacks using the following schemas:

### A. Void Leap Slam JSON Specification

```json
{
  "action": "fractured_utils:leap_slam",
  "params": {
    "target": {
      "type": "selector",
      "selector": "@p"
    },
    "slamRadius": 6.0,
    "damage": 18.0,
    "windupTicks": 40,
    "recoveryTicks": 25
  }
}
```

*Alternative target formats:*
```json
// Static position:
"target": [128.5, 70.0, -256.0]

// Explicit Entity UUID:
"target": "c7a840e5-79a4-4a4b-8cf7-21a4f00bcf9e"
```

### B. Abyssal Barrage JSON Specification

```json
{
  "action": "fractured_utils:abyssal_barrage",
  "params": {
    "channelTicks": 120,
    "waveInterval": 20,
    "projectileSpeed": 0.8
  }
}
```

---

## 7. Entity Implementation: Void Herald Boss

Any `Mob` (such as [`VoidHeraldBoss`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/boss/VoidHeraldBoss.java)) implements GeckoLib's `GeoEntity` and can dispatch attacks via the capability:

```java
// Inside custom boss entity or AI step:
this.getCapability(PuppetCapabilityProvider.PUPPET_HANDLER).ifPresent(handler -> {
    if (!handler.isPuppetingActive()) {
        handler.dispatch(
            ModPuppetActions.LEAP_SLAM,
            new LeapSlamAction.LeapSlamParams(
                ActionTarget.fromEntity(target.getUUID()),
                6.0,   // slamRadius
                18.0F, // damage
                40,    // windupTicks
                30     // recoveryTicks
            )
        );
    }
});
```

---

## 8. Debug Boss Entity Registration & Config Control

The Void Herald Boss is registered as a real entity (`fractured_utils:void_herald`) with client GeckoLib rendering and a creative spawn egg (`fractured_utils:void_herald_spawn_egg`).

### A. Config Control (`fracturedutils-server.json`)

Its presence in the game is strictly governed by `enableDebugBoss`:

```json
{
  "keepInventoryNoXp": false,
  "enableDebugBoss": true,
  "teamWipeScreenDurationSeconds": 3
}
```

* **When `enableDebugBoss` is `false` (default)**:
  * Any spawned or existing instances are discarded immediately on spawn/tick (`discard()`).
  * `checkSpawnRules` prevents natural or automated spawning.
  * In-game attempts to spawn via `/puppetboss spawn` will notify the operator that it is disabled.
* **When `enableDebugBoss` is `true`**:
  * The boss can spawn naturally, via spawn egg, or with commands.

### B. In-Game Debug Commands (`/puppetboss`)

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/puppetboss status` | Level 2 (OP) | Checks config status and counts active Void Herald bosses. |
| `/puppetboss enable` | Level 2 (OP) | Enables `enableDebugBoss` in config and saves immediately. |
| `/puppetboss disable` | Level 2 (OP) | Disables `enableDebugBoss` and automatically discards all active instances. |
| `/puppetboss spawn` | Level 2 (OP) | Spawns a Void Herald at the executor's position (if enabled). |
| `/puppetboss action <action_name>` | Level 2 (OP) | Triggers `leap_slam` or `abyssal_barrage` on nearby Void Herald boss. |
| `/puppetboss indicator circle <radius> <duration> [color]` | Level 2 (OP) | Spawns a preview circular attack indicator at the executor's position. |
| `/puppetboss indicator line <length> <width> <duration> [color]` | Level 2 (OP) | Spawns a preview directional line attack indicator facing the executor's yaw. |

---

## 9. Ground Attack Indicator Utilities (Circles & Lines)

Actions and external mods can display rendered ground telegraph decals using dedicated circle (`textures/misc/attack_indicator_circle.png`) and line (`textures/misc/attack_indicator_line.png`) textures. Indicators automatically synchronize from the server to nearby tracking clients and render smoothly with depth and pulse animations.

### A. Calling from Any `PuppetActionInstance`

Within any custom action subclassing `PuppetActionInstance`:

#### 1. Circular Indicators
```java
// Spawn indicator at position with radius and duration (in ticks):
int indicatorId = showCircleIndicator(targetPos, radius, durationTicks);

// Spawn with custom color preset (e.g. AttackIndicatorUtils.COLOR_VOID, COLOR_RED, COLOR_ORANGE, COLOR_CYAN):
int indicatorId = showCircleIndicator(targetPos, radius, durationTicks, AttackIndicatorUtils.COLOR_VOID);

// Update position or radius as target moves during windup:
updateCircleIndicator(indicatorId, updatedPos, radius);

// Remove early (e.g. when attack lands or is canceled):
removeCircleIndicator(indicatorId, impactPos);
```

#### 2. Linear Indicators (Beams, Charges, Rectangular Telegraphs)
```java
// Spawn between two points (start to end) with width and duration:
int lineId = showLineIndicator(startPos, endPos, width, durationTicks);

// Spawn from origin with facing yaw angle (degrees), length, and width:
int lineId = showLineIndicator(startPos, yawDegrees, length, width, durationTicks, AttackIndicatorUtils.COLOR_RED);

// Spawn directly forward from the executing mob:
int lineId = showLineIndicatorForward(15.0, 2.5, 40, AttackIndicatorUtils.COLOR_ORANGE);

// Update endpoints as the target moves or the caster turns:
updateLineIndicator(lineId, startPos, endPos, width);
updateLineIndicator(lineId, startPos, newYaw, length, width);

// Remove early:
removeLineIndicator(lineId, startPos);
```

### B. Calling via `AttackIndicatorUtils` Static Methods (For Any External Mod)

From commands, event listeners, weapon abilities, or custom mob tick loops in any external mod:

```java
// --- CIRCLES ---
// Spawn
int circleId = AttackIndicatorUtils.spawnCircle(serverLevel, targetPos, 7.0, 40, AttackIndicatorUtils.COLOR_RED);
// Update
AttackIndicatorUtils.updateCircle(serverLevel, circleId, newPos, 7.0);
// Remove
AttackIndicatorUtils.removeCircle(serverLevel, circleId, newPos);

// --- LINES ---
// Spawn between two Vec3 points:
int lineId = AttackIndicatorUtils.spawnLine(serverLevel, startPos, targetPos, 2.0, 50, AttackIndicatorUtils.COLOR_CYAN);

// Spawn from position, yaw (degrees), length, and width:
int lineId = AttackIndicatorUtils.spawnLine(serverLevel, startPos, mob.getYRot(), 16.0, 3.0, 40, AttackIndicatorUtils.COLOR_VOID);

// Spawn directly forward from an Entity:
int lineId = AttackIndicatorUtils.spawnLine(entity, 12.0, 2.0, 30, AttackIndicatorUtils.COLOR_RED);

// Spawn centered at a point:
int lineId = AttackIndicatorUtils.spawnCenteredLine(serverLevel, centerPos, yaw, 20.0, 2.5, 40, AttackIndicatorUtils.COLOR_YELLOW);

// Update:
AttackIndicatorUtils.updateLine(serverLevel, lineId, newStart, newEnd, 2.0);
AttackIndicatorUtils.updateLine(serverLevel, lineId, newStart, newYaw, 16.0, 3.0);

// Remove:
AttackIndicatorUtils.removeLine(serverLevel, lineId, startPos);
```

### Color Presets Available in `AttackIndicatorUtils`
- `COLOR_VOID` (`0xD4B026FF`) - Glowing void violet
- `COLOR_RED` (`0xD4FF2222`) - Danger crimson
- `COLOR_ORANGE` (`0xD4FF8800`) - Warning amber
- `COLOR_CYAN` (`0xD400E5FF`) - Arcane cyan
- `COLOR_YELLOW` (`0xD4FFDD00`) - Hazard yellow

---

## 10. Custom Timing Phases & Music Sequencer Integration

Each `PuppetActionType<T>` can define its own lifecycle timing states, colors, default durations, and execution points for the **Music Sequencer** timeline and modal editor by overriding `getTimingPhases()` and `getExecutionLabel()`:

```java
public class MyCustomAction extends PuppetActionType<MyParams> {
    public static final ResourceLocation ID = new ResourceLocation("mymod", "flame_strike");

    public MyCustomAction() {
        super(ID, MyParams.CODEC, Instance::new);
    }

    @Override
    public List<ActionTimingPhase> getTimingPhases() {
        return List.of(
            new ActionTimingPhase("windup", "Charge", 600, 0xFFFFCC00, false),
            new ActionTimingPhase("jump", "Aim", 400, 0xFF4A69BD, false),
            new ActionTimingPhase("duration", "Strike", 1200, 0xFFFF3300, true),
            new ActionTimingPhase("recovery", "Exhaustion", 800, 0xFF00E5FF, false)
        );
    }

    @Override
    public String getExecutionLabel() {
        return "STRIKE";
    }
}
```

### Integration Features:
1. **Dynamic Modal Labels & Inputs**: When selecting an action in [`EditMusicEntryModalScreen`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/client/gui/EditMusicEntryModalScreen.java), input fields dynamically relabel to match the action's states (e.g. *Indicator / Jump / Slam / Recovery* for Leap Slam vs. *Charge / Cast / Barrage / Cooldown* for Abyssal Barrage).
2. **Interactive Editing**: All timing values remain fully editable by the user for every sequence entry.
3. **Timeline Duration Bars**: On the timeline ([`MusicSequenceScreen`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/client/gui/MusicSequenceScreen.java)), multi-color segmented bars display each action's distinct phase names and colors, with keyframe impact diamonds and status badges using the action's execution label (e.g. `[SLAM ...]` vs `[BARRAGE ...]`).

---

## 11. Custom Action Parameters & Sequencer Panel Integration

Puppet actions are not limited to timing phases; they can define discrete parameters of any type—including **booleans**, **floats**, **doubles**, **integers**, **strings**, and **options (enums)**. The **Music Sequencer** automatically detects these parameters in the puppet action editor modal, rendering dedicated cyberpunk widgets (toggles, numeric inputs, option cyclers) for interactive tweaking.

### A. Supported Parameter Types (`ActionParameter<T>`)

Construct parameters using the factory methods on [`ActionParameter`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/fsm/ActionParameter.java):

| Type | Factory Method | Rendered Sequencer Widget |
| :--- | :--- | :--- |
| **Boolean** | `ActionParameter.ofBoolean(key, label, defaultBool, description)` | Styled Cyberpunk toggle button (`[✓] LABEL: YES` / `[ ] LABEL: NO`). Glowing green when active. |
| **Float** | `ActionParameter.ofFloat(key, label, defaultFloat, description)` | Numeric EditBox with decimal validation, default hint, and hover tooltip. |
| **Double** | `ActionParameter.ofDouble(key, label, defaultDouble, description)` | Numeric EditBox with decimal validation, default hint, and hover tooltip. |
| **Integer** | `ActionParameter.ofInt(key, label, defaultInt, description)` | Integer EditBox with number validation, default hint, and hover tooltip. |
| **String** | `ActionParameter.ofString(key, label, defaultString, description)` | Text EditBox with default hint and hover tooltip. |
| **Options** | `ActionParameter.ofOptions(key, label, List.of(...), defaultVal, description)` | Cycle button that loops through allowed values on click. |

---

### B. Implementing Custom Parameters in Your Content Mod

To implement an action with custom parameters (for example, a boss that spins with a **spin direction** boolean and a **spin speed** float):

#### 1. Define the Parameter Record and Mojang Codec

```java
package com.mymod.puppet.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.dandare21.fracturedutils.puppet.fsm.ActionParameter;
import net.dandare21.fracturedutils.puppet.fsm.ActionTimingPhase;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionInstance;
import net.dandare21.fracturedutils.puppet.fsm.PuppetActionType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

import java.util.List;

public class BossSpinAction extends PuppetActionType<BossSpinAction.BossSpinParams> {
    public static final ResourceLocation ID = new ResourceLocation("content_mod", "boss_spin");

    // 1. Strongly typed parameter record matching the Codec
    public record BossSpinParams(
            boolean spinDirection,
            float spinSpeed,
            int durationTicks
    ) {
        public static final Codec<BossSpinParams> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.BOOL.optionalFieldOf("spinDirection", true).forGetter(BossSpinParams::spinDirection),
                        Codec.FLOAT.optionalFieldOf("spinSpeed", 2.5F).forGetter(BossSpinParams::spinSpeed),
                        Codec.INT.optionalFieldOf("durationTicks", 40).forGetter(BossSpinParams::durationTicks)
                ).apply(instance, BossSpinParams::new)
        );
    }

    public BossSpinAction() {
        super(ID, BossSpinParams.CODEC, Instance::new);
    }

    // 2. Declare parameters for the Sequencer to automatically detect
    @Override
    public List<ActionParameter<?>> getParameters() {
        return List.of(
                ActionParameter.ofBoolean("spinDirection", "Spin Clockwise", true, "When true rotates clockwise, otherwise counter-clockwise"),
                ActionParameter.ofFloat("spinSpeed", "Spin Speed", 2.5F, "Angular velocity of the spin attack")
        );
    }

    // 3. (Optional) Custom lifecycle timing phases for the timeline
    @Override
    public List<ActionTimingPhase> getTimingPhases() {
        return List.of(
                new ActionTimingPhase("windup", "Windup", 400, 0xFFFFCC00, false),
                new ActionTimingPhase("jump", "Telegraph", 0, 0xFF4A69BD, false),
                new ActionTimingPhase("duration", "Spin", 2000, 0xFFFF3366, true),
                new ActionTimingPhase("recovery", "Dizzy", 600, 0xFF00E5FF, false)
        );
    }

    @Override
    public String getExecutionLabel() {
        return "SPIN";
    }

    // 4. Action instance handling the attack logic
    public static class Instance extends PuppetActionInstance<BossSpinParams> {
        public Instance(Mob mob, BossSpinParams params) {
            super(mob, params);
        }

        @Override
        public void onStart() {
            // Read typed parameters directly from the record:
            boolean clockwise = params.spinDirection();
            float speed = params.spinSpeed();
            // ... trigger spin animation and rotational motion ...
        }
    }
}
```

#### 2. Register the Action with `ModPuppetActions`

During common/mod setup in your content mod:

```java
import net.dandare21.fracturedutils.puppet.registry.ModPuppetActions;

public class ContentModPuppetActions {
    public static final BossSpinAction BOSS_SPIN = ModPuppetActions.register(new BossSpinAction());

    public static void init() {
        // Called during FMLCommonSetupEvent
    }
}
```

---

### C. Sequencer GUI & Serialization Behavior

Once registered:

1. **Automatic Detection**: When opening [`EditMusicEntryModalScreen`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/client/gui/EditMusicEntryModalScreen.java) on a Puppet track and selecting `⚡ BOSS SPIN` from the action dropdown, the **⚙ ACTION PARAMETERS** card instantly detects `Spin Clockwise` and `Spin Speed`.
2. **Interactive Toggling & Editing**:
   - Clicking `[✓] SPIN CLOCKWISE: YES` toggles between clockwise (`true`) and counter-clockwise (`false`) with immediate visual feedback.
   - The `Spin Speed:` box allows live numeric adjustment, initialized with the declared default `2.5`.
3. **Dynamic Custom Parameters**: Users can also click `➕ ADD PARAM` in the modal at any time to add arbitrary ad-hoc parameters or override custom action IDs.
4. **Serialization**:
   - Parameters are saved directly to `MusicSequenceEntry#puppetParams` as key-value pairs and serialized to the sequence JSON.
   - Parameters are also appended to the command string (e.g. `puppet_action tag:boss action:content_mod:boss_spin target:@p windup:400 jump:0 duration:2000 recovery:600 spinDirection:true spinSpeed:2.5`).
5. **Runtime Codec Execution**:
   - [`MusicSequenceManager`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/sound/sequence/MusicSequenceManager.java) reads the entry parameters and maps them to proper NBT types (`ByteTag`/boolean, `FloatTag`, `DoubleTag`, `IntTag`, `StringTag`) using the action's `ActionParameter` definitions.
   - The parameters are decoded by Mojang's DFU `Codec<T>` into your strongly-typed record `BossSpinParams` without any manual deserialization or type casting required.



