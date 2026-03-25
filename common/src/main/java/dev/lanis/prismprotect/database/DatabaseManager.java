package dev.lanis.prismprotect.database;

import dev.lanis.prismprotect.PrismProtect;
import dev.lanis.prismprotect.handler.ItemProvenanceTracker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DatabaseManager {

    private static final String DB_FILE = "prismprotect.db";

    private final Path dir;
    private Connection conn;

    public DatabaseManager(Path dir) {
        this.dir = dir;
    }

    public void initialize() {
        try {
            Files.createDirectories(dir);
            Class.forName("org.sqlite.JDBC");

            Path dbPath = dir.resolve(DB_FILE).toAbsolutePath();
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            try (Statement statement = conn.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
            }

            createTables();
            PrismProtect.LOGGER.info("SQLite DB at {}/{}", dir, DB_FILE);
        } catch (Exception ex) {
            PrismProtect.LOGGER.error("DB init failed", ex);
        }
    }

    private synchronized void createTables() throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS block_log (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        time        INTEGER NOT NULL,
                        player      TEXT    NOT NULL,
                        world       TEXT    NOT NULL,
                        x           INTEGER NOT NULL,
                        y           INTEGER NOT NULL,
                        z           INTEGER NOT NULL,
                        block_type  TEXT    NOT NULL,
                        block_data  TEXT,
                        action      INTEGER NOT NULL,
                        rolled_back INTEGER NOT NULL DEFAULT 0
                    )""");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS entity_log (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        time        INTEGER NOT NULL,
                        player      TEXT    NOT NULL,
                        world       TEXT    NOT NULL,
                        x           REAL    NOT NULL,
                        y           REAL    NOT NULL,
                        z           REAL    NOT NULL,
                        entity_type TEXT    NOT NULL,
                        entity_data TEXT,
                        action      INTEGER NOT NULL,
                        rolled_back INTEGER NOT NULL DEFAULT 0
                    )""");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS container_log (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        time        INTEGER NOT NULL,
                        player      TEXT    NOT NULL,
                        world       TEXT    NOT NULL,
                        x           INTEGER NOT NULL,
                        y           INTEGER NOT NULL,
                        z           INTEGER NOT NULL,
                        item_type   TEXT    NOT NULL,
                        amount      INTEGER NOT NULL,
                        item_data   TEXT,
                        action      INTEGER NOT NULL,
                        rolled_back INTEGER NOT NULL DEFAULT 0
                    )""");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_log (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        time        INTEGER NOT NULL,
                        player      TEXT    NOT NULL,
                        world       TEXT    NOT NULL,
                        x           INTEGER NOT NULL,
                        y           INTEGER NOT NULL,
                        z           INTEGER NOT NULL,
                        item_type   TEXT    NOT NULL,
                        amount      INTEGER NOT NULL,
                        item_data   TEXT,
                        action      INTEGER NOT NULL DEFAULT 0,
                        source      INTEGER NOT NULL,
                        group_id    INTEGER NOT NULL DEFAULT 0,
                        provenance_data TEXT,
                        rolled_back INTEGER NOT NULL DEFAULT 0
                    )""");

            statement.execute("CREATE INDEX IF NOT EXISTS idx_bl_xyz  ON block_log     (world,x,y,z)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_bl_time ON block_log     (time)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_el_time ON entity_log    (world,time)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_cl_xyz  ON container_log (world,x,y,z)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_il_xyz  ON item_log      (world,x,y,z)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_il_time ON item_log      (time)");

            try {
                statement.execute("ALTER TABLE container_log ADD COLUMN rolled_back INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
            }
            try {
                statement.execute("ALTER TABLE item_log ADD COLUMN action INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
            }
            try {
                statement.execute("ALTER TABLE item_log ADD COLUMN group_id INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
            }
            try {
                statement.execute("ALTER TABLE item_log ADD COLUMN provenance_data TEXT");
            } catch (SQLException ignored) {
            }
        }
    }

    public void logBlock(BlockLogEntry entry) {
        exec(
                "INSERT INTO block_log (time,player,world,x,y,z,block_type,block_data,action) VALUES(?,?,?,?,?,?,?,?,?)",
                entry.time,
                entry.player,
                entry.world,
                entry.x,
                entry.y,
                entry.z,
                entry.blockType,
                entry.blockData,
                entry.action
        );
    }

    public List<BlockLogEntry> lookupBlocks(LookupParams params) {
        return queryBlocks(buildBlockSelect(params, false, false), params);
    }

    public List<BlockLogEntry> getForRollback(LookupParams params) {
        return queryBlocks(buildBlockSelect(params, true, false), params);
    }

    public List<BlockLogEntry> getForRestore(LookupParams params) {
        return queryBlocks(buildBlockSelect(params, false, true), params);
    }

    private String buildBlockSelect(LookupParams params, boolean onlyNotRolledBack, boolean onlyRolledBack) {
        StringBuilder sql = new StringBuilder(
                "SELECT id,time,player,world,x,y,z,block_type,block_data,action,rolled_back FROM block_log WHERE 1=1"
        );

        if (onlyNotRolledBack) {
            sql.append(" AND rolled_back=0");
        }
        if (onlyRolledBack) {
            sql.append(" AND rolled_back=1");
        }

        appendBlockFilters(sql, params);
        sql.append(" ORDER BY time ").append(onlyRolledBack ? "ASC" : "DESC");
        sql.append(" LIMIT ").append(params.limit);

        return sql.toString();
    }

    private void appendBlockFilters(StringBuilder sql, LookupParams params) {
        if (params.player != null) {
            sql.append(" AND player=?");
        }
        if (params.since > 0) {
            sql.append(" AND time>=?");
        }
        if (params.world != null) {
            sql.append(" AND world=?");
        }
        if (params.hasBox()) {
            sql.append(" AND x>=? AND x<=? AND y>=? AND y<=? AND z>=? AND z<=?");
        }
        if (params.actionFilter >= 0) {
            sql.append(" AND action=?");
        }
    }

    private synchronized List<BlockLogEntry> queryBlocks(String sql, LookupParams params) {
        List<BlockLogEntry> results = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = fillParams(ps, params, 1);
            if (params.actionFilter >= 0) {
                ps.setInt(index, params.actionFilter);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(readBlockEntry(rs));
                }
            }
        } catch (SQLException ex) {
            PrismProtect.LOGGER.error("lookupBlocks failed", ex);
        }

        return results;
    }

    private BlockLogEntry readBlockEntry(ResultSet rs) throws SQLException {
        BlockLogEntry entry = new BlockLogEntry();
        entry.id = rs.getInt("id");
        entry.time = rs.getLong("time");
        entry.player = rs.getString("player");
        entry.world = rs.getString("world");
        entry.x = rs.getInt("x");
        entry.y = rs.getInt("y");
        entry.z = rs.getInt("z");
        entry.blockType = rs.getString("block_type");
        entry.blockData = rs.getString("block_data");
        entry.action = rs.getInt("action");
        entry.rolledBack = rs.getInt("rolled_back") == 1;
        return entry;
    }

    public void setBlockRolledBack(int id, boolean rolledBack) {
        exec("UPDATE block_log SET rolled_back=? WHERE id=?", rolledBack ? 1 : 0, id);
    }

    public void logEntity(EntityLogEntry entry) {
        exec(
                "INSERT INTO entity_log (time,player,world,x,y,z,entity_type,entity_data,action) VALUES(?,?,?,?,?,?,?,?,?)",
                entry.time,
                entry.player,
                entry.world,
                entry.x,
                entry.y,
                entry.z,
                entry.entityType,
                entry.entityData,
                entry.action
        );
    }

    public synchronized List<EntityLogEntry> lookupEntities(LookupParams params) {
        StringBuilder sql = new StringBuilder(
                "SELECT id,time,player,world,x,y,z,entity_type,entity_data,action,rolled_back FROM entity_log WHERE 1=1"
        );

        if (params.player != null) {
            sql.append(" AND player=?");
        }
        if (params.since > 0) {
            sql.append(" AND time>=?");
        }
        if (params.world != null) {
            sql.append(" AND world=?");
        }

        sql.append(" ORDER BY time DESC LIMIT ").append(params.limit);

        List<EntityLogEntry> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int index = 1;
            if (params.player != null) {
                ps.setString(index++, params.player);
            }
            if (params.since > 0) {
                ps.setLong(index++, params.since);
            }
            if (params.world != null) {
                ps.setString(index++, params.world);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EntityLogEntry entry = new EntityLogEntry();
                    entry.id = rs.getInt("id");
                    entry.time = rs.getLong("time");
                    entry.player = rs.getString("player");
                    entry.world = rs.getString("world");
                    entry.x = rs.getDouble("x");
                    entry.y = rs.getDouble("y");
                    entry.z = rs.getDouble("z");
                    entry.entityType = rs.getString("entity_type");
                    entry.entityData = rs.getString("entity_data");
                    entry.action = rs.getInt("action");
                    entry.rolledBack = rs.getInt("rolled_back") == 1;
                    results.add(entry);
                }
            }
        } catch (SQLException ex) {
            PrismProtect.LOGGER.error("lookupEntities failed", ex);
        }

        return results;
    }

    public synchronized List<EntityLogEntry> getEntitiesForRollback(LookupParams params) {
        StringBuilder sql = new StringBuilder(
                "SELECT id,time,player,world,x,y,z,entity_type,entity_data,action FROM entity_log WHERE rolled_back=0 AND action=0"
        );

        if (params.player != null) {
            sql.append(" AND player=?");
        }
        if (params.since > 0) {
            sql.append(" AND time>=?");
        }
        if (params.world != null) {
            sql.append(" AND world=?");
        }
        if (params.hasBox()) {
            sql.append(" AND x>=? AND x<=? AND z>=? AND z<=?");
        }

        sql.append(" ORDER BY time DESC LIMIT ").append(params.limit);

        List<EntityLogEntry> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int index = 1;
            if (params.player != null) {
                ps.setString(index++, params.player);
            }
            if (params.since > 0) {
                ps.setLong(index++, params.since);
            }
            if (params.world != null) {
                ps.setString(index++, params.world);
            }
            if (params.hasBox()) {
                ps.setDouble(index++, params.minX);
                ps.setDouble(index++, params.maxX);
                ps.setDouble(index++, params.minZ);
                ps.setDouble(index++, params.maxZ);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EntityLogEntry entry = new EntityLogEntry();
                    entry.id = rs.getInt("id");
                    entry.time = rs.getLong("time");
                    entry.player = rs.getString("player");
                    entry.world = rs.getString("world");
                    entry.x = rs.getDouble("x");
                    entry.y = rs.getDouble("y");
                    entry.z = rs.getDouble("z");
                    entry.entityType = rs.getString("entity_type");
                    entry.entityData = rs.getString("entity_data");
                    entry.action = rs.getInt("action");
                    results.add(entry);
                }
            }
        } catch (SQLException ex) {
            PrismProtect.LOGGER.error("getEntitiesForRollback failed", ex);
        }

        return results;
    }

    public void setEntityRolledBack(int id, boolean rolledBack) {
        exec("UPDATE entity_log SET rolled_back=? WHERE id=?", rolledBack ? 1 : 0, id);
    }

    public void logContainer(ContainerLogEntry entry) {
        exec(
                "INSERT INTO container_log (time,player,world,x,y,z,item_type,amount,item_data,action,rolled_back) VALUES(?,?,?,?,?,?,?,?,?,?,0)",
                entry.time,
                entry.player,
                entry.world,
                entry.x,
                entry.y,
                entry.z,
                entry.itemType,
                entry.amount,
                entry.itemData,
                entry.action
        );
    }

    public void logItem(ItemLogEntry entry) {
        exec(
                "INSERT INTO item_log (time,player,world,x,y,z,item_type,amount,item_data,action,source,group_id,provenance_data,rolled_back) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                entry.time,
                entry.player,
                entry.world,
                entry.x,
                entry.y,
                entry.z,
                entry.itemType,
                entry.amount,
                entry.itemData,
                entry.action,
                entry.source,
                entry.groupId,
                entry.provenanceData
        );
    }

    public List<ContainerLogEntry> lookupContainer(String world, int x, int y, int z) {
        return queryContainers(
                "SELECT * FROM container_log WHERE world=? AND x=? AND y=? AND z=? ORDER BY time DESC LIMIT 50",
                world,
                x,
                y,
                z
        );
    }

    public List<ContainerLogEntry> lookupContainerByParams(LookupParams params) {
        StringBuilder sql = new StringBuilder("SELECT * FROM container_log WHERE 1=1");
        if (params.player != null) {
            sql.append(" AND player=?");
        }
        if (params.since > 0) {
            sql.append(" AND time>=?");
        }
        if (params.world != null) {
            sql.append(" AND world=?");
        }
        if (params.hasBox()) {
            sql.append(" AND x>=? AND x<=? AND y>=? AND y<=? AND z>=? AND z<=?");
        }
        sql.append(" ORDER BY time DESC LIMIT ").append(params.limit);

        return queryContainersPS(sql.toString(), params);
    }

    public List<ContainerLogEntry> getContainerForRollback(LookupParams params) {
        return buildContainerQuery(params, false);
    }

    public List<ContainerLogEntry> getContainerForRestore(LookupParams params) {
        return buildContainerQuery(params, true);
    }

    private List<ContainerLogEntry> buildContainerQuery(LookupParams params, boolean rolledBackOnly) {
        StringBuilder sql = new StringBuilder("SELECT * FROM container_log WHERE 1=1");
        sql.append(rolledBackOnly ? " AND rolled_back=1" : " AND rolled_back=0");

        if (params.player != null) {
            sql.append(" AND player=?");
        }
        if (params.since > 0) {
            sql.append(" AND time>=?");
        }
        if (params.world != null) {
            sql.append(" AND world=?");
        }
        if (params.hasBox()) {
            sql.append(" AND x>=? AND x<=? AND y>=? AND y<=? AND z>=? AND z<=?");
        }

        sql.append(rolledBackOnly ? " ORDER BY time ASC" : " ORDER BY time DESC");
        sql.append(" LIMIT ").append(params.limit);

        return queryContainersPS(sql.toString(), params);
    }

    private synchronized List<ContainerLogEntry> queryContainersPS(String sql, LookupParams params) {
        List<ContainerLogEntry> results = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            if (params.player != null) {
                ps.setString(index++, params.player);
            }
            if (params.since > 0) {
                ps.setLong(index++, params.since);
            }
            if (params.world != null) {
                ps.setString(index++, params.world);
            }
            if (params.hasBox()) {
                ps.setInt(index++, params.minX);
                ps.setInt(index++, params.maxX);
                ps.setInt(index++, params.minY);
                ps.setInt(index++, params.maxY);
                ps.setInt(index++, params.minZ);
                ps.setInt(index++, params.maxZ);
            }

            results = readContainerRows(ps);
        } catch (SQLException ex) {
            PrismProtect.LOGGER.error("queryContainersPS failed", ex);
        }

        return results;
    }

    private synchronized List<ContainerLogEntry> queryContainers(String sql, Object... params) {
        List<ContainerLogEntry> results = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            results = readContainerRows(ps);
        } catch (SQLException ex) {
            PrismProtect.LOGGER.error("queryContainers failed", ex);
        }

        return results;
    }

    private List<ContainerLogEntry> readContainerRows(PreparedStatement ps) throws SQLException {
        List<ContainerLogEntry> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ContainerLogEntry entry = new ContainerLogEntry();
                entry.id = rs.getInt("id");
                entry.time = rs.getLong("time");
                entry.player = rs.getString("player");
                entry.world = rs.getString("world");
                entry.x = rs.getInt("x");
                entry.y = rs.getInt("y");
                entry.z = rs.getInt("z");
                entry.itemType = rs.getString("item_type");
                entry.amount = rs.getInt("amount");
                entry.itemData = rs.getString("item_data");
                entry.action = rs.getInt("action");
                entry.rolledBack = rs.getInt("rolled_back") == 1;
                results.add(entry);
            }
        }
        return results;
    }

    public void setContainerRolledBack(int id, boolean rolledBack) {
        exec("UPDATE container_log SET rolled_back=? WHERE id=?", rolledBack ? 1 : 0, id);
    }

    public synchronized long containerLogCount() {
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM container_log")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException ignored) {
        }
        return 0;
    }

    public List<ItemLogEntry> getItemsForRollback(LookupParams params) {
        return buildItemQuery(params, false);
    }

    public List<ItemLogEntry> getItemsForRestore(LookupParams params) {
        return buildItemQuery(params, true);
    }

    private List<ItemLogEntry> buildItemQuery(LookupParams params, boolean rolledBackOnly) {
        List<ItemLogEntry> base = queryItemsPS(buildItemBaseQuery(rolledBackOnly), params);
        if (!params.hasBox()) {
            return trimItems(base, params.limit, rolledBackOnly);
        }

        Set<Long> matchingCraftGroups = new LinkedHashSet<>();
        for (ItemLogEntry entry : base) {
            if (entry.source != ItemLogEntry.SOURCE_CRAFT
                    || entry.action != ItemLogEntry.ACTION_REMOVE
                    || entry.groupId == 0L) {
                continue;
            }

            if (ItemProvenanceTracker.matchesArea(
                    entry.provenanceData,
                    params.world,
                    params.minX,
                    params.maxX,
                    params.minY,
                    params.maxY,
                    params.minZ,
                    params.maxZ
            )) {
                matchingCraftGroups.add(entry.groupId);
            }
        }

        List<ItemLogEntry> filtered = new ArrayList<>();
        for (ItemLogEntry entry : base) {
            if (entry.source == ItemLogEntry.SOURCE_CRAFT) {
                if (entry.groupId != 0L && matchingCraftGroups.contains(entry.groupId)) {
                    filtered.add(entry);
                }
                continue;
            }

            if (matchesEventArea(entry, params)) {
                filtered.add(entry);
            }
        }

        return trimItems(filtered, params.limit, rolledBackOnly);
    }

    private String buildItemBaseQuery(boolean rolledBackOnly) {
        StringBuilder sql = new StringBuilder("SELECT * FROM item_log WHERE 1=1");
        sql.append(rolledBackOnly ? " AND rolled_back=1" : " AND rolled_back=0");

        sql.append(" AND (? IS NULL OR player=?)");
        sql.append(" AND (? <= 0 OR time>=?)");
        sql.append(" ORDER BY time ").append(rolledBackOnly ? "ASC" : "DESC");
        return sql.toString();
    }

    private synchronized List<ItemLogEntry> queryItemsPS(String sql, LookupParams params) {
        List<ItemLogEntry> results = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, params.player);
            ps.setString(2, params.player);
            ps.setLong(3, params.since);
            ps.setLong(4, params.since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(readItemEntry(rs));
                }
            }
        } catch (SQLException ex) {
            PrismProtect.LOGGER.error("queryItemsPS failed", ex);
        }

        return results;
    }

    private ItemLogEntry readItemEntry(ResultSet rs) throws SQLException {
        ItemLogEntry entry = new ItemLogEntry();
        entry.id = rs.getInt("id");
        entry.time = rs.getLong("time");
        entry.player = rs.getString("player");
        entry.world = rs.getString("world");
        entry.x = rs.getInt("x");
        entry.y = rs.getInt("y");
        entry.z = rs.getInt("z");
        entry.itemType = rs.getString("item_type");
        entry.amount = rs.getInt("amount");
        entry.itemData = rs.getString("item_data");
        entry.action = rs.getInt("action");
        entry.source = rs.getInt("source");
        entry.groupId = rs.getLong("group_id");
        entry.provenanceData = rs.getString("provenance_data");
        entry.rolledBack = rs.getInt("rolled_back") == 1;
        return entry;
    }

    public void setItemRolledBack(int id, boolean rolledBack) {
        exec("UPDATE item_log SET rolled_back=? WHERE id=?", rolledBack ? 1 : 0, id);
    }

    public synchronized int purgeOlderThan(long cutoffMs) {
        int deleted = 0;

        for (String table : new String[]{"block_log", "entity_log", "container_log", "item_log"}) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE time<?")) {
                ps.setLong(1, cutoffMs);
                deleted += ps.executeUpdate();
            } catch (SQLException ex) {
                PrismProtect.LOGGER.error("purge {} failed", table, ex);
            }
        }

        return deleted;
    }

    public synchronized long blockLogCount() {
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM block_log")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException ignored) {
        }
        return 0;
    }

    public synchronized long entityLogCount() {
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM entity_log")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException ignored) {
        }
        return 0;
    }

    public synchronized long itemLogCount() {
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM item_log")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException ignored) {
        }
        return 0;
    }

    private boolean matchesEventArea(ItemLogEntry entry, LookupParams params) {
        if (params.world != null && !params.world.equals(entry.world)) {
            return false;
        }

        return entry.x >= params.minX && entry.x <= params.maxX
                && entry.y >= params.minY && entry.y <= params.maxY
                && entry.z >= params.minZ && entry.z <= params.maxZ;
    }

    private List<ItemLogEntry> trimItems(List<ItemLogEntry> items, int limit, boolean rolledBackOnly) {
        if (items.size() <= limit) {
            return items;
        }

        return new ArrayList<>(items.subList(0, limit));
    }

    private int fillParams(PreparedStatement ps, LookupParams params, int startIndex) throws SQLException {
        int index = startIndex;

        if (params.player != null) {
            ps.setString(index++, params.player);
        }
        if (params.since > 0) {
            ps.setLong(index++, params.since);
        }
        if (params.world != null) {
            ps.setString(index++, params.world);
        }
        if (params.hasBox()) {
            ps.setInt(index++, params.minX);
            ps.setInt(index++, params.maxX);
            ps.setInt(index++, params.minY);
            ps.setInt(index++, params.maxY);
            ps.setInt(index++, params.minZ);
            ps.setInt(index++, params.maxZ);
        }

        return index;
    }

    private synchronized void exec(String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException ex) {
            PrismProtect.LOGGER.error("DB exec failed: {}", sql, ex);
        }
    }

    public synchronized void close() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException ex) {
            PrismProtect.LOGGER.error("DB close failed", ex);
        }
    }
}
