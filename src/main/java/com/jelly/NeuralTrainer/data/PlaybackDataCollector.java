package com.jelly.NeuralTrainer.data;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class PlaybackDataCollector {
    private final Minecraft mc = Minecraft.getMinecraft();
    public static final String DATASET_PATH = "raw_dataset/movement_dataset.txt";

    private final KeyBinding keybindA = mc.gameSettings.keyBindLeft;
    private final KeyBinding keybindD =  mc.gameSettings.keyBindRight;
    private final KeyBinding keybindW = mc.gameSettings.keyBindForward;
    private final KeyBinding keybindS = mc.gameSettings.keyBindBack;
    private final KeyBinding keyBindJump = mc.gameSettings.keyBindJump;
    private final File raw_dataset = new File(DATASET_PATH);
    private boolean recording;
    private int tick;

    public boolean isRecording() {
        return recording;
    }
    public void startRecording() {
        recording = true;
        tick = 0;
        initializeFile();
    }

    public void stopRecording() {
        recording = false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END || mc.thePlayer == null || mc.theWorld == null || !recording)
            return;

        tick += 1;
        tick %= 20;

        if (tick % 4 != 0)
            return;

        Boolean[] movement_array = {keybindA.isKeyDown(), keybindW.isKeyDown(), keybindS.isKeyDown(),
                                    keybindD.isKeyDown(), keyBindJump.isKeyDown()};

        int encoded_movements = 0;
        for(int i = 0; i < movement_array.length; i++) {
            encoded_movements += (int) ((movement_array[i] ? 1 : 0) * Math.pow(2, i));
        }

        try (FileWriter writer = new FileWriter(raw_dataset, true)) {
            writer.write(encoded_movements + " ");
        } catch (IOException e) {
            System.out.println("An error occurred while writing to the file.");
            e.printStackTrace();
        }
    }

    private void initializeFile() {
        String directoryName = "raw_dataset";
        File directory = new File(directoryName);
        if (!directory.exists()) {
            boolean result = directory.mkdir();
            if (result) {
                System.out.println("Directory created successfully.");
            } else {
                System.out.println("Failed to create directory.");
            }
        } else {
            System.out.println("Directory already exists.");
        }


        try {
            if (raw_dataset.createNewFile()) {
                System.out.println("File created: " + raw_dataset.getName());
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
            System.out.println("An error occurred while creating the file.");
            e.printStackTrace();
        }

    }


}
