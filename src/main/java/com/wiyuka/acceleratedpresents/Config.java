package com.wiyuka.acceleratedpresents;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = Acceleratedpresents.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue BLOCK_SOLID_BACKGROUND_TEXT_DISPLAYS = BUILDER
            .comment("Client only. When enabled, the vanilla rendering of TextDisplay entities that have a solid (visible) background is skipped")
            .define("blockSolidBackgroundTextDisplays", true);

    private static final ModConfigSpec.IntValue MIN_BACKGROUND_ALPHA = BUILDER
            .comment("Minimum background alpha (0-255) a TextDisplay must have for it to count as a 'solid background' and be skipped. 1 = any visible background.")
            .defineInRange("minBackgroundAlpha", 1, 0, 255);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static volatile boolean blockSolidBackgroundTextDisplays;
    public static volatile int minBackgroundAlpha;

    public static void setBlockEnabled(final boolean value) {
        blockSolidBackgroundTextDisplays = value;
        BLOCK_SOLID_BACKGROUND_TEXT_DISPLAYS.set(value);
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        blockSolidBackgroundTextDisplays = BLOCK_SOLID_BACKGROUND_TEXT_DISPLAYS.get();
        minBackgroundAlpha = MIN_BACKGROUND_ALPHA.get();
    }
}
