package com.example.litematicaaddon;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class LitematicaSubterraneanBot {

    // ===================== НАСТРОЙКИ =====================
    private static final int TORCH_SLOT = 3;
    private static final int RADIUS = 8;
    private static final int LAYERS_UP = 3;
    private static final int LAYERS_DOWN = 0;
    private static final int TORCH_LIGHT_BELOW = 8;
    private static final int TORCH_COOLDOWN_TICKS = 20;
    private static final float MIN_HEALTH = 8.0F;
    private static final int MAX_BREAK_TICKS = 400;
    private static final int MAX_WALK_TICKS = 160;
    private static final int MIN_PICKAXE_DURABILITY = 10;
    // =====================================================

    private static boolean active;
    private static BlockPos origin = BlockPos.ZERO;
    private static int topY;
    private static int bottomY;

    private static final Set<Long> skipped = new HashSet<>();
    private static BlockPos target;
    private static int targetTicks;
    private static int walkTicks;
    private static int torchCooldown;
    private static boolean torchWarned;
    private static float lastHealth;
    private static boolean walking;
    private static boolean jumping;

    private LitematicaSubterraneanBot() {
    }

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (active) {
            stop("§c[SubMiner] Выключен");
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        origin = mc.player.blockPosition();
        topY = origin.getY() + LAYERS_UP;
        bottomY = Math.max(origin.getY() - LAYERS_DOWN, mc.level.getMinY());
        skipped.clear();
        target = null;
        targetTicks = 0;
        walkTicks = 0;
        torchCooldown = 0;
        torchWarned = false;
        lastHealth = mc.player.getHealth();
        active = true;
        say("§a[SubMiner] Включен. Зона: ±" + RADIUS + " блоков, слои Y " + bottomY + ".." + topY);
    }

    private static void stop(String message) {
        active = false;
        target = null;
        setWalking(false);
        setJumping(false);
        say(message);
    }

    public static void onTick(Minecraft mc) {
        if (!active) {
            return;
        }
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        MultiPlayerGameMode gameMode = mc.gameMode;

        if (player == null || level == null || gameMode == null) {
            active = false;
            target = null;
            setWalking(false);
            setJumping(false);
            return;
        }
        if (mc.gui.screen() != null) {
            setWalking(false);
            setJumping(false);
            return;
        }

        float health = player.getHealth();
        if (health < lastHealth) {
            stop("§c[SubMiner] Получен урон — остановка");
            return;
        }
        lastHealth = health;
        if (health < MIN_HEALTH) {
            stop("§c[SubMiner] Мало здоровья — остановка");
            return;
        }
        if (player.isInLava()) {
            stop("§c[SubMiner] Игрок в лаве — остановка");
            return;
        }

        if (torchCooldown > 0) {
            torchCooldown--;
        }
        tryPlaceTorch(player, level, gameMode);

        if (!selectPickaxe(player)) {
            stop("§c[SubMiner] В хотбаре нет целой кирки (кроме слота факелов)");
            return;
        }

        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange() - 0.5;

        if (target != null && !isStillValid(level, target, eye, reach)) {
            target = null;
            targetTicks = 0;
        }
        if (target == null) {
            BlockPos candidate = pickTarget(level, eye, reach);
            if (candidate != null && isUnsafe(level, candidate)) {
                skipped.add(candidate.asLong());
                return;
            }
            target = candidate;
            targetTicks = 0;
        }

        if (target != null) {
            setWalking(false);
            setJumping(false);
            walkTicks = 0;
            if (++targetTicks > MAX_BREAK_TICKS) {
                skipped.add(target.asLong());
                target = null;
                return;
            }
            faceBlock(player, target);
            Vec3 c = Vec3.atCenterOf(target);
            Direction face = Direction.getApproximateNearest(eye.x - c.x, eye.y - c.y, eye.z - c.z);
            gameMode.continueDestroyBlock(target, face);
            player.swing(InteractionHand.MAIN_HAND);
            return;
        }

        BlockPos far = nearestRemaining(level, eye);
        if (far == null) {
            String extra = skipped.isEmpty() ? "" : " (пропущено блоков: " + skipped.size() + ")";
            stop("§6[SubMiner] Готово! Зона очищена" + extra);
            return;
        }
        Vec3 fc = Vec3.atCenterOf(far);
        double dx = fc.x - player.getX();
        double dz = fc.z - player.getZ();
        if (dx * dx + dz * dz < 0.5 || ++walkTicks > MAX_WALK_TICKS) {
            skipped.add(far.asLong());
            walkTicks = 0;
            setWalking(false);
            setJumping(false);
            return;
        }
        faceBlock(player, far);
        player.setXRot(0.0F);
        setWalking(true);
        setJumping(player.horizontalCollision);
    }

    private static BlockPos pickTarget(ClientLevel level, Vec3 eye, double reach) {
        double reachSq = reach * reach;
        int minX = Math.max(origin.getX() - RADIUS, Mth.floor(eye.x - reach));
        int maxX = Math.min(origin.getX() + RADIUS, Mth.floor(eye.x + reach));
        int minZ = Math.max(origin.getZ() - RADIUS, Mth.floor(eye.z - reach));
        int maxZ = Math.min(origin.getZ() + RADIUS, Mth.floor(eye.z + reach));
        int yFrom = Math.min(topY, Mth.floor(eye.y + reach));
        int yTo = Math.max(bottomY, Mth.floor(eye.y - reach));

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = yFrom; y >= yTo; y--) {
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    double d = Vec3.atCenterOf(pos).distanceToSqr(eye);
                    if (d > reachSq || d >= bestDist) {
                        continue;
                    }
                    if (!isMineable(level, pos) || skipped.contains(pos.asLong())) {
                        continue;
                    }
                    best = pos.immutable();
                    bestDist = d;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static BlockPos nearestRemaining(ClientLevel level, Vec3 eye) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = topY; y >= bottomY; y--) {
            for (int x = origin.getX() - RADIUS; x <= origin.getX() + RADIUS; x++) {
                for (int z = origin.getZ() - RADIUS; z <= origin.getZ() + RADIUS; z++) {
                    pos.set(x, y, z);
                    double d = Vec3.atCenterOf(pos).distanceToSqr(eye);
                    if (d >= bestDist) {
                        continue;
                    }
                    if (!isMineable(level, pos) || skipped.contains(pos.asLong())) {
                        continue;
                    }
                    best = pos.immutable();
                    bestDist = d;
                }
            }
        }
        return best;
    }

    private static boolean isStillValid(ClientLevel level, BlockPos pos, Vec3 eye, double reach) {
        return isMineable(level, pos)
                && !skipped.contains(pos.asLong())
                && Vec3.atCenterOf(pos).distanceToSqr(eye) <= reach * reach;
    }

    private static boolean isMineable(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private static boolean isUnsafe(ClientLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (!level.getFluidState(pos.relative(dir)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean selectPickaxe(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int held = inv.getSelectedSlot();
        if (held != TORCH_SLOT && isUsablePickaxe(inv.getItem(held))) {
            return true;
        }
        for (int i = 0; i < 9; i++) {
            if (i != TORCH_SLOT && isUsablePickaxe(inv.getItem(i))) {
                inv.setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }

    private static boolean isUsablePickaxe(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES)
                && stack.getMaxDamage() - stack.getDamageValue() > MIN_PICKAXE_DURABILITY;
    }

    private static void tryPlaceTorch(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode) {
        if (torchCooldown > 0) {
            return;
        }
        Inventory inv = player.getInventory();
        ItemStack torch = inv.getItem(TORCH_SLOT);
        if (!torch.is(Items.TORCH) && !torch.is(Items.SOUL_TORCH)) {
            if (!torchWarned) {
                torchWarned = true;
                say("§e[SubMiner] В 4 слоте нет факелов — не ставлю");
            }
            return;
        }
        torchWarned = false;

        BlockPos feet = player.blockPosition();
        BlockPos floor = feet.below();
        if (!player.onGround()
                || !level.getBlockState(feet).isAir()
                || level.getBrightness(LightLayer.BLOCK, feet) >= TORCH_LIGHT_BELOW
                || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            return;
        }

        int previous = inv.getSelectedSlot();
        inv.setSelectedSlot(TORCH_SLOT);
        Vec3 hitPos = new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5);
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hitPos, Direction.UP, floor, false));
        player.swing(InteractionHand.MAIN_HAND);
        inv.setSelectedSlot(previous);
        torchCooldown = TORCH_COOLDOWN_TICKS;
    }

    private static void faceBlock(LocalPlayer player, BlockPos pos) {
        Vec3 d = Vec3.atCenterOf(pos).subtract(player.getEyePosition());
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        player.setYRot((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0));
        player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, horizontal)));
    }

    private static void setWalking(boolean on) {
        if (walking != on) {
            walking = on;
            Minecraft.getInstance().options.keyUp.setDown(on);
        }
    }

    private static void setJumping(boolean on) {
        if (jumping != on) {
            jumping = on;
            Minecraft.getInstance().options.keyJump.setDown(on);
        }
    }

    private static void say(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal(text));
        }
    }
}
