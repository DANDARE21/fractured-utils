package net.dandare21.fracturedutils.puppet.fsm;

/**
 * Metadata defining a timing phase / state for a puppet action.
 * Allows each action type (Leap Slam, Abyssal Barrage, custom mod actions) to declare
 * its own state names, default durations, visual colors, and execution points.
 */
public record ActionTimingPhase(
        String id,               // "windup", "jump", "duration", "recovery"
        String label,            // "Indicator", "Jump", "Slam", "Recovery", "Barrage", etc.
        int defaultMs,           // Default duration in milliseconds
        int color,               // ARGB rendering color for UI timeline and preview bars
        boolean isExecutionPoint // Whether the transition to this phase is the main execution/hit keyframe
) {}
