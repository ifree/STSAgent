package stsagent.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.ui.buttons.GridSelectConfirmButton;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Reads game state directly from game memory.
 * Provides formatted output for LLM consumption.
 */
public class GameStateReader {
    private static final Logger logger = LogManager.getLogger(GameStateReader.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public boolean isInGame() {
        try {
            return AbstractDungeon.player != null && AbstractDungeon.currMapNode != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isInCombat() {
        try {
            if (!isInGame()) return false;
            AbstractRoom room = AbstractDungeon.getCurrRoom();
            return room != null &&
                   room.phase == AbstractRoom.RoomPhase.COMBAT &&
                   !AbstractDungeon.isScreenUp;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get basic game state: character, HP, gold, floor, etc.
     */
    public String getGameState() {
        try {
            if (!isInGame()) {
                return "Not in game (main menu or loading).";
            }

            AbstractPlayer player = AbstractDungeon.player;
            Map<String, Object> state = new LinkedHashMap<>();

            state.put("character", player.title);
            state.put("hp", player.currentHealth + "/" + player.maxHealth);
            state.put("gold", player.gold);
            state.put("floor", AbstractDungeon.floorNum);
            state.put("act", AbstractDungeon.actNum);
            state.put("ascension", AbstractDungeon.ascensionLevel);

            if (AbstractDungeon.id != null) {
                state.put("dungeon", AbstractDungeon.id);
            }

            return gson.toJson(state);
        } catch (Exception e) {
            logger.error("Error reading game state", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Get combat state: energy, hand, enemies, buffs/debuffs.
     */
    public String getCombatState() {
        try {
            if (!isInGame()) {
                return "{\"error\": \"Not in game\"}";
            }
            if (!isInCombat()) {
                return "{\"error\": \"Not in combat\"}";
            }

            AbstractPlayer player = AbstractDungeon.player;
            Map<String, Object> state = new LinkedHashMap<>();

            // Energy
            state.put("energy", EnergyPanel.totalCount + "/" + player.energy.energyMaster);

            // Player HP and Block
            state.put("player_hp", player.currentHealth + "/" + player.maxHealth);
            if (player.currentBlock > 0) {
                state.put("player_block", player.currentBlock);
            }

            // Player powers (buffs/debuffs)
            if (!player.powers.isEmpty()) {
                List<Map<String, Object>> powers = new ArrayList<>();
                for (AbstractPower p : player.powers) {
                    Map<String, Object> power = new LinkedHashMap<>();
                    power.put("name", p.name);
                    power.put("amount", p.amount);
                    powers.add(power);
                }
                state.put("player_powers", powers);
            }

            // Hand (1-indexed for LLM)
            List<Map<String, Object>> hand = new ArrayList<>();
            for (int i = 0; i < player.hand.size(); i++) {
                AbstractCard card = player.hand.group.get(i);
                Map<String, Object> cardInfo = new LinkedHashMap<>();
                cardInfo.put("index", i + 1);
                cardInfo.put("name", card.name);
                cardInfo.put("cost", card.costForTurn);
                cardInfo.put("type", card.type.toString());
                cardInfo.put("playable", card.canUse(player, null));
                if (card.type == AbstractCard.CardType.ATTACK) {
                    cardInfo.put("damage", card.damage);
                    cardInfo.put("needs_target", true);
                }
                if (card.type == AbstractCard.CardType.SKILL && card.block > 0) {
                    cardInfo.put("block", card.block);
                }
                hand.add(cardInfo);
            }
            state.put("hand", hand);

            // Enemies (1-indexed, only alive)
            MonsterGroup monsters = AbstractDungeon.getCurrRoom().monsters;
            if (monsters != null) {
                List<Map<String, Object>> enemies = new ArrayList<>();
                int idx = 1;
                for (AbstractMonster m : monsters.monsters) {
                    if (!m.isDead && !m.escaped) {
                        Map<String, Object> enemy = new LinkedHashMap<>();
                        enemy.put("index", idx);
                        enemy.put("name", m.name);
                        enemy.put("hp", m.currentHealth + "/" + m.maxHealth);
                        enemy.put("intent", getIntentDescription(m));
                        if (m.currentBlock > 0) {
                            enemy.put("block", m.currentBlock);
                        }
                        // Enemy powers
                        if (!m.powers.isEmpty()) {
                            List<String> powers = new ArrayList<>();
                            for (AbstractPower p : m.powers) {
                                powers.add(p.name + (p.amount != 0 ? " " + p.amount : ""));
                            }
                            enemy.put("powers", powers);
                        }
                        enemies.add(enemy);
                        idx++;
                    }
                }
                state.put("enemies", enemies);
            }

            // Draw pile and discard pile sizes
            state.put("draw_pile", player.drawPile.size());
            state.put("discard_pile", player.discardPile.size());
            state.put("exhaust_pile", player.exhaustPile.size());

            return gson.toJson(state);
        } catch (Exception e) {
            logger.error("Error reading combat state", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Get current screen state: type, choices, buttons.
     */
    public String getScreen() {
        try {
            if (!isInGame()) {
                return "{\"screen_type\": \"MAIN_MENU\"}";
            }

            Map<String, Object> state = new LinkedHashMap<>();

            // Screen type
            String screenType = getScreenType();
            state.put("screen_type", screenType);

            // Room phase
            AbstractRoom room = AbstractDungeon.getCurrRoom();
            if (room != null) {
                state.put("room_phase", room.phase.toString());
            }

            // Choices (if available)
            List<String> choices = getChoices();
            if (!choices.isEmpty()) {
                List<Map<String, Object>> choiceList = new ArrayList<>();
                for (int i = 0; i < choices.size(); i++) {
                    Map<String, Object> choice = new LinkedHashMap<>();
                    choice.put("index", i + 1);
                    choice.put("name", choices.get(i));
                    choiceList.add(choice);
                }
                state.put("choices", choiceList);
            }

            // Button states
            state.put("can_proceed", isProceedAvailable());
            state.put("can_skip", isSkipAvailable());
            state.put("can_cancel", isCancelAvailable());

            return gson.toJson(state);
        } catch (Exception e) {
            logger.error("Error reading screen state", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Get deck information.
     */
    public String getDeck() {
        try {
            if (!isInGame()) {
                return "{\"error\": \"Not in game\"}";
            }

            AbstractPlayer player = AbstractDungeon.player;
            Map<String, Object> state = new LinkedHashMap<>();

            // Master deck
            List<Map<String, Object>> deck = new ArrayList<>();
            for (AbstractCard card : player.masterDeck.group) {
                Map<String, Object> cardInfo = new LinkedHashMap<>();
                cardInfo.put("name", card.name);
                cardInfo.put("type", card.type.toString());
                cardInfo.put("cost", card.cost);
                cardInfo.put("rarity", card.rarity.toString());
                if (card.upgraded) {
                    cardInfo.put("upgraded", true);
                }
                deck.add(cardInfo);
            }
            state.put("deck", deck);
            state.put("deck_size", deck.size());

            return gson.toJson(state);
        } catch (Exception e) {
            logger.error("Error reading deck", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Get relics.
     */
    public String getRelics() {
        try {
            if (!isInGame()) {
                return "{\"error\": \"Not in game\"}";
            }

            AbstractPlayer player = AbstractDungeon.player;
            List<Map<String, Object>> relics = new ArrayList<>();

            for (AbstractRelic r : player.relics) {
                Map<String, Object> relic = new LinkedHashMap<>();
                relic.put("name", r.name);
                relic.put("id", r.relicId);
                if (r.counter >= 0) {
                    relic.put("counter", r.counter);
                }
                relics.add(relic);
            }

            Map<String, Object> state = new LinkedHashMap<>();
            state.put("relics", relics);
            return gson.toJson(state);
        } catch (Exception e) {
            logger.error("Error reading relics", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Get potions.
     */
    public String getPotions() {
        try {
            if (!isInGame()) {
                return "{\"error\": \"Not in game\"}";
            }

            AbstractPlayer player = AbstractDungeon.player;
            List<Map<String, Object>> potions = new ArrayList<>();

            for (int i = 0; i < player.potions.size(); i++) {
                AbstractPotion p = player.potions.get(i);
                Map<String, Object> potion = new LinkedHashMap<>();
                potion.put("slot", i + 1);
                if (p instanceof PotionSlot) {
                    potion.put("name", "Empty");
                    potion.put("empty", true);
                } else {
                    potion.put("name", p.name);
                    potion.put("id", p.ID);
                    potion.put("can_use", p.canUse());
                    potion.put("requires_target", p.isThrown);
                }
                potions.add(potion);
            }

            Map<String, Object> state = new LinkedHashMap<>();
            state.put("potions", potions);
            state.put("potion_slots", player.potionSlots);
            return gson.toJson(state);
        } catch (Exception e) {
            logger.error("Error reading potions", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Get map information.
     */
    public String getMap() {
        try {
            if (!isInGame()) {
                return "{\"error\": \"Not in game\"}";
            }

            Map<String, Object> state = new LinkedHashMap<>();
            state.put("current_floor", AbstractDungeon.floorNum);
            state.put("act", AbstractDungeon.actNum);

            // Current node
            MapRoomNode currentNode = AbstractDungeon.currMapNode;
            if (currentNode != null && currentNode.room != null) {
                state.put("current_room", currentNode.room.getClass().getSimpleName());
            }

            // Available paths (next nodes)
            if (currentNode != null && AbstractDungeon.map != null) {
                List<Map<String, Object>> nextNodes = new ArrayList<>();
                ArrayList<ArrayList<MapRoomNode>> map = AbstractDungeon.map;
                int nextY = currentNode.y + 1;
                if (nextY < map.size()) {
                    for (MapRoomNode nextNode : map.get(nextY)) {
                        if (nextNode != null && nextNode.hasEdges()) {
                            // Check if this node is connected from current
                            for (com.megacrit.cardcrawl.map.MapEdge edge : currentNode.getEdges()) {
                                if (edge.dstX == nextNode.x && edge.dstY == nextNode.y) {
                                    Map<String, Object> node = new LinkedHashMap<>();
                                    node.put("x", nextNode.x);
                                    node.put("y", nextNode.y);
                                    node.put("symbol", nextNode.getRoomSymbol(true));
                                    nextNodes.add(node);
                                    break;
                                }
                            }
                        }
                    }
                }
                if (!nextNodes.isEmpty()) {
                    state.put("next_nodes", nextNodes);
                }
            }

            // Boss
            if (AbstractDungeon.bossKey != null) {
                state.put("boss", AbstractDungeon.bossKey);
            }

            return gson.toJson(state);
        } catch (Exception e) {
            logger.error("Error reading map", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    // ========== Helper Methods ==========

    private String getScreenType() {
        try {
            if (!isInGame()) return "MAIN_MENU";

            if (AbstractDungeon.screen != null) {
                return AbstractDungeon.screen.name();
            }

            if (isInCombat()) {
                return "COMBAT";
            }

            AbstractRoom room = AbstractDungeon.getCurrRoom();
            if (room != null) {
                return room.phase.toString();
            }

            return "UNKNOWN";
        } catch (Exception e) {
            return "ERROR";
        }
    }

    private String getIntentDescription(AbstractMonster m) {
        if (m.intent == null) return "Unknown";

        switch (m.intent) {
            case ATTACK:
                int dmg = m.getIntentDmg();
                // Use reflection for private fields
                try {
                    java.lang.reflect.Field isMultiDmgField = AbstractMonster.class.getDeclaredField("isMultiDmg");
                    isMultiDmgField.setAccessible(true);
                    boolean isMulti = isMultiDmgField.getBoolean(m);
                    if (isMulti) {
                        java.lang.reflect.Field multiAmtField = AbstractMonster.class.getDeclaredField("intentMultiAmt");
                        multiAmtField.setAccessible(true);
                        int multiAmt = multiAmtField.getInt(m);
                        return "Attack " + dmg + "x" + multiAmt;
                    }
                } catch (Exception ignored) {}
                return "Attack " + dmg;
            case ATTACK_BUFF:
                return "Attack " + m.getIntentDmg() + " + Buff";
            case ATTACK_DEBUFF:
                return "Attack " + m.getIntentDmg() + " + Debuff";
            case ATTACK_DEFEND:
                return "Attack " + m.getIntentDmg() + " + Defend";
            case BUFF:
                return "Buff";
            case DEBUFF:
                return "Debuff";
            case STRONG_DEBUFF:
                return "Strong Debuff";
            case DEFEND:
                return "Defend";
            case DEFEND_DEBUFF:
                return "Defend + Debuff";
            case DEFEND_BUFF:
                return "Defend + Buff";
            case ESCAPE:
                return "Escape";
            case MAGIC:
                return "Magic";
            case SLEEP:
                return "Sleeping";
            case STUN:
                return "Stunned";
            case UNKNOWN:
            default:
                return "Unknown";
        }
    }

    private List<String> getChoices() {
        List<String> choices = new ArrayList<>();
        try {
            if (!isInGame()) return choices;

            // Card reward screen
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.CARD_REWARD) {
                if (AbstractDungeon.cardRewardScreen != null &&
                    AbstractDungeon.cardRewardScreen.rewardGroup != null) {
                    for (AbstractCard card : AbstractDungeon.cardRewardScreen.rewardGroup) {
                        choices.add(card.name);
                    }
                }
            }
            // Combat reward screen
            else if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.COMBAT_REWARD) {
                if (AbstractDungeon.combatRewardScreen != null &&
                    AbstractDungeon.combatRewardScreen.rewards != null) {
                    for (com.megacrit.cardcrawl.rewards.RewardItem reward :
                         AbstractDungeon.combatRewardScreen.rewards) {
                        choices.add(reward.type.toString());
                    }
                }
            }
            // Map screen
            else if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
                MapRoomNode currentNode = AbstractDungeon.currMapNode;
                if (currentNode != null && AbstractDungeon.map != null) {
                    ArrayList<ArrayList<MapRoomNode>> map = AbstractDungeon.map;
                    int nextY = currentNode.y + 1;
                    if (nextY < map.size()) {
                        for (MapRoomNode nextNode : map.get(nextY)) {
                            if (nextNode != null) {
                                for (com.megacrit.cardcrawl.map.MapEdge edge : currentNode.getEdges()) {
                                    if (edge.dstX == nextNode.x && edge.dstY == nextNode.y) {
                                        choices.add(nextNode.getRoomSymbol(true));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Grid select screen
            else if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID) {
                if (AbstractDungeon.gridSelectScreen != null &&
                    AbstractDungeon.gridSelectScreen.selectedCards != null) {
                    for (AbstractCard card : AbstractDungeon.gridSelectScreen.targetGroup.group) {
                        choices.add(card.name);
                    }
                }
            }
            // Boss reward screen
            else if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.BOSS_REWARD) {
                if (AbstractDungeon.bossRelicScreen != null &&
                    AbstractDungeon.bossRelicScreen.relics != null) {
                    for (AbstractRelic relic : AbstractDungeon.bossRelicScreen.relics) {
                        choices.add(relic.name);
                    }
                }
            }
            // Shop screen
            else if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.SHOP) {
                if (AbstractDungeon.shopScreen != null) {
                    // Shop has cards, relics, potions - simplified
                    choices.add("(Shop items available)");
                }
            }
        } catch (Exception e) {
            logger.error("Error getting choices", e);
        }
        return choices;
    }

    private boolean isProceedAvailable() {
        try {
            if (!isInGame()) return false;

            AbstractRoom room = AbstractDungeon.getCurrRoom();
            if (room == null) return false;

            // Combat reward screen has proceed
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.COMBAT_REWARD) {
                return true;
            }
            // After rest
            if (room.phase == AbstractRoom.RoomPhase.COMPLETE) {
                return true;
            }
            // Grid confirm
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID) {
                GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
                if (screen != null && screen.confirmButton != null) {
                    return !screen.confirmButton.isDisabled;
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSkipAvailable() {
        try {
            if (!isInGame()) return false;

            // Card reward can be skipped
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.CARD_REWARD) {
                return true;
            }
            // Boss reward can be skipped
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.BOSS_REWARD) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isCancelAvailable() {
        try {
            if (!isInGame()) return false;

            // Shop can be cancelled
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.SHOP) {
                return true;
            }
            // Map can be cancelled
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
