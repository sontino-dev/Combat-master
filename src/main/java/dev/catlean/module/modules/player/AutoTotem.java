package dev.catlean.module.modules.player;

import dev.catlean.module.Category;
import dev.catlean.module.Module;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

public class AutoTotem extends Module {

    private final FloatSetting   health = addSetting(new FloatSetting  ("MinHealth", 8f,  1f, 20f));
    private final BooleanSetting always = addSetting(new BooleanSetting("Always",    false));

    private int cooldown;

    public AutoTotem() {
        super("AutoTotem", "Keeps a totem in the offhand", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onTick() {
        if (cooldown > 0) { cooldown--; return; }
        if (mc.player == null || mc.currentScreen != null) return;

        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        if (!always.isEnabled() && mc.player.getHealth() > health.getValue()) return;

        var handler = mc.player.currentScreenHandler;
        for (int i = 9; i <= 44; i++) {
            if (handler.getSlot(i).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                mc.interactionManager.clickSlot(handler.syncId, i, 40, SlotActionType.SWAP, mc.player);
                cooldown = 3;
                return;
            }
        }
    }
}
