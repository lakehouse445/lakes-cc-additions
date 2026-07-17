package gg.lakehouse.scanner.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gg.lakehouse.scanner.PenItem;
import gg.lakehouse.scanner.ScannerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First-person pen rendering. Vanilla draws only the held item, so the pen's
 * pencil-grip pose floats in midair with no hand attached. We cancel the
 * vanilla pass and draw both:
 *
 * - the player's bare arm, using the same transform chain as
 *   ItemInHandRenderer.renderPlayerArm (the empty-hand pose). Going through
 *   PlayerRenderer.renderRightHand/-LeftHand picks up the player's skin and
 *   slim/wide arm variant for free.
 * - the pen itself, using vanilla's untouched item transform chain
 *   (swing translate + applyItemArmTransform + applyItemArmAttackTransform),
 *   so the grip tuned in pen.json's firstperson display entries applies
 *   unchanged and equip/swing animate exactly as before.
 *
 * The arm is leaned slightly inward (ARM_LEAN) so the fist reads as wrapped
 * around the pen's lower end rather than hanging beside it.
 */
@Mod.EventBusSubscriber(modid = ScannerRegistry.MOD_ID, value = Dist.CLIENT)
public final class PenHandRenderer {
    private static final float PI = (float) Math.PI;

    /** Degrees the arm is rolled toward the screen centre to meet the pen. */
    private static final float ARM_LEAN = 10f;

    /**
     * View-space nudge that seats the pen in the fist, applied before the
     * grip rotations so it moves along plain screen axes, not the pen's tilt.
     * PEN_RIGHT is towards the arm (mirrored for lefties), PEN_FORWARD is
     * into the screen.
     */
    private static final float PEN_RIGHT = 0.06f;
    private static final float PEN_FORWARD = 0.10f;

    /** View-space pull of the whole arm in towards the camera, so more of
     *  it sits out of frame and it takes up less of the screen. */
    private static final float ARM_BACK = 0.15f;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof PenItem)) return;

        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null) return;

        event.setCanceled(true);

        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND
            ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean right = arm != HumanoidArm.LEFT;
        float side = right ? 1f : -1f;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float equip = event.getEquipProgress();
        float swing = event.getSwingProgress();

        pose.pushPose();

        if (!player.isInvisible()) {
            pose.pushPose();
            pose.translate(0f, 0f, ARM_BACK);
            pose.mulPose(Axis.ZP.rotationDegrees(side * ARM_LEAN));
            renderArm(pose, buffers, light, equip, swing, side, right, mc, player);
            pose.popPose();
        }

        // Vanilla's item chain, verbatim, so pen.json's grip pose is unchanged.
        float swingSqrt = Mth.sqrt(swing);
        pose.translate(
            side * -0.4f * Mth.sin(swingSqrt * PI),
            0.2f * Mth.sin(swingSqrt * (PI * 2f)),
            -0.2f * Mth.sin(swing * PI));
        pose.translate(side * 0.56f, -0.52f + equip * -0.6f, -0.72f);
        pose.translate(side * PEN_RIGHT, 0f, -PEN_FORWARD);
        float attack = Mth.sin(swing * swing * PI);
        pose.mulPose(Axis.YP.rotationDegrees(side * (45f + attack * -20f)));
        float attackSqrt = Mth.sin(swingSqrt * PI);
        pose.mulPose(Axis.ZP.rotationDegrees(side * attackSqrt * -20f));
        pose.mulPose(Axis.XP.rotationDegrees(attackSqrt * -80f));
        pose.mulPose(Axis.YP.rotationDegrees(side * -45f));

        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
            player, stack,
            right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                  : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            !right, pose, buffers, light);

        pose.popPose();
    }

    /** ItemInHandRenderer.renderPlayerArm's transform chain (it's private). */
    private static void renderArm(PoseStack pose, MultiBufferSource buffers, int light,
                                  float equip, float swing, float side, boolean right,
                                  Minecraft mc, AbstractClientPlayer player) {
        float swingSqrt = Mth.sqrt(swing);
        float x = -0.3f * Mth.sin(swingSqrt * PI);
        float y = 0.4f * Mth.sin(swingSqrt * (PI * 2f));
        float z = -0.4f * Mth.sin(swing * PI);
        pose.translate(side * (x + 0.64000005f), y + -0.6f + equip * -0.6f, z + -0.71999997f);
        pose.mulPose(Axis.YP.rotationDegrees(side * 45f));
        float swingSin = Mth.sin(swing * swing * PI);
        float swingSqrtSin = Mth.sin(swingSqrt * PI);
        pose.mulPose(Axis.YP.rotationDegrees(side * swingSqrtSin * 70f));
        pose.mulPose(Axis.ZP.rotationDegrees(side * swingSin * -20f));
        pose.translate(side * -1f, 3.6f, 3.5f);
        pose.mulPose(Axis.ZP.rotationDegrees(side * 120f));
        pose.mulPose(Axis.XP.rotationDegrees(200f));
        pose.mulPose(Axis.YP.rotationDegrees(side * -135f));
        pose.translate(side * 5.6f, 0f, 0f);

        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        if (right) {
            renderer.renderRightHand(pose, buffers, light, player);
        } else {
            renderer.renderLeftHand(pose, buffers, light, player);
        }
    }

    private PenHandRenderer() {}
}
