package dev.catlean.module.modules.player;

import dev.catlean.module.Category;
import dev.catlean.module.Module;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class AutoGapple extends Module {

    private final FloatSetting health = addSetting(new FloatSetting("Health", 10f, 2f, 19f));

    private enum S { IDLE, EAT, RESTORE }

    private S state = S.IDLE;
    private int prevSlot = -1;
    private int cooldown;

    public AutoGapple() {
        super("AutoGapple", "Eats a golden apple at low health", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (cooldown > 0) { cooldown--; return; }

        switch (state) {
            case IDLE -> tickIdle();
            case EAT -> tickEat();
            case RESTORE -> tickRestore();
        }
    }

    private void tickIdle() {
        float hp = mc.player.getHealth();
        if (hp > health.getValue() || hp >= mc.player.getMaxHealth()) return;

        int slot = findHotbar(Items.ENCHANTED_GOLDEN_APPLE);
        if (slot == -1) slot = findHotbar(Items.GOLDEN_APPLE);
        if (slot == -1) { cooldown = 20; return; }

        prevSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));

        var result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (result.isAccepted()) mc.player.setCurrentHand(Hand.MAIN_HAND);

        state = S.EAT;
    }

    private void tickEat() {
        if (!mc.player.isUsingItem()) {
            state = S.RESTORE;
            return;
        }
        if (mc.player.getItemUseTime() > 64) {
            mc.interactionManager.stopUsingItem(mc.player);
            state = S.RESTORE;
        }
    }

    private void tickRestore() {
        if (prevSlot != -1) {
            mc.player.getInventory().selectedSlot = prevSlot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
            prevSlot = -1;
        }
        state = S.IDLE;
        cooldown = 30;
    }

    @Override
    public void onDisable() {
        if (state == S.EAT && mc.player != null && mc.player.isUsingItem())
            mc.interactionManager.stopUsingItem(mc.player);
        if (prevSlot != -1 && mc.player != null) {
            mc.player.getInventory().selectedSlot = prevSlot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
            prevSlot = -1;
        }
        state = S.IDLE;
    }

    private int findHotbar(Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
}
