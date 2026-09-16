package dev.catlean.module.modules.player;

import dev.catlean.module.Category;
import dev.catlean.module.Module;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

public class AutoArmor extends Module {

    private static final Item[][] TIERS = {
        { Items.NETHERITE_HELMET,    Items.DIAMOND_HELMET,    Items.IRON_HELMET,
          Items.GOLDEN_HELMET,       Items.CHAINMAIL_HELMET,  Items.LEATHER_HELMET,
          Items.TURTLE_HELMET },
        { Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.IRON_CHESTPLATE,
          Items.GOLDEN_CHESTPLATE,    Items.CHAINMAIL_CHESTPLATE, Items.LEATHER_CHESTPLATE },
        { Items.NETHERITE_LEGGINGS,  Items.DIAMOND_LEGGINGS,  Items.IRON_LEGGINGS,
          Items.GOLDEN_LEGGINGS,     Items.CHAINMAIL_LEGGINGS, Items.LEATHER_LEGGINGS },
        { Items.NETHERITE_BOOTS,     Items.DIAMOND_BOOTS,     Items.IRON_BOOTS,
          Items.GOLDEN_BOOTS,        Items.CHAINMAIL_BOOTS,   Items.LEATHER_BOOTS }
    };

    private int cooldown;
    private int step;
    private int pendingBest;
    private int pendingArmorSlot;

    public AutoArmor() {
        super("AutoArmor", "Equips the best armor from inventory", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onTick() {
        if (cooldown > 0) { cooldown--; return; }
        if (mc.player == null || mc.currentScreen != null) return;

        var handler = mc.player.currentScreenHandler;

        if (step > 0) {
            continueSequence(handler);
            return;
        }

        for (int a = 0; a < 4; a++) {
            int armorSlotId = 5 + a;
            ItemStack cur = handler.getSlot(armorSlotId).getStack();
            int curScore = score(cur, a);

            int bestSlot = -1;
            int bestScore = curScore;
            for (int i = 9; i <= 44; i++) {
                ItemStack s = handler.getSlot(i).getStack();
                int sc = score(s, a);
                if (sc > bestScore) {
                    bestScore = sc;
                    bestSlot = i;
                }
            }

            if (bestSlot != -1) {
                mc.interactionManager.clickSlot(handler.syncId, bestSlot, 0, SlotActionType.PICKUP, mc.player);
                step = 1;
                pendingBest = bestSlot;
                pendingArmorSlot = armorSlotId;
                cooldown = 1;
                return;
            }
        }
    }

    private void continueSequence(net.minecraft.screen.ScreenHandler handler) {
        ItemStack cursor = handler.getCursorStack();

        if (cursor.isEmpty()) {
            step = 0;
            cooldown = 1;
            return;
        }

        if (step == 1) {
            mc.interactionManager.clickSlot(handler.syncId, pendingArmorSlot, 0, SlotActionType.PICKUP, mc.player);
            step = 2;
            cooldown = 1;
            return;
        }

        if (step == 2) {
            mc.interactionManager.clickSlot(handler.syncId, pendingBest, 0, SlotActionType.PICKUP, mc.player);
            step = 0;
            cooldown = 3;
        }
    }

    private static int score(ItemStack stack, int armorIdx) {
        if (stack.isEmpty()) return -1;
        Item item = stack.getItem();
        Item[] tiers = TIERS[armorIdx];
        for (int i = 0; i < tiers.length; i++)
            if (item == tiers[i]) return tiers.length - i;
        return -1;
    }
}
