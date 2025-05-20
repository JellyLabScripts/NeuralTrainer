package com.jelly.NeuralTrainer.run;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.jelly.NeuralTrainer.utils.KeyBindUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class NeuralNetworkRunner {

    private static final float[] mouseXBins = {-90, -50, -20, -10, -4, -2, 0, 2, 4, 10, 20, 50, 90};

    private static final float[] mouseYBins = {-40, -20, -10, -4, -2, 0, 2, 4, 10, 20, 40};

    private static final int frame_x = 80;
    private static final int frame_y = 45;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final KeyBinding[] keybinds = {
            mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindForward,
            mc.gameSettings.keyBindBack, mc.gameSettings.keyBindRight,
            mc.gameSettings.keyBindJump
    };

    private boolean running = false;
    private final Gson gson = new Gson();

    private final int TIMESTEPS = 20;
    private final Deque<float[][][]> frameBuffer = new ArrayDeque<>();

    private ExecutorService executor;
    private volatile List<Float> latestActions = null;
    private volatile boolean isInferenceRunning = false;

    public boolean running() {
        return running;
    }

    public void startRunning() {
        running = true;
        latestActions = null;

        frameBuffer.clear();
        isInferenceRunning = false;
        executor = Executors.newSingleThreadExecutor();
    }

    public void stopRunning() {
        running = false;
        for (KeyBinding key : keybinds) {
            KeyBinding.setKeyBindState(key.getKeyCode(), false);
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!running || event.phase != TickEvent.Phase.END || mc.thePlayer == null || mc.theWorld == null)
            return;

        float[][][] frame = captureAndResizeScreen();
        frameBuffer.addLast(frame);
        if (frameBuffer.size() < TIMESTEPS) return;

        // Keep buffer at max size
        if (frameBuffer.size() > TIMESTEPS) {
            frameBuffer.removeFirst();
        }

        // Convert to TIMESTEPS-frame array
        float[][][][] frames = new float[TIMESTEPS][frame_x][frame_y][3];
        int i = 0;
        for (float[][][] f : frameBuffer) {
            frames[i++] = f;
        }

        if (executor != null && !isInferenceRunning && latestActions == null) {
            float[][][][] framesToSend = frames.clone(); // clone to avoid mutation
            isInferenceRunning = true;
            executor.submit(() -> {
                List<Float> result = sendToInferenceServer(framesToSend);
                latestActions = result;
                isInferenceRunning = false;
            });
        }

        if (latestActions == null) {
            for (KeyBinding key : keybinds) {
                KeyBinding.setKeyBindState(key.getKeyCode(), false);
            }
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            return;
        }

        for (int k = 0; k < 5; k++) {
            boolean pressed = latestActions.get(k) > 0.5f;
            KeyBinding.setKeyBindState(keybinds[k].getKeyCode(), pressed);
        }

        // Handle left and right clicks (indexes 5 and 6)
        boolean leftClick = latestActions.get(5) > 0.5f;
        boolean rightClick = latestActions.get(6) > 0.5f;

        KeyBindUtils.setKeyBindState(mc.gameSettings.keyBindAttack, leftClick);
        KeyBindUtils.setKeyBindState(mc.gameSettings.keyBindUseItem, rightClick);

        // Yaw (mouse X): softmax from index 7–28
        int yawStart = 7;
        int pitchStart = yawStart + mouseXBins.length;

        int yawIdx = 0;
        float yawMax = -1;
        for (int k = 0; k < mouseXBins.length; k++) {
            float val = latestActions.get(yawStart + k);
            if (val > yawMax) {
                yawMax = val;
                yawIdx = k;
            }
        }

        int pitchIdx = 0;
        float pitchMax = -1;
        for (int k = 0; k < mouseYBins.length; k++) {
            float val = latestActions.get(pitchStart + k);
            if (val > pitchMax) {
                pitchMax = val;
                pitchIdx = k;
            }
        }

        float yawDelta = mouseXBins[yawIdx];
        float pitchDelta = mouseYBins[pitchIdx];

        // Print descriptive action log
        System.out.printf("Actions: [W: %b, A: %b, S: %b, D: %b, Space: %b, LeftClick: %b, RightClick: %b, YawDelta: %.2f, PitchDelta: %.2f]%n",
                latestActions.get(1) > 0.5f, latestActions.get(0) > 0.5f, latestActions.get(2) > 0.5f, latestActions.get(3) > 0.5f, latestActions.get(4) > 0.5f,
                latestActions.get(5) > 0.5f, latestActions.get(6) > 0.5f, yawDelta, pitchDelta);

        mc.thePlayer.rotationYaw += yawDelta;
        mc.thePlayer.rotationPitch += pitchDelta;

        latestActions = null;
    }

    private float[][][] captureAndResizeScreen() {
        int width = mc.getFramebuffer().framebufferTextureWidth;
        int height = mc.getFramebuffer().framebufferTextureHeight;

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4); // 4 bytes per pixel (RGBA)

        GL11.glReadBuffer(GL11.GL_FRONT);
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buffer);

        // Grayscale pixel array
        int[] grayscaleFullRes = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            int b = buffer.get(i * 4) & 0xFF;
            int g = buffer.get(i * 4 + 1) & 0xFF;
            int r = buffer.get(i * 4 + 2) & 0xFF;
            int gray = (r + g + b) / 3;
            grayscaleFullRes[i] = gray;
        }

        // Resize to 280x150 and convert to RGB float array normalized to [0,1]
        float[][][] result = new float[frame_y][frame_x][3];

        int scaleX = width / frame_x;
        int scaleY = height / frame_y;

        for (int ty = 0; ty < frame_y; ty++) {
            for (int tx = 0; tx < frame_x; tx++) {
                int sum = 0;
                int count = 0;
                for (int sy = 0; sy < scaleY; sy++) {
                    for (int sx = 0; sx < scaleX; sx++) {
                        int sourceX = tx * scaleX + sx;
                        int sourceY = (height - 1) - (ty * scaleY + sy); // Flip vertically

                        if (sourceX < width && sourceY < height) {
                            int gray = grayscaleFullRes[sourceY * width + sourceX];
                            sum += gray;
                            count++;
                        }
                    }
                }

                float avgGray = sum / (float) count;

                // Convert to "fake" RGB (grayscale repeated in 3 channels)
                result[ty][tx][0] = avgGray;
                result[ty][tx][1] = avgGray;
                result[ty][tx][2] = avgGray;
            }
        }

        return result;
    }



    private List<Float> sendToInferenceServer(float[][][][] frames) {
        try {
            URL url = new URL("http://localhost:8000/predict");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            Map<String, Object> payload = new HashMap<>();
            payload.put("frame", frames);

            String json = gson.toJson(payload);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes());
                os.flush();
            }

            if (conn.getResponseCode() != 200) {
                System.err.println("Error: HTTP " + conn.getResponseCode());
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                System.out.println("Raw JSON response: " + sb);
                Map<?, ?> resp = gson.fromJson(sb.toString(), Map.class);
                List<Double> doubles = (List<Double>) resp.get("actions");
                List<Float> result = new ArrayList<>();
                for (Double d : doubles) result.add(d.floatValue());

                return result;
            }

        } catch (Exception e) {
            System.err.println("Inference server error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}