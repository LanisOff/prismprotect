# Needs Architectury

## Like this mod to support me

## **Item rollback will be added in the next update(aprx March 13-15)!**

**PrismProtect is a server-side Architectury mod for Minecraft 1.20.1 that provides CoreProtect-like logging and rollback — without plugins, built for modded servers and high performance.**

---

## Features

Logs block break/place/replace with full BlockEntity NBT support

Tracks explosions (TNT, creepers, vanilla explosion pipeline)  
**Some mods with custom explosion logic can bypass vanilla hooks and not be logged**

Logs entity deaths with complete NBT snapshot for accurate restoration

Records container and inventory changes with menu open/close diff tracking

Supports blocks and entities from **any mod** — namespaced IDs are stored as-is

Optimized SQLite storage with WAL journaling and thread-safe writes

Inspect mode — click or break blocks to view full change history

---

## Commands

```
/pp inspect
```

Toggles inspect mode to view block history by clicking blocks

```
/pp lookup [u:<name>] [t:<time>] [r:<radius>] [a:block|entity|container]
```

Searches logged actions using filters (`a:block` is used by default)

```
/pp rollback [u:<name>] [t:<time>] [r:<radius>] [a:entity]
```

Reverts changes within the selected time/radius.  
Without `a:entity`, rolls back block + container changes

```
/pp restore [u:<name>] [t:<time>] [r:<radius>]
```

Re-applies previously rolled-back block/container changes

```
/pp purge t:<time>
```

Deletes log data older than the specified time (requires op level 4)

```
/pp status
```

Shows database counters and mod runtime status

---

## Time Format

Supports `s` `m` `h` `d` `w` — for example: `1h30m`, `2d`, `1w`, `30m`

---

## Entity Rollback

Entity rollback is available as a **separate argument** — add `a:entity` to rollback.  
PrismProtect re-spawns entities at original coordinates using the full NBT snapshot captured at death.

---

## Storage

PrismProtect uses **SQLite** (bundled — no external database required).  
The database is stored at `config/prismprotect/prismprotect.db`.  
WAL journaling ensures safe concurrent access during active play.

---

## Version Support

| Minecraft | Loader | Status |
|-----------|--------|--------|
| 1.20.1    | Forge 47.2.0+ | ✅ Supported |
| 1.20.1    | Fabric Loader 0.14.25+ + Fabric API 0.92.2+1.20.1 | ✅ Supported |
| 1.20.x    | Other builds | ⚠️ Not tested |

> Server-side only. Clients do **not** need PrismProtect installed to join.