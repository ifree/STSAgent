# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

STS Agent is a Slay the Spire mod that provides LLM-powered gameplay assistance with a unified Agent architecture:
- **Built-in tools** for reading game state directly from memory
- **MCP tool** (`execute_actions`) for game control via MCPTheSpire server
- Three modes: **analyze** (advice only), **play** (autonomous), **chat** (Q&A)

## Build Commands

```bash
mvn package           # Build (outputs to target/STSAgent.jar)
mvn clean package     # Clean and rebuild
```

## Required Dependencies

System-scoped JARs in `../lib/`:
- `desktop-1.0.jar` - Slay the Spire game
- `ModTheSpire.jar` - Mod loader
- `BaseMod.jar` - Modding library

## Architecture

```
STSAgent.java          # Entry point, lifecycle management
├── config/
│   └── AgentConfig.java       # LLM, MCP, prompt configuration
├── llm/
│   ├── LLMClient.java         # OpenAI-compatible API client
│   └── LLMMessage.java        # Message structures
├── mcp/
│   └── MCPClient.java         # MCP client (execute_actions only)
├── tools/
│   ├── BuiltinTools.java      # Tool definitions + dispatcher
│   └── GameStateReader.java   # Direct game memory access
├── agent/
│   └── Agent.java             # Unified agent (analyze/play/chat)
└── ui/
    └── ChatOverlay.java       # In-game chat UI
```

### Built-in Tools (GameStateReader)

| Tool | Description |
|------|-------------|
| `get_game_state` | Character, HP, gold, floor, act, ascension |
| `get_combat_state` | Energy, hand cards, enemies, buffs/debuffs |
| `get_screen` | Screen type, choices, button states |
| `get_deck` | Full deck with card details |
| `get_relics` | Equipped relics |
| `get_potions` | Potion slots |
| `get_map` | Current floor, next nodes, boss |

### MCP Tool (MCPClient)

`execute_actions` - Batch execute game actions via MCPTheSpire:
- `play_card`, `end_turn`, `choose`, `proceed`, `skip`, `cancel`, `confirm`
- `use_potion`, `discard_potion`, `select_cards`

All indices are 1-based.

### Agent Modes

- **analyze()**: Uses built-in tools only, provides advice without executing actions
- **play()**: Full REACT loop with built-in + MCP tools, requires MCPTheSpire server
- **chat()**: Answers questions using built-in tools

## Configuration

Config file: `SlayTheSpire/preferences/STSAgent/config.properties`

```properties
llm.apiKey=           # Required
llm.baseUrl=https://api.openai.com/v1
llm.model=gpt-4o-mini
mcp.serverUrl=http://127.0.0.1:8080
prompt.system=...     # Customizable
prompt.analyze=...
prompt.play=...
```

## Hotkeys

- F8: Toggle chat overlay
- F9: Quick analyze

## MCP Integration

Play mode requires MCPTheSpire server at `mcp.serverUrl`. The server provides `execute_actions` for game control. See `../MCPTheSpire/` for the MCP server implementation.
