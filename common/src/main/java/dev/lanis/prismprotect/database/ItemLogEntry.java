package dev.lanis.prismprotect.database;

public class ItemLogEntry {

    public static final int ACTION_ADD = 0;
    public static final int ACTION_REMOVE = 1;

    public static final int SOURCE_BLOCK_DROP = 0;
    public static final int SOURCE_CRAFT = 1;

    public int id;
    public long time;
    public String player;
    public String world;
    public int x, y, z;
    public String itemType;
    public int amount;
    public String itemData;
    public int action;
    public int source;
    public long groupId;
    public String provenanceData;
    public boolean rolledBack;

    public ItemLogEntry() {
    }

    public ItemLogEntry(
            long time,
            String player,
            String world,
            int x,
            int y,
            int z,
            String itemType,
            int amount,
            String itemData,
            int action,
            int source,
            long groupId,
            String provenanceData
    ) {
        this.time = time;
        this.player = player;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.itemType = itemType;
        this.amount = amount;
        this.itemData = itemData;
        this.action = action;
        this.source = source;
        this.groupId = groupId;
        this.provenanceData = provenanceData;
    }

    public String sourceName() {
        return source == SOURCE_CRAFT ? "craft" : "block_drop";
    }

    public String actionName() {
        return action == ACTION_REMOVE ? "remove" : "add";
    }
}
