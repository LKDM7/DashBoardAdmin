package Fabric.test.client;

import Fabric.test.networking.ReportSubmitPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            // Grab a clipboard image OFF the render thread: touching AWT's system clipboard on
            // the GLFW main thread hard-crashes the game on Windows (native access violation,
            // not catchable). Text paste is left to the focused EditBox via super.keyPressed
            // (Minecraft's GLFW-based clipboard), so pasting text into the message works too.
            pasteImageAsync();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Collage d'image (presse-papiers / dernier screenshot), hors thread rendu ──────

    private volatile boolean pasteInFlight = false;

    private void pasteImageAsync() {
        if (pasteInFlight) return;
        pasteInFlight = true;
        Thread worker = new Thread(() -> {
            byte[] sendPng = null, previewPng = null;
            // 1) Image du presse-papiers (AWT — sûr hors du thread GLFW)
            try {
                java.awt.image.BufferedImage bimg = readClipboardImage();
                if (bimg != null) {
                    int sendW = bimg.getWidth(), sendH = bimg.getHeight();
                    if (sendW > MAX_SEND_W || sendH > MAX_SEND_H) {
                        double s = Math.min((double) MAX_SEND_W / sendW, (double) MAX_SEND_H / sendH);
                        sendW = Math.max(1, (int) (sendW * s));
                        sendH = Math.max(1, (int) (sendH * s));
                    }
                    sendPng    = encodePng(bimg, sendW, sendH);
                    previewPng = encodePng(bimg, PREVIEW_W, PREVIEW_H);
                }
            } catch (Throwable ignored) {}
            // 2) Repli : dernier screenshot F2
            if (sendPng == null) {
                try {
                    byte[][] r = readLastScreenshot();
                    if (r != null) { sendPng = r[0]; previewPng = r[1]; }
                } catch (Throwable ignored) {}
            }

            final byte[] fSend = sendPng, fPreview = previewPng;
            Minecraft.getInstance().execute(() -> {
                pasteInFlight = false;
                if (Minecraft.getInstance().screen != this) return; // écran fermé entre-temps
                if (fSend == null || fPreview == null) {
                    if (!hasImage) hintText = "§cAucune image — prenez un screenshot F2 puis Ctrl+V";
                    return;
                }
                try {
                    NativeImage preview = NativeImage.read(fPreview);
                    sendImageBytes = fSend;
                    applyPreviewTexture(preview);
                    hintText = "§a✔ Image collée";
                    hasImage = true;
                    init();
                } catch (Exception ignored) {}
            });
        }, "ReportClipboardPaste");
        worker.setDaemon(true);
        worker.start();
    }

    /** Lit une image du presse-papiers système via AWT. À n'appeler QUE depuis un thread annexe. */
    private static java.awt.image.BufferedImage readClipboardImage() throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) return null;
        java.awt.datatransfer.Clipboard cb =
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
        java.awt.datatransfer.Transferable t = cb.getContents(null);
        if (t == null) return null;

        if (t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)) {
            java.awt.Image awt = (java.awt.Image)
                t.getTransferData(java.awt.datatransfer.DataFlavor.imageFlavor);
            if (awt != null && awt.getWidth(null) > 0) {
                java.awt.image.BufferedImage b = new java.awt.image.BufferedImage(
                    awt.getWidth(null), awt.getHeight(null), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2d = b.createGraphics();
                g2d.drawImage(awt, 0, 0, null);
                g2d.dispose();
                return b;
            }
        }
        for (java.awt.datatransfer.DataFlavor f : t.getTransferDataFlavors()) {
            if (!"image".equals(f.getPrimaryType())) continue;
            try {
                Object data = t.getTransferData(f);
                if (data instanceof java.io.InputStream is) {
                    java.awt.image.BufferedImage b = javax.imageio.ImageIO.read(is);
                    if (b != null && b.getWidth() > 0) return b;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Charge le dernier screenshot F2 et renvoie [pngEnvoi, pngApercu]. Thread annexe uniquement. */
    private static byte[][] readLastScreenshot() throws Exception {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
        if (!Files.isDirectory(dir)) return null;
        Path last;
        try (Stream<Path> s = Files.list(dir)) {
            last = s.filter(p -> p.toString().toLowerCase().endsWith(".png"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElse(null);
        }
        if (last == null) return null;
        java.awt.image.BufferedImage bimg = javax.imageio.ImageIO.read(last.toFile());
        if (bimg == null) return null;

        int sendW = bimg.getWidth(), sendH = bimg.getHeight();
        if (sendW > MAX_SEND_W || sendH > MAX_SEND_H) {
            double s = Math.min((double) MAX_SEND_W / sendW, (double) MAX_SEND_H / sendH);
            sendW = Math.max(1, (int) (sendW * s));
            sendH = Math.max(1, (int) (sendH * s));
        }
        return new byte[][]{ encodePng(bimg, sendW, sendH), encodePng(bimg, PREVIEW_W, PREVIEW_H) };
    }

    /** Redimensionne (exactement w×h) puis encode en PNG. CPU only — sûr hors thread rendu. */
    private static byte[] encodePng(java.awt.image.BufferedImage src, int w, int h) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(scale(src, w, h), "PNG", baos);
        return baos.toByteArray();
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

        previewTex    = new net.minecraft.client.renderer.texture.DynamicTexture(img);
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
        PacketDistributor.sendToServer(new ReportSubmitPayload(msg, hasImage ? sendImageBytes : new byte[0]));
        onClose();
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);

        int bx = (width - POP_W) / 2;
        int by = (height - POP_H) / 2;

        g.fill(bx, by, bx + POP_W, by + POP_H, C_BG);
        g.fill(bx, by, bx + POP_W, by + 2, C_ACCENT);

        g.drawCenteredString(font,
            Component.literal("§bEnvoyer un signalement"),
            bx + POP_W / 2, by + 7, 0xFFFFFFFF);

        g.fill(bx + 12, by + 20, bx + 12 + PREVIEW_W, by + 20 + PREVIEW_H, C_PANEL);
        if (hasImage && previewTexLoc != null) {
            g.blit(previewTexLoc,
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

    @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float delta) {}
    @Override
    public boolean isPauseScreen() { return false; }
}

