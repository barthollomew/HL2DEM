package hl2dem.entity;

public final class PlayerState {

    public int entityId;
    public long steamId;
    public String name = "";
    public float x, y, z;
    public float yaw, pitch;
    public float flashDuration;
    public int lastUpdateTick;
    public boolean isAlive;

    public PlayerState copy() {
        PlayerState c = new PlayerState();
        c.entityId = this.entityId;
        c.steamId = this.steamId;
        c.name = this.name;
        c.x = this.x;
        c.y = this.y;
        c.z = this.z;
        c.yaw = this.yaw;
        c.pitch = this.pitch;
        c.flashDuration = this.flashDuration;
        c.lastUpdateTick = this.lastUpdateTick;
        c.isAlive = this.isAlive;
        return c;
    }
}
