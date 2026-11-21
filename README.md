# AI World Director

An AI-driven godlike director for Minecraft Java 1.20.1 using Forge 47.4.10 and Java 17.

The player survives and explores while ChatGPT acts as the world's director. The director observes gameplay, evaluates events, and freely chooses whether to intervene with rewards, challenges, hostile encounters, missions, weather changes, or no action at all.

## Repository layout

- `minecraft-mod/`: Forge mod, Java 17.
- `mcp-plugin/`: local MCP server that exposes the mod to ChatGPT.
- `docs/`: architecture, protocol, and development notes.

## Core design

- Mixed operation: periodic observation plus event-driven updates.
- ChatGPT makes the gameplay decisions; the mod only observes and executes validated orders.
- Local authenticated communication between the mod and MCP plugin.
- All Minecraft world mutations run on the server thread.
- Safe defaults, bounded actions, deduplication, and action history.
- The director may choose to do nothing. It is not a fixed random-event generator.

## Target environment

- Minecraft Java 1.20.1
- Forge 47.4.10
- Java 17
- Gradle

## Status

Initial repository setup. The playable mod and MCP bridge are not implemented yet.

See [Architecture](docs/ARCHITECTURE.md) and [Protocol](docs/PROTOCOL.md).
