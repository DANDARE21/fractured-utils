# Boss Puppet Framework v2.0

The **Boss Puppet Framework v2.0** is an enterprise-grade AI orchestration and attack dispatch system for Forge 1.20.1. It replaces legacy intrusive interface inheritance, loosely typed `CompoundTag` parameters, and leaky AI goals with **Forge Capabilities**, **DFU Codecs**, **Global Registries**, **Goal Flag Suppression**, and **S2C GeckoLib Animation Synchronization**.

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
| **Capability** | `IPuppetHandler` | Attached to any `Mob` via `AttachCapabilitiesEvent<Entity>`. Controls FSM lifecycle and goal flag suppression. |
| **Provider** | `PuppetCapabilityProvider` | Exposes thread-safe `LazyOptional<IPuppetHandler>`. |
| **Targeting** | `ActionTarget` | Polymorphic target union (`Vec3`, `UUID`, or command selector string) parsed via DFU Codecs. |
| **Action Definition** | `PuppetActionType<T>` | Global immutable action blueprint backed by Mojang `Codec<T>`. |
| **Action Parameter** | `ActionParameter<T>` | Strongly typed parameter descriptor (booleans, numbers, strings) detected by the visual Sequencer. |
| **Action FSM** | `PuppetActionInstance<T>` | Running action finite state machine (`WINDUP`, `ACTIVE`, `RECOVERY`, `IDLE`). |
| **Registry** | `ModPuppetActions` | Global central registry mapping `ResourceLocation` to `PuppetActionType<?>`. |
| **Anim Sync Packet** | `ClientboundPuppetAnimPacket` | S2C packet triggering GeckoLib animations on tracking clients. |
| **Telegraph Decals** | `AttackIndicatorUtils` | Spawns visual floor telegraph indicators (circles, lines, cones) before impact. |

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
mob.goalSelector.setControlFlag(Goal.Flag.TARGET, false);
mob.getNavigation().stop();
mob.setTarget(null);

// Unlocking and restoring AI control
mob.goalSelector.setControlFlag(Goal.Flag.MOVE, true);
mob.goalSelector.setControlFlag(Goal.Flag.LOOK, true);
mob.goalSelector.setControlFlag(Goal.Flag.JUMP, true);
mob.goalSelector.setControlFlag(Goal.Flag.TARGET, true);
```

---

## 5. Telegraph Attack Indicators (`AttackIndicatorUtils`)

Telegraph indicators are client-rendered floor decals that communicate impending attacks, giving players time to dodge.

### Indicator Types
- **Circle (`spawnCircleIndicator`)**: Renders expanding warning circles on the ground (e.g. meteor impacts, ground slams).
- **Line (`spawnLineIndicator`)**: Renders directional rectangles on the ground (e.g. charge paths, laser beams).

### Spawning Telegraphs in Java
```java
import net.dandare21.fracturedutils.puppet.util.AttackIndicatorUtils;

// Circle indicator with 3.5 block radius for 30 ticks (1.5 seconds)
AttackIndicatorUtils.spawnCircleIndicator(
    serverLevel,
    impactPos,
    3.5,   // radius
    30,    // duration in ticks
    "void" // color palette theme
);

// Line indicator 20 blocks long, 2 blocks wide for 40 ticks
AttackIndicatorUtils.spawnLineIndicator(
    serverLevel,
    originPos,
    facingDirection,
    20.0,  // length
    2.0,   // width
    40,    // duration in ticks
    "crimson"
);
```

---

## 6. Creating a Custom Puppet Action

### Step 1: Define Strongly Typed Parameter Record & Codec
```java
public record GroundSlamParams(ActionTarget target, int windupTicks, float impactRadius, float damage) {
    public static final Codec<GroundSlamParams> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ActionTarget.CODEC.fieldOf("target").forGetter(GroundSlamParams::target),
            Codec.INT.fieldOf("windupTicks").forGetter(GroundSlamParams::windupTicks),
            Codec.FLOAT.fieldOf("impactRadius").forGetter(GroundSlamParams::impactRadius),
            Codec.FLOAT.fieldOf("damage").forGetter(GroundSlamParams::damage)
        ).apply(instance, GroundSlamParams::new)
    );
}
```

### Step 2: Implement `PuppetAction<T>`
```java
public class GroundSlamAction extends PuppetAction<GroundSlamParams> {

    @Override
    public void onStart(Mob mob, GroundSlamParams params, IPuppetHandler handler) {
        // Face the target
        Vec3 targetPos = params.target().getPosition((ServerLevel) mob.level());
        mob.getLookControl().setLookAt(targetPos.x, targetPos.y, targetPos.z);

        // Spawn visual floor telegraph
        AttackIndicatorUtils.spawnCircleIndicator(
            (ServerLevel) mob.level(),
            targetPos,
            params.impactRadius(),
            params.windupTicks(),
            "void"
        );

        // Trigger GeckoLib windup animation
        ClientboundPuppetAnimPacket.send(mob, "slam_controller", "anim_windup");
    }

    @Override
    public void onPhaseTransition(Mob mob, Phase oldPhase, Phase newPhase, GroundSlamParams params) {
        if (newPhase == Phase.ACTIVE) {
            // Trigger impact animation & damage
            ClientboundPuppetAnimPacket.send(mob, "slam_controller", "anim_impact");
            dealDamage((ServerLevel) mob.level(), mob, params);
        }
    }
}
```

### Step 3: Register in `ModPuppetActions`
```java
public static final RegistryObject<PuppetActionType<GroundSlamParams>> GROUND_SLAM =
    PUPPET_ACTIONS.register("ground_slam", () ->
        new PuppetActionType<>(
            GroundSlamParams.CODEC,
            GroundSlamAction::new,
            List.of(
                ActionParameter.target("target", "Target to slam"),
                ActionParameter.integer("windupTicks", "Windup duration", 20),
                ActionParameter.floating("impactRadius", "AOE radius", 4.0f),
                ActionParameter.floating("damage", "Damage dealt", 15.0f)
            )
        )
    );
```

---

## 7. Command Reference

All commands require OP Permission Level 2:

| Command | Usage | Description |
| :--- | :--- | :--- |
| `status` | `/puppetboss status` | Checks debug boss configuration and active world instances. |
| `enable` / `disable` | `/puppetboss enable` | Toggles the debug puppet boss in server config. |
| `spawn` | `/puppetboss spawn` | Spawns a Void Herald Boss entity at your crosshair. |
| `action` | `/puppetboss action <action_name>` | Manually orders the nearby Void Herald to perform an attack (`leap_slam`, `abyssal_barrage`). |
| `indicator circle` | `/puppetboss indicator circle <radius> <duration> [color]` | Tests a circle telegraph decal at your location. |
| `indicator line` | `/puppetboss indicator line <length> <width> <duration> [color]` | Tests a directional line telegraph decal. |

