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

---

## 9. Circular Attack Indicator Utilities (`attackIndicatorCircle.png`)

Actions can display rendered ground decals using the dedicated attack indicator circle texture (`textures/misc/attack_indicator_circle.png`). Indicators automatically synchronize from the server to nearby tracking clients and render smoothly with depth and pulse animations.

### A. Calling from Any `PuppetActionInstance`

Within any custom action subclassing `PuppetActionInstance`:

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

### B. Calling via `AttackIndicatorUtils` Static Methods

From commands, event listeners, or mob tick loops:

```java
// Spawn
int id = AttackIndicatorUtils.spawnCircle(serverLevel, targetPos, 7.0, 40, AttackIndicatorUtils.COLOR_RED);

// Update
AttackIndicatorUtils.updateCircle(serverLevel, id, newPos, 7.0);

// Remove
AttackIndicatorUtils.removeCircle(serverLevel, id, newPos);
```

### Color Presets Available in `AttackIndicatorUtils`
- `COLOR_VOID` (`0xD4B026FF`) - Glowing void violet
- `COLOR_RED` (`0xD4FF2222`) - Danger crimson
- `COLOR_ORANGE` (`0xD4FF8800`) - Warning amber
- `COLOR_CYAN` (`0xD400E5FF`) - Arcane cyan
- `COLOR_YELLOW` (`0xD4FFDD00`) - Hazard yellow


