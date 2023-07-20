package com.jelly.NeuralTrainer;


import com.jelly.NeuralTrainer.data.MovementDataCollector;
import com.jelly.NeuralTrainer.utils.KeyBindUtils;
import com.jelly.NeuralTrainer.utils.LogUtils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = NeuralTrainer.MODID, version = NeuralTrainer.VERSION)
public class NeuralTrainer
{
    public static final String MODID = "neuraltrainer";
    public static final String VERSION = "v1.0";
    public static MovementDataCollector movementDataCollector = new MovementDataCollector();
    @Mod.EventHandler
    public void init(FMLInitializationEvent event)
    {
        KeyBindUtils.init();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(movementDataCollector);
    }

    @SubscribeEvent
    public void onKeyPress(InputEvent.KeyInputEvent ev) {
        KeyBindUtils.onKeyPress();
    }


}
