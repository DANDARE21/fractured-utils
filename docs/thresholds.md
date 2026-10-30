# Health Thresholds & Phase Gating

The **Health Threshold System** provides a mathematical damage reduction and health-locking engine for boss encounters and scripted PvE fights. It prevents players from skipping boss phases with high-burst weapons, guarantees cinematic stage transitions, and fires Forge events when threshold gates are hit.

---

## 1. The Problem: Phase Skipping & One-Shots

In modded Minecraft, endgame weapons, critical hits, and damage stacking frequently deal hundreds or thousands of points of damage in a single tick. This often:
- Instantly kills bosses through phase gates.
- Breaks cutscene triggers, dialog, and spawn choreographies.
- Causes music sequences and orchestrator timelines to desynchronize.

The Health Threshold system solves this with **strict health clamping** ($h_{\text{min}}$) combined with an **exponential damping falloff curve** ($h_{\text{umbral}}$).

---

## 2. Mathematical Damage Falloff Model

Each threshold consists of two values:
- **$h_{\text{min}}$ (Minimum Health Gate)**: The absolute hard floor. An entity's health **can never drop below this value** under any circumstances.
- **$h_{\text{umbral}}$ (Umbral Threshold)**: The health level at which incoming damage begins to decay exponentially. Above $h_{\text{umbral}}$, attacks deal 100% full damage. Inside $[h_{\text{min}}, h_{\text{umbral}}]$, additional damage produces exponentially diminishing returns.

Let the buffer width be $L = h_{\text{umbral}} - h_{\text{min}}$:

### Case 1: Entity Health Begins Above $h_{\text{umbral}}$
$$\Delta D_{\text{to\_umbral}} = h_{\text{current}} - h_{\text{umbral}}$$
If the raw damage $D_{\text{raw}} \le \Delta D_{\text{to\_umbral}}$, standard full damage is applied. If $D_{\text{raw}} > \Delta D_{\text{to\_umbral}}$, the remaining damage $D_{\text{remainder}} = D_{\text{raw}} - \Delta D_{\text{to\_umbral}}$ enters the reduction buffer:
$$h_{\text{final}} = h_{\text{min}} + L \cdot e^{-\frac{D_{\text{remainder}}}{L}}$$
$$\text{Effective Damage} = \min\left(D_{\text{raw}}, \ (h_{\text{current}} - h_{\text{final}})\right)$$

### Case 2: Entity Health Is Already Inside $[h_{\text{min}}, h_{\text{umbral}}]$
$$h_{\text{final}} = h_{\text{min}} + (h_{\text{current}} - h_{\text{min}}) \cdot e^{-\frac{D_{\text{raw}}}{L}}$$
$$\text{Effective Damage} = \min\left(D_{\text{raw}}, \ (h_{\text{current}} - h_{\text{final}})\right)$$

### Case 3: Entity Is At or Below $h_{\text{min}}$
$$\text{Effective Damage} = 0.0$$
All incoming damage is completely cancelled (`LivingHurtEvent.setCanceled(true)`).

---

## 3. Threshold Progression Stacks

Thresholds are maintained as a **Last-In, First-Out (LIFO) stack** on the entity. This enables seamless multi-phase bosses:

```
┌────────────────────────────────────────────────────────┐
│                    Threshold Stack                     │
│                                                        │
│  [Top] Phase 1: min = 70%, umbral = 75%  (Active)      │
│        Phase 2: min = 40%, umbral = 45%  (Pending)     │
│  [Bot] Phase 3: min = 0%,  umbral = 10%  (Final Phase) │
└────────────────────────────────────────────────────────┘
```
1. While Phase 1 is active, health cannot drop below 70%.
2. When the threshold is reached, remove or pop Phase 1.
3. The entity immediately begins tracking Phase 2 (allowing health to drop down to 40%).

---

## 4. Developer API & Forge Events

### Scripting with `HealthThresholdEvent.Reached`
Fractured Utils posts `HealthThresholdEvent.Reached` to the Forge Event Bus whenever an entity's health hits $h_{\text{min}}$ or resists damage while clamped:

```java
import net.dandare21.fracturedutils.threshold.HealthThresholdEvent;
import net.dandare21.fracturedutils.threshold.HealthThresholdManager;
import net.dandare21.fracturedutils.orchestrator.OrchestratorManager;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "your_mod_id", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PhaseTransitionHandler {

    @SubscribeEvent
    public static void onThresholdReached(HealthThresholdEvent.Reached event) {
        LivingEntity entity = event.getEntity();
        String thresholdId = event.getThreshold().getId();

        if ("phase_1_gate".equals(thresholdId)) {
            // 1. Pop Phase 1 threshold so the boss can take damage in Phase 2
            HealthThresholdManager.popThreshold(entity);

            // 2. Trigger an orchestrator cutscene or dialog sequence
            OrchestratorManager.getInstance().startSequence("boss_phase2_cutscene.json", null, 0);

            // 3. Play screen shake & impact frame
            ScreenEffectManager.playEffect(
                entity.getServer().getPlayerList().getPlayers(),
                ScreenEffects.impactFrame(300, 0xFFFF0055, 0xFF000000, 40)
            );
        }
    }
}
```

### Applying Thresholds Programmatically in Java
```java
import net.dandare21.fracturedutils.threshold.HealthThreshold;
import net.dandare21.fracturedutils.threshold.HealthThresholdManager;
import net.dandare21.fracturedutils.threshold.HealthValue;

LivingEntity boss = ...;

// Set 75% threshold with 80% umbral buffer
HealthThreshold phase1 = new HealthThreshold(
    "phase_1_gate",
    HealthValue.ofPercent(0.75f), // Clamped floor
    HealthValue.ofPercent(0.80f)  // Damping starts here
);

// Set absolute HP threshold (clamped at 200 HP, damping starts at 400 HP)
HealthThreshold hardCap = new HealthThreshold(
    "survival_phase",
    HealthValue.ofAbsolute(200.0f),
    HealthValue.ofAbsolute(400.0f)
);

HealthThresholdManager.setThreshold(boss, phase1);
```

---

## 5. Command Reference

All commands require OP Permission Level 2:

| Command | Usage | Description |
| :--- | :--- | :--- |
| `set` | `/healththreshold set <entities> <min> <umbral> [id]` | Overwrites current threshold with a new one. |
| `add` | `/healththreshold add <entities> <min> <umbral> [id]` | Pushes a new phase threshold onto the entity's stack. |
| `remove` | `/healththreshold remove <entities> [id]` | Removes a specific threshold or pops the top threshold. |
| `clear` | `/healththreshold clear <entities>` | Clears all active thresholds from the targeted entities. |
| `get` | `/healththreshold get <entity>` | Inspects the active threshold, min HP, and umbral values. |
| `list` | `/healththreshold list` | Lists all entities currently tracked by the threshold manager. |

*Command Alias: `/threshold`*

### Value Syntax Examples:
- **Percentages**: `50%`, `0.5`, `75%`
- **Absolute Health**: `100`, `250.0`, `500`

```mcfunction
# Protect the boss at 50% HP with falloff starting at 60% HP
/healththreshold set @e[type=fractured_utils:void_herald,limit=1] 50% 60% phase_1

# Check current active threshold
/threshold get @e[type=fractured_utils:void_herald,limit=1]
```

