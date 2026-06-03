package com.wiyuka.acceleratedpresents.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wiyuka.acceleratedpresents.Config;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SolidTextDisplayManager {
    public static final SolidTextDisplayManager INSTANCE = new SolidTextDisplayManager();

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final int LIGHT_REFRESH_FRAMES = 10;
    private static final double MOVE_EPSILON = 1.0E-4;

    private Cached[] tracked = new Cached[64];
    private int trackedCount;

    private final Quaternionf scratchQuat = new Quaternionf();
    private final Matrix4f scratchMat = new Matrix4f();
    private final Vector3f scratchVec = new Vector3f();

    private final List<Cached> opaqueBatch = new ArrayList<>();
    private final List<Cached> seeThroughBatch = new ArrayList<>();

    private Frustum lastFrustum;

    private SolidTextDisplayManager() {
    }

    public void captureFrustum(Frustum frustum) {
        this.lastFrustum = frustum;
    }

    public void track(Display.TextDisplay entity) {
        SolidTextDisplayHolder holder = (SolidTextDisplayHolder) entity;
        if (holder.acceleratedpresents$getCached() != null) {
            return;
        }
        if (trackedCount == tracked.length) {
            tracked = Arrays.copyOf(tracked, trackedCount * 2);
        }
        Cached cached = new Cached(entity);
        cached.index = trackedCount;
        tracked[trackedCount++] = cached;
        holder.acceleratedpresents$setCached(cached);
    }

    public void untrack(Display.TextDisplay entity) {
        SolidTextDisplayHolder holder = (SolidTextDisplayHolder) entity;
        Object handle = holder.acceleratedpresents$getCached();
        if (handle == null) {
            return;
        }
        Cached cached = (Cached) handle;
        int removed = cached.index;
        int last = --trackedCount;
        Cached moved = tracked[last];
        tracked[removed] = moved;
        moved.index = removed;
        tracked[last] = null;
        holder.acceleratedpresents$setCached(null);
    }

    public void markDirty(Display.TextDisplay entity) {
        Object handle = ((SolidTextDisplayHolder) entity).acceleratedpresents$getCached();
        if (handle != null) {
            Cached cached = (Cached) handle;
            cached.geometryDirty = true;
            cached.textDirty = true;
        }
    }

    public void clear() {
        for (int i = 0; i < trackedCount; i++) {
            ((SolidTextDisplayHolder) tracked[i].entity).acceleratedpresents$setCached(null);
            tracked[i] = null;
        }
        trackedCount = 0;
    }

    public static boolean isSolidBackground(Display.TextDisplay entity) {
        if (!Config.blockSolidBackgroundTextDisplays) {
            return false;
        }
        
        Cached cached = (Cached) ((SolidTextDisplayHolder) entity).acceleratedpresents$getCached();
        boolean isSingleSpace;
        if (cached != null) {
            if (cached.textDirty) {
                cached.isSingleSpace = " ".equals(entity.getText().getString());
                cached.textDirty = false;
            }
            isSingleSpace = cached.isSingleSpace;
        } else {
            isSingleSpace = " ".equals(entity.getText().getString());
        }
        
        if (!isSingleSpace) {
            return false;
        }
        
        int backgroundColor;
        byte flags = entity.getFlags();
        if ((flags & Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND) != 0) {
            float opacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
            backgroundColor = (int) (opacity * 255.0F) << 24;
        } else {
            backgroundColor = entity.getBackgroundColor();
        }
        return ((backgroundColor >>> 24) & 0xFF) >= Config.minBackgroundAlpha;
    }

    public void render(PoseStack poseStack) {
        if (trackedCount == 0 || !Config.blockSolidBackgroundTextDisplays) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camPos = camera.position();
        float cameraXRot = camera.xRot();
        float cameraYRot = camera.yRot();
        Font font = minecraft.font;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Frustum frustum = this.lastFrustum;

        opaqueBatch.clear();
        seeThroughBatch.clear();
        for (int i = 0; i < trackedCount; i++) {
            Cached cached = tracked[i];
            if (prepare(cached, minecraft, font, partialTick, camPos, frustum)) {
                (cached.seeThrough ? seeThroughBatch : opaqueBatch).add(cached);
            }
        }

        if (!opaqueBatch.isEmpty()) {
            VertexConsumer consumer = buffers.getBuffer(RenderTypes.textBackground());
            for (Cached cached : opaqueBatch) {
                emit(cached, consumer, poseStack, camPos, cameraXRot, cameraYRot, partialTick);
            }
        }
        if (!seeThroughBatch.isEmpty()) {
            VertexConsumer consumer = buffers.getBuffer(RenderTypes.textBackgroundSeeThrough());
            for (Cached cached : seeThroughBatch) {
                emit(cached, consumer, poseStack, camPos, cameraXRot, cameraYRot, partialTick);
            }
        }

        buffers.endBatch(RenderTypes.textBackground());
        buffers.endBatch(RenderTypes.textBackgroundSeeThrough());
    }

    private boolean prepare(Cached cached, Minecraft minecraft, Font font, float partialTick, Vec3 camPos, Frustum frustum) {
        Display.TextDisplay entity = cached.entity;
        if (entity.isRemoved() || !isSolidBackground(entity)) {
            return false;
        }

        double dx = entity.getX() - camPos.x;
        double dy = entity.getY() - camPos.y;
        double dz = entity.getZ() - camPos.z;
        if (!entity.shouldRenderAtSqrDistance(dx * dx + dy * dy + dz * dz)) {
            return false;
        }
        if (frustum != null && entity.affectedByCulling() && !frustum.isVisible(entity.getBoundingBoxForCulling().inflate(0.5))) {
            return false;
        }

        Display.RenderState renderState = entity.renderState();
        Display.TextDisplay.TextRenderState textRenderState = entity.textRenderState();
        if (renderState == null || textRenderState == null) {
            return false;
        }

        if (cached.needsRebuild(renderState)) {
            cached.geometryDirty = true;
        }
        if (cached.geometryDirty) {
            cached.rebuild(minecraft, font, renderState, textRenderState, scratchQuat, scratchMat, scratchVec);
        }

        if (cached.color == 0) {
            return false;
        }

        cached.frameRenderState = renderState;
        cached.frameLight = cached.resolveLight(renderState, partialTick);
        cached.interpolating = cached.fixed && isAnimating(entity, partialTick);

        return true;
    }
    private static boolean isAnimating(Display.TextDisplay entity, float partialTick) {
        return entity.calculateInterpolationProgress(partialTick) < 1.0F
                || entity.getYRot(0.0F) != entity.getYRot(1.0F)
                || entity.getXRot(0.0F) != entity.getXRot(1.0F);
    }
    private void emit(Cached cached, VertexConsumer consumer, PoseStack poseStack, Vec3 camPos,
                      float cameraXRot, float cameraYRot, float partialTick) {
        if (cached.fixed && !cached.interpolating) {
            emitCachedQuad(cached, consumer, camPos);
        } else {
            emitDynamicQuad(cached, consumer, poseStack, camPos, cameraXRot, cameraYRot, partialTick);
        }
    }

    private void emitCachedQuad(Cached cached, VertexConsumer consumer, Vec3 camPos) {
        double ox = cached.posX - camPos.x;
        double oy = cached.posY - camPos.y;
        double oz = cached.posZ - camPos.z;
        float[] q = cached.localQuad;
        for (int i = 0; i < 4; i++) {
            consumer.addVertex((float) (ox + q[i * 3]), (float) (oy + q[i * 3 + 1]), (float) (oz + q[i * 3 + 2]))
                    .setColor(cached.color)
                    .setLight(cached.frameLight);
        }
    }

    private void emitDynamicQuad(Cached cached, VertexConsumer consumer, PoseStack poseStack, Vec3 camPos,
                                 float cameraXRot, float cameraYRot, float partialTick) {
        Display.TextDisplay entity = cached.entity;
        Display.RenderState renderState = cached.frameRenderState;
        float progress = entity.calculateInterpolationProgress(partialTick);

        poseStack.pushPose();
        poseStack.translate(entity.getX() - camPos.x, entity.getY() - camPos.y, entity.getZ() - camPos.z);
        applyOrientation(renderState.billboardConstraints(), entity.getYRot(partialTick), entity.getXRot(partialTick),
                cameraXRot, cameraYRot, scratchQuat);
        poseStack.mulPose(scratchQuat);
        poseStack.mulPose(renderState.transformation().get(progress).getMatrix());

        Matrix4f matrix = poseStack.last().pose();
        matrix.rotate((float) Math.PI, 0.0F, 1.0F, 0.0F);
        matrix.scale(-0.025F, -0.025F, -0.025F);
        int width = cached.backgroundWidth;
        int height = cached.backgroundHeight;
        matrix.translate(1.0F - width / 2.0F, -height, 0.0F);

        consumer.addVertex(matrix, -1.0F, -1.0F, 0.0F).setColor(cached.color).setLight(cached.frameLight);
        consumer.addVertex(matrix, -1.0F, (float) height, 0.0F).setColor(cached.color).setLight(cached.frameLight);
        consumer.addVertex(matrix, (float) width, (float) height, 0.0F).setColor(cached.color).setLight(cached.frameLight);
        consumer.addVertex(matrix, (float) width, -1.0F, 0.0F).setColor(cached.color).setLight(cached.frameLight);

        poseStack.popPose();
    }

    private static void applyOrientation(Display.BillboardConstraints billboard, float entityYRot, float entityXRot,
                                         float cameraXRot, float cameraYRot, Quaternionf out) {
        switch (billboard) {
            case FIXED -> out.rotationYXZ(-DEG_TO_RAD * entityYRot, DEG_TO_RAD * entityXRot, 0.0F);
            case HORIZONTAL -> out.rotationYXZ(-DEG_TO_RAD * entityYRot, DEG_TO_RAD * -cameraXRot, 0.0F);
            case VERTICAL -> out.rotationYXZ(-DEG_TO_RAD * (cameraYRot - 180.0F), DEG_TO_RAD * entityXRot, 0.0F);
            case CENTER -> out.rotationYXZ(-DEG_TO_RAD * (cameraYRot - 180.0F), DEG_TO_RAD * -cameraXRot, 0.0F);
        }
    }

    private static final class Cached {
        private final Display.TextDisplay entity;
        private int index;
        private boolean geometryDirty = true;
        private boolean textDirty = true;
        private boolean isSingleSpace;

        private int backgroundWidth;
        private int backgroundHeight;
        private int color;
        private boolean seeThrough;
        private boolean fixed;

        private final float[] localQuad = new float[12];
        private double posX;
        private double posY;
        private double posZ;

        private float bakedYRot;
        private float bakedXRot;
        private Display.RenderState bakedRenderState;

        private int cachedLight;
        private int lightCooldown;

        private int frameLight;
        private boolean interpolating;
        private Display.RenderState frameRenderState;

        private Cached(Display.TextDisplay entity) {
            this.entity = entity;
        }

        private boolean needsRebuild(Display.RenderState renderState) {
            if (renderState != bakedRenderState) {
                return true;
            }
            if (!fixed) {
                return false;
            }
            return Math.abs(entity.getX() - posX) > MOVE_EPSILON
                    || Math.abs(entity.getY() - posY) > MOVE_EPSILON
                    || Math.abs(entity.getZ() - posZ) > MOVE_EPSILON
                    || entity.getYRot(1.0F) != bakedYRot
                    || entity.getXRot(1.0F) != bakedXRot;
        }

        private void rebuild(
                Minecraft minecraft,
                Font font,
                Display.RenderState renderState,
                Display.TextDisplay.TextRenderState textRenderState,
                Quaternionf tmpQuat,
                Matrix4f tmpMat,
                Vector3f tmpVec
        ) {
            List<FormattedCharSequence> lines = font.split(textRenderState.text(), textRenderState.lineWidth());
            int maxWidth = 0;
            for (FormattedCharSequence line : lines) {
                maxWidth = Math.max(maxWidth, font.width(line));
            }
            int lineHeight = font.lineHeight + 1;
            this.backgroundWidth = maxWidth;
            this.backgroundHeight = Math.max(0, lines.size() * lineHeight - 1);

            byte flags = textRenderState.flags();
            this.seeThrough = (flags & Display.TextDisplay.FLAG_SEE_THROUGH) != 0;
            if ((flags & Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND) != 0) {
                this.color = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
            } else {
                this.color = textRenderState.backgroundColor().get(1.0F);
            }

            this.posX = entity.getX();
            this.posY = entity.getY();
            this.posZ = entity.getZ();
            this.bakedYRot = entity.getYRot(1.0F);
            this.bakedXRot = entity.getXRot(1.0F);
            this.bakedRenderState = renderState;
            this.fixed = renderState.billboardConstraints() == Display.BillboardConstraints.FIXED;
            this.lightCooldown = 0;

            if (this.fixed && this.color != 0) {
                applyOrientation(Display.BillboardConstraints.FIXED, entity.getYRot(1.0F), entity.getXRot(1.0F),
                        0.0F, 0.0F, tmpQuat);
                tmpMat.identity();
                tmpMat.rotate(tmpQuat);
                tmpMat.mul(renderState.transformation().get(1.0F).getMatrix());
                tmpMat.rotate((float) Math.PI, 0.0F, 1.0F, 0.0F);
                tmpMat.scale(-0.025F, -0.025F, -0.025F);
                tmpMat.translate(1.0F - this.backgroundWidth / 2.0F, -this.backgroundHeight, 0.0F);

                bakeCorner(tmpMat, tmpVec, -1.0F, -1.0F, 0);
                bakeCorner(tmpMat, tmpVec, -1.0F, this.backgroundHeight, 1);
                bakeCorner(tmpMat, tmpVec, this.backgroundWidth, this.backgroundHeight, 2);
                bakeCorner(tmpMat, tmpVec, this.backgroundWidth, -1.0F, 3);
            }

            this.geometryDirty = false;
        }

        private void bakeCorner(Matrix4f matrix, Vector3f tmp, float localX, float localY, int index) {
            matrix.transformPosition(localX, localY, 0.0F, tmp);
            this.localQuad[index * 3] = tmp.x;
            this.localQuad[index * 3 + 1] = tmp.y;
            this.localQuad[index * 3 + 2] = tmp.z;
        }

        private int resolveLight(Display.RenderState renderState, float partialTick) {
            int override = renderState.brightnessOverride();
            if (override != -1) {
                return LightTexture.pack(LightTexture.block(override), LightTexture.sky(override));
            }
            if (this.lightCooldown > 0) {
                this.lightCooldown--;
                return this.cachedLight;
            }
            Level level = entity.level();
            BlockPos pos = BlockPos.containing(entity.getLightProbePosition(partialTick));
            int block = level.getBrightness(LightLayer.BLOCK, pos);
            int sky = level.getBrightness(LightLayer.SKY, pos);
            this.cachedLight = LightTexture.pack(block, sky);
            this.lightCooldown = LIGHT_REFRESH_FRAMES;
            return this.cachedLight;
        }
    }
}
