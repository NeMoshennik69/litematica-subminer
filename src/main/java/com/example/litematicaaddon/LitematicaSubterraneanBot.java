package com.example.litematicaaddon;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.text.Text;

public class LitematicaSubterraneanBot {
    private static boolean active = false;
    private static int stepCounter = 0;
    private static BlockPos lastBlockPos = null;
    
    private static final int TARGET_HOTBAR_SLOT = 3; 
    private static final int STEPS_INTERVAL = 10;
    private static int currentLayerY = -1;

    public static void toggle() {
        active = !active;
        if (!active) {
            currentLayerY = -1;
            sendMessage("§c[SubMiner] Выключен");
        } else {
            stepCounter = 0;
            lastBlockPos = null;
            sendMessage("§a[SubMiner] Включен. Копаем подземную базу по слоям...");
        }
    }

    public static void onTick(MinecraftClient client) {
        if (!active || client.player == null || client.world == null) return;

        BlockPos currentPos = client.player.getBlockPos();

        if (lastBlockPos == null) {
            lastBlockPos = currentPos;
        } else if (currentPos.getX() != lastBlockPos.getX() || currentPos.getZ() != lastBlockPos.getZ()) {
            stepCounter++;
            lastBlockPos = currentPos;

            if (stepCounter >= STEPS_INTERVAL) {
                placeFourthSlotUnderfoot(client);
                stepCounter = 0;
            }
        }

        if (currentLayerY == -1) {
            currentLayerY = currentPos.getY() + 3;
        }

        BlockPos targetBlock = findNextBlockInLayer(client, currentPos, currentLayerY);

        if (targetBlock == null) {
            currentLayerY--;
            if (currentLayerY < client.world.getBottomY()) {
                sendMessage("§6[SubMiner] Готово! Зона полностью очищена.");
                toggle();
            }
            return;
        }

        if (currentPos.isWithinDistance(targetBlock, 4.2)) {
            autoBreakTarget(client, targetBlock);
        }
    }

    private static BlockPos findNextBlockInLayer(MinecraftClient client, BlockPos playerPos, int layerY) {
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        int radius = 16; 

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                mutablePos.set(playerPos.getX() + x, layerY, playerPos.getZ() + z);
                BlockState worldState = client.world.getBlockState(mutablePos);

                if (!worldState.isAir() && !worldState.isLiquid()) {
                    return mutablePos.toImmutable();
                }
            }
        }
        return null;
    }

    private static void autoBreakTarget(MinecraftClient client, BlockPos pos) {
        if (client.interactionManager == null || client.player == null) return;
        lookAt(client.player, pos);
        client.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    private static void placeFourthSlotUnderfoot(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        BlockPos targetPos = client.player.getBlockPos().down();
        if (client.world.getBlockState(targetPos).isAir()) return; 

        int prevSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = TARGET_HOTBAR_SLOT;

        BlockHitResult hitResult = new BlockHitResult(
            new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5),
            Direction.UP, targetPos, false
        );

        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult);
        client.player.swingHand(Hand.MAIN_HAND);
        client.player.getInventory().selectedSlot = prevSlot;
    }

    private static void lookAt(net.minecraft.entity.player.PlayerEntity player, BlockPos target) {
        Vec3d dir = Vec3d.ofCenter(target).subtract(player.getEyePos());
        double diffX = dir.x;
        double diffY = dir.y;
        double diffZ = dir.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    private static void sendMessage(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.of(text), false);
        }
    }
}
