# STS Agent

LLM-powered AI assistant mod for Slay the Spire.

## Features

- **AI Chat Overlay** - In-game draggable chat window for interacting with LLM
- **Game State Analysis** - AI reads game state directly and provides strategic advice
- **Autonomous Play** - Let AI play the game for you (requires MCPTheSpire)
- **Conversation Memory** - Chat history with automatic summarization
- **OpenAI Compatible** - Works with any OpenAI-compatible API (OpenAI, Claude, local models, etc.)

## Requirements

- Slay the Spire
- [ModTheSpire](https://github.com/kiooeht/ModTheSpire)
- [BaseMod](https://github.com/daviscook477/BaseMod)
- [MCPTheSpire](../MCPTheSpire/) (optional, for autonomous play mode)
- Java 8+
- Maven 3.x

## Installation

### 1. Build the mod

```bash
# Clone the repository
cd STSAgent

# Build (requires lib/ folder with game JARs)
mvn package
```

The built JAR will be automatically copied to `../_ModTheSpire/mods/STSAgent.jar`.

### 2. Configure API Key

First launch will create config file at:
```
SlayTheSpire/preferences/STSAgent/config.properties
```

Edit and add your API key:
```properties
llm.apiKey=sk-your-api-key-here
```

### 3. Launch the game

Launch Slay the Spire via ModTheSpire with STSAgent enabled.

## Usage

### Hotkeys

| Key | Action |
|-----|--------|
| **F8** | Toggle chat overlay |
| **F9** | Quick analyze current state |

### Chat Commands

| Command | Description |
|---------|-------------|
| `/analyze` or `/a` | Analyze current game state |
| `/tip` or `/t` | Get quick combat suggestion |
| `/clear` or `/c` | Clear chat history |
| `/help` or `/h` | Show available commands |

### Buttons

| Button | Description |
|--------|-------------|
| **Play** | Start autonomous AI play (requires MCPTheSpire) |
| **Analyze** | Get detailed strategic analysis |
| **Tip** | Quick combat suggestion (1-2 sentences) |
| **Clear** | Clear chat and conversation history |
| **CJK** | Open text input dialog (for Chinese/Japanese/Korean input) |

### Chat Features

- Type messages directly and press Enter to send
- Ctrl+V to paste text
- Scroll mouse wheel to view message history
- Drag the window header to reposition

## Configuration

Config file: `SlayTheSpire/preferences/STSAgent/config.properties`

```properties
# LLM Configuration
llm.apiKey=                              # Required: Your API key
llm.baseUrl=https://api.openai.com/v1   # API endpoint
llm.model=gpt-4o-mini                    # Model name

# MCP Configuration (for Play mode)
mcp.serverUrl=http://127.0.0.1:8080     # MCPTheSpire server URL

# UI Configuration
ui.overlayOpacity=0.85                   # Chat window opacity

# Hotkeys (key codes or names like F8, F9)
hotkey.toggle=131                        # F8
hotkey.analyze=132                       # F9

# Prompts (use \n for newlines)
prompt.system=...                        # Base system prompt
prompt.analyze=...                       # Added for analyze mode
prompt.play=...                          # Added for play mode
```

### Using with Other LLM Providers

**Claude API:**
```properties
llm.baseUrl=https://api.anthropic.com/v1
llm.model=claude-3-5-sonnet-20241022
llm.apiKey=sk-ant-...
```

**Local Models (Ollama, LM Studio, etc.):**
```properties
llm.baseUrl=http://localhost:11434/v1
llm.model=llama3.2
llm.apiKey=dummy
```

## Architecture

```
STSAgent/
├── STSAgent.java              # Entry point, mod lifecycle
├── agent/
│   └── Agent.java             # Unified agent with analyze/play/chat modes
├── config/
│   └── AgentConfig.java       # Configuration management
├── llm/
│   ├── LLMClient.java         # OpenAI-compatible API client
│   └── LLMMessage.java        # Chat message structures
├── mcp/
│   └── MCPClient.java         # MCP client for execute_actions
├── tools/
│   ├── BuiltinTools.java      # Tool definitions and dispatcher
│   └── GameStateReader.java   # Direct game memory reader
├── ui/
│   └── ChatOverlay.java       # In-game chat UI
└── patches/
    └── InputPatch.java        # Hotkey handling
```

### Built-in Tools

These tools read game state directly from memory:

| Tool | Description |
|------|-------------|
| `get_game_state` | Character, HP, gold, floor, act, ascension level |
| `get_combat_state` | Energy, hand cards (with indices), enemies (with intents), buffs/debuffs |
| `get_screen` | Current screen type, available choices, button states |
| `get_deck` | Full deck with card details |
| `get_relics` | Equipped relics with counters |
| `get_potions` | Potion slots with usability |
| `get_map` | Map nodes, current position, boss info |

### MCP Integration

Play mode uses MCPTheSpire's `execute_actions` tool for game control:
- `play_card` - Play a card (with target for attacks)
- `end_turn` - End combat turn
- `choose` - Select a choice option
- `proceed` / `skip` / `cancel` / `confirm` - UI navigation
- `use_potion` / `discard_potion` - Potion management

All indices are **1-based** (first card = 1, first enemy = 1).

## Chat History & Summarization

The agent maintains conversation history across all interactions:
- Chat messages, analyze results, tips, and play actions are all recorded
- When history reaches 16 messages, older messages are automatically summarized
- Summary is injected into context so AI remembers previous discussions
- Use `/clear` to reset history

## Development

### Build Commands

```bash
mvn package           # Build and copy to mods folder
mvn clean package     # Clean rebuild
```

### Dependencies

Place these JARs in `../lib/`:
- `desktop-1.0.jar` - Slay the Spire game
- `ModTheSpire.jar` - Mod loader
- `BaseMod.jar` - Modding library

### Project Structure

The mod uses:
- **OkHttp** for HTTP requests to LLM API
- **Gson** for JSON parsing
- **SpirePatch** for game hooks
- **LibGDX** for UI rendering

## License

MIT License

## Acknowledgments

- [Slay the Spire](https://www.megacrit.com/) by Mega Crit Games
- [ModTheSpire](https://github.com/kiooeht/ModTheSpire)
- [BaseMod](https://github.com/daviscook477/BaseMod)
- [MCPTheSpire](https://github.com/ifree/MCPTheSpire) for game control integration
