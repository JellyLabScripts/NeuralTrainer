package com.jelly.NeuralTrainer.utils;

import com.jelly.NeuralTrainer.NeuralTrainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class KeyBindUtils {

    private static Minecraft mc = Minecraft.getMinecraft();
    public static KeyBinding[] keybinds = new KeyBinding[2];

    public static void init() {
        keybinds[0] = new KeyBinding("Start/stop data collection", 0, "NeuralTrainer");
        keybinds[1] = new KeyBinding("Start/stop running neural network", 0, "NeuralTrainer");
        for (KeyBinding customKeyBind : keybinds) {
            ClientRegistry.registerKeyBinding(customKeyBind);
        }
    }

    public static void onKeyPress (){

        if(keybinds[0].isKeyDown()){

            if(NeuralTrainer.screenDataCollector.isRecording()) {
                LogUtils.addMessage("Disabled recording");
                NeuralTrainer.screenDataCollector.stopRecording();
            } else {
                LogUtils.addMessage("Enabled recording");
                NeuralTrainer.screenDataCollector.startRecording();

            }
        }

        if(keybinds[1].isKeyDown()){

            if(NeuralTrainer.neuralNetworkRunner.running()) {
                LogUtils.addMessage("Stopped running neural network");
                NeuralTrainer.neuralNetworkRunner.stopRunning();
            } else {
                LogUtils.addMessage("Started running neural network");
                NeuralTrainer.neuralNetworkRunner.startRunning();
            }
        }

    }

    public static void setKeyBindState(KeyBinding key, boolean pressed) {
        if (pressed) {
            if (mc.currentScreen != null) {
                realSetKeyBindState(key, false);
                return;
            }
        }
        realSetKeyBindState(key, pressed);
    }

    public static void onTick(KeyBinding key) {
        if (mc.currentScreen == null) {
            KeyBinding.onTick(key.getKeyCode());
        }
    }

    private static void realSetKeyBindState(KeyBinding key, boolean pressed){
        if(pressed){
            if(!key.isKeyDown()){
                KeyBinding.onTick(key.getKeyCode());
            }
            KeyBinding.setKeyBindState(key.getKeyCode(), true);

        } else {
            KeyBinding.setKeyBindState(key.getKeyCode(), false);
        }

    }
}
