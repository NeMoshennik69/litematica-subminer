package com.example.litematicaaddon;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Вкладка «Блоки»: блок для установки (слот и частота) и список игнорируемых блоков. */
public class SubMinerBlocksScreen extends Screen {

    private static final int PAGE_SIZE = 10;

    private final Screen parent;
    private final List<String> entries = new ArrayList<>(SubMinerConfig.ignored);
    private int page;
    private int everyValue = SubMinerConfig.placeEvery;
    private String slotText = Integer.toString(SubMinerConfig.placeSlot);
    private String error = "";

    public SubMinerBlocksScreen(Screen parent) {
        super(Component.literal("SubMiner"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (entries.isEmpty()) {
            entries.add("");
        }
        int left = this.width / 2 - 108;

        this.addRenderableWidget(Button.builder(Component.literal("Зона"), btn -> openZone())
                .bounds(left, 6, 106, 20).build());
        Button blocksTab = Button.builder(Component.literal("Блоки"), btn -> { })
                .bounds(left + 110, 6, 106, 20).build();
        blocksTab.active = false;
        this.addRenderableWidget(blocksTab);

        EditBox slotBox = new EditBox(this.font, left, 46, 40, 20, Component.literal("slot"));
        slotBox.setMaxLength(1);
        slotBox.setValue(slotText);
        slotBox.setResponder(text -> slotText = text);
        this.addRenderableWidget(slotBox);

        this.addRenderableWidget(new EverySlider(left + 46, 46, 170, 20, everyValue));

        int first = page * PAGE_SIZE;
        int last = Math.min(entries.size(), first + PAGE_SIZE);
        for (int i = first; i < last; i++) {
            int index = i;
            int cell = i - first;
            EditBox box = new EditBox(this.font, left + (cell % 2) * 112, 86 + (cell / 2) * 22, 104, 20,
                    Component.literal("id"));
            box.setMaxLength(64);
            box.setValue(entries.get(i));
            box.setResponder(text -> entries.set(index, text));
            this.addRenderableWidget(box);
        }

        this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> addEntry())
                .bounds(left + 220, 86, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("<"), btn -> changePage(-1))
                .bounds(left + 220, 118, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), btn -> changePage(1))
                .bounds(left + 220, 154, 20, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Готово"), btn -> done())
                .bounds(left, 204, 106, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Отмена"), btn -> this.onClose())
                .bounds(left + 110, 204, 106, 20).build());
    }

    private int pageCount() {
        return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void addEntry() {
        entries.add("");
        page = (entries.size() - 1) / PAGE_SIZE;
        this.rebuildWidgets();
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
        this.rebuildWidgets();
    }

    /** Проверяет поля и записывает в настройки. false = ошибка в полях. */
    private boolean apply() {
        int slot;
        try {
            slot = Integer.parseInt(slotText.trim());
        } catch (NumberFormatException e) {
            error = "Слот блока: число от 1 до 9 (0 = выключено)";
            return false;
        }
        if (slot < 0 || slot > 9) {
            error = "Слот блока: число от 1 до 9 (0 = выключено)";
            return false;
        }
        if (slot != 0 && slot == SubMinerConfig.pickaxeSlot) {
            error = "Слот блока совпадает со слотом кирки";
            return false;
        }
        error = "";
        SubMinerConfig.placeSlot = slot;
        SubMinerConfig.placeEvery = everyValue;
        List<String> cleaned = SubMinerConfig.cleanedFrom(entries);
        SubMinerConfig.ignored.clear();
        SubMinerConfig.ignored.addAll(cleaned);
        SubMinerConfig.save();
        return true;
    }

    private void done() {
        if (apply()) {
            this.onClose();
        }
    }

    private void openZone() {
        if (apply()) {
            this.minecraft.gui.setScreen(new SubMinerScreen(this.parent));
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
        graphics.text(this.font, "Блок для установки: слот (0 = выкл) и частота", left, 34, 0xFFAAAAAA, true);
        graphics.text(this.font, "Не ломать (ID блоков, пусто = убрать)", left, 74, 0xFFAAAAAA, true);
        graphics.text(this.font, (page + 1) + "/" + pageCount(), left + 221, 142, 0xFFFFFFFF, true);
        if (!error.isEmpty()) {
            graphics.text(this.font, error, left, 230, 0xFFFF5555, true);
        }
    }

    /** Ползунок 3..16: через сколько блоков пути ставить блок. */
    private final class EverySlider extends AbstractSliderButton {

        EverySlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Component.empty(), fraction(initial));
            this.updateMessage();
        }

        private int steps() {
            return SubMinerConfig.EVERY_MIN
                    + (int) Math.round(this.value * (SubMinerConfig.EVERY_MAX - SubMinerConfig.EVERY_MIN));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal("Ставить каждые " + steps() + " бл."));
        }

        @Override
        protected void applyValue() {
            everyValue = steps();
        }
    }

    private static double fraction(int steps) {
        int clamped = Math.max(SubMinerConfig.EVERY_MIN, Math.min(SubMinerConfig.EVERY_MAX, steps));
        return (clamped - SubMinerConfig.EVERY_MIN) / (double) (SubMinerConfig.EVERY_MAX - SubMinerConfig.EVERY_MIN);
    }
}
