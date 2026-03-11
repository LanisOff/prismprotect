package dev.lanis.prismprotect.database;

public class LookupParams {

    public String player;
    public long   since;
    public String world;
    public int minX = Integer.MIN_VALUE, maxX = Integer.MAX_VALUE;
    public int minY = Integer.MIN_VALUE, maxY = Integer.MAX_VALUE;
    public int minZ = Integer.MIN_VALUE, maxZ = Integer.MAX_VALUE;
    public int actionFilter = -1;
    public int limit = 50;

    public static LookupParams ofRadius(String world, int cx, int cy, int cz, int r) {
        LookupParams p = new LookupParams();
        p.world = world;
        p.minX = cx - r; p.maxX = cx + r;
        p.minY = cy - r; p.maxY = cy + r;
        p.minZ = cz - r; p.maxZ = cz + r;
        return p;
    }

    public boolean hasBox() {
        return minX != Integer.MIN_VALUE;
    }
}
