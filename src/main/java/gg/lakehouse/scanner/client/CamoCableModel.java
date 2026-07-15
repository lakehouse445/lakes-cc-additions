package gg.lakehouse.scanner.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.lakehouse.scanner.CamoCableBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.ChunkRenderTypeSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * When the cable is disguised, delegate every quad to the camouflage
 * block's real model, so the disguise is genuine terrain geometry --
 * identical under shaders, correct light occlusion. Otherwise draw the
 * cable's own model.
 */
public class CamoCableModel implements IDynamicBakedModel {
    private final BakedModel base;

    public CamoCableModel(BakedModel base) {
        this.base = base;
    }

    private BakedModel modelFor(@Nullable BlockState camo) {
        if (camo == null) return base;
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(camo);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                             @NotNull RandomSource rand, @NotNull ModelData data,
                                             @Nullable net.minecraft.client.renderer.RenderType renderType) {
        BlockState camo = data.get(CamoModelData.CAMO);
        if (camo == null) {
            return renderType == null || renderType == net.minecraft.client.renderer.RenderType.solid()
                ? base.getQuads(state, side, rand) : List.of();
        }
        BakedModel model = modelFor(camo);
        return model.getQuads(camo, side, rand, data, renderType);
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state,
                                                      @NotNull RandomSource rand,
                                                      @NotNull ModelData data) {
        BlockState camo = data.get(CamoModelData.CAMO);
        if (camo == null) return net.minecraftforge.client.ChunkRenderTypeSet.of(
            net.minecraft.client.renderer.RenderType.solid());
        return net.minecraftforge.client.model.data.ModelData.EMPTY == data
            ? IDynamicBakedModel.super.getRenderTypes(state, rand, data)
            : modelFor(camo).getRenderTypes(camo, rand, data);
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        BlockState camo = data.get(CamoModelData.CAMO);
        return camo == null ? base.getParticleIcon()
            : modelFor(camo).getParticleIcon(ModelData.EMPTY);
    }

    // Non-camo item render (creative tab / inventory) uses the base model.
    @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext ctx,
            PoseStack pose, boolean leftHand) {
        return base.applyTransform(ctx, pose, leftHand);
    }
    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public @NotNull TextureAtlasSprite getParticleIcon() { return base.getParticleIcon(); }
    @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}
