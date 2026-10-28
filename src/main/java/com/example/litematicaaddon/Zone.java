package com.example.litematicaaddon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Зона расчистки и поиск блоков в ней. */
final class Zone {

    private static final int MAX_RETRIES = 3;

    static int minX;
    static int maxX;
    static int minY;
    static int maxY;
    static int minZ;
    static int maxZ;
    static boolean startAtMaxX;
    static int curLayer;
    static int retries;
    static final Set<Long> skipped = new HashSet<>();

    private static final Set<String> ignoredIds = new HashSet<>();
    private static final Map<Block, Boolean> ignoreCache = new HashMap<>();
    private static final Set<Block> autoIgnored = new HashSet<>();

    private Zone() {
    }

    /** Берёт углы из настроек. false = зона целиком ниже дна мира. */
    static boolean init(int worldMinY, double playerX) {
        minX = Math.min(SubMinerConfig.x1, SubMinerConfig.x2);
        maxX = Math.max(SubMinerConfig.x1, SubMinerConfig.x2);
        minY = Math.max(Math.min(SubMinerConfig.y1, SubMinerConfig.y2), worldMinY);
        maxY = Math.max(SubMinerConfig.y1, SubMinerConfig.y2);
        minZ = Math.min(SubMinerConfig.z1, SubMinerConfig.z2);
        maxZ = Math.max(SubMinerConfig.z1, SubMinerConfig.z2);
        if (maxY < minY) {
            return false;
        }
        startAtMaxX = Math.abs(playerX - (maxX + 0.5)) < Math.abs(playerX - (minX + 0.5));
        skipped.clear();
        ignoredIds.clear();
        ignoredIds.addAll(SubMinerConfig.cleanedFrom(SubMinerConfig.ignored));
        ignoreCache.clear();
        autoIgnored.clear();
        curLayer = maxY;
        retries = 0;
        return true;
    }

    /** Блок в списке игнорируемых (из меню или поставленный самим ботом). */
    static boolean isIgnored(Block block) {
        if (autoIgnored.contains(block)) {
            return true;
        }
        Boolean cached = ignoreCache.get(block);
        if (cached == null) {
            cached = ignoredIds.contains(BuiltInRegistries.BLOCK.getKey(block).toString());
            ignoreCache.put(block, cached);
        }
        return cached;
    }

    /** Запоминает блок, который поставил бот, чтобы не ломать его обратно. */
    static void addAutoIgnored(Block block) {
        autoIgnored.add(block);
    }

    /** Обновляет верхний непустой слой. true = зона полностью очищена. */
    static boolean updateCurLayer(ClientLevel level) {
        while (curLayer >= minY && layerEmpty(level, curLayer)) {
            curLayer--;
        }
        if (curLayer >= minY) {
            return false;
        }
        int top = highestNonEmptyLayer(level);
        if (top < minY || ++retries > MAX_RETRIES) {
            return true;
        }
        curLayer = top;
        return false;
    }

    static boolean layerEmpty(ClientLevel level, int y) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, y, z);
                if (isMineable(level, pos) && !skipped.contains(pos.asLong())) {
                    return false;
                }
            }
        }
        return true;
    }

    static int highestNonEmptyLayer(ClientLevel level) {
        for (int y = maxY; y >= minY; y--) {
            if (!layerEmpty(level, y)) {
                return y;
            }
        }
        return minY - 1;
    }

    /** Блок в досягаемости: сначала верхний слой, внутри слоя ближайший. */
    static BlockPos pickTarget(ClientLevel level, Vec3 eye, double reach, int yLo, int yHi) {
        double reachSq = reach * reach;
        int fromX = Math.max(minX, Mth.floor(eye.x - reach));
        int toX = Math.min(maxX, Mth.floor(eye.x + reach));
        int fromZ = Math.max(minZ, Mth.floor(eye.z - reach));
        int toZ = Math.min(maxZ, Mth.floor(eye.z + reach));
        int yFrom = Math.min(yHi, Mth.floor(eye.y + reach));
        int yTo = Math.max(yLo, Mth.floor(eye.y - reach));

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = yFrom; y >= yTo; y--) {
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
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

    /**
     * Первая по порядку змейки клетка (x, z) с оставшимся блоком в рабочих слоях.
     * Ряды идут с севера (меньший Z) на юг, направление по X чередуется.
     * В каждом следующем слое порядок разворачивается, чтобы змейка не прерывалась.
     */
    static BlockPos frontierCell(ClientLevel level, int yLo, int yHi) {
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        int total = width * depth;
        boolean reversed = ((maxY - curLayer) % 2) == 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < total; i++) {
            int idx = reversed ? total - 1 - i : i;
            int row = idx / width;
            int col = idx % width;
            boolean eastward = (row % 2 == 0) != startAtMaxX;
            int x = eastward ? minX + col : maxX - col;
            int z = minZ + row;
            for (int y = yHi; y >= yLo; y--) {
                pos.set(x, y, z);
                if (isMineable(level, pos) && !skipped.contains(pos.asLong())) {
                    return new BlockPos(x, yHi, z);
                }
            }
        }
        return null;
    }

    static void skipCell(BlockPos cell, int yLo, int yHi) {
        for (int y = yLo; y <= yHi; y++) {
            skipped.add(BlockPos.asLong(cell.getX(), y, cell.getZ()));
        }
    }

    static boolean isStillValid(ClientLevel level, BlockPos pos, Vec3 eye, double reach) {
        return isMineable(level, pos)
                && !skipped.contains(pos.asLong())
                && Vec3.atCenterOf(pos).distanceToSqr(eye) <= reach * reach;
    }

    /** Не воздух, не жидкость, не бедрок/барьер и не игнорируемый блок. */
    static boolean isMineable(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !isIgnored(state.getBlock());
    }

    /** Рядом с блоком (6 сторон) есть вода/лава: ломать опасно. */
    static boolean isUnsafe(ClientLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (!level.getFluidState(pos.relative(dir)).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
