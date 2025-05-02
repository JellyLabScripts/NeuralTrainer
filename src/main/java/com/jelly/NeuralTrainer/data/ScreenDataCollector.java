package com.jelly.NeuralTrainer.data;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.*;
import java.nio.ByteBuffer;

public class ScreenDataCollector {

    private final Minecraft mc = Minecraft.getMinecraft();
    public static final String DATASET_PATH = "raw_dataset/screendata.txt";

    private final KeyBinding keybindA = mc.gameSettings.keyBindLeft;
    private final KeyBinding keybindD =  mc.gameSettings.keyBindRight;
    private final KeyBinding keybindW = mc.gameSettings.keyBindForward;
    private final KeyBinding keybindS = mc.gameSettings.keyBindBack;
    private final KeyBinding keyBindJump = mc.gameSettings.keyBindJump;
    private final File raw_dataset = new File(DATASET_PATH);

    private float lastYaw = Float.NaN;
    private float lastPitch = Float.NaN;


    @Getter
    private boolean recording;

    public void startRecording() {
        recording = true;
        initializeFile();
    }

    public void stopRecording() {
        recording = false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END || mc.thePlayer == null || mc.theWorld == null || !recording)
            return;

        float currentYaw = mc.thePlayer.rotationYaw;
        float currentPitch = mc.thePlayer.rotationPitch;

        float deltaYaw = Float.isNaN(lastYaw) ? 0 : wrapAngle(currentYaw - lastYaw);
        float deltaPitch = Float.isNaN(lastPitch) ? 0 : wrapAngle(currentPitch - lastPitch);

        int discretizedYaw = discretize(deltaYaw, new int[]{-90, -50, -20, -10, -4, -2, 0, 2, 4, 10, 20, 50, 90});
        int discretizedPitch = discretize(deltaPitch, new int[]{-40, -20, -10, -4, -2, 0, 2, 4, 10, 20, 40});

        lastYaw = currentYaw;
        lastPitch = currentPitch;

        Boolean[] movement_array = {
                keybindA.isKeyDown(),
                keybindW.isKeyDown(),
                keybindS.isKeyDown(),
                keybindD.isKeyDown(),
                keyBindJump.isKeyDown(),
                mc.gameSettings.keyBindAttack.isKeyDown(), // left click
                mc.gameSettings.keyBindUseItem.isKeyDown() // right click
        };

        try {
            // Capture framebuffer pixels
            int width = mc.getFramebuffer().framebufferTextureWidth;
            int height = mc.getFramebuffer().framebufferTextureHeight;
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4); // 4 bytes per pixel (RGBA)

            GL11.glReadBuffer(GL11.GL_FRONT);
            GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buffer);

            // Create array to hold grayscale values (before downscaling)
            int[] grayscaleFullRes = new int[width * height];

            // Convert pixels to grayscale
            for (int i = 0; i < width * height; i++) {
                int b = buffer.get(i * 4) & 0xFF;
                int g = buffer.get(i * 4 + 1) & 0xFF;
                int r = buffer.get(i * 4 + 2) & 0xFF;
                // Calculate grayscale: simple average or weighted average
                int gray = (r + g + b) / 3;
                grayscaleFullRes[i] = gray;
            }

            // Downscale to 32 x 32
            int targetWidth = 32;
            int targetHeight = 32;
            int scaleX = width / targetWidth;
            int scaleY = height / targetHeight;

            // (after computing downscaled pixels)
            byte[] pixelArray = new byte[targetWidth * targetHeight];
            int index = 0;
            for (int ty = 0; ty < targetHeight; ty++) {
                for (int tx = 0; tx < targetWidth; tx++) {
                    int sum = 0;
                    int count = 0;
                    for (int sy = 0; sy < scaleY; sy++) {
                        for (int sx = 0; sx < scaleX; sx++) {
                            int sourceX = tx * scaleX + sx;
                            int sourceY = (height - 1) - (ty * scaleY + sy); // flip the vertical
                            if (sourceX < width && sourceY < height) {
                                sum += grayscaleFullRes[sourceY * width + sourceX];
                                count++;
                            }
                        }
                    }
                    int avgGray = (count > 0) ? (sum / count) : 0;
                    pixelArray[index++] = (byte) avgGray;
                }
            }

            // Serialize movement inputs
            StringBuilder inputData = new StringBuilder();
            for (boolean pressed : movement_array) {
                inputData.append(pressed ? "1" : "0").append(",");
            }

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(raw_dataset, true);
                 DataOutputStream dos = new DataOutputStream(fos)) {

                // Write all grayscale pixel data at once
                dos.write(pixelArray);

                // Write movement inputs (7 keys)
                for (boolean pressed : movement_array) {
                    dos.writeByte(pressed ? 1 : 0);
                }

                // Write head movement (2 bytes: yaw + pitch indices)
                dos.writeByte(discretizedYaw);
                dos.writeByte(discretizedPitch);

                // End of sample marker
                dos.writeByte(255);
            }

        } catch (Exception e) {
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

    private float wrapAngle(float angle) {
        angle = angle % 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }

    // Reference: https://arxiv.org/pdf/2104.04258
    private int discretize(float value, int[] bins) {
        int closestIndex = 0;
        float closestDiff = Float.MAX_VALUE;
        for (int i = 0; i < bins.length; i++) {
            float diff = Math.abs(value - bins[i]);
            if (diff < closestDiff) {
                closestDiff = diff;
                closestIndex = i;
            }
        }
        return closestIndex;
    }


}
