# Event Waiting Room & Server Maintenance Mode

This guide covers two server coordination features: the **Pre-Event Waiting Room Lobby** for gathering players before a match or raid, and **Server Maintenance Mode** for locking down the server to non-OP players during setup.

---

## 1. Event Waiting Room (Lobby System)

The **Waiting Room** creates a pre-event lobby where players gather, ready up, and view countdown timers before an event or boss raid begins.

```
                      ┌───────────────────────────────────────────┐
                      │    WaitingRoomManager (Server Singleton)  │
                      │  • Title, Countdown Timer, Player List    │
                      └─────────────────────┬─────────────────────┘
                                            │ SyncWaitingRoomStateS2CPacket
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              WaitingRoomScreen (Client UI)                             │
│                                                                                        │
│   ┌───────────────────────────┐                     ┌───────────────────────────────┐  │
│   │   Event Header & Clock    │                     │       Player Roster List      │  │
│   │  • Custom Event Title     │                     │  • Connected Players          │  │
│   │  • Large Countdown Timer  │                     │  • Ready Check Toggles [✓]    │  │
│   └───────────────────────────┘                     │  • Operator Controls          │  │
│   ┌───────────────────────────┐                     └───────────────────────────────┘  │
│   │     Integrated Chat Box   │                     ┌───────────────────────────────┐  │
│   │  • Live lobby messaging   │                     │     Hold-to-Join Handler      │  │
│   │  • Filtered event feed    │                     │  • Hold 'J' for 1.5 seconds   │  │
│   └───────────────────────────┘                     │  • Rising audio confirmation  │  │
│                                                     └───────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Key Features
1. **Hold-to-Join Mechanic**:
   - Regular players press and **hold `J`** for 1.5 seconds (`key.fractured_utils.waiting_room`) to open the lobby screen.
   - An experience orb chime plays with rising pitch ($0.8 \to 1.6$) until full.
   - Operators with permission level 2+ can tap `J` once to immediately enter.
2. **Player Protection**:
   - While joined in a waiting room, players are completely immune to mob attacks, PVP, and environmental damage (`LivingAttackEvent` cancellation).
3. **Ready Check System**:
   - Players click the "Ready" toggle in the UI to confirm their gear and status. Operators can monitor who is ready at a glance.
4. **Synchronized Countdown**:
   - Operators can trigger a visual countdown (e.g. 10 seconds). All players see a synchronized HUD countdown before the room closes and the event begins.

### Waiting Room Commands (`/waitingroom` or `/wr`)

All commands require OP Permission Level 2:

| Command | Usage | Description |
| :--- | :--- | :--- |
| `start` | `/waitingroom start [title]` | Activates the waiting room with a custom title. |
| `countdown` | `/waitingroom countdown [seconds]` | Starts a countdown timer (1 to 300 seconds). |
| `stop` | `/waitingroom stop` | Closes the waiting room and releases players. |
| `join` | `/waitingroom join` | Joins the active waiting room (available to all players). |
| `leave` | `/waitingroom leave` | Leaves the waiting room. |
| `status` | `/waitingroom status` | Displays room state, title, and connected player count. |

```mcfunction
# Start raid lobby
/wr start "Abyssal Raid: Stage 1"

# Trigger a 15-second countdown
/wr countdown 15
```

---

## 2. Server Maintenance Mode

**Maintenance Mode** enables server administrators to lock down a production server for maintenance, map building, or event preparation without shutting down the server process.

### How It Works
- When activated, all currently connected **non-OP players are immediately kicked** with a custom reason.
- Any non-OP player attempting to connect while maintenance mode is active is **instantly rejected during login**.
- Operators (permission level 2+) can connect and operate freely.
- The state persists until explicitly deactivated.

### Maintenance Commands (`/maintenance` or `/maint`)

All commands require OP Permission Level 2:

| Command | Usage | Description |
| :--- | :--- | :--- |
| `start` / `enable` | `/maintenance start [reason]` | Activates maintenance mode and kicks non-OP players. |
| `stop` / `disable` | `/maintenance stop` | Deactivates maintenance mode and reopens the server. |
| `status` | `/maintenance status` | Checks if maintenance mode is currently active. |

### Examples
```mcfunction
# Lock down server with custom kick notice
/maintenance start Server is undergoing scheduled event maintenance. Reopening at 18:00 UTC!

# Reopen server
/maintenance stop
```

