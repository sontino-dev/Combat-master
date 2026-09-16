package dev.catlean.module.modules.combat;

import dev.catlean.module.Category;
import dev.catlean.module.Module;
import dev.catlean.util.EntityUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WebPvP extends Module {

    public enum PlaceMode { UNDER_ENEMY, AT_ENEMY, BOTH, PREDICT }
    private enum State { IDLE, PLACE_LAVA, BURNING, PICK_LAVA, PICK_WATER, ESCAPE_PLACE, ESCAPE_WAIT, ESCAPE_PICK }

    private final FloatSetting   range          = addSetting(new FloatSetting  ("Range",     4f,   2f,  6f));
    private final IntSetting     delay          = addSetting(new IntSetting    ("Delay",     3,    1,  20));
    private final BooleanSetting autoSwitch     = addSetting(new BooleanSetting("AutoSwitch",true));
    private final BooleanSetting antiSelf       = addSetting(new BooleanSetting("AntiSelf",  true));
    private final BooleanSetting targetMobs     = addSetting(new BooleanSetting("Mobs",      false));
    private final EnumSetting<PlaceMode> placeMode = addSetting(new EnumSetting<>("Mode", PlaceMode.PREDICT));
    private final IntSetting     predictTicks   = addSetting(new IntSetting    ("Predict",   4,    1,  10));
    private final BooleanSetting autoLava       = addSetting(new BooleanSetting("AutoLava",  true));
    private final BooleanSetting antiExtinguish = addSetting(new BooleanSetting("AntiWater", true));
    private final BooleanSetting autoEscape     = addSetting(new BooleanSetting("AutoEscape",true));

    public static WebPvP INSTANCE;

    private State state = State.IDLE;
    private BlockPos webPos;
    private BlockPos lavaPos;
    private BlockPos waterPos;
    private long   stateMs;
    private long   lastWebPlace;
    private int    prevSlot = -1;
    private final  Map<UUID, Vec3d> lastPos   = new HashMap<>();
    private final  Map<UUID, Vec3d> velocity  = new HashMap<>();

    public WebPvP() {
        super("WebPvP", "Predictive web trap + lava burn + water counter", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
        INSTANCE = this;
    }

    @Override public void onEnable() {
        state = State.IDLE;
        prevSlot = -1;
        lastPos.clear();
        velocity.clear();
    }

    @Override public void onDisable() {
        restoreSlot();
        state = State.IDLE;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) { state = State.IDLE; return; }

        trackVelocities();

        switch (state) {
            case IDLE -> tickIdle();
            case PLACE_LAVA -> tickPlaceLava();
            case BURNING -> tickBurning();
            case PICK_LAVA -> tickPickLava();
            case PICK_WATER -> tickPickWater();
            case ESCAPE_PLACE -> tickEscapePlace();
            case ESCAPE_WAIT -> tickEscapeWait();
            case ESCAPE_PICK -> tickEscapePick();
        }
    }

    private void tickIdle() {
        if (autoEscape.isEnabled() && isSelfInWeb()) {
            beginEscape();
            return;
        }

        Entity target = nearestTarget();
        if (target == null) return;

        if (antiExtinguish.isEnabled() && lavaPos != null) {
            BlockPos wp = findWaterNear(lavaPos, 2);
            if (wp != null) {
                int bucket = findHotbar(Items.BUCKET);
                if (bucket != -1) {
                    waterPos = wp;
                    switchSlot(bucket);
                    state = State.PICK_WATER;
                    stateMs = System.currentTimeMillis();
                    return;
                }
            }
        }

        if (autoLava.isEnabled() && lavaPos != null
                && mc.world.getBlockState(lavaPos).isOf(Blocks.LAVA)
                && target.squaredDistanceTo(Vec3d.ofCenter(lavaPos)) <= 4.0
                && !target.isOnFire()) {
            state = State.BURNING;
            stateMs = System.currentTimeMillis();
            return;
        }

        if (autoLava.isEnabled() && lavaPos != null && webPos != null
                && mc.world.getBlockState(lavaPos).isAir()
                && !mc.world.getBlockState(lavaPos).isOf(Blocks.LAVA)
                && findHotbar(Items.LAVA_BUCKET) != -1
                && target.squaredDistanceTo(Vec3d.ofCenter(webPos)) <= 4.0
                && mc.player.squaredDistanceTo(Vec3d.ofCenter(lavaPos)) >= 9.0) {
            state = State.PLACE_LAVA;
            stateMs = System.currentTimeMillis();
            return;
        }

        if (System.currentTimeMillis() - lastWebPlace < delay.getValue() * 50L) return;

        BlockPos pos = chooseWebPos(target);
        if (pos == null) return;

        int webSlot = findHotbar(Items.COBWEB);
        if (webSlot == -1) return;

        placeBlockAt(webSlot, pos);
        webPos = pos;
        lastWebPlace = System.currentTimeMillis();

        if (autoLava.isEnabled()) {
            BlockPos above = pos.up();
            if (mc.world.getBlockState(above).isAir()
                    && mc.player.squaredDistanceTo(Vec3d.ofCenter(above)) >= 9.0) {
                lavaPos = above;
                state = State.PLACE_LAVA;
                stateMs = System.currentTimeMillis();
            }
        }
    }

    private void tickPlaceLava() {
        Entity target = nearestTarget();

        if (lavaPos == null
                || (target != null && target.squaredDistanceTo(Vec3d.ofCenter(webPos)) > 16.0)) {
            state = State.IDLE;
            return;
        }
        if (!mc.world.getBlockState(lavaPos).isAir()) {
            state = State.BURNING;
            stateMs = System.currentTimeMillis();
            return;
        }
        if (mc.player.squaredDistanceTo(Vec3d.ofCenter(lavaPos)) < 9.0) {
            state = State.IDLE;
            return;
        }

        int lavaSlot = findHotbar(Items.LAVA_BUCKET);
        if (lavaSlot == -1) { state = State.IDLE; return; }

        placeBlockAt(lavaSlot, lavaPos);
        state = State.BURNING;
        stateMs = System.currentTimeMillis();
    }

    private void tickBurning() {
        if (lavaPos == null) { state = State.IDLE; return; }

        if (antiExtinguish.isEnabled()) {
            BlockPos wp = findWaterNear(lavaPos, 2);
            if (wp != null) {
                int bucket = findHotbar(Items.BUCKET);
                if (bucket != -1) {
                    waterPos = wp;
                    switchSlot(bucket);
                    state = State.PICK_WATER;
                    stateMs = System.currentTimeMillis();
                    return;
                }
            }
        }

        if (!mc.world.getBlockState(lavaPos).isOf(Blocks.LAVA)) {
            state = State.IDLE;
            return;
        }

        Entity target = nearestTarget();
        if (target != null && target.isOnFire()) {
            stateMs = System.currentTimeMillis();
            return;
        }

        if (System.currentTimeMillis() - stateMs >= 1500L) {
            state = State.PICK_LAVA;
            stateMs = System.currentTimeMillis();
        }
    }

    private void tickPickLava() {
        if (lavaPos == null || !mc.world.getBlockState(lavaPos).isOf(Blocks.LAVA)) {
            state = State.IDLE;
            return;
        }
        int bucket = findHotbar(Items.BUCKET);
        if (bucket == -1) { state = State.IDLE; return; }

        pickupAt(bucket, lavaPos);
        state = State.IDLE;
    }

    private void tickPickWater() {
        if (waterPos == null || !isWater(waterPos)) { state = State.IDLE; return; }
        if (System.currentTimeMillis() - stateMs < 250L) return;

        int bucket = findHotbar(Items.BUCKET);
        if (bucket == -1) { state = State.IDLE; return; }

        pickupAt(bucket, waterPos);
        state = State.IDLE;
    }

    private void beginEscape() {
        if (findHotbar(Items.WATER_BUCKET) == -1) return;
        state = State.ESCAPE_PLACE;
        stateMs = System.currentTimeMillis();
    }

    private void tickEscapePlace() {
        int water = findHotbar(Items.WATER_BUCKET);
        if (water == -1) { state = State.IDLE; return; }

        BlockPos head = mc.player.getBlockPos().up();
        if (!isWater(head)) {
            placeBlockAt(water, head);
        }
        state = State.ESCAPE_WAIT;
        stateMs = System.currentTimeMillis();
    }

    private void tickEscapeWait() {
        if (System.currentTimeMillis() - stateMs < 600L) return;
        if (!isSelfInWeb()) {
            BlockPos wp = findWaterNear(mc.player.getBlockPos(), 2);
            if (wp != null && findHotbar(Items.BUCKET) != -1) {
                waterPos = wp;
                switchSlot(findHotbar(Items.BUCKET));
                state = State.PICK_WATER;
                stateMs = System.currentTimeMillis();
                return;
            }
            state = State.IDLE;
            return;
        }
        state = State.ESCAPE_PICK;
        stateMs = System.currentTimeMillis();
    }

    private void tickEscapePick() {
        if (System.currentTimeMillis() - stateMs < 800L) return;

        BlockPos wp = findWaterNear(mc.player.getBlockPos(), 2);
        int bucket = findHotbar(Items.BUCKET);
        if (wp != null && bucket != -1) {
            waterPos = wp;
            pickupAt(bucket, wp);
        }
        state = State.IDLE;
    }

    private BlockPos chooseWebPos(Entity target) {
        Vec3d base = target.getPos();
        if (placeMode.getValue() == PlaceMode.PREDICT) {
            Vec3d vel = velocity.getOrDefault(target.getUuid(), Vec3d.ZERO);
            base = base.add(vel.multiply(predictTicks.getValue()));
        }

        BlockPos tp = BlockPos.ofFloored(base.x, base.y, base.z);
        List<BlockPos> list = new ArrayList<>();
        switch (placeMode.getValue()) {
            case UNDER_ENEMY -> { list.add(tp.down()); list.add(tp); }
            case AT_ENEMY    -> { list.add(tp);        list.add(tp.down()); }
            case BOTH        -> { list.add(tp.down()); list.add(tp); list.add(tp.up()); }
            case PREDICT     -> { list.add(tp);        list.add(tp.down()); }
        }

        for (BlockPos pos : list) {
            if (!mc.world.getBlockState(pos).isAir()) continue;
            if (antiSelf.isEnabled() && isNearSelf(pos)) continue;
            if (!inReach(pos)) continue;
            return pos;
        }
        return null;
    }

    private void trackVelocities() {
        Box box = mc.player.getBoundingBox().expand(32);
        for (Entity e : mc.world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, box, e -> e != mc.player)) {
            Vec3d prev = lastPos.put(e.getUuid(), e.getPos());
            if (prev != null) velocity.put(e.getUuid(), e.getPos().subtract(prev));
        }
        if (lastPos.size() > 128) {
            lastPos.clear();
            velocity.clear();
        }
    }

    private Entity nearestTarget() {
        List<Entity> targets = EntityUtil.getTargetsInRange(
                mc.player, range.getValue(), true, targetMobs.isEnabled());
        if (targets.isEmpty()) return null;
        targets.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));
        return targets.get(0);
    }

    private void placeBlockAt(int slot, BlockPos pos) {
        if (autoSwitch.isEnabled()) switchSlot(slot);
        ItemStack held = mc.player.getInventory().getStack(mc.player.getInventory().selectedSlot);
        if (!held.isEmpty()) interactPlace(pos);
        restoreSlot();
    }

    private boolean interactPlace(BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos anchor = pos.offset(d);
            if (!inReach(anchor)) continue;
            BlockState as = mc.world.getBlockState(anchor);
            if (as.getCollisionShape(mc.world, anchor).isEmpty()) continue;
            Direction face = d.getOpposite();
            Vec3d hit = Vec3d.ofCenter(anchor).add(
                    face.getOffsetX() * 0.5,
                    face.getOffsetY() * 0.5,
                    face.getOffsetZ() * 0.5);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(hit, face, anchor, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private void pickupAt(int slot, BlockPos pos) {
        switchSlot(slot);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
        mc.player.swingHand(Hand.MAIN_HAND);
        restoreSlot();
    }

    private boolean isSelfInWeb() {
        BlockPos feet = mc.player.getBlockPos();
        return mc.world.getBlockState(feet).isOf(Blocks.COBWEB)
                || mc.world.getBlockState(feet.up()).isOf(Blocks.COBWEB);
    }

    private boolean isNearSelf(BlockPos pos) {
        BlockPos sp = mc.player.getBlockPos();
        return pos.equals(sp) || pos.equals(sp.up()) || pos.equals(sp.down());
    }

    private boolean isWater(BlockPos pos) {
        BlockState s = mc.world.getBlockState(pos);
        return s.isOf(Blocks.WATER);
    }

    private BlockPos findWaterNear(BlockPos center, int r) {
        for (BlockPos pos : BlockPos.iterateOutwards(center, r, r, r))
            if (isWater(pos)) return pos.toImmutable();
        return null;
    }

    private boolean inReach(BlockPos pos) {
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= 4.5 * 4.5;
    }

    private int findHotbar(Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    private void switchSlot(int slot) {
        prevSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private void restoreSlot() {
        if (prevSlot == -1) return;
        mc.player.getInventory().selectedSlot = prevSlot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
        prevSlot = -1;
    }
}
