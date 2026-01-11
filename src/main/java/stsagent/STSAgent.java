package stsagent;

import basemod.BaseMod;
import basemod.ModLabel;
import basemod.ModPanel;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostRenderSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stsagent.agent.Agent;
import stsagent.config.AgentConfig;
import stsagent.llm.LLMClient;
import stsagent.mcp.MCPClient;
import stsagent.ui.ChatOverlay;

/**
 * STS Agent - AI Assistant mod for Slay the Spire.
 *
 * Architecture:
 * - Built-in tools for reading game state (get_game_state, get_combat_state, etc.)
 * - MCP tool (execute_actions) for game control via MCPTheSpire
 * - Unified Agent with analyze/play/chat modes
 *
 * Hotkeys:
 * - F8: Toggle chat overlay
 * - F9: Quick analyze current state
 */
@SpireInitializer
public class STSAgent implements PostInitializeSubscriber, PostUpdateSubscriber, PostRenderSubscriber {

    private static final Logger logger = LogManager.getLogger(STSAgent.class);
    public static final String MOD_ID = "stsagent";

    private static STSAgent instance;

    private AgentConfig config;
    private LLMClient llmClient;
    private MCPClient mcpClient;
    private Agent agent;
    private ChatOverlay chatOverlay;

    private String statusMessage = "Initializing...";

    public static void initialize() {
        logger.info("Initializing STS Agent mod");
        instance = new STSAgent();
    }

    public STSAgent() {
        BaseMod.subscribe(this);
    }

    @Override
    public void receivePostInitialize() {
        logger.info("Post-initialize: Setting up STS Agent");

        // Load configuration
        config = new AgentConfig();

        // Initialize LLM client
        llmClient = new LLMClient(
                config.getLlmApiKey(),
                config.getLlmBaseUrl(),
                config.getLlmModel()
        );

        // Initialize MCP client
        mcpClient = new MCPClient(config.getMcpServerUrl());

        // Initialize unified agent
        agent = new Agent(llmClient, mcpClient, config);

        // Initialize chat overlay
        chatOverlay = new ChatOverlay(agent);

        // Set up mod options panel
        setUpOptionsMenu();

        // Check API key
        if (!config.hasApiKey()) {
            statusMessage = "API Key not set!";
            logger.warn("LLM API key not configured. Edit the config file.");
        } else {
            statusMessage = "Ready";
        }

        logger.info("STS Agent initialization complete");
        logger.info("Press {} to toggle, {} to analyze",
                config.getToggleKeyName(), config.getAnalyzeKeyName());
    }

    private void setUpOptionsMenu() {
        ModPanel settingsPanel = new ModPanel();

        float yPos = 750;
        float xPos = 350;
        float lineHeight = 50;

        // Title
        ModLabel titleLabel = new ModLabel(
                "STS Agent Configuration",
                xPos, yPos, Settings.GOLD_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {}
        );
        settingsPanel.addUIElement(titleLabel);
        yPos -= lineHeight;

        // Status
        ModLabel statusLabel = new ModLabel(
                "", xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    modLabel.text = "Status: " + statusMessage;
                }
        );
        settingsPanel.addUIElement(statusLabel);
        yPos -= lineHeight;

        // LLM Model
        ModLabel modelLabel = new ModLabel(
                "", xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    modLabel.text = "LLM Model: " + config.getLlmModel();
                }
        );
        settingsPanel.addUIElement(modelLabel);
        yPos -= lineHeight;

        // LLM Base URL
        ModLabel baseUrlLabel = new ModLabel(
                "", xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    modLabel.text = "LLM URL: " + config.getLlmBaseUrl();
                }
        );
        settingsPanel.addUIElement(baseUrlLabel);
        yPos -= lineHeight;

        // MCP Server URL
        ModLabel mcpLabel = new ModLabel(
                "", xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    modLabel.text = "MCP Server: " + config.getMcpServerUrl();
                }
        );
        settingsPanel.addUIElement(mcpLabel);
        yPos -= lineHeight;

        // API Key status
        ModLabel apiKeyLabel = new ModLabel(
                "", xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    if (config.hasApiKey()) {
                        modLabel.text = "API Key: Configured";
                    } else {
                        modLabel.text = "API Key: NOT SET (edit config file)";
                    }
                }
        );
        settingsPanel.addUIElement(apiKeyLabel);
        yPos -= lineHeight;

        // Instructions
        yPos -= lineHeight;
        ModLabel instructionsLabel = new ModLabel(
                "Press " + config.getToggleKeyName() + " to toggle chat overlay",
                xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {}
        );
        settingsPanel.addUIElement(instructionsLabel);
        yPos -= lineHeight;

        ModLabel instructions2Label = new ModLabel(
                "Press " + config.getAnalyzeKeyName() + " to analyze current state",
                xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {}
        );
        settingsPanel.addUIElement(instructions2Label);
        yPos -= lineHeight;

        ModLabel instructions3Label = new ModLabel(
                "Type /help in chat for commands",
                xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {}
        );
        settingsPanel.addUIElement(instructions3Label);
        yPos -= lineHeight * 2;

        ModLabel configPathLabel = new ModLabel(
                "Config: SlayTheSpire/preferences/STSAgent/config.properties",
                xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {}
        );
        settingsPanel.addUIElement(configPathLabel);

        // Register mod badge
        com.badlogic.gdx.graphics.Texture badgeTexture;
        try {
            badgeTexture = ImageMaster.loadImage("stsagent/badge.png");
        } catch (Exception e) {
            badgeTexture = ImageMaster.loadImage("img/BlankCardSmall.png");
            logger.warn("Could not load mod icon, using default");
        }
        BaseMod.registerModBadge(
                badgeTexture,
                "STS Agent",
                "ifree",
                "AI Assistant mod with LLM integration",
                settingsPanel
        );
    }

    @Override
    public void receivePostUpdate() {
        if (chatOverlay != null) {
            chatOverlay.update();
        }
        // Hotkeys are handled by InputPatch to avoid double-toggle
    }

    @Override
    public void receivePostRender(SpriteBatch sb) {
        if (chatOverlay != null) {
            chatOverlay.render(sb);
        }
    }

    // Public accessors
    public static STSAgent getInstance() { return instance; }
    public AgentConfig getConfig() { return config; }
    public Agent getAgent() { return agent; }
    public ChatOverlay getChatOverlay() { return chatOverlay; }
}
