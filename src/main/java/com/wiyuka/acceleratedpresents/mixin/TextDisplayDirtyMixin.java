package com.wiyuka.acceleratedpresents.mixin;

import com.wiyuka.acceleratedpresents.client.SolidTextDisplayHolder;
import com.wiyuka.acceleratedpresents.client.SolidTextDisplayManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Invalidates the cached geometry held by {@link SolidTextDisplayManager} whenever a TextDisplay's synced data
 * changes (text, line width, background color, flags, transformation, ...), so the manager re-extracts only on
 * change instead of every frame.
 *
 * <p>Also implements {@link SolidTextDisplayHolder} so the manager can store the cache entry directly on the
 * entity instead of keeping an id-keyed map.
 */
@Mixin(Display.TextDisplay.class)
public abstract class TextDisplayDirtyMixin implements SolidTextDisplayHolder {

    @Unique
    private Object acceleratedpresents$cached;

    @Override
    public Object acceleratedpresents$getCached() {
        return this.acceleratedpresents$cached;
    }

    @Override
    public void acceleratedpresents$setCached(Object cached) {
        this.acceleratedpresents$cached = cached;
    }

    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("TAIL"))
    private void acceleratedpresents$markDirty(EntityDataAccessor<?> key, CallbackInfo ci) {
        Display.TextDisplay self = (Display.TextDisplay) (Object) this;
        if (self.level().isClientSide()) {
            SolidTextDisplayManager.INSTANCE.markDirty(self);
        }
    }
}
