package dev.catlean.util;

import net.minecraft.client.network.ClientPlayerEntity;

public final class RotationManager {

    public static boolean pending;
    public static float queuedYaw;
    public static float queuedPitch;

    private static float savedYaw;
    private static float savedPitch;

    private RotationManager() {}

    public static void queue(float yaw, float pitch) {
        queuedYaw   = RotationUtil.normalizeYaw(yaw);
        queuedPitch = RotationUtil.clampPitch(pitch);
        pending     = true;
    }

    public static void clear() {
        pending = false;
    }

    public static void approach(float targetYaw, float targetPitch, float yawStep, float pitchStep) {
        float dy = RotationUtil.wrapDegrees(targetYaw - queuedYaw);
        float dp = targetPitch - queuedPitch;
        float sy = Math.max(-yawStep, Math.min(yawStep, dy));
        float sp = Math.max(-pitchStep, Math.min(pitchStep, dp));
        queue(queuedYaw + sy, queuedPitch + sp);
    }

    public static void preSendMovement(ClientPlayerEntity player) {
        if (!pending) return;
        savedYaw   = player.getYaw();
        savedPitch = player.getPitch();
        player.setYaw(queuedYaw);
        player.setPitch(queuedPitch);
    }

    public static void postSendMovement(ClientPlayerEntity player) {
        if (!pending) return;
        player.setYaw(savedYaw);
        player.setPitch(savedPitch);
        pending = false;
    }
}
