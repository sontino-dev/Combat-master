package dev.catlean.module.modules.combat;

import dev.catlean.module.Category;
import dev.catlean.module.Module;
import dev.catlean.util.EntityUtil;
import dev.catlean.util.RotationManager;
import dev.catlean.util.RotationUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

public class KillAura extends Module {

    public enum AcMode { NONE, GRIM, VULCAN, HVH, LEGIT, LEGIT_EXTRA }
    public enum TargetMode { CLOSEST, LOWEST_HP, SWITCH }

    private final EnumSetting<AcMode>     acMode     = addSetting(new EnumSetting<>("Mode",      AcMode.GRIM));
    private final EnumSetting<TargetMode> targetMode = addSetting(new EnumSetting<>("Target",    TargetMode.CLOSEST));
    private final FloatSetting   range       = addSetting(new FloatSetting  ("Range",      3.2f,  1f,  6f));
    private final FloatSetting   fov         = addSetting(new FloatSetting  ("FOV",        180f,  10f, 360f));
    private final FloatSetting   rageSpeed   = addSetting(new FloatSetting  ("RageCPS",    12f,   1f,  20f));
    private final BooleanSetting players     = addSetting(new BooleanSetting("Players",    true));
    private final BooleanSetting mobs        = addSetting(new BooleanSetting("Mobs",       false));
    private final BooleanSetting throughWall = addSetting(new BooleanSetting("ThroughWall",false));
    private final BooleanSetting wtap        = addSetting(new BooleanSetting("WTap",       true));
    private final BooleanSetting onlyCrits   = addSetting(new BooleanSetting("OnlyCrits",  false));

    public static KillAura INSTANCE;

    private Entity target;
    private long   lastAttack;
    private long   nextAttackAt;
    private int    wtapTicks;
    private int    rotTicks;
    private int    lastTargetId;
    private final SplittableRandom rng = new SplittableRandom();

    public KillAura() {
        super("KillAura", "Auto attacks nearby entities", Category.COMBAT, GLFW.GLFW_KEY_R);
        INSTANCE = this;
    }

    @Override public void onEnable() {
        target = null;
        lastAttack = 0L;
        nextAttackAt = 0L;
        rotTicks = 0;
        RotationManager.clear();
    }

    @Override public void onDisable() {
        target = null;
        RotationManager.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) { reset(); return; }

        if (wtapTicks > 0 && --wtapTicks == 0
                && mc.options.forwardKey.isPressed()
                && !mc.player.isSprinting()
                && mc.player.getHungerManager().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }

        List<Entity> candidates = EntityUtil.getTargetsInRange(
                mc.player, range.getValue(), players.isEnabled(), mobs.isEnabled());

        AcMode mode = acMode.getValue();

        if (mode != AcMode.HVH && fov.getValue() < 360f)
            candidates.removeIf(e -> !EntityUtil.isInFOV(mc.player, e, fov.getValue()));

        boolean forceLos = mode == AcMode.GRIM || mode == AcMode.VULCAN
                        || mode == AcMode.LEGIT || mode == AcMode.LEGIT_EXTRA;
        if (forceLos || !throughWall.isEnabled()) {
            candidates.removeIf(e -> {
                HitResult hit = mc.world.raycast(new RaycastContext(
                        mc.player.getEyePos(), e.getEyePos(),
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        mc.player));
                return hit.getType() == HitResult.Type.BLOCK;
            });
        }

        if (candidates.isEmpty()) { reset(); return; }

        target = switch (targetMode.getValue()) {
            case CLOSEST -> candidates.stream()
                    .min(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)))
                    .orElse(null);
            case LOWEST_HP -> candidates.stream()
                    .filter(e -> e instanceof LivingEntity)
                    .min(Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth()))
                    .orElse(null);
            case SWITCH -> {
                if (target != null && candidates.contains(target)) yield target;
                yield candidates.stream()
                        .min(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)))
                        .orElse(null);
            }
        };

        if (target == null) { reset(); return; }

        if (target.getId() != lastTargetId) {
            lastTargetId = target.getId();
            rotTicks = 0;
            RotationManager.queue(RotationManager.queuedYaw, RotationManager.queuedPitch);
        }

        float[] rot = RotationUtil.getRotationTo(mc.player, target);

        switch (mode) {
            case GRIM -> {
                RotationManager.approach(rot[0], rot[1], 40f, 25f);
                rotTicks++;
            }
            case VULCAN -> {
                RotationManager.approach(rot[0], rot[1], 65f, 45f);
                rotTicks++;
            }
            case HVH -> {
                RotationManager.queue(rot[0], rot[1]);
                rotTicks++;
            }
            case LEGIT -> {
                RotationUtil.smoothRotate(mc.player, rot[0], rot[1], 0.35f);
                RotationManager.clear();
                rotTicks++;
            }
            case LEGIT_EXTRA -> {
                float jY = (float) (rng.nextDouble() - 0.5) * 0.8f;
                float jP = (float) (rng.nextDouble() - 0.5) * 0.5f;
                RotationUtil.smoothRotate(mc.player, rot[0] + jY, rot[1] + jP, 0.32f);
                RotationManager.clear();
                rotTicks++;
            }
            case NONE -> {
                RotationManager.clear();
                rotValidReset();
            }
        }

        if (!canAttackPerMode(mode, rot)) return;
        if (onlyCrits.isEnabled() && mode != AcMode.NONE && !canCrit()) return;
        if (!target.isAlive()) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastAttack = System.currentTimeMillis();
        applyPostAttackDelay(mode);

        if (wtap.isEnabled() && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
            wtapTicks = 2;
        }
    }

    private void rotValidReset() {
        rotTicks = 0;
    }

    private boolean canAttackPerMode(AcMode mode, float[] rotToTarget) {
        long now = System.currentTimeMillis();

        return switch (mode) {
            case NONE -> mc.player.getAttackCooldownProgress(0f) >= 1f;
            case GRIM -> mc.player.getAttackCooldownProgress(0f) >= 1f
                    && rotTicks >= 1
                    && RotationUtil.angularDistance(RotationManager.queuedYaw, rotToTarget[0]) <= 30f;
            case VULCAN -> mc.player.getAttackCooldownProgress(0f) >= 0.9f
                    && rotTicks >= 1
                    && RotationUtil.angularDistance(RotationManager.queuedYaw, rotToTarget[0]) <= 40f
                    && now >= nextAttackAt;
            case HVH -> now - lastAttack >= (long) (1000f / rageSpeed.getValue());
            case LEGIT -> mc.player.getAttackCooldownProgress(0f) >= 1f
                    && rotTicks >= 2
                    && RotationUtil.angularDistance(mc.player.getYaw(), rotToTarget[0]) <= 12f
                    && Math.abs(mc.player.getPitch() - rotToTarget[1]) <= 12f
                    && now >= nextAttackAt;
            case LEGIT_EXTRA -> mc.player.getAttackCooldownProgress(0f) >= 1f
                    && rotTicks >= 2
                    && RotationUtil.angularDistance(mc.player.getYaw(), rotToTarget[0]) <= 10f
                    && Math.abs(mc.player.getPitch() - rotToTarget[1]) <= 10f
                    && now >= nextAttackAt;
        };
    }

    private void applyPostAttackDelay(AcMode mode) {
        nextAttackAt = switch (mode) {
            case VULCAN -> System.currentTimeMillis() + rng.nextLong(30, 81);
            case LEGIT -> System.currentTimeMillis() + rng.nextLong(40, 121);
            case LEGIT_EXTRA -> System.currentTimeMillis() + rng.nextLong(80, 201);
            default -> nextAttackAt;
        };
    }

    private boolean canCrit() {
        return mc.player.fallDistance > 0f
                && !mc.player.isOnGround()
                && mc.player.getVelocity().y < -0.05
                && !mc.player.isTouchingWater()
                && !mc.player.hasVehicle()
                && !mc.player.isClimbing();
    }

    private void reset() {
        target = null;
        rotTicks = 0;
        RotationManager.clear();
    }

    public Entity getTarget() { return target; }
}
