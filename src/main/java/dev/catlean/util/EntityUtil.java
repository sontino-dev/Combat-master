package dev.catlean.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

public final class EntityUtil {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private EntityUtil() {}

    public static List<Entity> getTargetsInRange(
            PlayerEntity player, float range, boolean players, boolean mobs) {

        List<Entity> list = new ArrayList<>();
        if (mc.world == null) return list;

        Box box = player.getBoundingBox().expand(range);
        mc.world.getEntitiesByClass(LivingEntity.class, box, e -> {
            if (e == player)   return false;
            if (!e.isAlive())  return false;
            if (e.squaredDistanceTo(player) > (double) range * range) return false;

            if (e instanceof PlayerEntity p) {
                if (!players)        return false;
                if (p.isSpectator()) return false;
                if (p.isCreative())  return false;
            } else if (e instanceof MobEntity) {
                if (!mobs) return false;
            } else {
                // FIX BUG-03: unknown LivingEntity types (e.g. ArmorStand)
                // were incorrectly included when players=true || mobs=true.
                return false;
            }
            return true;
        }).forEach(list::add);

        return list;
    }

    // FIX: replaced World.raycastBlock (doesn't exist in 1.21.4 Yarn)
    // with the correct World.raycast(RaycastContext) API.
    public static boolean hasLineOfSight(PlayerEntity player, Entity target) {
        HitResult hit = player.getWorld().raycast(new RaycastContext(
                player.getEyePos(), target.getEyePos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player));
        return hit.getType() != HitResult.Type.BLOCK;
    }

    public static boolean isInFOV(PlayerEntity player, Entity target, float fov) {
        if (fov >= 360f) return true;
        float[] rot  = RotationUtil.getRotationTo(player, target);
        float   diff = RotationUtil.angularDistance(rot[0], player.getYaw());
        return diff <= fov * 0.5f;
    }

    public static boolean isTeammate(PlayerEntity player, Entity target) {
        if (!(target instanceof PlayerEntity p)) return false;
        if (player.getScoreboardTeam() == null)  return false;
        return player.getScoreboardTeam() == p.getScoreboardTeam();
    }
}
