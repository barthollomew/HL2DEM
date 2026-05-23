package hl2dem.entity;

public final class EntityContext {

    public static final int MAX_ENTITIES = 2048;
    private static final int MAX_PLAYERS = 64;

    // Entity slot array indexed by entity ID. Most slots are null.
    private final PlayerState[] slots = new PlayerState[MAX_ENTITIES];

    // Compact list of player entity IDs for fast iteration.
    private final int[] playerEntityIds = new int[MAX_PLAYERS];
    private int playerCount = 0;

    public PlayerState getOrCreate(int entityId) {
        if (entityId < 0 || entityId >= MAX_ENTITIES) return null;
        PlayerState s = slots[entityId];
        if (s == null) {
            s = new PlayerState();
            s.entityId = entityId;
            slots[entityId] = s;
            if (playerCount < MAX_PLAYERS) {
                playerEntityIds[playerCount++] = entityId;
            }
        }
        return s;
    }

    public PlayerState get(int entityId) {
        if (entityId < 0 || entityId >= MAX_ENTITIES) return null;
        return slots[entityId];
    }

    public void remove(int entityId) {
        if (entityId < 0 || entityId >= MAX_ENTITIES) return;
        if (slots[entityId] != null) {
            slots[entityId] = null;
            // Compact the player ID list.
            for (int i = 0; i < playerCount; i++) {
                if (playerEntityIds[i] == entityId) {
                    playerEntityIds[i] = playerEntityIds[--playerCount];
                    break;
                }
            }
        }
    }

    // Returns a deep copy of all active player states. Allocates intentionally -
    // this copy is produced once per tick and consumed off the hot path.
    public PlayerState[] snapshot() {
        PlayerState[] snap = new PlayerState[playerCount];
        for (int i = 0; i < playerCount; i++) {
            PlayerState s = slots[playerEntityIds[i]];
            snap[i] = s != null ? s.copy() : null;
        }
        return snap;
    }

    public int playerCount() {
        return playerCount;
    }
}
