package com.wiyuka.acceleratedpresents.client;

import com.wiyuka.acceleratedpresents.Acceleratedpresents;
import net.minecraft.world.entity.Display;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = Acceleratedpresents.MODID, value = Dist.CLIENT)
public final class ClientDisplayHandler {

    private ClientDisplayHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoin(final EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Display.TextDisplay textDisplay) {
            SolidTextDisplayManager.INSTANCE.track(textDisplay);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(final EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Display.TextDisplay textDisplay) {
            SolidTextDisplayManager.INSTANCE.untrack(textDisplay);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            SolidTextDisplayManager.INSTANCE.clear();
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(final RenderLevelStageEvent.AfterTranslucentBlocks event) {
        SolidTextDisplayManager.INSTANCE.render(event.getPoseStack());
    }
}
