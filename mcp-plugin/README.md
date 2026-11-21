# AI World Director — Custom Remote MCP Server

This is a **custom remote MCP server**, not a local-only MCP process.

## Connection model

- ChatGPT connects to a stable HTTPS MCP URL, for example `https://<your-domain>/mcp`.
- The MCP endpoint uses MCP Streamable HTTP transport.
- ChatGPT authenticates with OAuth 2.1 Authorization Code + PKCE.
- The Forge mod makes an outbound authenticated TLS connection to the relay service.
- The relay routes authorized tool requests between the MCP server and the correct active Minecraft world.

The player's PC does not need a public inbound port or router port-forwarding. A managed tunnel can be used temporarily during development; production should use a stable domain and controlled relay.

## Planned tools

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

## Security

OAuth credentials for ChatGPT and device credentials for the Forge mod are separate. Use short-lived tokens, scopes, per-installation revocation, session binding, rate limits, validation, and action audit logs. Never commit secrets, OAuth keys, or device tokens.

## Implementation

Initial target: Node.js LTS and TypeScript. The MCP server, OAuth authorization server/resource metadata, and relay service will be deployed behind HTTPS. See [architecture](../docs/ARCHITECTURE.md) and [remote protocol](../docs/PROTOCOL.md).
