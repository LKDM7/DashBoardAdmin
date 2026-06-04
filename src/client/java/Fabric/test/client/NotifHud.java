package Fabric.test.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class NotifHud {

    private record Notif(long spawnMs, String type, String message) {}

    private static final List<Notif> queue = new ArrayList<>();
    private static final long DURATION_MS  = 6000;
    private static final int  NOTIF_W      = 220;
    private static final int  NOTIF_H      = 18;

    public static void push(String type, String message) {
        queue.add(new Notif(System.currentTimeMillis(), type, message));
        if (queue.size() > 5) queue.remove(0);
    }

    public static void register() {
        HudRenderCallback.EVENT.register(NotifHud::onHudRender);
    }

    private static void onHudRender(GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        long now = System.currentTimeMillis();
        queue.removeIf(n -> now - n.spawnMs > DURATION_MS);
        if (queue.isEmpty()) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int padX = sw - NOTIF_W - 6;
        // stack above hotbar (hotbar is ~22px tall)
        int baseY = sh - 26 - NOTIF_H;

        for (int i = queue.size() - 1; i >= 0; i--) {
            Notif n = queue.get(i);
            int y = baseY - (queue.size() - 1 - i) * (NOTIF_H + 3);

            long age = now - n.spawnMs;
            int alpha = age > DURATION_MS - 1000
                ? (int)(255L * (DURATION_MS - age) / 1000L)
                : 255;
            if (alpha <= 0) continue;

            int bgA   = (int)(alpha * 0.75f);
            int bg    = (bgA << 24);
            int barC  = (alpha << 24) | (colorFor(n.type) & 0x00FFFFFF);
            int textC = (alpha << 24) | 0xFFFFFF;
            int borderA = alpha / 3;

            // Dark outline
            g.fill(padX - 1, y - 1, padX + NOTIF_W + 1, y + NOTIF_H + 1, (borderA << 24));
            // Background
            g.fill(padX, y, padX + NOTIF_W, y + NOTIF_H, bg);
            // Left color bar (4px)
            g.fill(padX, y, padX + 4, y + NOTIF_H, barC);
            // Type label + message
            String typeLabel = switch (n.type) {
                case "MAIL"         -> "MAIL";
                case "DEAL"         -> "DEAL";
                case "GROUP_INVITE" -> "GRP";
                case "REPORT"       -> "RPT";
                default             -> "SYS";
            };
            int typeLabelColor = (alpha << 24) | (colorFor(n.type) & 0x00FFFFFF);
            int labelW = mc.font.width(typeLabel + " ");
            g.drawString(mc.font, typeLabel, padX + 7, y + 5, typeLabelColor, false);
            String txt = truncate(mc, n.message, NOTIF_W - 12 - labelW);
            g.drawString(mc.font, txt, padX + 7 + labelW, y + 5, textC, false);
            // Time-remaining progress bar at bottom
            long remaining = DURATION_MS - age;
            int progW = (int)((NOTIF_W - 2) * remaining / DURATION_MS);
            g.fill(padX + 1, y + NOTIF_H - 2, padX + 1 + progW, y + NOTIF_H - 1, barC);
        }
    }

    private static int colorFor(String type) {
        return switch (type) {
            case "MAIL"         -> 0x00E5FF; // cyan
            case "DEAL"         -> 0xFFFF55; // yellow
            case "GROUP_INVITE" -> 0x55FF55; // green
            case "REPORT"       -> 0xFF5555; // red
            default             -> 0xFFFFFF;
        };
    }

    private static String truncate(Minecraft mc, String s, int maxPx) {
        if (mc.font.width(s) <= maxPx) return s;
        while (s.length() > 0 && mc.font.width(s + "…") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
