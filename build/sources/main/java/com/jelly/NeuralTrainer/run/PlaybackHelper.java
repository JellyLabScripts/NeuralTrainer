package com.jelly.NeuralTrainer.run;

import cc.polyfrost.oneconfig.config.annotations.KeyBind;
import com.jelly.NeuralTrainer.utils.KeyBindUtils;
import com.jelly.NeuralTrainer.utils.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static com.jelly.NeuralTrainer.data.MovementDataCollector.DATASET_PATH;

public class PlaybackHelper {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final KeyBinding[] keybinds = {mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindForward,
                                            mc.gameSettings.keyBindBack, mc.gameSettings.keyBindRight,
                                            mc.gameSettings.keyBindJump};
    private boolean playback;
    private int tick;
    private String[] movementData;

    public boolean playingBack() {
        return playback;
    }
    public void startPlayback() {
        playback = true;
        tick = 0;
        movementData = getFileAsString(DATASET_PATH).split(" ");
    }

    public void stopPlayback() {
        playback = false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END || mc.thePlayer == null || mc.theWorld == null || !playback)
            return;

        tick += 1;
        tick %= 20;

        if (tick % 4 != 0)
            return;

        int current_mov_int = Integer.parseInt(movementData[0]);

        for (int i = 0; i < keybinds.length; i++) {
            int keybindState = (current_mov_int >> i) & 1;
            KeyBindUtils.setKeyBindState(keybinds[i], keybindState != 0);
        }

        movementData = Arrays.copyOfRange(movementData, 1, movementData.length);

        /*int current_mov_int = Integer.parseInt(movementData[0]);
        String current_mov_bin = String.format("%5s", Integer.toBinaryString(current_mov_int)).replace(' ', '0');

        LogUtils.addMessage(current_mov_bin);
        for(int i = 0; i < keybinds.length; i++) {
            int keybindState = Integer.parseInt(String.valueOf(current_mov_bin.charAt(i)));
            KeyBindUtils.setKeyBindState(keybinds[i], keybindState != 0);
        }
        movementData = Arrays.copyOfRange(movementData, 1, movementData.length);*/
    }

    private String getFileAsString(String dataset_path) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(dataset_path));
            return new String(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }




}
