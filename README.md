# Fractured Utils

[![Forge](https://img.shields.io/badge/Minecraft-1.20.1-orange.svg)](https://files.minecraftforge.net/)
[![Platform](https://img.shields.io/badge/Loader-Forge%2047.3.0+-blue.svg)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-LGPL--3.0-green.svg)](LICENSE.txt)

**Fractured Utils** is an enterprise-grade utility and orchestration framework for Minecraft Forge 1.20.1, designed for live events, MMORPG adventure maps, scripted boss encounters, and machinima cinematics.

It provides server administrators, mapmakers, and mod developers with a unified suite of tools including real-time visual sequencers, multi-entity boss health bars, mathematical damage threshold falloffs, AI puppet choreography, custom HTTP audio streaming, 3D keyframe spline cameras, anime impact shaders, downed player revive mechanics, and off-screen tactical HUD pings.

---

## Subsystem Architecture & Documentation Matrix

Every major system is fully modular and covered by dedicated documentation in the [`docs/`](docs/) directory:

| Module | Core Responsibility | Primary Command | Documentation Guide |
| :--- | :--- | :--- | :--- |
| **Event Orchestrator** | 17+ Scripting actions, branching sequences, live operator monitor HUD, and visual sequence editor. | `/orchestrator` | [**docs/orchestrator.md**](docs/orchestrator.md) |
| **Boss Health Bars** | Multi-entity health pooling, custom textures, ghost bars, notch phase markers, and dynamic HUD renderer. | `/bosshealthbar` | [**docs/bossbar.md**](docs/bossbar.md) |
| **Health Thresholds** | Exponential damage falloff damping math ($h_{\text{min}} \le h \le h_{\text{umbral}}$) preventing boss burst kills. | `/healththreshold` | [**docs/thresholds.md**](docs/thresholds.md) |
| **Boss Puppet Framework** | Forge capability `IPuppetHandler` AI goal suppression, FSM phases, DFU Codecs, and attack decals. | `/puppetboss` | [**docs/puppet_framework.md**](docs/puppet_framework.md) |
| **RPG Dialog System** | Typewriter text, 8 letter sound blips, input locking, cinematic speaker focus camera, and dialog GUI. | `/dialog` | [**docs/dialog.md**](docs/dialog.md) |
| **Audio & Sequencer** | Embedded HTTP streaming server, zero-download custom soundtracks, and multi-track waveform sequencer. | `/musicsequence`<br>`/eventmusic` | [**docs/audio_sequencer.md**](docs/audio_sequencer.md) |
| **Cinematics & 3D Camera** | Remote video caching & playback, Catmull-Rom/Bezier spline camera paths, and 11 easing curves. | `/playcinematic`<br>`/downloadcinematic` | [**docs/cinematics_camera.md**](docs/cinematics_camera.md) |
| **Screen Effects** | Anime manga impact frames, custom GLSL shaders, camera shake, cinematic letterbox bars, and invert. | `/screeneffect` | [**docs/screen_effects.md**](docs/screen_effects.md) |
| **Checkpoints & Reviving** | Downed state on lethal damage, hold-to-revive teammate interaction, team wipe screen, and auto-rollback. | `/orchestrator checkpoint` | [**docs/checkpoint_downed.md**](docs/checkpoint_downed.md) |
| **HUD Pings & Markers** | 3D world markers with distance readout and mathematical off-screen edge clamping. | `/ping` | [**docs/pings_hud.md**](docs/pings_hud.md) |
| **Waiting Room & Maint.** | Pre-event cyberpunk lobby, hold-to-join, countdown timer, and server maintenance lockdown. | `/waitingroom`<br>`/maintenance` | [**docs/waiting_room_maintenance.md**](docs/waiting_room_maintenance.md) |
| **Client Guide & Config** | In-game UI tutorials, keybindings, client HUD options, and server configuration settings. | *Keybinds* | [**docs/client_guide.md**](docs/client_guide.md) |

---

## Keybindings Quick Reference

| Action | Default Key | Registry Name | Context |
| :--- | :--- | :--- | :--- |
| **Open Waiting Room / Hold to Join** | `J` | `key.fractured_utils.waiting_room` | In-Game |
| **Advance RPG Dialog** | `E` | `key.fractured_utils.dialog_advance` | In-Game (When Dialog Active) |
| **Skip Video Cutscene** | `Space` | `key.fractured_utils.skip_cutscene` | Cinematic Screen |
| **Operator Quick Resume** | `\` | `key.fractured_utils.operator_resume` | In-Game (OP Permission Level 2+) |

---

## Quickstart Guide

### 1. For Mapmakers & Event Operators

#### Opening the In-Game Sequencers
Fractured Utils features two rich visual timeline editors:
- **Event Orchestrator Timeline**: Run `/orchestrator ui` to create sequences, set checkpoints, dispatch puppet attacks, display objectives, and configure triggers.
- **Audio & Beat Sequencer**: Run `/musicsequence ui` to inspect audio waveforms, lay down track cues, and trigger commands in sync with the beat.

#### Running Pre-Built Sequences
To trigger a sequence saved in `config/orchestrator_sequences/`:
```mcfunction
/orchestrator run boss_intro.json @a
```

#### Placing World Markers / Pings
To point players towards an objective without opening coordinates:
```mcfunction
/ping add boss_arena "Boss Chamber" 120.5 64.0 -350.2 #00E5FF
```

---

### 2. For Developers & Modders

#### Maven / Gradle Dependency Setup
Add Fractured Utils to your workspace `build.gradle`:
```groovy
repositories {
    maven { url = "https://maven.kosmx.dev/" }       // PlayerAnimator
    maven { url = "https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/" } // GeckoLib
}

dependencies {
    implementation fg.deobf("net.dandare21:fractured_utils:${fractured_utils_version}")
}
```

#### Controlling an Entity via Puppet Handler Capability
```java
LivingEntity targetMob = ...;

targetMob.getCapability(PuppetCapabilityProvider.PUPPET_HANDLER_CAPABILITY).ifPresent(handler -> {
    // Suppress AI movement and pathfinding
    handler.suppressAi(true);

    // Dispatch a choreographed action
    handler.executeAction(
        ModPuppetActions.LEAP_SLAM.get(),
        new LeapSlamAction.Params(ActionTarget.fromSelector("@p"), 30, 20, 15)
    );
});
```

#### Subscribing to Boss Phase Threshold Events
```java
@Mod.EventBusSubscriber(modid = "your_mod_id", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BossFightListener {

    @SubscribeEvent
    public static void onThresholdReached(HealthThresholdEvent.Reached event) {
        LivingEntity entity = event.getEntity();
        HealthThreshold threshold = event.getThreshold();

        if ("phase_2_transition".equals(threshold.getId())) {
            // Trigger phase 2 cutscene, dialog, or attack sequence
            MinecraftServer server = entity.getServer();
            OrchestratorManager.getInstance().startSequence("phase2_cutscene.json", null, 0);
        }
    }
}
```

---

## Configuration Files

The mod produces two primary configuration files in your `.minecraft/config/` (or server `config/`) directory:
- [`config/fracturedutils-server.json`](docs/client_guide.md#server-configuration): Configures KeepInventory without XP retention, test puppet boss presence, dynamic HTTP audio streaming port (`eventAudioPort`), external streaming URLs, and team wipe timers.
- [`config/fracturedutils-client.json`](docs/client_guide.md#client-configuration): Configures video audio volume, operator telemetry monitor position, opacity, scale, and HUD visibility.

---

## Building from Source

### Prerequisites
- Java Development Kit (JDK) 17+
- Gradle 8.0+ (or use included `gradlew` wrapper)

### Build Command
```powershell
./gradlew build
```
Built JAR binaries will be output to `build/libs/`.

---

## License

This project is licensed under the LGPL v3.0 License. See [LICENSE.txt](LICENSE.txt) for details.

