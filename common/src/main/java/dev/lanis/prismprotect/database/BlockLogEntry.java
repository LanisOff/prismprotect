package dev.lanis.prismprotect.database;

public class BlockLogEntry {

    public static final int ACTION_BREAK   = 0;
    public static final int ACTION_PLACE   = 1;
    public static final int ACTION_INTERACT = 2;
    public static final int ACTION_EXPLODE = 3;
    public static final int ACTION_BURN    = 4;

    public int    id;
    public long   time;
    public String player;
    public String world;
    public int    x, y, z;
    public String blockType;
    public String blockData;
    public int    action;
    public boolean rolledBack;

    public BlockLogEntry() {}

    public BlockLogEntry(long time, String player, String world,
                         int x, int y, int z,
                         String blockType, String blockData, int action) {
        this.time      = time;
        this.player    = player;
        this.world     = world;
        this.x = x; this.y = y; this.z = z;
        this.blockType = blockType;
        this.blockData = blockData;
        this.action    = action;
    }

    public String actionName() {
        return switch (action) {
            case ACTION_BREAK   -> "broke";
            case ACTION_PLACE   -> "placed";
            case ACTION_INTERACT -> "interacted with";
            case ACTION_EXPLODE -> "exploded";
            case ACTION_BURN    -> "burned";
            default -> "modified";
        };
    }
}
