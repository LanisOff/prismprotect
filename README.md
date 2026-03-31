# NOT WORKING

# PrismProtect

Server-side rollback and investigation tools for modded Minecraft servers (Architectury, Fabric + Forge).

![Stars](https://img.shields.io/github/stars/LanisOff/prismprotect)
![Forks](https://img.shields.io/github/forks/LanisOff/prismprotect)
![Issues](https://img.shields.io/github/issues/LanisOff/prismprotect)
![PRs](https://img.shields.io/github/issues-pr/LanisOff/prismprotect)
![Last Commit](https://img.shields.io/github/last-commit/LanisOff/prismprotect)

## What It Does

PrismProtect logs world changes and gives moderators fast tools to inspect, rollback, and restore damage.

- Block logging (place/break/replace) with BlockEntity/NBT support
- Explosion and fire tracking through vanilla pipelines
- Container and inventory change logging
- Entity death logging with NBT snapshot for restore
- Item rollback support (inventory + nearby drops)
- SQLite storage (bundled) with WAL mode
- In-game inspect mode (`/pp inspect`)
- Beta highlighter mode (`/pp highlight`) for visual investigation

## Supported Versions

| Minecraft | Loader | Status |
|---|---|---|
| 1.20.1 | Forge 47.2.0+ | Supported |
| 1.20.1 | Fabric Loader 0.14.25+ + Fabric API 0.92.2+1.20.1 | Supported |

> This is primarily a server-side moderation mod.

## Current Beta

- Branch: `beta/change-highlighter`
- Version: `1.3.2`
- Added: visual change highlighting + client config screens

## Commands

```bash
/pp inspect
```
Toggle inspect mode and click blocks to view history.

```bash
/pp lookup [u:<name>] [t:<time>] [r:<radius>] [w:<world>] [a:block|entity|container] [l:<limit>] [p:<page>]
```
Search logs with filters and pagination.

```bash
/pp highlight [off] [u:<name>] [t:<time>] [r:<radius>] [w:<world>] [d:<sec>] [l:<limit>] [p:<page>]
```
Render colored outline highlights around matched blocks.
By default, highlight stays active until `/pp highlight off`. Running `/pp highlight ...` again replaces the previous selection.

```bash
/pp rollback [u:<name>] [t:<time>] [r:<radius>] [w:<world>] [a:entity] [preview]
```
Rollback changes. Use `preview`/`dry-run` to estimate impact first.

```bash
/pp restore [u:<name>] [t:<time>] [r:<radius>] [w:<world>] [preview]
```
Restore previously rolled-back changes.

```bash
/pp purge t:<time>
```
Delete old records (requires op level 4).

```bash
/pp status
```
Show counters, DB info, uptime, and highlighter state.

### Time Format

Use `s`, `m`, `h`, `d`, `w`.

Examples: `30m`, `1h30m`, `2d`, `1w`

## Highlighter (Beta)

Action colors:

- Green: block place
- Red: block break
- Orange: explosion
- Yellow: fire

Client settings UI:

- Forge: Mods -> PrismProtect -> Config
- Fabric: ModMenu -> PrismProtect -> Configure

Config file path:

- `config/prismprotect/prismprotect-client.json`

## Storage

Database file:

- `config/prismprotect/prismprotect.db`

PrismProtect uses SQLite with WAL mode by default.

## Development

```bash
git clone https://github.com/LanisOff/prismprotect.git
cd prismprotect
```

Main branches used in this repo:

- `main`
- `beta`

## License

This repository includes the GNU GPL v3 license text.
See `LICENSE`.
