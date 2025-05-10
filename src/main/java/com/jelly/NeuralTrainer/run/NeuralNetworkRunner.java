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

    private static final int frame_x = 32;
    private static final int frame_y = 32;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final KeyBinding[] keybinds = {
            mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindForward,
            mc.gameSettings.keyBindBack, mc.gameSettings.keyBindRight,
            mc.gameSettings.keyBindJump
    };

    private boolean running = false;
    private final Gson gson = new Gson();

    private final int TIMESTEPS = 10;
    private final Deque<float[][][]> frameBuffer = new ArrayDeque<>();

    private ExecutorService executor;
    private volatile List<Float> latestActions = null;
    private volatile boolean isInferenceRunning = false;

    public boolean running() {
        return running;
    }

    public void startRunning() {
        running = true;
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

        // Convert to 96-frame array
        float[][][][][] frames96 = new float[1][TIMESTEPS][frame_x][frame_y][3];
        int i = 0;
        for (float[][][] f : frameBuffer) {
            frames96[0][i++] = f;
        }

        if (executor != null && !isInferenceRunning && latestActions == null) {
            float[][][][][] framesToSend = frames96.clone(); // clone to avoid mutation
            isInferenceRunning = true;
            executor.submit(() -> {
                List<Float> result = sendToInferenceServer(framesToSend);
                latestActions = result;
                isInferenceRunning = false;
            });
        }

        if (latestActions == null) return;

        List<Float> actions = latestActions;

        for (int k = 0; k < 5; k++) {
            boolean pressed = actions.get(k) > 0.5f;
            KeyBinding.setKeyBindState(keybinds[k].getKeyCode(), pressed);
        }

        // Handle left and right clicks (indexes 5 and 6)
        boolean leftClick = actions.get(5) > 0.5f;
        boolean rightClick = actions.get(6) > 0.5f;

        KeyBindUtils.setKeyBindState(mc.gameSettings.keyBindAttack, leftClick);
        KeyBindUtils.setKeyBindState(mc.gameSettings.keyBindUseItem, rightClick);

        // Yaw (mouse X): softmax from index 7–28
        int yawStart = 7;
        int pitchStart = yawStart + mouseXBins.length;

        int yawIdx = 0;
        float yawMax = -1;
        for (int k = 0; k < mouseXBins.length; k++) {
            float val = actions.get(yawStart + k);
            if (val > yawMax) {
                yawMax = val;
                yawIdx = k;
            }
        }

        int pitchIdx = 0;
        float pitchMax = -1;
        for (int k = 0; k < mouseYBins.length; k++) {
            float val = actions.get(pitchStart + k);
            if (val > pitchMax) {
                pitchMax = val;
                pitchIdx = k;
            }
        }

        float yawDelta = mouseXBins[yawIdx];
        float pitchDelta = mouseYBins[pitchIdx];

        // Print descriptive action log
        System.out.printf("Actions: [W: %b, A: %b, S: %b, D: %b, Space: %b, LeftClick: %b, RightClick: %b, YawDelta: %.2f, PitchDelta: %.2f]%n",
                actions.get(1) > 0.5f, actions.get(0) > 0.5f, actions.get(2) > 0.5f, actions.get(3) > 0.5f, actions.get(4) > 0.5f,
                actions.get(5) > 0.5f, actions.get(6) > 0.5f, yawDelta, pitchDelta);

        mc.thePlayer.rotationYaw += yawDelta;
        mc.thePlayer.rotationPitch += pitchDelta;

        latestActions = null;
    }

    private float[][][] captureAndResizeScreen() {
        int fullW = mc.getFramebuffer().framebufferTextureWidth;
        int fullH = mc.getFramebuffer().framebufferTextureHeight;
        int targetW = frame_x;
        int targetH = frame_y;

        // Crop middle 70% of screen
        int cropX = fullW / 15; // 15% margin on left and right
        int cropY = fullH / 15; // 15% margin on top and bottom
        int cropW = fullW - 2 * cropX;
        int cropH = fullH - 2 * cropY;

        ByteBuffer buffer = BufferUtils.createByteBuffer(cropW * cropH * 4);
        GL11.glReadBuffer(GL11.GL_FRONT);
        GL11.glReadPixels(cropX, cropY, cropW, cropH, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buffer);
        buffer.rewind();

        float[][][] result = new float[targetH][targetW][3];
        int scaleX = cropW / targetW;
        int scaleY = cropH / targetH;

        for (int ty = 0; ty < targetH; ty++) {
            for (int tx = 0; tx < targetW; tx++) {
                int r = 0, g = 0, b = 0, count = 0;
                for (int sy = 0; sy < scaleY; sy++) {
                    for (int sx = 0; sx < scaleX; sx++) {
                        int x = tx * scaleX + sx;
                        int y = (cropH - 1) - (ty * scaleY + sy); // vertical flip
                        if (x < cropW && y < cropH) {
                            int i = (y * cropW + x) * 4;
                            b += buffer.get(i) & 0xFF;
                            g += buffer.get(i + 1) & 0xFF;
                            r += buffer.get(i + 2) & 0xFF;
                            count++;
                        }
                    }
                }
                result[ty][tx][0] = r / (float) (count * 255);
                result[ty][tx][1] = g / (float) (count * 255);
                result[ty][tx][2] = b / (float) (count * 255);
            }
        }

        return result;
    }

    private List<Float> sendToInferenceServer(float[][][][][] frames96) {
        try {
            URL url = new URL("http://localhost:8000/predict");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            Map<String, Object> payload = new HashMap<>();
            payload.put("frame", frames96);

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