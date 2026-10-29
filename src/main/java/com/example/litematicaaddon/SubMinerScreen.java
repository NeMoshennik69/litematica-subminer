package com.example.litematicaaddon;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Вкладка «Зона»: координаты двух углов и слот кирки. */
public class SubMinerScreen extends Screen {

    private final Screen parent;
    private final EditBox[] coords = new EditBox[6];
    private EditBox slotBox;
    private String error = "";

    public SubMinerScreen(Screen parent) {
        super(Component.literal("SubMiner"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 108;

        Button zoneTab = Button.builder(Component.literal("Зона"), btn -> { })
                .bounds(left, 6, 106, 20).build();
        zoneTab.active = false;
        this.addRenderableWidget(zoneTab);
        this.addRenderableWidget(Button.builder(Component.literal("Блоки"), btn -> openBlocks())
                .bounds(left + 110, 6, 106, 20).build());

        int[] values = {
                SubMinerConfig.x1, SubMinerConfig.y1, SubMinerConfig.z1,
                SubMinerConfig.x2, SubMinerConfig.y2, SubMinerConfig.z2
        };
        for (int i = 0; i < 6; i++) {
            int col = i % 3;
            int row = i / 3;
            EditBox box = new EditBox(this.font, left + col * 74, 44 + row * 64, 68, 20, Component.literal("coord"));
            box.setMaxLength(9);
            box.setValue(Integer.toString(values[i]));
            coords[i] = box;
            this.addRenderableWidget(box);
        }

        this.addRenderableWidget(Button.builder(Component.literal("Мои координаты → угол 1"), btn -> fillFromPlayer(0))
                .bounds(left, 68, 216, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Мои координаты → угол 2"), btn -> fillFromPlayer(3))
                .bounds(left, 132, 216, 20).build());

        slotBox = new EditBox(this.font, left, 172, 40, 20, Component.literal("slot"));
        slotBox.setMaxLength(1);
        slotBox.setValue(Integer.toString(SubMinerConfig.pickaxeSlot));
        this.addRenderableWidget(slotBox);

        this.addRenderableWidget(Button.builder(Component.literal("Сохранить"), btn -> save())
                .bounds(left, 204, 106, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Отмена"), btn -> this.onClose())
                .bounds(left + 110, 204, 106, 20).build());
    }

    private void fillFromPlayer(int firstIndex) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        BlockPos pos = this.minecraft.player.blockPosition();
        coords[firstIndex].setValue(Integer.toString(pos.getX()));
        coords[firstIndex + 1].setValue(Integer.toString(pos.getY()));
        coords[firstIndex + 2].setValue(Integer.toString(pos.getZ()));
    }

    /** Проверяет поля и записывает в настройки. false = ошибка в полях. */
    private boolean apply() {
        int[] v = new int[6];
        int slot;
        try {
            for (int i = 0; i < 6; i++) {
                v[i] = Integer.parseInt(coords[i].getValue().trim());
            }
            slot = Integer.parseInt(slotBox.getValue().trim());
        } catch (NumberFormatException e) {
            error = "Нужны целые числа (координаты и слот)";
            return false;
        }
        if (slot < 0 || slot > 9) {
            error = "Слот кирки: число от 1 до 9 (0 = авто)";
            return false;
        }
        if (slot != 0 && slot == SubMinerConfig.placeSlot) {
            error = "Слот кирки совпадает со слотом блока";
            return false;
        }
        error = "";
        SubMinerConfig.x1 = v[0];
        SubMinerConfig.y1 = v[1];
        SubMinerConfig.z1 = v[2];
        SubMinerConfig.x2 = v[3];
        SubMinerConfig.y2 = v[4];
        SubMinerConfig.z2 = v[5];
        SubMinerConfig.pickaxeSlot = slot;
        boolean anyNonZero = false;
        for (int value : v) {
            anyNonZero |= value != 0;
        }
        SubMinerConfig.zoneSet = anyNonZero;
        SubMinerConfig.save();
        return true;
    }

    private void save() {
        if (apply()) {
            this.onClose();
        }
    }

    private void openBlocks() {
        if (apply()) {
            this.minecraft.gui.setScreen(new SubMinerBlocksScreen(this.parent));
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = this.width / 2 - 108;
        graphics.text(this.font, "Угол 1: X   Y   Z", left, 32, 0xFFAAAAAA, true);
        graphics.text(this.font, "Угол 2: X   Y   Z", left, 96, 0xFFAAAAAA, true);
        graphics.text(this.font, "Слот кирки (1-9, 0 = авто)", left, 160, 0xFFAAAAAA, true);
        if (!error.isEmpty()) {
            graphics.text(this.font, error, left, 230, 0xFFFF5555, true);
        }
    }
}
