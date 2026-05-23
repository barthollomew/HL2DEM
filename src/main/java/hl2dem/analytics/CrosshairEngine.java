package hl2dem.analytics;

import hl2dem.entity.PlayerState;

import java.util.concurrent.atomic.LongAdder;

// Estimates per-player crosshair placement accuracy against opponent hitboxes.
// Runs off the hot path in the analytics virtual thread.
public final class CrosshairEngine {

    private static final int MAX_PLAYERS = 64;

    // Hitbox half-extents in game units (1 unit ~ 1.905 cm in Source 2).
    private static final float HEAD_HW = 9.4f,  HEAD_HH = 9.4f,  HEAD_HD = 4.7f;   // head
    private static final float BODY_HW = 16.8f, BODY_HH = 16.8f, BODY_HD = 33.6f;  // torso AABB

    private final long[] aimed   = new long[MAX_PLAYERS]; // ticks this player has aimed at anyone
    private final long[] hits    = new long[MAX_PLAYERS]; // ticks crosshair was on an opponent

    public void process(PlayerState[] snap) {
        if (snap == null) return;
        for (PlayerState shooter : snap) {
            if (shooter == null || !shooter.isAlive) continue;
            int si = slotOf(shooter.entityId);
            aimed[si]++;
            for (PlayerState target : snap) {
                if (target == null || !target.isAlive) continue;
                if (target.entityId == shooter.entityId) continue;
                if (rayHitsPlayer(shooter, target)) {
                    hits[si]++;
                    break;
                }
            }
        }
    }

    // Simple ray-AABB intersection: shooter aim ray vs. target head + body AABB.
    private boolean rayHitsPlayer(PlayerState shooter, PlayerState target) {
        // Forward unit vector from yaw/pitch.
        double yawRad   = Math.toRadians(shooter.yaw);
        double pitchRad = Math.toRadians(shooter.pitch);
        double cosPitch = Math.cos(pitchRad);
        float dx = (float) (cosPitch * Math.cos(yawRad));
        float dy = (float) (cosPitch * Math.sin(yawRad));
        float dz = (float) -Math.sin(pitchRad);

        float ox = shooter.x, oy = shooter.y, oz = shooter.z + 64f; // eye offset

        // Check head AABB.
        float hx = target.x, hy = target.y, hz = target.z + 64f + HEAD_HD;
        if (rayHitsBox(ox, oy, oz, dx, dy, dz, hx, hy, hz, HEAD_HW, HEAD_HH, HEAD_HD)) {
            return true;
        }
        // Check body AABB.
        float bz = target.z + BODY_HD;
        return rayHitsBox(ox, oy, oz, dx, dy, dz, target.x, target.y, bz, BODY_HW, BODY_HH, BODY_HD);
    }

    // Slab-based AABB ray intersection test. Returns true if the ray hits the box.
    private boolean rayHitsBox(float ox, float oy, float oz,
                                float dx, float dy, float dz,
                                float cx, float cy, float cz,
                                float hw, float hh, float hd) {
        float tMinX = (cx - hw - ox) / (dx == 0 ? 1e-6f : dx);
        float tMaxX = (cx + hw - ox) / (dx == 0 ? 1e-6f : dx);
        if (tMinX > tMaxX) { float t = tMinX; tMinX = tMaxX; tMaxX = t; }

        float tMinY = (cy - hh - oy) / (dy == 0 ? 1e-6f : dy);
        float tMaxY = (cy + hh - oy) / (dy == 0 ? 1e-6f : dy);
        if (tMinY > tMaxY) { float t = tMinY; tMinY = tMaxY; tMaxY = t; }

        float tMinZ = (cz - hd - oz) / (dz == 0 ? 1e-6f : dz);
        float tMaxZ = (cz + hd - oz) / (dz == 0 ? 1e-6f : dz);
        if (tMinZ > tMaxZ) { float t = tMinZ; tMinZ = tMaxZ; tMaxZ = t; }

        float tEnter = Math.max(tMinX, Math.max(tMinY, tMinZ));
        float tExit  = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));

        return tExit > tEnter && tExit > 0 && tEnter < 8192f; // max aim range 8192 units
    }

    // Returns accuracy [0.0, 1.0] for the player with this entity ID.
    public float accuracy(int entityId) {
        int s = slotOf(entityId);
        long a = aimed[s];
        return a == 0 ? 0f : (float) hits[s] / a;
    }

    private static int slotOf(int entityId) {
        return entityId & (MAX_PLAYERS - 1);
    }
}
