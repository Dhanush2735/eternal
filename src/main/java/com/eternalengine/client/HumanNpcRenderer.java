package com.eternalengine.client;

import com.eternalengine.entity.HumanNpcEntity;
import com.eternalengine.entity.SkinMatrixRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side renderer for HumanNpcEntity and subclasses using standard Minecraft PlayerModel.
 */
public class HumanNpcRenderer<T extends HumanNpcEntity> extends MobRenderer<T, PlayerModel<T>> {

    public HumanNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
    }

    /**
     * The pool's painted skin first, then whatever the pack ships.
     *
     * <p>A person is painted from his own UUID, his place and the year he holds, so two people are two
     * people and a returning one is the same one. If nothing can be painted (no client texture manager,
     * an unreadable position), the registry's texture - a real file if one ships, a default skin
     * otherwise - is what was always there.
     */
    @Override
    public ResourceLocation getTextureLocation(T entity) {
        ResourceLocation painted = SkinTextures.of(entity);
        return painted != null ? painted : SkinMatrixRegistry.getTextureLocation(entity);
    }

    @Override
    protected void renderNameTag(T entity, Component displayName, PoseStack poseStack,
                                 MultiBufferSource buffer, int packedLight) {
        if (entity.isSpeaking()) {
            Component speechComponent = Component.literal("💬 " + entity.getSpeechText());
            poseStack.pushPose();
            poseStack.translate(0.0D, 0.3D, 0.0D);
            super.renderNameTag(entity, speechComponent, poseStack, buffer, packedLight);
            poseStack.popPose();
        }
        super.renderNameTag(entity, displayName, poseStack, buffer, packedLight);
    }
}
