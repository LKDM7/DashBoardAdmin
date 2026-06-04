package Fabric.test.client;

import Fabric.test.networking.ReportSubmitPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class ReportScreen extends Screen {

    // Taille du thumbnail affiché dans le GUI (juste pour l'aperçu)
    private static final int PREVIEW_W = 240;
    private static final int PREVIEW_H = 135;
    // Résolution max envoyée au serveur / Discord (bonne qualité)
    private static final int MAX_SEND_W = 1280;
    private static final int MAX_SEND_H = 720;

    private static final int POP_W = 264;
    private static final int POP_H = 240;

    private static final int C_ACCENT = 0xFF00E5FF;
    private static final int C_BG     = 0xFF1A1A1A;
    private static final int C_PANEL  = 0xFF0C0C0C;

    private EditBox          messageBox;
    private byte[]           sendImageBytes = new byte[0];  // pleine résolution → serveur/Discord
    private DynamicTexture   previewTex     = null;         // aperçu 240×135 → GUI uniquement
    private ResourceLocation previewTexLoc  = null;
    private boolean          hasImage       = false;
    private String           hintText       = "§8Ctrl+V pour coller le dernier screenshot (F2)";

    public ReportScreen() {
        super(Component.literal("Envoyer un signalement"));
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int bx = (width - POP_W) / 2;
        int by = (height - POP_H) / 2;

        messageBox = new EditBox(font, bx + 10, by + 163, POP_W - 20, 18,
            Component.literal(""));
        messageBox.setMaxLength(256);
        messageBox.setHint(Component.literal("§8Décrivez le problème..."));
        addRenderableWidget(messageBox);
        setFocused(messageBox);

        if (hasImage) {
            addRenderableWidget(Button.builder(
                Component.literal("§7Retirer l'image"), b -> removeImage()
            ).bounds(bx + 10, by + 190, 130, 16).build());
        }

        addRenderableWidget(Button.builder(Component.literal("§cAnnuler"), b -> onClose())
            .bounds(bx + POP_W - 80, by + 190, 70, 16).build());

        addRenderableWidget(Button.builder(
            Component.literal("§aEnvoyer"), b -> submit()
        ).bounds(bx + 10, by + 212, POP_W - 20, 20).build());
    }

    // ── Ctrl+V ────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_V && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (tryPasteFromClipboard()) return true;
            if (tryLoadLastScreenshot())  return true;
            hintText = "§cAucune image — prenez un screenshot F2 puis Ctrl+V";
            init();
            return true;
        }
        return super.keyPressed(event);
    }

    // ── Presse-papiers AWT ────────────────────────────────────────────────────

    private boolean tryPasteFromClipboard() {
        try {
            if (java.awt.GraphicsEnvironment.isHeadless()) return false;

            java.awt.datatransfer.Clipboard cb =
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            java.awt.datatransfer.Transferable t = cb.getContents(null);
            if (t == null) return false;

            java.awt.image.BufferedImage bimg = null;

            if (t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)) {
                java.awt.Image awt = (java.awt.Image)
                    t.getTransferData(java.awt.datatransfer.DataFlavor.imageFlavor);
                if (awt != null && awt.getWidth(null) > 0) {
                    bimg = new java.awt.image.BufferedImage(awt.getWidth(null), awt.getHeight(null),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g2d = bimg.createGraphics();
                    g2d.drawImage(awt, 0, 0, null);
                    g2d.dispose();
                }
            }
            if (bimg == null) {
                for (java.awt.datatransfer.DataFlavor f : t.getTransferDataFlavors()) {
                    if (!"image".equals(f.getPrimaryType())) continue;
                    try {
                        Object data = t.getTransferData(f);
                        if (data instanceof java.io.InputStream is)
                            bimg = javax.imageio.ImageIO.read(is);
                        if (bimg != null && bimg.getWidth() > 0) break;
                        bimg = null;
                    } catch (Exception ignored) {}
                }
            }
            if (bimg == null) return false;

            return applyFromBufferedImage(bimg, "§a✔ Image collée depuis le presse-papiers");
        } catch (Exception ignored) {
            return false;
        }
    }

    // ── Dernier screenshot F2 ─────────────────────────────────────────────────

    private boolean tryLoadLastScreenshot() {
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
            if (!Files.isDirectory(dir)) return false;

            Path last;
            try (Stream<Path> s = Files.list(dir)) {
                last = s.filter(p -> p.toString().toLowerCase().endsWith(".png"))
                        .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                        .orElse(null);
            }
            if (last == null) return false;

            byte[] raw = Files.readAllBytes(last);
            NativeImage full = NativeImage.read(raw);
            int srcW = full.getWidth(), srcH = full.getHeight();

            // Image envoyée au serveur : pleine résolution si ≤ 1280×720, sinon redimensionnée
            if (srcW <= MAX_SEND_W && srcH <= MAX_SEND_H) {
                sendImageBytes = raw;
            } else {
                NativeImage send = new NativeImage(MAX_SEND_W, MAX_SEND_H, false);
                full.resizeSubRectTo(0, 0, srcW, srcH, send);
                Path tmp = Files.createTempFile("rpt_", ".png");
                send.writeToFile(tmp);
                sendImageBytes = Files.readAllBytes(tmp);
                Files.deleteIfExists(tmp);
                send.close();
            }

            // Aperçu GUI : toujours 240×135
            NativeImage preview = new NativeImage(PREVIEW_W, PREVIEW_H, false);
            full.resizeSubRectTo(0, 0, srcW, srcH, preview);
            full.close();

            applyPreviewTexture(preview);
            hintText  = "§a✔ Screenshot chargé";
            hasImage  = true;
            init();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Prend un BufferedImage AWT, produit :
     *  - sendImageBytes  : PNG à résolution max 1280×720
     *  - previewTexture  : NativeImage 240×135 pour le GUI
     */
    private boolean applyFromBufferedImage(java.awt.image.BufferedImage src, String successMsg) {
        try {
            int srcW = src.getWidth(), srcH = src.getHeight();

            // Calcule les dimensions d'envoi (conserver les proportions)
            int sendW = srcW, sendH = srcH;
            if (sendW > MAX_SEND_W || sendH > MAX_SEND_H) {
                double scale = Math.min((double) MAX_SEND_W / sendW, (double) MAX_SEND_H / sendH);
                sendW = Math.max(1, (int) (sendW * scale));
                sendH = Math.max(1, (int) (sendH * scale));
            }

            // Encode l'image d'envoi en PNG
            java.awt.image.BufferedImage sendBuf = scale(src, sendW, sendH);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(sendBuf, "PNG", baos);
            sendImageBytes = baos.toByteArray();

            // Crée l'aperçu 240×135 via PNG → NativeImage.read (plus simple qu'une boucle pixel)
            java.awt.image.BufferedImage previewBuf = scale(src, PREVIEW_W, PREVIEW_H);
            ByteArrayOutputStream pBaos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(previewBuf, "PNG", pBaos);
            NativeImage previewNative = NativeImage.read(pBaos.toByteArray());

            applyPreviewTexture(previewNative);
            hintText = successMsg;
            hasImage = true;
            init();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static java.awt.image.BufferedImage scale(java.awt.image.BufferedImage src, int w, int h) {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private void applyPreviewTexture(NativeImage img) {
        if (previewTexLoc != null)
            Minecraft.getInstance().getTextureManager().release(previewTexLoc);
        if (previewTex != null) previewTex.close();

        previewTex    = new DynamicTexture(() -> "report_preview", img);
        previewTexLoc = ResourceLocation.fromNamespaceAndPath("dashboardadmin", "report_preview");
        Minecraft.getInstance().getTextureManager().register(previewTexLoc, previewTex);
    }

    private void removeImage() {
        sendImageBytes = new byte[0];
        if (previewTexLoc != null) {
            Minecraft.getInstance().getTextureManager().release(previewTexLoc);
            if (previewTex != null) previewTex.close();
        }
        previewTex    = null;
        previewTexLoc = null;
        hasImage      = false;
        hintText      = "§8Ctrl+V pour coller le dernier screenshot (F2)";
        init();
    }

    // ── Envoi ─────────────────────────────────────────────────────────────────

    private void submit() {
        String msg = messageBox.getValue().trim();
        if (msg.isEmpty()) return;
        ClientPlayNetworking.send(new ReportSubmitPayload(msg, hasImage ? sendImageBytes : new byte[0]));
        onClose();
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderTransparentBackground(g);

        int bx = (width - POP_W) / 2;
        int by = (height - POP_H) / 2;

        g.fill(bx, by, bx + POP_W, by + POP_H, C_BG);
        g.fill(bx, by, bx + POP_W, by + 2, C_ACCENT);

        g.drawCenteredString(font,
            Component.literal("§bEnvoyer un signalement"),
            bx + POP_W / 2, by + 7, 0xFFFFFFFF);

        g.fill(bx + 12, by + 20, bx + 12 + PREVIEW_W, by + 20 + PREVIEW_H, C_PANEL);
        if (hasImage && previewTexLoc != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, previewTexLoc,
                bx + 12, by + 20, 0f, 0f, PREVIEW_W, PREVIEW_H, PREVIEW_W, PREVIEW_H);
        } else {
            g.drawCenteredString(font, hintText,
                bx + 12 + PREVIEW_W / 2, by + 20 + PREVIEW_H / 2 - 4, 0xFF333333);
        }

        g.drawString(font, "§7Message :", bx + 10, by + 155, 0xFF888888);

        super.render(g, mx, my, delta);
    }

    @Override
    public void removed() {
        if (previewTexLoc != null) {
            Minecraft.getInstance().getTextureManager().release(previewTexLoc);
            if (previewTex != null) previewTex.close();
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
