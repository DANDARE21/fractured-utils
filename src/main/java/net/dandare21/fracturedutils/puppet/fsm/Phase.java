package net.dandare21.fracturedutils.puppet.fsm;

/**
 * Lifecycle phases for the Boss Puppet Finite State Machine.
 * Standard flow: WINDUP -> ACTIVE -> RECOVERY -> IDLE
 */
public enum Phase {
    IDLE,
    WINDUP,
    ACTIVE,
    RECOVERY;

    public boolean isBusy() {
        return this != IDLE;
    }
}
