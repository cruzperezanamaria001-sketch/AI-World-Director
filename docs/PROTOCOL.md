# AI World Director Remote Protocol — Draft v1

## Overview

ChatGPT connects to the hosted custom MCP server at a stable HTTPS URL. The MCP server uses OAuth 2.1. The Minecraft mod maintains an outbound authenticated TLS connection to a relay service. The player's PC does not expose a public game-control listener.

## Public MCP endpoint

Example deployment URL:

```text
https://<your-domain>/mcp
```

The endpoint uses the MCP Streamable HTTP transport and OAuth 2.1 Authorization Code with PKCE. The deployment must publish OAuth protected-resource and authorization-server metadata required by the MCP client.

## Authentication boundaries

There are two independent authentication flows:

1. **ChatGPT to MCP:** OAuth 2.1 Authorization Code + PKCE. The MCP server validates access tokens, audience, issuer, expiry, and scopes.
2. **Forge mod to relay:** Per-installation device credential or short-lived device token over TLS. This credential is not the ChatGPT OAuth token and is never committed to Git.

Do not reuse the mod's device credential as an OAuth bearer token.

## Relay transport

The mod initiates an outbound persistent TLS connection to the relay. WebSocket is the initial candidate. The relay binds each authenticated mod installation to an owner and active world session.

The relay forwards validated action requests to the mod and returns the mod's authoritative result. No inbound router port-forwarding is required.

## MCP tools

Read:
- get_world_status
- get_player_status
- get_recent_events
- get_event_history

Actions:
- spawn_entity
- give_item
- set_time
- set_weather
- create_event
- execute_director_action

Each tool must identify the active world session or use the user's selected active session. Never guess between multiple connected worlds.

## Action envelope

```json
{
  "action_id": "client-generated-uuid",
  "session_id": "active-world-session",
  "type": "spawn_entity",
  "payload": {
    "entity": "minecraft:zombie",
    "count": 2,
    "radius": 8
  }
}
```

The relay and mod validate the payload. The mod schedules game mutations on the Minecraft server thread. The response distinguishes accepted, queued, executed, rejected, and failed. The MCP tool must not report success merely because the relay accepted the request.

## Initial action types

- spawn_entity — allowlisted entity IDs and bounded count.
- give_item — allowlisted item IDs and bounded quantity.
- set_time — day/night or a validated numeric time.
- set_weather — clear/rain/thunder with bounded duration.
- create_event — a small composition of individually validated actions.

Every request is authenticated, schema-validated, rate-limited, deduplicated, session-scoped, and audited.

## Disconnect behavior

If the mod loses its relay connection, it continues as normal Minecraft. Pending actions expire and are not replayed after reconnect unless the user explicitly requests a fresh action. The MCP server reports the world as disconnected.
