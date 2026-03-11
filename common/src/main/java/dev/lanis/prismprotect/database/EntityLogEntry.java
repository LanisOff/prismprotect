package dev.lanis.prismprotect.database;

public class EntityLogEntry {

    public static final int ACTION_KILL  = 0;
    public static final int ACTION_SPAWN = 1;

    public int     id;
    public long    time;
    public String  player;
    public String  world;
    public double  x, y, z;
    public String  entityType;
    public String  entityData;
    public int     action;
    public boolean rolledBack;

    public EntityLogEntry() {}

    public EntityLogEntry(long time, String player, String world,
                          double x, double y, double z,
                          String entityType, String entityData, int action) {
        this.time       = time;
        this.player     = player;
        this.world      = world;
        this.x = x; this.y = y; this.z = z;
        this.entityType = entityType;
        this.entityData = entityData;
        this.action     = action;
    }

    public String actionName() {
        return switch (action) {
            case ACTION_KILL  -> "killed";
            case ACTION_SPAWN -> "spawned";
            default -> "modified";
        };
    }
}
