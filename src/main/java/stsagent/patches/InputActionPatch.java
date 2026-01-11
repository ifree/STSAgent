package stsagent.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.helpers.input.InputAction;
import stsagent.STSAgent;
import stsagent.ui.ChatOverlay;

/**
 * Patches InputAction to block all game hotkeys when chat is capturing input.
 *
 * This is necessary because:
 * 1. InputProcessor event consumption only affects event propagation
 * 2. Gdx.input.isKeyJustPressed() is a polling method that bypasses InputProcessor
 * 3. STS uses InputAction for most hotkey detection
 *
 * By patching InputAction.isJustPressed() and isPressed(), we block game hotkeys
 * at the source.
 */
public class InputActionPatch {

    private static boolean isCapturing() {
        STSAgent agent = STSAgent.getInstance();
        if (agent != null) {
            ChatOverlay overlay = agent.getChatOverlay();
            return overlay != null && overlay.isCapturingInput();
        }
        return false;
    }

    @SpirePatch(clz = InputAction.class, method = "isJustPressed")
    public static class IsJustPressedPatch {
        @SpirePrefixPatch
        public static SpireReturn<Boolean> Prefix(InputAction __instance) {
            if (isCapturing()) {
                return SpireReturn.Return(false);
            }
            return SpireReturn.Continue();
        }
    }

    @SpirePatch(clz = InputAction.class, method = "isPressed")
    public static class IsPressedPatch {
        @SpirePrefixPatch
        public static SpireReturn<Boolean> Prefix(InputAction __instance) {
            if (isCapturing()) {
                return SpireReturn.Return(false);
            }
            return SpireReturn.Continue();
        }
    }
}
