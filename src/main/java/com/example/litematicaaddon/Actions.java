package com.example.litematicaaddon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Действия бота: ломание блока, поворот, ходьба, выбор кирки, сообщения. */
final class Actions {

    static final int MAX_BREAK_TICKS = 400;
    private static final int MIN_PICKAXE_DURABILITY = 10;

    static BlockPos target;
    static int targetTicks;
    private static boolean walking;
    private static boolean jumping;

    private Actions() {
    }

    static void clearTarget() {
        target = null;
        targetTicks = 0;
    }

    /** Ломает блок. false = слишком долго не ломается. */
    static boolean mine(LocalPlayer player, MultiPlayerGameMode gm, BlockPos pos, Vec3 eye) {
        if (target == null || !target.equals(pos)) {
            target = pos;
            targetTicks = 0;
        }
        if (++targetTicks > MAX_BREAK_TICKS) {
            return false;
        }
        releaseKeys();
        faceBlock(player, pos);
        Vec3 c = Vec3.atCenterOf(pos);
        Direction face = Direction.getApproximateNearest(eye.x - c.x, eye.y - c.y, eye.z - c.z);
        gm.continueDestroyBlock(pos, face);
        return true;
    }

    static void faceBlock(LocalPlayer player, BlockPos pos) {
        Vec3 d = Vec3.atCenterOf(pos).subtract(player.getEyePosition());
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        player.setYRot((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0));
        player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, horizontal)));
    }

    static void walkToward(LocalPlayer player, double tx, double tz, boolean jump) {
        double dx = tx - player.getX();
        double dz = tz - player.getZ();
        player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        player.setXRot(0.0F);
        setWalking(true);
        setJumping(jump || player.horizontalCollision);
    }

    static void releaseKeys() {
        setWalking(false);
        setJumping(false);
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

    /** Выбирает кирку. Возвращает null, если всё хорошо, иначе текст ошибки. */
    static String selectPickaxe(LocalPlayer player, int pickSlot) {
        Inventory inv = player.getInventory();
        if (pickSlot >= 0) {
            if (!isUsablePickaxe(inv.getItem(pickSlot))) {
                return "§c[SubMiner] В слоте " + (pickSlot + 1) + " нет целой кирки — остановка";
            }
            if (inv.getSelectedSlot() != pickSlot) {
                inv.setSelectedSlot(pickSlot);
            }
            return null;
        }
        if (isUsablePickaxe(inv.getItem(inv.getSelectedSlot()))) {
            return null;
        }
        for (int i = 0; i < 9; i++) {
            if (isUsablePickaxe(inv.getItem(i))) {
                inv.setSelectedSlot(i);
                return null;
            }
        }
        return "§c[SubMiner] В хотбаре нет целой кирки — остановка";
    }

    private static boolean isUsablePickaxe(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES)
                && stack.getMaxDamage() - stack.getDamageValue() > MIN_PICKAXE_DURABILITY;
    }

    static void say(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            // Во всех сообщениях бота префикс заменяется на актуальное название мода
            player.sendSystemMessage(Component.literal(text.replace("[SubMiner]", "[AutoMiner]")));
        }
    }
}
