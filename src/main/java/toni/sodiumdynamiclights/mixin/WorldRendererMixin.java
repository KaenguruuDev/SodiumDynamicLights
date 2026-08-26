package toni.sodiumdynamiclights.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import toni.sodiumdynamiclights.SodiumDynamicLights;

import com.mojang.blaze3d.vertex.PoseStack;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))

    private void beforeRender(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        SodiumDynamicLights.get().updateAll((LevelRenderer) (Object) this);
    }
}
