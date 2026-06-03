package com.wiyuka.acceleratedpresents.mixin;

import com.wiyuka.acceleratedpresents.client.SolidTextDisplayManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(
            method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void acceleratedpresents$skipSolidBackgroundTextDisplay(
            Entity entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        SolidTextDisplayManager.INSTANCE.captureFrustum(frustum);
        if (entity instanceof Display.TextDisplay textDisplay && SolidTextDisplayManager.isSolidBackground(textDisplay)) {
            cir.setReturnValue(false);
        }
    }
}
