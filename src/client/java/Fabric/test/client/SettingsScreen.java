package Fabric.test.client;

import Fabric.test.networking.OpenSettingsPayload;
import Fabric.test.networking.UpdateSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class SettingsScreen extends Screen {
    private static final int C_BG     = 0xF01A1A1A;
    private static final int C_HEADER = 0xFF111111;
    private static final int C_ACCENT = 0xFF00E5FF;
    private static final int C_ROW    = 0x22FFFFFF;
    private static final int C_TABSEL = 0x1A00AAFF;
    private static final int C_DIV    = 0x33FFFFFF;

    private static final int POP_W  = 340;
    private static final int POP_H  = 278;
    private static final int TAB_H  = 22;
    private static final int ROW_H  = 38;

    private boolean allowPrivateMessages;
    private boolean allowTpaRequests;
    private boolean allowTrades;
    private boolean showChatNotifications;
    private boolean showConnectionAlerts;

    private final List<String[]> commandList = new ArrayList<>();
    private int currentTab = 0;

    private static final String[] LABELS = {
        "Messages privés",
        "Demandes de TP",
        "Échanges joueurs",
        "Notifications chat",
        "Alertes connexion"
    };
    private static final String[] DESCS = {
        "Recevoir les messages via /mail",
        "Recevoir les demandes /tpa",
        "Recevoir les demandes d'échange",
        "Notification lors d'un message reçu",
        "Voir les connexions / déconnexions"
    };

    public SettingsScreen(OpenSettingsPayload payload) {
        super(Component.literal("MENU"));
        this.allowPrivateMessages  = payload.allowPrivateMessages();
        this.allowTpaRequests      = payload.allowTpaRequests();
        this.allowTrades           = payload.allowTrades();
        this.showChatNotifications = payload.showChatNotifications();
        this.showConnectionAlerts  = payload.showConnectionAlerts();
        for (String entry : payload.commands().split("\\|")) {
            if (entry.contains(":")) {
                int idx = entry.indexOf(':');
                commandList.add(new String[]{ entry.substring(0, idx), entry.substring(idx + 1) });
            }
        }
    }

    private int px() { return (this.width  - POP_W) / 2; }
    private int py() { return (this.height - POP_H) / 2; }

    @Override
    protected void init() {
        int px = px(), py = py();

        // tab buttons
        int tabW = POP_W / 2;
        this.addRenderableWidget(Button.builder(
            Component.literal("PARAMÈTRES"),
            b -> { currentTab = 0; this.init(); }
        ).bounds(px, py + 26, tabW, TAB_H).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("COMMANDES"),
            b -> { currentTab = 1; this.init(); }
        ).bounds(px + tabW, py + 26, tabW, TAB_H).build());

        if (currentTab == 0) {
            int btnX  = px + POP_W - 68;
            int start = py + 26 + TAB_H + 4;
            boolean[] vals = { allowPrivateMessages, allowTpaRequests, allowTrades, showChatNotifications, showConnectionAlerts };
            for (int i = 0; i < vals.length; i++) {
                final int idx = i;
                final boolean cur = vals[i];
                this.addRenderableWidget(Button.builder(
                    Component.literal(cur ? "  ON  " : "  OFF  ").withStyle(cur ? ChatFormatting.GREEN : ChatFormatting.RED),
                    b -> { toggle(idx); }
                ).bounds(btnX, start + ROW_H * i + 10, 58, 18).build());
            }
        }

        this.addRenderableWidget(Button.builder(
            Component.literal("FERMER"),
            b -> this.onClose()
        ).bounds(px + POP_W / 2 - 50, py + POP_H - 26, 100, 20).build());
    }

    private void toggle(int idx) {
        switch (idx) {
            case 0 -> allowPrivateMessages  = !allowPrivateMessages;
            case 1 -> allowTpaRequests      = !allowTpaRequests;
            case 2 -> allowTrades           = !allowTrades;
            case 3 -> showChatNotifications = !showChatNotifications;
            case 4 -> showConnectionAlerts  = !showConnectionAlerts;
        }
        sendSettings();
        this.init();
    }

    private void sendSettings() {
        ClientPlayNetworking.send(new UpdateSettingsPayload(
            allowPrivateMessages, allowTpaRequests, allowTrades,
            showChatNotifications, showConnectionAlerts
        ));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int px = px(), py = py();

        this.renderTransparentBackground(g);

        // background
        g.fill(px, py, px + POP_W, py + POP_H, C_BG);

        // title bar
        g.fill(px, py, px + POP_W, py + 26, C_HEADER);
        g.drawCenteredString(this.font, Component.literal("MENU").withStyle(s -> s.withColor(0x00E5FF).withBold(true)), px + POP_W / 2, py + 9, 0xFFFFFFFF);

        // active tab highlight
        int tabW = POP_W / 2;
        g.fill(px + currentTab * tabW, py + 26, px + currentTab * tabW + tabW, py + 26 + TAB_H, C_TABSEL);
        g.fill(px + currentTab * tabW, py + 26 + TAB_H - 2, px + currentTab * tabW + tabW, py + 26 + TAB_H, C_ACCENT);

        // tab divider
        g.fill(px, py + 26 + TAB_H, px + POP_W, py + 26 + TAB_H + 1, C_DIV);

        if (currentTab == 0) {
            renderSettings(g, px, py);
        } else {
            renderCommands(g, px, py);
        }

        // bottom divider
        g.fill(px, py + POP_H - 32, px + POP_W, py + POP_H - 31, C_DIV);

        super.render(g, mouseX, mouseY, delta);
    }

    private void renderSettings(GuiGraphics g, int px, int py) {
        int start = py + 26 + TAB_H + 4;
        for (int i = 0; i < LABELS.length; i++) {
            int ry = start + ROW_H * i;
            if (i % 2 == 0) g.fill(px + 4, ry + 2, px + POP_W - 4, ry + ROW_H - 2, C_ROW);
            g.drawString(this.font, "§f" + LABELS[i], px + 12, ry + 6,  0xFFFFFFFF);
            g.drawString(this.font, "§7" + DESCS[i],  px + 12, ry + 18, 0xFFAAAAAA);
        }
    }

    private void renderCommands(GuiGraphics g, int px, int py) {
        int contentTop = py + 26 + TAB_H + 6;
        int colW = POP_W / 2;
        int entryH = 26;
        int half = (commandList.size() + 1) / 2;

        for (int i = 0; i < commandList.size(); i++) {
            String[] cmd = commandList.get(i);
            int col  = i < half ? 0 : 1;
            int row  = i < half ? i : i - half;
            int ex   = px + 6 + col * colW;
            int ey   = contentTop + row * entryH;

            if (row % 2 == 0) g.fill(ex, ey + 1, ex + colW - 8, ey + entryH - 1, C_ROW);
            g.drawString(this.font, "§b" + cmd[0], ex + 5, ey + 4,  0xFFFFFFFF);
            g.drawString(this.font, "§8" + cmd[1], ex + 5, ey + 14, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
