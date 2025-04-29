package com.jelly.NeuralTrainer;


import com.jelly.NeuralTrainer.data.PlaybackDataCollector;
import com.jelly.NeuralTrainer.data.ScreenDataCollector;
import com.jelly.NeuralTrainer.run.PlaybackHelper;
import com.jelly.NeuralTrainer.utils.KeyBindUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

@Mod(modid = NeuralTrainer.MODID, version = NeuralTrainer.VERSION)
public class NeuralTrainer
{
    public static final String MODID = "neuraltrainer";
    public static final String VERSION = "v1.0";

    public static PlaybackDataCollector playbackDataCollector = new PlaybackDataCollector();
    public static PlaybackHelper playbackHelper = new PlaybackHelper();
    public static ScreenDataCollector screenDataCollector = new ScreenDataCollector();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event)
    {
        KeyBindUtils.init();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(playbackDataCollector);
        MinecraftForge.EVENT_BUS.register(playbackHelper);
        MinecraftForge.EVENT_BUS.register(screenDataCollector);
    }

    @SubscribeEvent
    public void onKeyPress(InputEvent.KeyInputEvent ev) {
        KeyBindUtils.onKeyPress();
    }


}
