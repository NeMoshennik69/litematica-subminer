package com.example.litematicaaddon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ModInit implements ClientModInitializer {
    private static KeyBinding toggleBinding;

    @Override
    public void onInitializeClient() {
        toggleBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.litematicsubminer.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J, 
                "category.litematicsubminer"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (toggleBinding.wasPressed()) {
                LitematicaSubterraneanBot.toggle();
            }
            LitematicaSubterraneanBot.onTick(client);
        });
    }
}
