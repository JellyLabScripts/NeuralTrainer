package com.jelly.NeuralTrainer.utils;

import com.jelly.NeuralTrainer.NeuralTrainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

public class KeyBindUtils {

    private static Minecraft mc = Minecraft.getMinecraft();

    public static KeyBinding[] keybinds = new KeyBinding[2];

    public static void init() {
        keybinds[0] = new KeyBinding("Start/stop data collection", Keyboard.KEY_K, "NeuralTrainer");
        keybinds[1] = new KeyBinding("Start/stop data playback", Keyboard.KEY_GRAVE, "NeuralTrainer");
        for (KeyBinding customKeyBind : keybinds) {
            ClientRegistry.registerKeyBinding(customKeyBind);
        }
    }

    public static void onKeyPress (){
        if(keybinds[0].isKeyDown()){

            if(NeuralTrainer.movementDataCollector.isRecording()) {
                LogUtils.addMessage("Disabled recording");
                NeuralTrainer.movementDataCollector.stopRecording();
            } else {
                LogUtils.addMessage("Enabled recording");
                NeuralTrainer.movementDataCollector.startRecording();

            }
        }

    }
}
