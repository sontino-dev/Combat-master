package dev.catlean.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public final class RotationUtil {

    private RotationUtil() {}

    public static float[] getRotationTo(PlayerEntity from, Entity to) {
        return getRotationToPoint(from, to.getX(), to.getEyeY(), to.getZ());
    }

    public static float[] getRotationToPoint(PlayerEntity from, double tx, double ty, double tz) {
        double dx   = tx - from.getX();
        double dy   = ty - from.getEyeY();
        double dz   = tz - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        return new float[]{ normalizeYaw(yaw), clampPitch(pitch) };
    }

    public static void smoothRotate(PlayerEntity player, float tYaw, float tPitch, float speed) {
        float cYaw   = player.getYaw();
        float cPitch = player.getPitch();
        float dYaw   = wrapDegrees(tYaw - cYaw);
        float dPitch = tPitch - cPitch;
        player.setYaw  (cYaw   + dYaw   * speed);
        player.setPitch(cPitch + dPitch * speed);
    }

    public static float normalizeYaw(float yaw) {
        yaw %= 360f;
        if (yaw < -180f) yaw += 360f;
        if (yaw >  180f) yaw -= 360f;
        return yaw;
    }

    public static float clampPitch(float p) {
        return Math.max(-90f, Math.min(90f, p));
    }

    public static float wrapDegrees(float d) {
        d %= 360f;
        if (d < -180f) d += 360f;
        if (d >  180f) d -= 360f;
        return d;
    }

    public static float angularDistance(float a, float b) {
        return Math.abs(wrapDegrees(a - b));
    }
}
