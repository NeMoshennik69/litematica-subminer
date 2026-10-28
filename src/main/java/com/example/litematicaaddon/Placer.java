package com.example.litematicaaddon;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Ставит блок из выбранного слота (факел и т.п.) каждые N блоков пройденного пути. */
final class Placer {

    private static double distance;
    private static Vec3 lastPos;
    private static int cooldown;
    private static BlockPos pending;
    private static boolean warned;

    private Placer() {
    }

    static void reset() {
        distance = 0.0;
        lastPos = null;
        cooldown = 0;
        pending = null;
        warned = false;
    }

    static void tick(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gm) {
        // Запоминаем поставленный блок, чтобы бот потом не сломал его обратно
        if (pending != null) {
            BlockState placed = level.getBlockState(pending);
            if (!placed.isAir()) {
                Zone.addAutoIgnored(placed.getBlock());
            }
            pending = null;
        }

        int slot = SubMinerConfig.placeSlot - 1;
        if (slot < 0) {
            return;
        }

        Vec3 now = player.position();
        if (lastPos != null) {
            distance += now.distanceTo(lastPos);
        }
        lastPos = now;
        if (cooldown > 0) {
            cooldown--;
        }
        if (distance < SubMinerConfig.placeEvery || cooldown > 0) {
            return;
        }

        Inventory inv = player.getInventory();
        if (inv.getItem(slot).isEmpty()) {
            if (!warned) {
                warned = true;
                Actions.say("§e[SubMiner] В слоте " + (slot + 1) + " пусто — нечего ставить");
            }
            return;
        }
        warned = false;

        BlockPos feet = player.blockPosition();
        BlockPos floor = feet.below();
        if (!player.onGround()
                || !level.getBlockState(feet).isAir()
                || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            return;
        }

        int previous = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        Vec3 hit = new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5);
        gm.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, floor, false));
        inv.setSelectedSlot(previous);
        distance = 0.0;
        cooldown = 10;
        pending = feet;
    }
}
