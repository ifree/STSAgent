package stsagent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Built-in tools for reading game state.
 * These tools run directly in the game process without MCP.
 */
public class BuiltinTools {

    // Tool names
    public static final String GET_GAME_STATE = "get_game_state";
    public static final String GET_COMBAT_STATE = "get_combat_state";
    public static final String GET_SCREEN = "get_screen";
    public static final String GET_DECK = "get_deck";
    public static final String GET_RELICS = "get_relics";
    public static final String GET_POTIONS = "get_potions";
    public static final String GET_MAP = "get_map";

    private static final Set<String> BUILTIN_TOOL_NAMES = new HashSet<>(Arrays.asList(
            GET_GAME_STATE, GET_COMBAT_STATE, GET_SCREEN,
            GET_DECK, GET_RELICS, GET_POTIONS, GET_MAP
    ));

    private final GameStateReader reader;

    public BuiltinTools() {
        this.reader = new GameStateReader();
    }

    public BuiltinTools(GameStateReader reader) {
        this.reader = reader;
    }

    /**
     * Check if a tool name is a built-in tool.
     */
    public boolean isBuiltinTool(String name) {
        return BUILTIN_TOOL_NAMES.contains(name);
    }

    /**
     * Execute a built-in tool and return the result.
     */
    public String execute(String toolName, JsonObject args) {
        switch (toolName) {
            case GET_GAME_STATE:
                return reader.getGameState();
            case GET_COMBAT_STATE:
                return reader.getCombatState();
            case GET_SCREEN:
                return reader.getScreen();
            case GET_DECK:
                return reader.getDeck();
            case GET_RELICS:
                return reader.getRelics();
            case GET_POTIONS:
                return reader.getPotions();
            case GET_MAP:
                return reader.getMap();
            default:
                return "{\"error\": \"Unknown tool: " + toolName + "\"}";
        }
    }

    /**
     * Get all built-in tool definitions in OpenAI function calling format.
     */
    public JsonArray getToolDefinitions() {
        JsonArray tools = new JsonArray();

        tools.add(createTool(
                GET_GAME_STATE,
                "Get basic game state: character, HP, gold, floor, act, ascension level. " +
                "Use this first to understand the overall situation."
        ));

        tools.add(createTool(
                GET_COMBAT_STATE,
                "Get combat state: energy, hand cards (with index, name, cost, type, playable status), " +
                "enemies (with index, name, HP, intent, powers), player powers/buffs. " +
                "Only available during combat. Card and enemy indices are 1-based."
        ));

        tools.add(createTool(
                GET_SCREEN,
                "Get current screen state: screen type, available choices (with 1-based index), " +
                "button availability (can_proceed, can_skip, can_cancel). " +
                "Use this to understand what actions are available."
        ));

        tools.add(createTool(
                GET_DECK,
                "Get full deck information: all cards with name, type, cost, rarity, upgrade status."
        ));

        tools.add(createTool(
                GET_RELICS,
                "Get equipped relics: name, id, counter value."
        ));

        tools.add(createTool(
                GET_POTIONS,
                "Get potion slots: slot number (1-based), name, whether empty, " +
                "can_use status, requires_target."
        ));

        tools.add(createTool(
                GET_MAP,
                "Get map information: current floor, act, current room, " +
                "available next nodes with symbols, boss name."
        ));

        return tools;
    }

    /**
     * Get the GameStateReader instance.
     */
    public GameStateReader getReader() {
        return reader;
    }

    // ========== Helper Methods ==========

    private JsonObject createTool(String name, String description) {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");

        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);

        // No parameters for these tools
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", new JsonObject());
        function.add("parameters", parameters);

        tool.add("function", function);
        return tool;
    }
}
