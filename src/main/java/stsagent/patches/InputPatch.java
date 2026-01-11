package stsagent.patches;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stsagent.STSAgent;
import stsagent.config.AgentConfig;
import stsagent.ui.ChatOverlay;

/**
 * Patches input handling to completely block game input when chat is active.
 *
 * Uses PREFIX patch to skip InputHelper.updateFirst() entirely when capturing.
 * Also installs an InputProcessor that consumes all keyboard events when capturing,
 * since many game classes check Gdx.input.isKeyJustPressed() directly.
 *
 * Hotkeys are configurable via AgentConfig:
 * - hotkey.toggle (default F8): Toggle chat overlay
 * - hotkey.analyze (default F9): Quick analyze
 */
@SpirePatch(clz = InputHelper.class, method = "updateFirst")
public class InputPatch {
    private static final Logger logger = LogManager.getLogger(InputPatch.class);

    private static boolean togglePressed = false;
    private static boolean analyzePressed = false;

    // Scroll tracking
    private static volatile int scrollAmount = 0;
    private static boolean processorInstalled = false;

    // Previous mouse state for click detection
    private static boolean wasMouseDown = false;
    private static boolean wasMouseDownR = false;

    @SpirePrefixPatch
    public static SpireReturn<Void> Prefix() {
        // Install input processor if not done (handles keyboard + scroll)
        if (!processorInstalled) {
            installInputProcessor();
            processorInstalled = true;
        }

        STSAgent agent = STSAgent.getInstance();
        if (agent == null) return SpireReturn.Continue();

        ChatOverlay overlay = agent.getChatOverlay();
        if (overlay == null) return SpireReturn.Continue();

        AgentConfig config = agent.getConfig();
        int toggleKey = config != null ? config.getToggleKey() : Input.Keys.F8;
        int analyzeKey = config != null ? config.getAnalyzeKey() : Input.Keys.F9;

        // Debug: log once when processor is first installed
        if (!processorInstalled) {
            logger.info("InputPatch using toggleKey={} ({}), analyzeKey={} ({})",
                    toggleKey, Input.Keys.toString(toggleKey),
                    analyzeKey, Input.Keys.toString(analyzeKey));
        }

        // Toggle key - always works, checked before processor consumes
        boolean toggleDown = Gdx.input.isKeyPressed(toggleKey);
        if (toggleDown && !togglePressed) {
            togglePressed = true;
            logger.info("Toggle key pressed! Toggling overlay.");
            overlay.toggle();
        } else if (!toggleDown) {
            togglePressed = false;
        }

        // If overlay is capturing, block ALL game input
        if (overlay.isCapturingInput()) {
            // Manually update only what we need
            updateMouseState();
            updateScrollState();

            // Skip the original method - this blocks ALL keyboard input to the game
            return SpireReturn.Return(null);
        }

        // Analyze key - only when not capturing
        boolean analyzeDown = Gdx.input.isKeyPressed(analyzeKey);
        if (analyzeDown && !analyzePressed) {
            analyzePressed = true;
            overlay.triggerAnalysis();
        } else if (!analyzeDown) {
            analyzePressed = false;
        }

        // Let original method run
        return SpireReturn.Continue();
    }

    /**
     * Update mouse position and click state for our overlay.
     */
    private static void updateMouseState() {
        // Position
        InputHelper.mX = Gdx.input.getX();
        InputHelper.mY = Gdx.graphics.getHeight() - Gdx.input.getY();

        // Left click
        boolean mouseDown = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        InputHelper.justClickedLeft = mouseDown && !wasMouseDown;
        InputHelper.justReleasedClickLeft = !mouseDown && wasMouseDown;  // Track mouse release for card drag
        InputHelper.isMouseDown = mouseDown;
        wasMouseDown = mouseDown;

        // Right click
        boolean mouseDownR = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
        InputHelper.justClickedRight = mouseDownR && !wasMouseDownR;
        InputHelper.isMouseDown_R = mouseDownR;
        wasMouseDownR = mouseDownR;

        // Clear keyboard state to be safe
        InputHelper.pressedEscape = false;
    }

    /**
     * Update scroll state from our captured scroll events.
     */
    private static void updateScrollState() {
        InputHelper.scrolledDown = scrollAmount > 0;
        InputHelper.scrolledUp = scrollAmount < 0;
        scrollAmount = 0; // Reset after reading
    }

    /**
     * Install an InputProcessor to capture keyboard and scroll events.
     * When capturing, this consumes ALL keyboard events so the game never sees them.
     * This is necessary because many game classes check Gdx.input.isKeyJustPressed() directly.
     */
    private static void installInputProcessor() {
        try {
            InputProcessor chatProcessor = new InputAdapter() {
                private boolean isCapturing() {
                    STSAgent agent = STSAgent.getInstance();
                    if (agent != null) {
                        ChatOverlay overlay = agent.getChatOverlay();
                        return overlay != null && overlay.isCapturingInput();
                    }
                    return false;
                }

                private int getToggleKey() {
                    STSAgent agent = STSAgent.getInstance();
                    if (agent != null && agent.getConfig() != null) {
                        return agent.getConfig().getToggleKey();
                    }
                    return Input.Keys.F8;
                }

                @Override
                public boolean keyDown(int keycode) {
                    // Always let toggle key through
                    if (keycode == getToggleKey()) return false;
                    // Consume all other keys when capturing
                    return isCapturing();
                }

                @Override
                public boolean keyUp(int keycode) {
                    if (keycode == getToggleKey()) return false;
                    return isCapturing();
                }

                @Override
                public boolean keyTyped(char character) {
                    return isCapturing();
                }

                @Override
                public boolean scrolled(int amount) {
                    if (isCapturing()) {
                        scrollAmount = amount;
                        return true; // Consume
                    }
                    return false; // Let game handle
                }
            };

            InputProcessor current = Gdx.input.getInputProcessor();
            if (current instanceof InputMultiplexer) {
                ((InputMultiplexer) current).addProcessor(0, chatProcessor);
            } else if (current != null) {
                InputMultiplexer mux = new InputMultiplexer(chatProcessor, current);
                Gdx.input.setInputProcessor(mux);
            } else {
                Gdx.input.setInputProcessor(chatProcessor);
            }

            logger.debug("Chat input processor installed");
        } catch (Exception e) {
            logger.warn("Failed to install chat input processor: {}", e.getMessage());
        }
    }
}
