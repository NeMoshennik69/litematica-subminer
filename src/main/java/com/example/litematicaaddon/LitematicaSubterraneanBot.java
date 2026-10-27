package com.example.litematicaaddon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Этап 1: прокапывается к северному верхнему углу зоны.
 * Этап 2: чистит зону слоями сверху вниз, змейкой.
 */
public final class LitematicaSubterraneanBot {

    private static final float MIN_HEALTH = 8.0F;
    private static final int MAX_WALK_TICKS = 160;
    private static final int MAX_STUCK_TICKS = 200;
    private static final int MAX_TRAVEL_TICKS = 24000;

    private static boolean active;
    private static boolean traveling;
    private static int pickSlot;
    private static int walkTicks;
    private static int travelTicks;
    private static int stuckTicks;
    private static BlockPos lastCell;
    private static float lastHealth;

    private LitematicaSubterraneanBot() {
    }

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (active) {
            stop("§c[SubMiner] Выключен");
            return;
        }
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }
        if (!SubMinerConfig.zoneSet) {
            Actions.say("§c[SubMiner] Зона не задана. Нажми K и введи координаты двух углов");
            return;
        }
        if (!Zone.init(level.getMinY(), player.getX())) {
            Actions.say("§c[SubMiner] Зона ниже дна мира, проверь координаты (клавиша K)");
            return;
        }
        pickSlot = SubMinerConfig.pickaxeSlot - 1;
        Actions.clearTarget();
        walkTicks = 0;
        travelTicks = 0;
        stuckTicks = 0;
        lastCell = null;
        lastHealth = player.getHealth();
        traveling = true;
        active = true;
        Actions.say("§a[SubMiner] Включен. Зона X " + Zone.minX + ".." + Zone.maxX
                + ", Y " + Zone.minY + ".." + Zone.maxY
                + ", Z " + Zone.minZ + ".." + Zone.maxZ + ". Иду к северному верхнему углу");
    }

    private static void stop(String message) {
        active = false;
        Actions.clearTarget();
        Actions.releaseKeys();
        Actions.say(message);
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
            Actions.clearTarget();
            Actions.releaseKeys();
            return;
        }
        if (mc.gui.screen() != null) {
            Actions.releaseKeys();
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
        String pickError = Actions.selectPickaxe(player, pickSlot);
        if (pickError != null) {
            stop(pickError);
            return;
        }

        if (traveling) {
            travelStep(player, level, gameMode);
        } else {
            workStep(player, level, gameMode);
        }
    }

    // ===================== Этап 1: путь к углу =====================

    private static void travelStep(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gm) {
        if (++travelTicks > MAX_TRAVEL_TICKS) {
            stop("§c[SubMiner] Не дошёл до угла за отведённое время — остановка");
            return;
        }
        BlockPos cur = player.blockPosition();
        int goalX = Zone.startAtMaxX ? Zone.maxX : Zone.minX;
        int goalZ = Zone.minZ;
        int goalY = Zone.maxY > Zone.minY ? Zone.maxY - 1 : Zone.maxY;
        int dx = goalX - cur.getX();
        int dz = goalZ - cur.getZ();
        int dy = goalY - cur.getY();

        if (dx == 0 && dz == 0 && dy == 0) {
            traveling = false;
            walkTicks = 0;
            Actions.clearTarget();
            Actions.releaseKeys();
            Actions.say("§a[SubMiner] Дошёл до северного верхнего угла. Начинаю расчистку сверху вниз");
            return;
        }

        Vec3 eye = player.getEyePosition();

        // Нужно только вниз: копаем под собой
        if (dx == 0 && dz == 0 && dy < 0) {
            Actions.releaseKeys();
            ensureClear(player, level, gm, cur.below(), eye);
            return;
        }

        int stepX = 0;
        int stepZ = 0;
        if (dx != 0 && (dz == 0 || Math.abs(dx) >= Math.abs(dz))) {
            stepX = Integer.signum(dx);
        } else if (dz != 0) {
            stepZ = Integer.signum(dz);
        } else {
            stepX = 1; // нужно только вверх: поднимаемся лесенкой на восток
        }
        BlockPos next = cur.offset(stepX, 0, stepZ);

        if (dy > 0) {
            // Лесенка вверх: блок-ступенька (next на текущем уровне) остаётся на месте
            if (level.getBlockState(next).getCollisionShape(level, next).isEmpty()) {
                stop("§c[SubMiner] Не могу подняться по лесенке: нет опоры — остановка");
                return;
            }
            if (!ensureClear(player, level, gm, cur.above(2), eye)
                    || !ensureClear(player, level, gm, next.above(), eye)
                    || !ensureClear(player, level, gm, next.above(2), eye)) {
                return;
            }
        } else {
            if (!ensureClear(player, level, gm, next, eye)
                    || !ensureClear(player, level, gm, next.above(), eye)) {
                return;
            }
        }

        if (cur.equals(lastCell)) {
            if (++stuckTicks > MAX_STUCK_TICKS) {
                stop("§c[SubMiner] Застрял по пути к углу — остановка");
                return;
            }
        } else {
            lastCell = cur;
            stuckTicks = 0;
        }
        Actions.walkToward(player, next.getX() + 0.5, next.getZ() + 0.5, dy > 0);
    }

    /** true = клетка свободна. false = идёт ломание или бот остановлен. */
    private static boolean ensureClear(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gm,
                                       BlockPos pos, Vec3 eye) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            stop("§c[SubMiner] На пути жидкость — остановка");
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            stop("§c[SubMiner] На пути неразрушимый блок — остановка");
            return false;
        }
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return true;
        }
        if (Zone.isUnsafe(level, pos)) {
            stop("§c[SubMiner] Рядом с путём жидкость — остановка");
            return false;
        }
        stuckTicks = 0;
        if (!Actions.mine(player, gm, pos.immutable(), eye)) {
            stop("§c[SubMiner] Не получается сломать блок на пути — остановка");
        }
        return false;
    }

    // ===================== Этап 2: расчистка зоны =====================

    private static void workStep(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gm) {
        if (Zone.updateCurLayer(level)) {
            String extra = Zone.skipped.isEmpty() ? "" : " (пропущено блоков: " + Zone.skipped.size() + ")";
            stop("§6[SubMiner] Готово! Зона очищена" + extra);
            return;
        }
        int yHi = Zone.curLayer;
        int yLo = Math.max(Zone.curLayer - 1, Zone.minY);
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange() - 0.5;

        BlockPos target = Actions.target;
        if (target != null && (target.getY() < yLo || target.getY() > yHi
                || !Zone.isStillValid(level, target, eye, reach))) {
            Actions.clearTarget();
            target = null;
        }
        if (target == null) {
            BlockPos candidate = Zone.pickTarget(level, eye, reach, yLo, yHi);
            if (candidate != null) {
                if (Zone.isUnsafe(level, candidate)) {
                    Zone.skipped.add(candidate.asLong());
                    return;
                }
                target = candidate;
            }
        }

        if (target != null) {
            walkTicks = 0;
            if (!Actions.mine(player, gm, target, eye)) {
                Zone.skipped.add(target.asLong());
                Actions.clearTarget();
            }
            return;
        }

        // В досягаемости ничего нет: идём к следующей клетке по змейке
        BlockPos cell = Zone.frontierCell(level, yLo, yHi);
        if (cell == null) {
            return;
        }
        double dx = cell.getX() + 0.5 - player.getX();
        double dz = cell.getZ() + 0.5 - player.getZ();
        if (dx * dx + dz * dz < 0.5 || ++walkTicks > MAX_WALK_TICKS) {
            Zone.skipCell(cell, yLo, yHi);
            walkTicks = 0;
            Actions.releaseKeys();
            return;
        }
        Actions.walkToward(player, cell.getX() + 0.5, cell.getZ() + 0.5, false);
    }
}
