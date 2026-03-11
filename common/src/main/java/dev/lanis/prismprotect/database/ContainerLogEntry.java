package dev.lanis.prismprotect.database;

public class ContainerLogEntry {

    public static final int ACTION_ADD    = 0;
    public static final int ACTION_REMOVE = 1;

    public int     id;
    public long    time;
    public String  player;
    public String  world;
    public int     x, y, z;
    public String  itemType;
    public int     amount;
    public String  itemData;
    public int     action;
    public boolean rolledBack;

    public ContainerLogEntry() {}

    public ContainerLogEntry(long time, String player, String world,
                             int x, int y, int z,
                             String itemType, int amount, String itemData, int action) {
        this.time     = time;
        this.player   = player;
        this.world    = world;
        this.x = x; this.y = y; this.z = z;
        this.itemType = itemType;
        this.amount   = amount;
        this.itemData = itemData;
        this.action   = action;
    }

    public String actionName() {
        return action == ACTION_ADD ? "put" : "took";
    }
}
