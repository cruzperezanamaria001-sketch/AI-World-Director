# Architecture

## Components

### Forge mod

Runs inside the integrated or dedicated Minecraft server.

Responsibilities:
- Observe server-side player and world state.
- Track recent gameplay events through Forge events and controlled tick sampling.
- Expose snapshots: player health, food, armor, inventory summary, dimension, position, world time, weather, and nearby entities.
- Accept authenticated action requests from the local bridge.
- Validate and schedule mutations on the Minecraft server thread.
- Return authoritative action results and maintain an audit history.

### MCP plugin

A separate local process exposes narrow tools to ChatGPT:
- get_world_status
- get_player_status
- get_recent_events
- get_event_history
- spawn_entity
- give_item
- set_time
- set_weather
- create_event
- execute_director_action

The plugin communicates with the mod over an authenticated local HTTP interface bound to 127.0.0.1.

### Mixed director loop

- Periodic snapshots allow the director to notice gradual changes.
- Important events trigger prompt updates in the active ChatGPT session.
- Cooldowns and event coalescing avoid flooding the conversation with duplicate updates.
- ChatGPT chooses whether to act, and which action to take.
- The mod never independently chooses a punishment or reward.

## Security and reliability

- Bind the bridge to 127.0.0.1 only; do not expose a public listener.
- Generate a random bridge secret locally on first launch.
- Store secrets only in the local config directory, never in Git.
- Validate entity IDs, item IDs, counts, duration, and payload size.
- Use configurable limits for spawns and item quantities.
- Require explicit opt-in for destructive or irreversible actions.
- Never edit player NBT or world files directly.
- Run all game mutations on the logical server thread.
- Deduplicate commands with unique action IDs.
- Write an append-only action audit log.
- Drop expired queued actions when the session disconnects.
- If the bridge disconnects, Minecraft continues normally.
