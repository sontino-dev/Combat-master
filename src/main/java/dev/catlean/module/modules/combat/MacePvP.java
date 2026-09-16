package dev.catlean.module.modules.combat;

import dev.catlean.module.Category;
import dev.catlean.module.Module;
import dev.catlean.util.EntityUtil;
import dev.catlean.util.RotationUtil;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public class MacePvP extends Module {

    public enum Mode { AUTO_FALL, WIND_COMBO, SMART }

    private final FloatSetting   range      = addSetting(new FloatSetting  ("Range",    5f,   1f,  8f));
    private final FloatSetting   minFall    = addSetting(new FloatSetting  ("MinFall",  1.5f, 0.5f, 15f));
    private final FloatSetting   minSpeed   = addSetting(new FloatSetting  ("MinVelY",  0.2f, 0.1f, 3f));
    private final BooleanSetting autoEquip  = addSetting(new BooleanSetting("AutoEquip", true));
    private final BooleanSetting autoSwing  = addSetting(new BooleanSetting("AutoSwing", true));
    private final BooleanSetting windCharge = addSetting(new BooleanSetting("WindCombo", true));
    private final BooleanSetting autoJump   = addSetting(new BooleanSetting("AutoJump",  true));
    private final FloatSetting   windDelay  = addSetting(new FloatSetting  ("WindDelay", 1.2f, 0.5f, 3f));
    private final EnumSetting<Mode> mode    = addSetting(new EnumSetting<> ("Mode",     Mode.SMART));

    public static MacePvP INSTANCE;

    private Entity  target;
    private boolean launched;
    private long    launchMs;
    private int     jumpCooldown;

    public MacePvP() {
        super("MacePvP", "Mace PvP — fall bonus + wind charge combo", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
        INSTANCE = this;
    }

    @Override public void onEnable()  { launched = false; target = null; }
    @Override public void onDisable() { launched = false; target = null; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        boolean hasMace = mc.player.getMainHandStack().isOf(Items.MACE)
                       || mc.player.getOffHandStack().isOf(Items.MACE);
        if (autoEquip.isEnabled() && !hasMace) hasMace = equipMace();
        if (!hasMace) return;

        List<Entity> targets = EntityUtil.getTargetsInRange(mc.player, range.getValue(), true, false);
        if (targets.isEmpty()) { target = null; return; }
        targets.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));
        target = targets.get(0);

        if (jumpCooldown > 0) jumpCooldown--;

        switch (mode.getValue()) {
            case AUTO_FALL  -> tickAutoFall();
            case WIND_COMBO -> tickWindCombo();
            case SMART      -> tickSmart();
        }
    }

    private void tickAutoFall() {
        if (target == null) return;
        double velY   = -mc.player.getVelocity().y;
        float  fallD  = mc.player.fallDistance;
        boolean falling = velY >= minSpeed.getValue() && !mc.player.isOnGround();

        if (!falling || fallD < minFall.getValue()) {
            if (autoJump.isEnabled()
                    && jumpCooldown == 0
                    && mc.player.isOnGround()
                    && mc.player.squaredDistanceTo(target) <= 3.5 * 3.5
                    && mc.player.getAttackCooldownProgress(0f) >= 1f
                    && mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(0, 1.05, 0))) {
                mc.player.jump();
                jumpCooldown = 6;
            }
            return;
        }
        if (mc.player.squaredDistanceTo(target) > range.getValue() * range.getValue()) return;

        float[] rot = RotationUtil.getRotationTo(mc.player, target);
        mc.player.setYaw(rot[0]);
        mc.player.setPitch(rot[1]);

        Hand hand = mc.player.getMainHandStack().isOf(Items.MACE) ? Hand.MAIN_HAND : Hand.OFF_HAND;
        mc.interactionManager.attackEntity(mc.player, target);
        if (autoSwing.isEnabled()) mc.player.swingHand(hand);
    }

    private void tickWindCombo() {
        if (target == null) return;

        if (!launched) {
            boolean hasWind = mc.player.getInventory().contains(s -> s.isOf(Items.WIND_CHARGE));
            if (!hasWind) { tickAutoFall(); return; }
            if (!mc.player.isOnGround()) { tickAutoFall(); return; }

            int windSlot = findSlot(Items.WIND_CHARGE);
            if (windSlot == -1) { tickAutoFall(); return; }

            int prev = mc.player.getInventory().selectedSlot;

            // FIX BUG-04: changing selectedSlot directly never sends a packet to the server.
            // The server would process interactItem with the OLD slot, so Wind Charge never
            // fired server-side. Send UpdateSelectedSlotC2SPacket before and after.
            mc.player.getInventory().selectedSlot = windSlot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(windSlot));

            mc.player.setPitch(90f);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

            mc.player.getInventory().selectedSlot = prev;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prev));

            launched = true;
            launchMs = System.currentTimeMillis();
            return;
        }

        long elapsed = System.currentTimeMillis() - launchMs;
        if (elapsed > (long)(windDelay.getValue() * 1000f)) {
            tickAutoFall();
            if (mc.player.isOnGround()) launched = false;
        }
    }

    private void tickSmart() {
        if (target == null) return;
        if (mc.player.fallDistance >= minFall.getValue()) {
            tickAutoFall();
        } else if (windCharge.isEnabled() && mc.player.isOnGround()) {
            tickWindCombo();
        }
    }

    private boolean equipMace() {
        int slot = findSlot(Items.MACE);
        if (slot == -1) return false;
        mc.player.getInventory().selectedSlot = slot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        return true;
    }

    private int findSlot(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    public Entity getTarget() { return target; }
}
