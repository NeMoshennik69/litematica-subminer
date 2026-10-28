package com.example.litematicaaddon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Маршрут по уже расчищенным клеткам. Обходит ямы глубиной 2 блока и больше,
 * жидкости и непроходимые места. Падение на 1 блок и подъём на 1 блок разрешены.
 */
final class Pathing {

    private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final int MAX_NODES = 3000;
    private static final int RADIUS = 24;
    private static final int REPLAN_TICKS = 20;
    private static final BlockPos.MutableBlockPos TMP = new BlockPos.MutableBlockPos();

    private static List<BlockPos> path = new ArrayList<>();
    private static int index;
    private static int age;
    private static BlockPos goalCell;

    private Pathing() {
    }

    static void reset() {
        path = new ArrayList<>();
        index = 0;
        age = 0;
        goalCell = null;
    }

    /** Следующая точка маршрута к клетке goal. null = ближе подобраться нельзя. */
    static BlockPos nextWaypoint(ClientLevel level, BlockPos cur, BlockPos goal) {
        boolean replan = goalCell == null || !goal.equals(goalCell) || ++age > REPLAN_TICKS;
        if (!replan) {
            while (index < path.size() && path.get(index).equals(cur)) {
                index++;
            }
            if (index >= path.size() || !isNear(path.get(index), cur)) {
                replan = true;
            }
        }
        if (replan) {
            goalCell = goal;
            age = 0;
            path = plan(level, cur, goal);
            index = 0;
        }
        return index < path.size() ? path.get(index) : null;
    }

    private static boolean isNear(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= 1
                && Math.abs(a.getZ() - b.getZ()) <= 1
                && Math.abs(a.getY() - b.getY()) <= 2;
    }

    /** Поиск в ширину: идёт по свободным клеткам к точке, ближайшей к цели по горизонтали. */
    private static List<BlockPos> plan(ClientLevel level, BlockPos start, BlockPos goal) {
        Map<Long, Long> parent = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        parent.put(start.asLong(), start.asLong());
        queue.add(start);
        BlockPos best = start;
        double bestScore = score(start, goal);
        int nodes = 0;
        while (!queue.isEmpty() && nodes++ < MAX_NODES) {
            BlockPos cur = queue.poll();
            double s = score(cur, goal);
            if (s < bestScore - 1.0E-9) {
                best = cur;
                bestScore = s;
            }
            for (Direction dir : DIRS) {
                BlockPos next = step(level, cur, dir);
                if (next == null) {
                    continue;
                }
                if (Math.abs(next.getX() - start.getX()) > RADIUS || Math.abs(next.getZ() - start.getZ()) > RADIUS) {
                    continue;
                }
                if (parent.containsKey(next.asLong())) {
                    continue;
                }
                parent.put(next.asLong(), cur.asLong());
                queue.add(next);
            }
        }
        List<BlockPos> out = new ArrayList<>();
        BlockPos c = best;
        while (!c.equals(start)) {
            out.add(c);
            c = BlockPos.of(parent.get(c.asLong()));
        }
        Collections.reverse(out);
        return out;
    }

    private static double score(BlockPos cell, BlockPos goal) {
        double dx = cell.getX() - goal.getX();
        double dz = cell.getZ() - goal.getZ();
        return dx * dx + dz * dz;
    }

    /** Соседняя клетка, куда можно встать, или null (стена, жидкость, яма 2+ блока). */
    private static BlockPos step(ClientLevel level, BlockPos cur, Direction dir) {
        int nx = cur.getX() + dir.getStepX();
        int nz = cur.getZ() + dir.getStepZ();
        int y = cur.getY();
        if (open(level, nx, y, nz) && open(level, nx, y + 1, nz)) {
            if (solid(level, nx, y - 1, nz)) {
                return new BlockPos(nx, y, nz);
            }
            // Пола нет: падение на 1 блок допустимо, глубже (яма 2+) обходим
            if (open(level, nx, y - 1, nz) && solid(level, nx, y - 2, nz)) {
                return new BlockPos(nx, y - 1, nz);
            }
            return null;
        }
        // Ступенька вверх на 1 блок
        if (solid(level, nx, y, nz)
                && open(level, nx, y + 1, nz)
                && open(level, nx, y + 2, nz)
                && open(level, cur.getX(), y + 2, cur.getZ())) {
            return new BlockPos(nx, y + 1, nz);
        }
        return null;
    }

    /** true = под клеткой пустота глубже 1 блока или жидкость: туда идти нельзя. */
    static boolean isPit(ClientLevel level, BlockPos cell) {
        int x = cell.getX();
        int y = cell.getY();
        int z = cell.getZ();
        if (solid(level, x, y - 1, z)) {
            return false;
        }
        return !(open(level, x, y - 1, z) && solid(level, x, y - 2, z));
    }

    /** Клетка проходима: нет жидкости и нет твёрдого блока. */
    static boolean open(ClientLevel level, int x, int y, int z) {
        BlockState state = level.getBlockState(TMP.set(x, y, z));
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, TMP).isEmpty();
    }

    /** Клетка твёрдая: на ней можно стоять. */
    static boolean solid(ClientLevel level, int x, int y, int z) {
        BlockState state = level.getBlockState(TMP.set(x, y, z));
        return state.getFluidState().isEmpty() && !state.getCollisionShape(level, TMP).isEmpty();
    }
}
