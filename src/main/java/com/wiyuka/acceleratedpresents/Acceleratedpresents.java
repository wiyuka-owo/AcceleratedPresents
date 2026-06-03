package com.wiyuka.acceleratedpresents;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Acceleratedpresents.MODID)
public class Acceleratedpresents {
    public static final String MODID = "acceleratedpresents";

    public Acceleratedpresents(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
