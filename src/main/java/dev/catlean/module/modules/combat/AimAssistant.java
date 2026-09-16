package dev.catlean.module.modules.combat;

import dev.catlean.module.Category;
import dev.catlean.module.Module;
import dev.catlean.util.EntityUtil;
import dev.catlean.util.RotationUtil;
import net.minecraft.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public class AimAssistant extends Module {

    public enum AimPoint { HEAD, NECK, BODY, FEET }
    public enum AimMode  { SMOOTH, INSTANT, NATURAL }

    private final FloatSetting   range       = addSetting(new FloatSetting  ("Range",   5f,    1f, 8f));
    private final FloatSetting   speed       = addSetting(new FloatSetting  ("Speed",   0.3f, 0.02f, 1f));
    private final FloatSetting   fov         = addSetting(new FloatSetting  ("FOV",     60f,  5f, 180f));
    private final BooleanSetting targPlayers = addSetting(new BooleanSetting("Players", true));
    private final BooleanSetting targMobs    = addSetting(new BooleanSetting("Mobs",    false));
    private final BooleanSetting onRMB       = addSetting(new BooleanSetting("OnRMB",   false));
    private final EnumSetting<AimPoint> aimAt   = addSetting(new EnumSetting<>("AimAt", AimPoint.BODY));
    private final EnumSetting<AimMode>  aimMode = addSetting(new EnumSetting<>("Mode",  AimMode.SMOOTH));

    public static AimAssistant INSTANCE;

    public AimAssistant() {
        super("AimAssistant", "Smooth aim assistance toward targets", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
        INSTANCE = this;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (onRMB.isEnabled()) {
            boolean rmb = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(),
                    GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            if (!rmb) return;
        }

        List<Entity> targets = EntityUtil.getTargetsInRange(
                mc.player, range.getValue(), targPlayers.isEnabled(), targMobs.isEnabled());
        targets.removeIf(e -> !EntityUtil.isInFOV(mc.player, e, fov.getValue()));
        if (targets.isEmpty()) return;

        targets.sort(Comparator.comparingDouble(e -> {
            float[] r  = RotationUtil.getRotationTo(mc.player, e);
            float   dy = RotationUtil.wrapDegrees(r[0] - mc.player.getYaw());
            float   dp = r[1] - mc.player.getPitch();
            return dy * dy + dp * dp;
        }));

        Entity target = targets.get(0);

        // FIX BUG-07: BODY had no explicit case and silently fell through to default.
        // All four enum constants now handled explicitly; compiler enforces exhaustiveness.
        double ty = switch (aimAt.getValue()) {
            case HEAD -> target.getEyeY() + 0.05;
            case NECK -> target.getEyeY() - 0.1;
            case BODY -> target.getEyeY() - target.getHeight() * 0.25;
            case FEET -> target.getY() + 0.1;
        };

        float[] dst = RotationUtil.getRotationToPoint(mc.player, target.getX(), ty, target.getZ());
        float   spd = speed.getValue();

        switch (aimMode.getValue()) {
            case INSTANT -> {
                mc.player.setYaw(dst[0]);
                mc.player.setPitch(dst[1]);
            }
            case NATURAL -> {
                float jY = (float)(Math.random() - 0.5) * 0.06f;
                float jP = (float)(Math.random() - 0.5) * 0.04f;
                RotationUtil.smoothRotate(mc.player, dst[0] + jY, dst[1] + jP, spd);
            }
            default -> RotationUtil.smoothRotate(mc.player, dst[0], dst[1], spd);
        }
    }
}
