# Architecture

## Product shape

AI World Director is a **remote, custom MCP server** connected to a Forge mod running in the player's Minecraft instance. ChatGPT connects to the MCP server through a stable HTTPS URL and authenticates with OAuth 2.1.

The Forge mod must not expose an unauthenticated or publicly reachable game-control HTTP endpoint.

## Components

### 1. Minecraft Forge mod

Runs inside the integrated or dedicated Minecraft server.

Responsibilities:
- Observe server-side player and world state.
- Track recent gameplay events through Forge events and controlled tick sampling.
- Expose snapshots: player health, food, armor, inventory summary, dimension, position, world time, weather, and nearby entities.
- Maintain an outbound authenticated connection to the relay service.
- Receive validated director actions from the relay and schedule mutations on the Minecraft server thread.
- Return authoritative action results and maintain an audit history.

The mod initiates the connection outward. No router port-forwarding or public listener on the player's PC is required.

### 2. Hosted MCP server

A separately deployed, custom MCP server with a stable HTTPS URL, for example `https://<your-domain>/mcp`.

Responsibilities:
- Implement MCP tools for ChatGPT.
- Authenticate the ChatGPT connection using OAuth 2.1 Authorization Code with PKCE.
- Validate access tokens, scopes, audience, expiry, and issuer.
- Expose only the intended game-control tools.
- Route authorized tool requests to the correct active Minecraft session through the relay.
- Enforce per-session authorization, rate limits, action limits, and audit logging.

Initial tools:
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

### 3. Secure relay / tunnel

The mod establishes an outbound TLS connection (WebSocket or equivalent persistent channel) to a relay endpoint hosted alongside the MCP service. The relay maps an authenticated MCP user/session to the correct active Minecraft instance.

This provides the public server URL ChatGPT needs without exposing the player's local game server. A managed tunnel may be used during development, but production should use a stable HTTPS domain and a controlled relay service.

### 4. Mixed director loop

- Periodic snapshots allow the director to notice gradual changes.
- Important gameplay events are sent through the relay and made available to the active MCP session.
- Cooldowns and event coalescing avoid flooding the conversation with duplicate updates.
- ChatGPT chooses whether to act, and which action to take.
- The mod never independently chooses a punishment or reward.
- The MCP server does not claim to run ChatGPT decisions in the background; decisions occur when the active ChatGPT session invokes tools or processes delivered context.

## OAuth and identity

- Use OAuth 2.1 Authorization Code flow with PKCE for the MCP endpoint.
- Use short-lived access tokens and refresh-token rotation where supported.
- Validate issuer, audience, signature, expiry, scopes, and redirect URI.
- Keep OAuth client secrets, signing keys, and relay credentials in deployment secrets, never in Git.
- Use separate identities/credentials for ChatGPT-to-MCP OAuth and mod-to-relay device authentication.
- Provide revocation and session disconnect controls.

## Security and reliability

- Publicly expose only the HTTPS MCP/OAuth and relay endpoints.
- The Minecraft mod makes outbound TLS connections; do not ask users to open inbound router ports.
- Authenticate each mod instance separately and allow users to revoke it.
- Validate entity IDs, item IDs, counts, duration, coordinates, and payload size.
- Use configurable limits for spawns and item quantities.
- Require explicit opt-in for destructive or irreversible actions.
- Never edit player NBT or world files directly.
- Run all game mutations on the logical server thread.
- Deduplicate commands with unique action IDs.
- Write an append-only action audit log.
- Expire queued actions when the session disconnects.
- If the relay or internet disconnects, Minecraft continues normally and no stale actions execute.

## Development note

The initial localhost HTTP bridge in the prototype is temporary and is not the final deployment architecture. It must be replaced by the outbound authenticated relay client before the project is considered ready for ChatGPT's remote MCP connection.
