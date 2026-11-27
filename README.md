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
- CPU-wide stored, active and pending counts; scheduling reasons; observed successful dispatch counts and last accepting provider, plus candidate GT machine progress.
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

This is a first implementation slice. It records successful push events at the AE2 crafting medium boundary, but does not yet correlate individual returned outputs, traverse subnet machine paths,
machine inventories/fluids, actual shared-machine attribution, completion summaries or the full animated
upstream UI. Dependencies are inferred from the real selected pattern inputs/outputs; alternatives, substitutions,
byproducts and input-only patterns may require more precise execution-plan tracking. Restored jobs may lack
already completed patterns. CPU counts are item-wide, not independently attributable to patterns.
Only a primary output is displayed on each card. Very large graphs are marked partial.
A provider can be a candidate without serving the selected CPU. A recorded accepted push identifies the provider, not necessarily the final machine or its current job. GT probe data must never be treated as proof
of the current recipe.

## Build

Use the GTNH convention build with the included wrapper (Gradle daemon JDK 25; Java 8 mod bytecode target).
```sh
./gradlew build
```

The workflow builds and uploads artifacts; it does not publish releases.
Before release, run dedicated/integrated server tests for all installation combinations, mismatched protocols,
GUI switching, canceled/merged/restored crafts, machine ownership and chunk boundaries, and large GTNH jobs.

### Legacy CI

One build per qualifying push to main/backport branches, not separate duplicate PR and push builds. Only source, resources, tests, Gradle and CI configuration changes trigger builds. Manual dispatch is available when Actions is enabled and the workflow exists on the default branch. Obsolete same-branch runs are canceled. No scheduled builds or release publishing. Fork Actions may need enabling in the repository UI.

### Preview 2

Protocol 2 adds bounded controller/tile energy and inventory/fluid summaries, same-dimension right-click highlighting (10 seconds), and recent completion/cancel summaries. Controller readings are owner-only and not a claim that the machine is running the selected recipe; multiblock hatch inventories are not traversed. Successful dispatch records retain the interface's exposed face mask where available. The server now validates that the selected CPU belongs to the open terminal's actual grid, and charges invalid requests against the per-tick processing budget. The mod still requires runtime compatibility tests.

### Preview 3

Protocol 3 records the receiving tile(s) reported by AE2's interface success hook within the specific CPU's synchronous dispatch, plus item/ fluid output amounts actually credited by the CPU. A credited output does not prove which shared machine produced it. Buffered later transfers and custom mediums without the interface hook are not captured as exact destinations. The latest dispatch's destination set is bounded to eight tiles; output counters are per CPU and stack, not per pattern. The resolver follows authorized neighboring ME interfaces into at most two subnet levels, with a per-snapshot cap of 128 nodes, 128 tile reads and eight grids. Multiple candidates or incomplete traversal suppress machine selection. Registered smart-hatch controller watchers can resolve a unique controller; standard controller input/output/energy hatch lists are sampled (up to twelve owned loaded hatches). ME-backed virtual inventories are skipped; no chunks are loaded. Ordinary hatches without controller watchers and unsupported pipes remain unresolved. Animated item pulses follow inferred graph edges on observed dispatch/return counter changes, not literal item movements, and are capped at 32 rendered pulses. Graph layout is cached per snapshot. Requires protocol-3 client and server for the new features; native AE2 remains available without Lattice. Runtime tests pending.

### Preview 4: page navigation, NEI, and UI

Protocol 4 pages tracked patterns in groups of 12, up to 2,048 per CPU. Page numbers are requested only while the authorized crafting container is open; local edges and bounded cross-page references are included. Page changes invalidate stale replies. PageUp/PageDown or the header arrows navigate; click a node for its observed pattern inputs/outputs and links to other pages. F opens search (page-local), Home fits the graph, A toggles flow, G/F8 returns to the native CPU grid. Tree/Grid buttons are available and view choice and animation preference are persisted in config/ae2lattice-client.cfg. On wide screens the selected-node panel remains visible. R/U opens NEI recipe/usage search for tagless items and fluids with a reconstructable identity; NBT-dependent keys are deliberately disabled. NEI is optional and client-only. Recipe searches return candidate recipes, not proof of the executing machine's recipe. Strings are bounded to 64 characters and ingredient/product summaries can be partial. No custom CPU mutation buttons: native CPU list and cancel controls are accessed via Grid.

Remaining parity gaps include full NBT item identity, exact recipe matching, rich inventory slot/tank rendering, and full shared-machine attribution. Basic molecular assembler and vanilla furnace progress probes are included; furnaces require a directly observed handoff and nearby usable tile. Assembler telemetry requires its own ME grid authorization. Neither probe sends inventory contents. This is a source-level preview, not a verified release.

Page topology is cached per CPU and invalidated as new patterns are observed. Page payloads are limited to 12 nodes at decode and encode time.

### Preview 5

Protocol 5 adds optional, uncompressed NBT (512-byte limit, bounded depth and strict preflight), for item and fluid recipe identities; oversized or unsupported tags are not queried. Press P on a node or click Pattern / machine for an embedded preview of the actual encoded pattern, with sampled inventory slots and fluid-tank fill bars. This is not an embedded NEI handler or proof of the currently running machine recipe. Details are omitted before core graph nodes if the packet is too large, and tag-dependent recipe lookup is disabled when its tag is omitted. Storage-bus-facing machine/subnet discovery is supported within existing traversal budgets. Recent competing CPU/pattern dispatches to the same observed receiver suppress attributed machine progress for 60 seconds; the bounded 4,096-entry receiver history does not imply exclusive ownership or provenance of returned items. Arbitrary pipe transport, all GT machine types, exact live recipe and producer attribution remain unresolved. No server commands, item changes, external networking or required client installation.
