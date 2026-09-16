package dev.catlean.module.modules.combat;

import dev.catlean.module.Category;
import dev.catlean.module.Module;
import dev.catlean.util.EntityUtil;
import dev.catlean.util.RotationUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.RailBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public class CartPvP extends Module {

    public enum Mode { TNT_CART, BOW_CART, COMBO }

    private final EnumSetting<Mode> mode    = addSetting(new EnumSetting<>("Mode",     Mode.COMBO));
    private final FloatSetting   range      = addSetting(new FloatSetting  ("Range",     6f,   2f,  10f));
    private final IntSetting     railScan   = addSetting(new IntSetting    ("RailScan",  5,    2,  8));
    private final FloatSetting   primeDist  = addSetting(new FloatSetting  ("PrimeDist", 2.5f, 1f,  5f));
    private final FloatSetting   bowRange   = addSetting(new FloatSetting  ("BowRange",  24f,  8f,  48f));
    private final BooleanSetting rotate     = addSetting(new BooleanSetting("Rotate",    true));
    private final BooleanSetting targetMobs = addSetting(new BooleanSetting("Mobs",      false));

    public static CartPvP INSTANCE;

    private long lastBowShot;
    private long lastCartAction;
    private int  bowCharge;
    private boolean drawing;

    public CartPvP() {
        super("CartPvP", "TNT minecart priming + auto bow while riding", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
        INSTANCE = this;
    }

    @Override public void onEnable() {
        lastBowShot = 0L;
        lastCartAction = 0L;
        bowCharge = 0;
        drawing = false;
    }

    @Override public void onDisable() {
        drawing = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        switch (mode.getValue()) {
            case TNT_CART -> tickTnt();
            case BOW_CART -> tickBow();
            case COMBO -> {
                if (mc.player.hasVehicle()) tickBow(); else tickTnt();
            }
        }
    }

    private void tickTnt() {
        List<Entity> targets = EntityUtil.getTargetsInRange(
                mc.player, range.getValue(), true, targetMobs.isEnabled());
        if (targets.isEmpty()) return;
        targets.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));
        Entity target = targets.get(0);
        if (!target.isAlive()) return;

        TntMinecartEntity cart = findTntCartNear(target, 8);
        if (cart != null) {
            if (System.currentTimeMillis() - lastCartAction < 500L) return;
            if (cart.squaredDistanceTo(target) <= primeDist.getValue() * primeDist.getValue()) {
                if (rotate.isEnabled()) aimAt(cart.getPos().add(0, cart.getHeight() * 0.5, 0));
                mc.interactionManager.attackEntity(mc.player, cart);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastCartAction = System.currentTimeMillis();
            }
            return;
        }

        if (System.currentTimeMillis() - lastCartAction < 1200L) return;
        if (mc.player.getVehicle() != null) return;

        BlockPos rail = findRailNear(target.getBlockPos(), railScan.getValue());
        if (rail == null) return;

        int slot = findHotbar(Items.TNT_MINECART);
        if (slot == -1) return;

        switchSlot(slot);
        if (rotate.isEnabled()) aimAt(Vec3d.ofCenter(rail));
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new net.minecraft.util.hit.BlockHitResult(
                        Vec3d.ofCenter(rail), Direction.UP, rail, false));
        mc.player.swingHand(Hand.MAIN_HAND);
        lastCartAction = System.currentTimeMillis();
    }

    private void tickBow() {
        if (!mc.player.hasVehicle()) return;

        List<Entity> targets = EntityUtil.getTargetsInRange(
                mc.player, bowRange.getValue(), true, targetMobs.isEnabled());
        if (targets.isEmpty()) return;
        targets.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));
        Entity target = targets.get(0);
        if (!target.isAlive()) return;

        if (!mc.player.getMainHandStack().isOf(Items.BOW)) {
            int bowSlot = findHotbar(Items.BOW);
            if (bowSlot == -1) return;
            if (!hasAmmo()) return;
            if (!drawing) switchSlot(bowSlot);
            return;
        }
        if (!hasAmmo()) return;

        if (System.currentTimeMillis() - lastBowShot < 1500L && !drawing) return;

        float[] sol = solveBow(target);
        if (sol == null) return;

        if (rotate.isEnabled()) {
            mc.player.setYaw(sol[0]);
            mc.player.setPitch(sol[1]);
        }

        if (!mc.player.isUsingItem()) {
            var result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (result.isAccepted()) {
                mc.player.setCurrentHand(Hand.MAIN_HAND);
                bowCharge = 20;
                drawing = true;
            }
            return;
        }

        if (mc.player.getItemUseTime() >= bowCharge) {
            mc.interactionManager.stopUsingItem(mc.player);
            drawing = false;
            lastBowShot = System.currentTimeMillis();
        }
    }

    private float[] solveBow(Entity target) {
        Vec3d from = mc.player.getEyePos();
        double bestErr = Double.MAX_VALUE;
        float bestPitch = 0f;
        float yaw = 0f;

        for (int pass = 0; pass < 2; pass++) {
            Vec3d aim = target.getPos().add(0, target.getHeight() * 0.5, 0);
            bestErr = Double.MAX_VALUE;
            float passYaw = RotationUtil.getRotationToPoint(mc.player, aim.x, aim.y, aim.z)[0];
            yaw = passYaw;

            for (float pitch = -35f; pitch <= 78f; pitch += 0.5f) {
                Vec3d pos = from;
                Vec3d vel = dirFromRot(yaw, pitch).multiply(3.0);
                double min = Double.MAX_VALUE;

                for (int t = 1; t <= 40; t++) {
                    pos = pos.add(vel);
                    if (target.getBoundingBox().expand(0.6).contains(pos)) {
                        min = 0;
                        break;
                    }
                    double d = pos.distanceTo(aim);
                    if (d < min) min = d;
                    vel = vel.multiply(0.99).add(0, -0.05, 0);
                }

                if (min < bestErr) {
                    bestErr = min;
                    bestPitch = pitch;
                }
            }

            if (bestErr == 0) break;
        }

        if (bestErr > 2.5) return null;
        return new float[]{ yaw, bestPitch };
    }

    private Vec3d dirFromRot(float yaw, float pitch) {
        double yawRad   = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z =  Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3d(x, y, z);
    }

    private TntMinecartEntity findTntCartNear(Entity target, double radius) {
        return mc.world.getEntitiesByClass(TntMinecartEntity.class,
                target.getBoundingBox().expand(radius),
                c -> c.isAlive() && mc.player.squaredDistanceTo(c) <= range.getValue() * range.getValue())
                .stream().findFirst().orElse(null);
    }

    private BlockPos findRailNear(BlockPos center, int r) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterateOutwards(center, r, 2, r)) {
            BlockState state = mc.world.getBlockState(pos);
            if (!(state.getBlock() instanceof RailBlock)) continue;
            double d = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
            if (d > 4.5 * 4.5) continue;
            if (d < bestDist) {
                bestDist = d;
                best = pos.toImmutable();
            }
        }
        return best;
    }

    private void aimAt(Vec3d pos) {
        float[] rot = RotationUtil.getRotationToPoint(mc.player, pos.x, pos.y, pos.z);
        mc.player.setYaw(rot[0]);
        mc.player.setPitch(rot[1]);
    }

    private boolean hasAmmo() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.ARROW) || s.isOf(Items.TIPPED_ARROW) || s.isOf(Items.SPECTRAL_ARROW)) return true;
        }
        return mc.player.getAbilities().creativeMode;
    }

    private int findHotbar(Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    private void switchSlot(int slot) {
        mc.player.getInventory().selectedSlot = slot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }
}
