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
                for (int z = mi
