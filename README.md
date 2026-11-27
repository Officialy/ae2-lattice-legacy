# AE2 Lattice Legacy

A Minecraft 1.7.10 / GTNH backport of [PolyAnthi/ae2-lattice](https://github.com/PolyAnthi/ae2-lattice).
The original MIT license and credits are retained.

**Development preview — not a feature-complete release.** Build target: Forge 10.13.4.1614,
GTNH AE2 rv3-beta-1104-GTNH and optional GT5U 5.09.54.195. Exact GTNH pack releases have not yet been tested.

## Intended behavior

- Install on server and client for Lattice features. Unmodified clients should still connect to a modded server.
- A modded client should also connect to a server without Lattice, with the normal AE2 GUI unchanged.
- Integrated server / singleplayer uses the same path.
- F8 in an AE2 crafting CPU/status GUI toggles the graph. Escape or F8 returns to native grid.
- The graph uses server-side task patterns, including remembered patterns from jobs observed since submission.
- CPU-wide stored, active and pending counts; scheduling reasons; provider candidates and limited GT machine progress.
- Pan and zoom; no custom commands, blocks or world data. Native AE2 controls remain authoritative.

## Security and performance

The client sends a protocol version, open container ID and request nonce, never arbitrary coordinates or a CPU ID.
Server requests are handled on the server tick, rate-limited, and require an active usable AE2 crafting
container and CRAFT permission on the selected CPU's network. No response is sent without a client request.
The client checks FML channel registration before sending. GT machine progress is owner-only, only for loaded
tiles adjacent to a unique provider candidate; attribution to a particular craft is deliberately not claimed.
Snapshots are capped at 24 KiB and 96 graph nodes, with bounded edges and machine lookups. No chunks are forced
loaded. No OpenComputers, external services or network listeners.

## Current limitations

This is a first implementation slice. It does not yet capture exact push/return events, subnet machine paths,
machine inventories/fluids, actual shared-machine attribution, completion summaries or the full animated
upstream UI. Dependencies are inferred from the real selected pattern inputs/outputs; alternatives, substitutions,
byproducts and input-only patterns may require more precise execution-plan tracking. Restored jobs may lack
already completed patterns. CPU counts are item-wide, not independently attributable to patterns.
Only a primary output is displayed on each card. Very large graphs are marked partial.
A provider can be a candidate without serving the selected CPU. GT probe data must never be treated as proof
of the current recipe.

## Build

Use the GTNH convention build with the included wrapper (Gradle daemon JDK 25; Java 8 mod bytecode target).
```sh
./gradlew build
```

The workflow builds and uploads artifacts; it does not publish releases.
Before release, run dedicated/integrated server tests for all installation combinations, mismatched protocols,
GUI switching, canceled/merged/restored crafts, machine ownership and chunk boundaries, and large GTNH jobs.
