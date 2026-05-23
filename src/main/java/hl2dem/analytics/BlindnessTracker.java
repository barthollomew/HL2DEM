package hl2dem.analytics;

import hl2dem.entity.PlayerState;

// Tracks flash grenade blindness per player.
// Detects flash onset and falloff, emits BlindEvent records, maintains last blind duration.
public final class BlindnessTracker {

    private static final int MAX_PLAYERS = 64;
    private static final float TICKS_PER_SECOND = 64f; // CS2 tick rate

    private final float[] prevFlash    = new float[MAX_PLAYERS];
    private final int[]   onsetTick    = new int[MAX_PLAYERS];
    private final long[]  lastBlindMs  = new long[MAX_PLAYERS];

    public record BlindEvent(int entityId, long steamId, int tick, long durationMs) {}

    private BlindEvent[] pendingEvents = new BlindEvent[16];
    private int pendingCount;

    public void process(PlayerState[] snap, int tick) {
        pendingCount = 0;
        if (snap == null) return;
        for (PlayerState p : snap) {
            if (p == null) continue;
            int slot = slotOf(p.entityId);
            float prev = prevFlash[slot];
            float curr = p.flashDuration;

            if (prev <= 0f && curr > 0f) {
                // Rising edge: flash started.
                onsetTick[slot] = tick;
            } else if (prev > 0f && curr <= 0f) {
                // Falling edge: flash ended.
                long durationMs = (long) ((tick - onsetTick[slot]) / TICKS_PER_SECOND * 1000);
                lastBlindMs[slot] = durationMs;
                if (pendingCount >= pendingEvents.length) {
                    pendingEvents = java.util.Arrays.copyOf(pendingEvents, pendingEvents.length * 2);
                }
                pendingEvents[pendingCount++] = new BlindEvent(p.entityId, p.steamId, tick, durationMs);
            }
            prevFlash[slot] = curr;
        }
    }

    // Returns the most recently emitted blind events from the last process() call.
    public BlindEvent[] pendingEvents() {
        return pendingEvents;
    }

    public int pendingEventCount() {
        return pendingCount;
    }

    // Returns the last measured blindness duration in ms for a given entity ID.
    public long lastBlindMs(int entityId) {
        return lastBlindMs[slotOf(entityId)];
    }

    // Returns the current flash duration for a given entity ID (from last process() call).
    public float currentFlash(int entityId) {
        return prevFlash[slotOf(entityId)];
    }

    private static int slotOf(int entityId) {
        return entityId & (MAX_PLAYERS - 1);
    }
}
