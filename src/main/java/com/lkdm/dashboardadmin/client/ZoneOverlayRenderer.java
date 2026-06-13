package com.lkdm.dashboardadmin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dessine en monde les arêtes des zones du {@link ClientZoneCache}
 * (wireframe façon structure block) ainsi que la sélection baguette en cours.
 *
 * <p>Affichage actif si le toggle de l'onglet Build est ON ou si le joueur
 * tient la Baguette de Zone en main principale.</p>
 *
 * <p>Couleurs : vert = zone active, gris = désactivée, jaune = sélection baguette.</p>
 */
public final class ZoneOverlayRenderer {

    /** Distance max (blocs) au-delà de laquelle une zone n'est plus dessinée. */
    private static final double MAX_DIST_SQ = 128 * 128;

    private ZoneOverlayRenderer() {}

    /** À appeler depuis DashboardAdminClient.initClient (même pattern que GroupHud/NotifHud). */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ZoneOverlayRenderer::onRenderLevel);
        // Purge le cache à la déconnexion (zones d'un autre serveur).
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientZoneCache.clear());
    }

    private static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        boolean wandInHand = isZoneWand(player.getMainHandItem());
        if (!ClientZoneCache.overlayEnabled && !wandInHand) return;

        boolean hasZones = !ClientZoneCache.all().isEmpty();
        boolean hasSelection = ClientZoneCache.wandA != null || ClientZoneCache.wandB != null;
        if (!hasZones && !hasSelection) return;

        Vec3 cam = evt.getCamera().getPosition();
        PoseStack pose = evt.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        // ─── Zones synchronisées ───────────────────────────────────────────────
        BlockPos playerPos = player.blockPosition();
        for (ClientZoneCache.ClientZone z : ClientZoneCache.all()) {
            if (z.center().distSqr(playerPos) > MAX_DIST_SQ) continue;
            // +1 sur max : Zone.contains() inclut le bloc max entier.
            AABB box = new AABB(z.minX(), z.minY(), z.minZ(),
                                z.maxX() + 1, z.maxY() + 1, z.maxZ() + 1);
            if (z.enabled()) {
                int c = z.color();
                LevelRenderer.renderLineBox(pose, lines, box,
                    ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 0.90f);
            } else {
                LevelRenderer.renderLineBox(pose, lines, box, 0.55f, 0.55f, 0.55f, 0.70f);
            }
            renderZoneName(mc, pose, buffers, evt, z);
        }

        // ─── Sélection baguette en cours (jaune) ───────────────────────────────
        BlockPos a = ClientZoneCache.wandA;
        BlockPos b = ClientZoneCache.wandB;
        if (a != null && b != null) {
            AABB sel = new AABB(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
            LevelRenderer.renderLineBox(pose, lines, sel, 1.00f, 0.90f, 0.20f, 1.00f);
        } else if (a != null) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(a), 1.00f, 0.90f, 0.20f, 1.00f);
        } else if (b != null) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(b), 0.40f, 0.80f, 1.00f, 1.00f);
        }

        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    /** Nom de la zone en billboard au centre de la boîte (taille proportionnelle à la distance). */
    private static void renderZoneName(Minecraft mc, PoseStack pose, MultiBufferSource buffers,
                                       RenderLevelStageEvent evt, ClientZoneCache.ClientZone z) {
        double nx = (z.minX() + z.maxX() + 1) / 2.0;
        double ny = (z.minY() + z.maxY() + 1) / 2.0;
        double nz = (z.minZ() + z.maxZ() + 1) / 2.0;
        Vec3 cam = evt.getCamera().getPosition();
        double dist = Math.sqrt(cam.distanceToSqr(nx, ny, nz));
        if (dist < 2) return; // trop près : le texte gênerait
        float scale = 0.025f * (float) Math.max(1.0, dist / 12.0);

        pose.pushPose();
        pose.translate(nx, ny, nz);
        pose.mulPose(evt.getCamera().rotation());
        pose.scale(scale, -scale, scale);
        net.minecraft.client.gui.Font font = mc.font;
        String label = z.name();
        int color = 0xFF000000 | (z.enabled() ? z.color() : 0x8C8C8C);
        font.drawInBatch(label, -font.width(label) / 2f, -4f, color, false,
            pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
            0x55000000, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** Réplique client de ZoneCommand.isZoneWand (blaze rod nommée "Baguette de Zone"). */
    private static boolean isZoneWand(ItemStack stack) {
        if (stack.getItem() != Items.BLAZE_ROD) return false;
        Component name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return name != null && name.getString().contains("Baguette de Zone");
    }
}
