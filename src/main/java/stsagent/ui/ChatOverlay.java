package stsagent.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stsagent.agent.Agent;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat overlay with STS-themed visuals.
 */
public class ChatOverlay {
    private static final Logger logger = LogManager.getLogger(ChatOverlay.class);

    // Layout
    private static final float WIDTH = 500f * Settings.scale;
    private static final float HEIGHT = 560f * Settings.scale;
    private static final float PAD = 14f * Settings.scale;

    // Window position (draggable)
    private float windowX = Settings.WIDTH - WIDTH - 30f * Settings.scale;
    private float windowY = (Settings.HEIGHT - HEIGHT) / 2f;

    // Theme Colors
    private static final Color BG_OUTER = new Color(0.02f, 0.02f, 0.04f, 0.75f);
    private static final Color BG_INNER = new Color(0.05f, 0.05f, 0.08f, 0.70f);
    private static final Color HEADER_BG = new Color(0.06f, 0.08f, 0.12f, 0.85f);

    private static final Color GOLD = new Color(0.95f, 0.80f, 0.30f, 1f);
    private static final Color CYAN = new Color(0.3f, 0.85f, 0.95f, 1f);
    private static final Color CYAN_DIM = new Color(0.2f, 0.5f, 0.6f, 0.4f);
    private static final Color CYAN_GLOW = new Color(0.2f, 0.7f, 0.8f, 0.15f);

    private static final Color USER_BG = new Color(0.15f, 0.25f, 0.40f, 0.90f);
    private static final Color USER_BORDER = new Color(0.3f, 0.5f, 0.7f, 0.6f);
    private static final Color AI_BG = new Color(0.12f, 0.14f, 0.20f, 0.90f);
    private static final Color AI_BORDER = new Color(0.4f, 0.35f, 0.5f, 0.5f);
    private static final Color SYS_BG = new Color(0.10f, 0.10f, 0.08f, 0.85f);
    private static final Color SYS_BORDER = new Color(0.5f, 0.45f, 0.2f, 0.4f);

    private static final Color BTN_BG = new Color(0.12f, 0.12f, 0.16f, 0.95f);
    private static final Color BTN_HOVER = new Color(0.18f, 0.22f, 0.30f, 0.98f);
    private static final Color BTN_BORDER = new Color(0.35f, 0.35f, 0.40f, 0.7f);

    private final Agent agent;
    private final List<Msg> messages = new ArrayList<>();
    private final StringBuilder input = new StringBuilder();
    private final GlyphLayout gl = new GlyphLayout();

    private boolean visible = false;
    private boolean focused = true;
    private int scroll = 0;
    private int hoverBtn = -1;
    private float animTimer = 0f;

    private volatile boolean streaming = false;
    private final StringBuilder streamBuf = new StringBuilder();

    private boolean escHeld, enterHeld, vHeld, bsHeld, ctrlEnterHeld;
    private long lastBs = 0;

    // Drag state
    private boolean dragging = false;
    private float dragOffsetX = 0;
    private float dragOffsetY = 0;

    public ChatOverlay(Agent agent) {
        this.agent = agent;
        addSys("STS Agent Ready - F8 to toggle");
        addSys("[Play] = AI plays, [Analyze] = advice");
    }

    public void show() { visible = true; focused = true; scroll = 0; }
    public void hide() { visible = false; }
    public void toggle() { if (visible) hide(); else show(); }
    public boolean isCapturingInput() { return visible && focused; }
    public boolean isVisible() { return visible; }

    public void update() {
        if (!visible) return;
        animTimer += Gdx.graphics.getDeltaTime();
        handleKeys();
        handleMouse();
    }

    private void handleKeys() {
        if (!focused) return;

        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) ||
                       Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ||
                        Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);

        if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
            if (!escHeld) { escHeld = true; hide(); }
        } else escHeld = false;

        if (ctrl && Gdx.input.isKeyPressed(Input.Keys.ENTER)) {
            if (!ctrlEnterHeld) { ctrlEnterHeld = true; openCJK(); }
        } else if (!Gdx.input.isKeyPressed(Input.Keys.ENTER)) ctrlEnterHeld = false;

        if (!ctrl && Gdx.input.isKeyPressed(Input.Keys.ENTER)) {
            if (!enterHeld) {
                enterHeld = true;
                if (input.length() > 0) {
                    send(input.toString());
                    input.setLength(0);
                }
            }
        } else if (!Gdx.input.isKeyPressed(Input.Keys.ENTER)) enterHeld = false;

        if (ctrl && Gdx.input.isKeyPressed(Input.Keys.V)) {
            if (!vHeld) { vHeld = true; paste(); }
        } else if (!Gdx.input.isKeyPressed(Input.Keys.V)) vHeld = false;

        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.A)) input.setLength(0);

        if (Gdx.input.isKeyPressed(Input.Keys.BACKSPACE)) {
            long now = System.currentTimeMillis();
            if (!bsHeld || now - lastBs > 70) {
                if (input.length() > 0) input.deleteCharAt(input.length() - 1);
                lastBs = now;
            }
            bsHeld = true;
        } else bsHeld = false;

        for (int k = Input.Keys.A; k <= Input.Keys.Z; k++) {
            if (Gdx.input.isKeyJustPressed(k)) {
                char c = (char)('a' + k - Input.Keys.A);
                input.append(shift ? Character.toUpperCase(c) : c);
            }
        }

        char[] shiftNums = {')', '!', '@', '#', '$', '%', '^', '&', '*', '('};
        for (int k = Input.Keys.NUM_0; k <= Input.Keys.NUM_9; k++) {
            if (Gdx.input.isKeyJustPressed(k)) {
                int n = k - Input.Keys.NUM_0;
                input.append(shift ? shiftNums[n] : (char)('0' + n));
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) input.append(' ');
        if (Gdx.input.isKeyJustPressed(Input.Keys.PERIOD)) input.append(shift ? '>' : '.');
        if (Gdx.input.isKeyJustPressed(Input.Keys.COMMA)) input.append(shift ? '<' : ',');
        if (Gdx.input.isKeyJustPressed(Input.Keys.SLASH)) input.append(shift ? '?' : '/');
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS)) input.append(shift ? '_' : '-');
        if (Gdx.input.isKeyJustPressed(Input.Keys.SEMICOLON)) input.append(shift ? ':' : ';');
        if (Gdx.input.isKeyJustPressed(Input.Keys.APOSTROPHE)) input.append(shift ? '"' : '\'');
    }

    private void handleMouse() {
        float mx = InputHelper.mX, my = InputHelper.mY;

        // Header area for dragging
        float headerH = 40f * Settings.scale;
        float headerY = windowY + HEIGHT - headerH;

        // Handle dragging
        if (InputHelper.justClickedLeft) {
            if (mx >= windowX && mx <= windowX + WIDTH && my >= headerY && my <= headerY + headerH) {
                // Start drag from header
                dragging = true;
                dragOffsetX = mx - windowX;
                dragOffsetY = my - windowY;
            }
        }

        if (dragging) {
            if (InputHelper.isMouseDown) {
                // Update position while dragging
                windowX = mx - dragOffsetX;
                windowY = my - dragOffsetY;

                // Clamp to screen bounds
                windowX = Math.max(0, Math.min(windowX, Settings.WIDTH - WIDTH));
                windowY = Math.max(0, Math.min(windowY, Settings.HEIGHT - HEIGHT));
            } else {
                // Stop dragging when mouse released
                dragging = false;
            }
            return; // Don't process other clicks while dragging
        }

        float inputY = windowY + PAD;
        float btnY = inputY + 40f * Settings.scale + PAD;
        float btnW = (WIDTH - PAD * 6) / 5;
        float btnH = 32f * Settings.scale;

        hoverBtn = -1;
        for (int i = 0; i < 5; i++) {
            float bx = windowX + PAD + i * (btnW + PAD);
            if (mx >= bx && mx <= bx + btnW && my >= btnY && my <= btnY + btnH) {
                hoverBtn = i;
            }
        }

        if (InputHelper.justClickedLeft) {
            if (hoverBtn == 0) playAgent();
            else if (hoverBtn == 1) analyze();
            else if (hoverBtn == 2) tip();
            else if (hoverBtn == 3) clear();
            else if (hoverBtn == 4) openCJK();
        }

        float msgBot = btnY + btnH + PAD;
        float msgTop = windowY + HEIGHT - 48f * Settings.scale - PAD;
        if (mx >= windowX && mx <= windowX + WIDTH && my >= msgBot && my <= msgTop) {
            // Scroll by lines (3 lines per scroll step)
            if (InputHelper.scrolledUp) scroll = Math.max(0, scroll + 3);
            if (InputHelper.scrolledDown) scroll = Math.max(0, scroll - 3);
        }
    }

    public void render(SpriteBatch sb) {
        if (!visible) return;

        drawGlow(sb, windowX, windowY, WIDTH, HEIGHT, CYAN_GLOW, 12f * Settings.scale);
        drawRect(sb, windowX, windowY, WIDTH, HEIGHT, BG_OUTER);
        drawRect(sb, windowX + 3f * Settings.scale, windowY + 3f * Settings.scale,
                WIDTH - 6f * Settings.scale, HEIGHT - 6f * Settings.scale, BG_INNER);
        drawGlowBorder(sb, windowX, windowY, WIDTH, HEIGHT, CYAN_DIM, 2f * Settings.scale);
        drawBorder(sb, windowX, windowY, WIDTH, HEIGHT, CYAN);

        renderHeader(sb);

        float inputY = windowY + PAD;
        float btnY = inputY + 40f * Settings.scale + PAD;
        float msgBot = btnY + 32f * Settings.scale + PAD * 0.5f;
        float msgTop = windowY + HEIGHT - 44f * Settings.scale - PAD;

        renderMsgs(sb, msgBot, msgTop);
        renderBtns(sb, btnY);
        renderInput(sb, inputY);
        renderCursor(sb);
    }

    private void renderHeader(SpriteBatch sb) {
        float headerH = 40f * Settings.scale;
        float headerY = windowY + HEIGHT - headerH;

        drawRect(sb, windowX, headerY, WIDTH, headerH, HEADER_BG);
        drawRect(sb, windowX, headerY, WIDTH, 1f * Settings.scale, CYAN_DIM);

        float titleX = windowX + PAD;
        float titleY = headerY + headerH * 0.5f + 7f * Settings.scale;
        FontHelper.renderFontLeft(sb, FontHelper.tipBodyFont, "AI Assistant", titleX, titleY, GOLD);

        renderStatus(sb, headerY, headerH);
    }

    private void renderStatus(SpriteBatch sb, float headerY, float headerH) {
        float statusX = windowX + WIDTH - PAD - 75f * Settings.scale;
        float statusY = headerY + headerH * 0.5f;

        float pulse = (float) Math.sin(animTimer * 4) * 0.3f + 0.7f;
        Color dotColor;
        String statusText;

        if (streaming) {
            dotColor = new Color(1f, 0.8f, 0.2f, pulse);
            statusText = "Thinking...";
        } else {
            dotColor = new Color(0.3f, 0.95f, 0.5f, 0.95f);
            statusText = "Ready";
        }

        float dotSize = 6f * Settings.scale;
        drawRect(sb, statusX - dotSize/2, statusY - dotSize/2, dotSize, dotSize, dotColor);

        if (streaming) {
            Color glowColor = new Color(1f, 0.8f, 0.2f, pulse * 0.25f);
            float glowSize = dotSize * 2f;
            drawRect(sb, statusX - glowSize/2, statusY - glowSize/2, glowSize, glowSize, glowColor);
        }

        FontHelper.renderFontLeft(sb, FontHelper.tipBodyFont, statusText,
                statusX + dotSize, statusY + 7f * Settings.scale,
                streaming ? Color.YELLOW : Color.GREEN);
    }

    private void renderMsgs(SpriteBatch sb, float bot, float top) {
        float lineH = 26f * Settings.scale;
        float maxW = WIDTH - PAD * 5;
        float msgGap = 10f * Settings.scale;

        // Prepare all messages and calculate total height
        List<List<String>> allLines = new ArrayList<>();
        List<Float> msgHeights = new ArrayList<>();
        float totalHeight = 0;

        for (Msg m : messages) {
            String text = cleanMarkdown(m.prefix + m.text);
            List<String> lines = wrapText(text, maxW);
            allLines.add(lines);
            float msgH = lines.size() * lineH + PAD * 1.5f;
            msgHeights.add(msgH);
            totalHeight += msgH + msgGap;
        }

        float viewHeight = top - bot;
        float maxScroll = Math.max(0, totalHeight - viewHeight);
        float scrollOffset = Math.min(scroll * lineH, maxScroll);

        // Clamp scroll
        if (scrollOffset > maxScroll) {
            scroll = (int)(maxScroll / lineH);
            scrollOffset = maxScroll;
        }

        sb.flush();
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor((int) windowX, (int) bot, (int) WIDTH, (int) viewHeight);

        // Render from newest (bottom) to oldest (top)
        float y = bot - scrollOffset;

        for (int idx = messages.size() - 1; idx >= 0; idx--) {
            Msg m = messages.get(idx);
            List<String> lines = allLines.get(idx);
            float msgH = msgHeights.get(idx);

            // Skip if completely below view
            if (y + msgH < bot) {
                y += msgH + msgGap;
                continue;
            }
            // Stop if completely above view
            if (y > top) break;

            // Draw message background
            drawRect(sb, windowX + PAD + 2f * Settings.scale, y - 2f * Settings.scale,
                    WIDTH - PAD * 2.5f, msgH, new Color(0, 0, 0, 0.3f));
            drawRect(sb, windowX + PAD, y, WIDTH - PAD * 2.5f, msgH, m.bg);
            drawRect(sb, windowX + PAD, y, 3f * Settings.scale, msgH, m.border);

            // Draw text lines
            float ty = y + msgH - lineH * 0.7f;
            for (String line : lines) {
                if (ty >= bot - lineH && ty <= top + lineH) {
                    FontHelper.renderFontLeft(sb, FontHelper.tipBodyFont, line,
                            windowX + PAD * 2, ty, m.color);
                }
                ty -= lineH;
            }

            y += msgH + msgGap;
        }

        // Streaming message
        if (streaming && streamBuf.length() > 0) {
            String txt;
            synchronized (streamBuf) {
                txt = streamBuf.toString();
                if (txt.length() > 500) txt = "..." + txt.substring(txt.length() - 497);
            }
            txt = cleanMarkdown("AI: " + txt);
            List<String> lines = wrapText(txt, maxW);
            float msgH = lines.size() * lineH + PAD * 1.5f;

            if (y > bot - msgH && y < top) {
                float glow = (float) Math.sin(animTimer * 4) * 0.2f + 0.5f;
                Color animBorder = new Color(0.8f, 0.6f, 0.2f, glow);

                drawRect(sb, windowX + PAD, y, WIDTH - PAD * 2.5f, msgH, AI_BG);
                drawRect(sb, windowX + PAD, y, 3f * Settings.scale, msgH, animBorder);

                float ty = y + msgH - lineH * 0.7f;
                for (String line : lines) {
                    if (ty >= bot - lineH && ty <= top + lineH) {
                        FontHelper.renderFontLeft(sb, FontHelper.tipBodyFont, line,
                                windowX + PAD * 2, ty, Color.WHITE);
                    }
                    ty -= lineH;
                }
            }
        }

        sb.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        // Scrollbar
        if (totalHeight > viewHeight) {
            float barX = windowX + WIDTH - 8f * Settings.scale;
            drawRect(sb, barX, bot, 4f * Settings.scale, viewHeight, new Color(0.2f, 0.2f, 0.25f, 0.5f));

            float thumbH = Math.max(20f * Settings.scale, viewHeight * viewHeight / totalHeight);
            float scrollRange = viewHeight - thumbH;
            float ratio = scrollOffset / maxScroll;
            float thumbY = bot + scrollRange * (1 - ratio);
            drawRect(sb, barX, thumbY, 4f * Settings.scale, thumbH, CYAN_DIM);
        }
    }

    private void renderBtns(SpriteBatch sb, float y) {
        String firstLabel = agent.isRunning() ? "Stop" : "Play";
        String[] labels = {firstLabel, "Analyze", "Tip", "Clear", "CJK"};
        float btnW = (WIDTH - PAD * 6) / 5;
        float btnH = 32f * Settings.scale;

        for (int i = 0; i < 5; i++) {
            float bx = windowX + PAD + i * (btnW + PAD);
            boolean hover = hoverBtn == i;
            boolean isRunning = (i == 0 && agent.isRunning());

            if (hover || isRunning) {
                Color glowColor = isRunning ? new Color(0.8f, 0.2f, 0.2f, 0.15f) : CYAN_GLOW;
                drawGlow(sb, bx, y, btnW, btnH, glowColor, 4f * Settings.scale);
            }

            Color bgColor = isRunning ? new Color(0.25f, 0.12f, 0.12f, 0.95f) : (hover ? BTN_HOVER : BTN_BG);
            Color borderColor = isRunning ? new Color(0.8f, 0.3f, 0.3f, 0.7f) : (hover ? CYAN : BTN_BORDER);
            drawRect(sb, bx, y, btnW, btnH, bgColor);
            drawBorder(sb, bx, y, btnW, btnH, borderColor);

            gl.setText(FontHelper.tipBodyFont, labels[i]);
            float tx = bx + (btnW - gl.width) / 2;
            float ty = y + btnH / 2 + gl.height / 2;

            Color textColor = isRunning ? new Color(1f, 0.6f, 0.6f, 1f) : (hover ? Color.WHITE : new Color(0.75f, 0.75f, 0.8f, 1f));
            FontHelper.tipBodyFont.setColor(textColor);
            FontHelper.tipBodyFont.draw(sb, labels[i], tx, ty);
        }
    }

    private void renderInput(SpriteBatch sb, float y) {
        float h = 40f * Settings.scale;

        if (focused) {
            drawGlow(sb, windowX + PAD, y, WIDTH - PAD * 2, h, CYAN_GLOW, 4f * Settings.scale);
        }

        drawRect(sb, windowX + PAD, y, WIDTH - PAD * 2, h, new Color(0.08f, 0.08f, 0.12f, 0.95f));
        drawBorder(sb, windowX + PAD, y, WIDTH - PAD * 2, h, focused ? CYAN : BTN_BORDER);

        String text = input.toString();
        boolean showCursor = focused && System.currentTimeMillis() % 800 < 400;

        gl.setText(FontHelper.tipBodyFont, text);
        float maxW = WIDTH - PAD * 4;
        while (gl.width > maxW - 10f * Settings.scale && text.length() > 4) {
            text = "..." + text.substring(4);
            gl.setText(FontHelper.tipBodyFont, text);
        }

        float ty = y + h / 2 + FontHelper.tipBodyFont.getLineHeight() * 0.35f;
        float textX = windowX + PAD * 1.5f;

        if (text.isEmpty()) {
            FontHelper.tipBodyFont.setColor(new Color(0.4f, 0.4f, 0.45f, 0.8f));
            FontHelper.tipBodyFont.draw(sb, "Type message or use CJK button...", textX, ty);
            if (showCursor) {
                drawRect(sb, textX, y + 8f * Settings.scale, 2f * Settings.scale, h - 16f * Settings.scale, CYAN);
            }
        } else {
            FontHelper.tipBodyFont.setColor(Color.WHITE);
            FontHelper.tipBodyFont.draw(sb, text, textX, ty);
            if (showCursor) {
                gl.setText(FontHelper.tipBodyFont, text);
                drawRect(sb, textX + gl.width + 2f * Settings.scale, y + 8f * Settings.scale,
                        2f * Settings.scale, h - 16f * Settings.scale, CYAN);
            }
        }
    }

    private void renderCursor(SpriteBatch sb) {
        float mx = InputHelper.mX, my = InputHelper.mY;
        if (mx >= windowX - 30 && mx <= windowX + WIDTH + 30 && my >= windowY - 30 && my <= windowY + HEIGHT + 30) {
            float size = 10f * Settings.scale;
            drawRect(sb, mx - 1f * Settings.scale, my - size, 2f * Settings.scale, size * 2, CYAN);
            drawRect(sb, mx - size, my - 1f * Settings.scale, size * 2, 2f * Settings.scale, CYAN);
            drawRect(sb, mx - 2f * Settings.scale, my - 2f * Settings.scale, 4f * Settings.scale, 4f * Settings.scale, Color.WHITE);
        }
    }

    // Drawing helpers
    private void drawRect(SpriteBatch sb, float x, float y, float w, float h, Color c) {
        sb.setColor(c);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y, w, h);
    }

    private void drawBorder(SpriteBatch sb, float x, float y, float w, float h, Color c) {
        sb.setColor(c);
        float t = 1f * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y, w, t);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y + h - t, w, t);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x, y, t, h);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x + w - t, y, t, h);
    }

    private void drawGlow(SpriteBatch sb, float x, float y, float w, float h, Color c, float size) {
        sb.setColor(c);
        for (float i = size; i > 0; i -= 2f * Settings.scale) {
            float alpha = c.a * (1f - i / size) * 0.5f;
            sb.setColor(c.r, c.g, c.b, alpha);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, x - i, y - i, w + i * 2, h + i * 2);
        }
    }

    private void drawGlowBorder(SpriteBatch sb, float x, float y, float w, float h, Color c, float t) {
        sb.setColor(c);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x - t, y - t, w + t * 2, t * 2);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x - t, y + h - t, w + t * 2, t * 2);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x - t, y, t * 2, h);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x + w - t, y, t * 2, h);
    }

    // Text processing
    private String cleanMarkdown(String text) {
        if (text == null) return "";
        text = text.replaceAll("```[\\s\\S]*?```", "[code]");
        text = text.replaceAll("`([^`]+)`", "$1");
        text = text.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        text = text.replaceAll("__([^_]+)__", "$1");
        text = text.replaceAll("\\*([^*]+)\\*", "$1");
        text = text.replaceAll("_([^_]+)_", "$1");
        text = text.replaceAll("(?m)^#{1,6}\\s*", "");
        text = text.replaceAll("(?m)^\\s*[-*+]\\s+", "- ");
        text = text.replaceAll("(?m)^\\s*\\d+\\.\\s+", "- ");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private List<String> wrapText(String text, float maxW) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) { lines.add(""); return lines; }

        StringBuilder line = new StringBuilder();
        StringBuilder word = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || isCJK(c)) {
                if (word.length() > 0) {
                    String testLine = line.toString() + word.toString();
                    gl.setText(FontHelper.tipBodyFont, testLine);
                    if (gl.width > maxW && line.length() > 0) {
                        lines.add(line.toString().trim());
                        line.setLength(0);
                    }
                    line.append(word);
                    word.setLength(0);
                }
                if (c == ' ') {
                    line.append(' ');
                } else {
                    String testLine = line.toString() + c;
                    gl.setText(FontHelper.tipBodyFont, testLine);
                    if (gl.width > maxW && line.length() > 0) {
                        lines.add(line.toString().trim());
                        line.setLength(0);
                    }
                    line.append(c);
                }
            } else {
                word.append(c);
            }
        }

        if (word.length() > 0) {
            String testLine = line.toString() + word.toString();
            gl.setText(FontHelper.tipBodyFont, testLine);
            if (gl.width > maxW && line.length() > 0) {
                lines.add(line.toString().trim());
                line.setLength(0);
            }
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString().trim());
        if (lines.isEmpty()) lines.add("");
        // Allow up to 50 lines per message (scrollable)
        while (lines.size() > 50) lines.remove(lines.size() - 1);
        return lines;
    }

    private boolean isCJK(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3040 && c <= 0x30FF) ||
               (c >= 0xAC00 && c <= 0xD7AF) || (c >= 0xFF00 && c <= 0xFFEF);
    }

    // Input handling
    private void openCJK() {
        new Thread(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            String r = JOptionPane.showInputDialog(null, "Enter text (CJK supported):", "Input", JOptionPane.PLAIN_MESSAGE);
            if (r != null && !r.trim().isEmpty()) {
                Gdx.app.postRunnable(() -> input.append(r.trim()));
            }
        }).start();
    }

    private void paste() {
        try {
            String t = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (t != null) input.append(t.replace("\n", " ").replace("\r", ""));
        } catch (Exception ignored) {}
    }

    private void send(String text) {
        text = text.trim();
        if (text.isEmpty()) return;
        if (text.startsWith("/")) {
            String cmd = text.toLowerCase();
            if (cmd.equals("/help") || cmd.equals("/h")) addSys("Commands: /analyze /tip /clear /help");
            else if (cmd.equals("/analyze") || cmd.equals("/a")) analyze();
            else if (cmd.equals("/tip") || cmd.equals("/t")) tip();
            else if (cmd.equals("/clear") || cmd.equals("/c")) clear();
            else addSys("Unknown: " + text);
        } else {
            addUser(text);
            ask(text);
        }
    }

    private void analyze() {
        if (streaming) return;
        addSys("Analyzing game state...");
        streaming = true;
        synchronized (streamBuf) { streamBuf.setLength(0); }

        agent.analyze(
            chunk -> { synchronized (streamBuf) { streamBuf.append(chunk); } },
            () -> {
                String r;
                synchronized (streamBuf) { r = streamBuf.toString(); streamBuf.setLength(0); }
                Gdx.app.postRunnable(() -> { addAI(r); streaming = false; scroll = 0; });
            }
        );
    }

    private void tip() {
        if (streaming) return;
        addSys("Getting combat tip...");
        agent.getQuickTip().thenAccept(t ->
            Gdx.app.postRunnable(() -> { addAI(t); scroll = 0; })
        );
    }

    private void playAgent() {
        if (streaming || agent.isRunning()) {
            if (agent.isRunning()) {
                agent.stop();
                addSys("Stopping agent...");
            }
            return;
        }
        addSys("AI Agent starting...");
        streaming = true;
        synchronized (streamBuf) { streamBuf.setLength(0); }

        agent.play(
            chunk -> {
                synchronized (streamBuf) { streamBuf.append(chunk); }
                String current;
                synchronized (streamBuf) { current = streamBuf.toString(); }
                if (current.length() > 0) {
                    final String display = current;
                    Gdx.app.postRunnable(() -> {
                        if (!messages.isEmpty() && messages.get(messages.size() - 1).prefix.equals("AI: ")) {
                            messages.remove(messages.size() - 1);
                        }
                        addAI(display);
                    });
                }
            },
            () -> {
                String r;
                synchronized (streamBuf) { r = streamBuf.toString(); streamBuf.setLength(0); }
                Gdx.app.postRunnable(() -> {
                    if (!r.isEmpty()) {
                        if (!messages.isEmpty() && messages.get(messages.size() - 1).prefix.equals("AI: ")) {
                            messages.remove(messages.size() - 1);
                        }
                        addAI(r);
                    }
                    addSys("Agent finished");
                    streaming = false;
                    scroll = 0;
                });
            }
        );
    }

    private void clear() {
        messages.clear();
        agent.clearChatHistory();  // Also clear LLM conversation history
        scroll = 0;
        addSys("Chat cleared");
    }

    private void ask(String q) {
        if (streaming) return;
        streaming = true;
        synchronized (streamBuf) { streamBuf.setLength(0); }

        agent.chat(q,
            chunk -> { synchronized (streamBuf) { streamBuf.append(chunk); } },
            () -> {
                String r;
                synchronized (streamBuf) { r = streamBuf.toString(); streamBuf.setLength(0); }
                Gdx.app.postRunnable(() -> { addAI(r); streaming = false; scroll = 0; });
            }
        );
    }

    private void addUser(String t) { add("You: ", t, USER_BG, USER_BORDER, Color.WHITE); }
    private void addAI(String t) { add("AI: ", t, AI_BG, AI_BORDER, Color.WHITE); }
    private void addSys(String t) { add("", t, SYS_BG, SYS_BORDER, GOLD); }

    private void add(String prefix, String text, Color bg, Color border, Color color) {
        if (text == null || text.isEmpty()) return;
        messages.add(new Msg(prefix, text.trim(), bg, border, color));
        while (messages.size() > 50) messages.remove(0);
        scroll = 0;
    }

    public void triggerAnalysis() {
        if (!visible) show();
        analyze();
    }

    private static class Msg {
        final String prefix, text;
        final Color bg, border, color;
        Msg(String p, String t, Color bg, Color border, Color c) {
            this.prefix = p; this.text = t; this.bg = bg; this.border = border; this.color = c;
        }
    }
}
