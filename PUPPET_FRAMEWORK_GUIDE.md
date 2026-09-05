# Developer Guide: Boss Puppet Framework Integration

This guide explains how to add **Fractured Utils** as a mod dependency and implement the **Boss Puppet Framework** (`IPuppetEntity` + `PuppetController`) in your custom Minecraft entities.

---

## 1. Overview

The **Boss Puppet Framework** provides a modular AI hijacking and action dispatch system. It uses a **Composition Pattern** to allow custom mobs and third-party entities to yield control to music sequencers and orchestrator scripts without forcing modifications to your mob class inheritance hierarchy.

---

## 2. Adding Fractured Utils as a Dependency

### A. Gradle Setup (`build.gradle`)

Add Fractured Utils to your `repositories` and `dependencies` blocks in `build.gradle`:

```groovy
repositories {
    // Maven repository hosting Fractured Utils (or local maven repository)
    maven {
        name = "Local Maven"
        url = "file://${project.projectDir}/mcmodsrepo"
    }
    // Alternatively, use CurseMaven if hosted on CurseForge:
    // maven { url = "https://www.cursemaven.com" }
}

dependencies {
    // Compile against Fractured Utils API
    implementation fg.deobf("net.dandare21.fracturedutils:fractured_utils:${fractured_utils_version}")
    
    // Or via CurseMaven:
    // implementation fg.deobf("curse.maven:fractured-utils-PROJECTID:FILEID")
}
```

### B. Mod Manifest (`META-INF/mods.toml`)

Declare Fractured Utils as a dependency in your `mods.toml`:

```toml
[[dependencies.your_mod_id]]
    modId = "fractured_utils"
    mandatory = true
    versionRange = "[1.0.0,)"
    ordering = "AFTER"
    side = "BOTH"
```

---

## 3. Architecture & Core Components

| Component | Class / Interface | Purpose |
| :--- | :--- | :--- |
| **Interface** | [`IPuppetEntity`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/IPuppetEntity.java) | Implemented by any `Mob` to allow puppeteering. |
| **Controller** | [`PuppetController`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/PuppetController.java) | Persistent entity component managing multi-phase actions, lifetimes, and AI aspect suppression. |
| **Action Callback** | [`PuppetAction`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/PuppetAction.java) | Multi-phase interface (`onWindupTick`, `execute`, `onActiveTick`, `onComplete`) defining attack routines. |
| **Registry** | [`PuppetActionRegistry`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/PuppetActionRegistry.java) | Builder helper used to map `ResourceLocation` action IDs to `PuppetAction` callbacks. |
| **Hijack Goal (GoalSelector)** | [`PuppetOverrideGoal`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/PuppetOverrideGoal.java) | Priority 0 AI goal (`MOVE`, `LOOK`, `JUMP`) that silences standard AI goals while active. |
| **Hijack Goal (TargetSelector)** | [`PuppetTargetOverrideGoal`](file:///d:/Projects/mc%20modding/Fractured%20Utils/src/main/java/net/dandare21/fracturedutils/puppet/PuppetTargetOverrideGoal.java) | Priority 0 target selector goal (`TARGET`) that suppresses target acquisition while active. |

---

## 4. Multi-Phase Action Lifecycle & Input Parameters

Every `PuppetAction` supports three lifecycle phases:

```
[Trigger Action] ──► [WINDUP PHASE (windupTicks)] ──► [EXECUTE / ACTIVE PHASE (durationTicks)] ──► [ON COMPLETE]
                      └─ onWindupTick()                 └─ execute() + onActiveTick()                 └─ onComplete()
```

1. **AI & Aspect Toggles**:
   - `puppetingActive`: Toggles the priority 0 hijack goals.
   - `suppressNavigation`: Stops pathfinding and freezes movement.
   - `suppressTargeting`: Clears combat target entity (`setTarget(null)`).
   - `suppressLook`: Suppresses look control.
2. **Action Parameters (`CompoundTag params`)**:
   - Actions receive key-value NBT parameters passed from sequence scripts or Java calls (e.g. `x, y, z` target coordinates, target player selectors, `radius`, `damage`).
3. **Timing Properties**:
   - **Windup Ticks** (`windupTicks`): Charge / telegraph phase (e.g., 60 ticks). Fires `onWindupTick(mob, params, currentTick, totalWindupTicks)` every tick so the entity can display attack indicators, play charge sounds, or track target positions.
   - **Duration Ticks** (`durationTicks`): Execution phase (e.g. 1 tick for instantaneous strike, or 40 ticks for sustained whirlwind/beam). Fires `execute(mob, params)` at phase start and `onActiveTick(...)` every tick during duration.
   - **On Complete**: Fired when duration finishes or when `stopAction()` is called.

---

## 5. Entity Integration Step-by-Step

To make your custom entity puppet-controllable:

1. Implement `IPuppetEntity`.
2. Instantiate a `PuppetController` inside your entity.
3. Pass `PuppetActionRegistry` to `registerPuppetActions(registry)` during constructor initialization.
4. Forward entity tick calls to `puppetController.tick()`.

### Boss Entity Code Example (Thunder Attack with 60t Windup + 1t Strike)

```java
package com.example.mymod.entity;

import net.dandare21.fracturedutils.puppet.IPuppetEntity;
import net.dandare21.fracturedutils.puppet.PuppetAction;
import net.dandare21.fracturedutils.puppet.PuppetActionRegistry;
import net.dandare21.fracturedutils.puppet.PuppetController;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class BossDemonEntity extends Monster implements IPuppetEntity {
    private final PuppetController puppetController = new PuppetController(this);

    public BossDemonEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.registerPuppetActions(new PuppetActionRegistry(this.puppetController));
    }

    @Override
    public PuppetController getPuppetController() {
        return this.puppetController;
    }

    @Override
    public void registerPuppetActions(PuppetActionRegistry registry) {
        // --- Action: Boss Thunder Strike (60 Tick Windup Indicator + 1 Tick Strike Duration) ---
        registry.register(ResourceLocation.fromNamespaceAndPath("mymod", "thunder_strike"), new PuppetAction() {
            @Override
            public void onWindupTick(Mob mob, CompoundTag params, int currentTick, int totalWindupTicks) {
                // Determine target position: from explicit NBT params (x, y, z) or target player
                Vec3 targetPos = getTargetPosition(mob, params);

                // Render ground charge indicator circle during windup (60 ticks = 3 seconds)
                if (mob.level() instanceof ServerLevel serverLevel) {
                    double radius = params.contains("radius") ? params.getDouble("radius") : 3.0;
                    for (int i = 0; i < 16; i++) {
                        double angle = i * (Math.PI * 2 / 16);
                        double px = targetPos.x + Math.cos(angle) * radius;
                        double pz = targetPos.z + Math.sin(angle) * radius;
                        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, targetPos.y + 0.1, pz, 1, 0, 0.05, 0, 0.01);
                    }
                }
            }

            @Override
            public void execute(Mob mob, CompoundTag params) {
                // Windup complete -> Strike lightning bolt!
                Vec3 targetPos = getTargetPosition(mob, params);
                if (mob.level() instanceof ServerLevel serverLevel) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
                    if (bolt != null) {
                        bolt.moveTo(targetPos.x, targetPos.y, targetPos.z);
                        serverLevel.addFreshEntity(bolt);
                    }
                }
            }

            private Vec3 getTargetPosition(Mob mob, CompoundTag params) {
                if (params.contains("x") && params.contains("y") && params.contains("z")) {
                    return new Vec3(params.getDouble("x"), params.getDouble("y"), params.getDouble("z"));
                }
                LivingEntity target = mob.getTarget();
                return target != null ? target.position() : mob.position();
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        // Forward tick update to PuppetController
        this.puppetController.tick();
    }
}
```

---

## 6. Programmatic Control API

### A. AI Suppression Controls

```java
PuppetController controller = puppetEntity.getPuppetController();

// Freeze pathfinding navigation
controller.setSuppressNavigation(true);

// Clear & suppress combat targeting
controller.setSuppressTargeting(true);

// Suppress look control
controller.setSuppressLook(true);

// Reset all suppression flags
controller.resetSuppressionFlags();
```

### B. Executing Multi-Phase Actions

```java
CompoundTag params = new CompoundTag();
params.putDouble("x", 100.5);
params.putDouble("y", 64.0);
params.putDouble("z", -250.5);
params.putDouble("radius", 4.0);

// Execute action with 60 ticks windup (charge phase) + 1 tick strike duration + completion callback
controller.executeAction(
    ResourceLocation.fromNamespaceAndPath("mymod", "thunder_strike"),
    params,
    60, // Windup Ticks
    1,  // Duration Ticks
    () -> System.out.println("Thunder Strike complete!")
);
```

---

## 7. Orchestrator Sequence Integration

In Fractured Utils orchestrator sequences, you can control puppet entities directly via JSON sequence scripts:

### Puppet Action Types

#### 1. Execute Scripted Puppet Action (`puppet_action` / `execute_puppet_action`)
Triggers a custom registered `PuppetAction` callback with optional `windupTicks`, `durationTicks`, and input `params`:
```json
{
  "type": "puppet_action",
  "actionId": "mymod:thunder_strike",
  "targetSelector": "@e[type=mymod:boss_demon]",
  "windupTicks": 60,
  "durationTicks": 1,
  "params": {
    "x": 100.5,
    "y": 64.0,
    "z": -250.5,
    "radius": 4.0
  }
}
```

#### 2. Move To Coordinates (`puppet_move_to` / `puppet_move`)
```json
{
  "type": "puppet_move_to",
  "x": 100.5,
  "y": 64.0,
  "z": -250.5,
  "speed": 1.5,
  "targetSelector": "@e[type=mymod:boss_demon]"
}
```

#### 3. Look At Position or Entity (`puppet_look_at` / `puppet_look`)
```json
{
  "type": "puppet_look_at",
  "x": 100.5,
  "y": 65.0,
  "z": -250.5,
  "lookTargetSelector": "@p",
  "targetSelector": "@e[type=mymod:boss_demon]"
}
```

#### 4. Configure AI Aspect Suppression (`puppet_suppress_ai` / `puppet_suppress`)
Toggles AI suppression flags on target puppet entities. Set `suppressAi` (or `disableAi`) to `true` to completely disable the mob's autonomous AI (suppressing all goals, targeting, and autonomous movement) while preserving full receptivity to manual actions (`puppet_action`, `puppet_move_to`, `puppet_look_at`) from the command orchestrator:
```json
{
  "type": "puppet_suppress_ai",
  "suppressAi": true,
  "suppressNavigation": true,
  "suppressTargeting": true,
  "suppressLook": false,
  "puppetingActive": true,
  "targetSelector": "@e[type=mymod:boss_demon]"
}
```

#### 5. Stop Active Action (`puppet_stop_action` / `puppet_stop`)
```json
{
  "type": "puppet_stop_action",
  "targetSelector": "@e[type=mymod:boss_demon]"
}
```


