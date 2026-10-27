package com.example.litematicaaddon;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class ModInit implements ClientModInitializer {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("litematica-subterranean-miner", "main"));

    private static KeyMapping toggleKey;
    private static KeyMapping menuKey;

    @Override
    public void onInitializeClient() {
        SubMinerConfig.load();

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.litematicsubminer.toggle",
                InputConstants.KEY_J,
                CATEGORY
        ));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.litematicsubminer.menu",
                InputConstants.KEY_K,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }
            while (menuKey.consumeClick()) {
                client.gui.setScreen(new SubMinerScreen(null));
            }
            while (toggleKey.consumeClick()) {
                LitematicaSubterraneanBot.toggle();
            }
            LitematicaSubterraneanBot.onTick(client);
        });
    }
}
