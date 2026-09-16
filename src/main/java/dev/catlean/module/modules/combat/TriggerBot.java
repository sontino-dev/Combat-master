package dev.catlean.module.modules.combat;

import dev.catlean.module.Category;
import dev.catlean.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public class TriggerBot extends Module {

    private final IntSetting     minDelay = addSetting(new IntSetting    ("MinDelay", 80,  0, 500));
    private final IntSetting     rndDelay = addSetting(new IntSetting    ("RndDelay", 60,  0, 300));
    private final BooleanSetting players  = addSetting(new BooleanSetting("Players",  true));
    private final BooleanSetting mobs     = addSetting(new BooleanSetting("Mobs",     false));
    private final BooleanSetting useCd   = addSetting(new BooleanSetting ("Cooldown", true));
    private final BooleanSetting onLMB   = addSetting(new BooleanSetting ("OnLMB",    false));

    public static TriggerBot INSTANCE;

    private long lastAttack;

    public TriggerBot() {
        super("TriggerBot", "Auto attacks when crosshair is on entity", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
        INSTANCE = this;
    }

    @Override public void onEnable() { lastAttack = 0L; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (mc.crosshairTarget == null
                || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return;

        Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
        if (!(entity instanceof LivingEntity)) return;
        if (!players.isEnabled() && entity instanceof PlayerEntity) return;
        if (!mobs.isEnabled() && !(entity instanceof PlayerEntity)) return;
        if (entity == mc.player) return;

        if (onLMB.isEnabled()) {
            boolean lmb = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(),
                    GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (!lmb) return;
        }

        if (useCd.isEnabled() && mc.player.getAttackCooldownProgress(0f) < 1f) return;

        long wait = minDelay.getValue() + (long)(Math.random() * rndDelay.getValue());
        if (System.currentTimeMillis() - lastAttack < wait) return;

        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastAttack = System.currentTimeMillis();
    }
}
