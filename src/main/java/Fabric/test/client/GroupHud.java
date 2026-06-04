package Fabric.test.client;

import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class GroupHud {

    public static final int[] COLOR_ARGB = {
        0xFF55FF55, // §a Green
        0xFF55FFFF, // §b Cyan
        0xFFFF5555, // §c Red
        0xFFFF55FF, // §d Pink
        0xFFFFFF55, // §e Yellow
        0xFFFFAA00, // §6 Orange
        0xFF5555FF, // §9 Blue
        0xFFAA00AA, // §5 Purple
        0xFFFFFFFF, // §f White
        0xFFAAAAAA, // §7 Gray
        0xFF00AA00, // §2 Dark Green
        0xFF00AAAA, // §3 Dark Aqua
        0xFFAA0000, // §4 Dark Red
        0xFF0000AA, // §1 Dark Blue
        0xFF555555, // §8 Dark Gray
        0xFFFFAA55, // §6+§c Salmon
    };
    public static final String[] COLOR_CODES = {
        "§a", "§b", "§c", "§d", "§e", "§6", "§9", "§5",
        "§f", "§7", "§2", "§3", "§4", "§1", "§8", "§6"
    };

    private static final String[] ARROWS = { "⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉" };

    public record MemberState(
        UUID uuid, String name, int colorIdx,
        double x, double y, double z,
        int health, boolean afk, boolean vanished
    ) {}

    private static final List<MemberState> members = new ArrayList<>();
    private static boolean showNames = true;
    private static boolean inGroup   = false;

    public static void updateMembers(List<MemberState> newMembers, boolean show) {
        members.clear();
        members.addAll(newMembers);
        showNames = show;
        inGroup   = !newMembers.isEmpty();
    }

    public static void clear() { members.clear(); inGroup = false; }
    public static List<MemberState> getMembers() { return Collections.unmodifiableList(members); }
    public static boolean isShowNames() { return showNames; }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post evt) -> onHudRender(evt.getGuiGraphics(), evt.getPartialTick()));
    }

    private static void onHudRender(GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !inGroup || members.isEmpty()) return;
        if (mc.options.hideGui) return;

        Vec3 myPos = mc.player.getEyePosition(delta.getGameTimeDeltaTicks());
        float yaw  = mc.player.getYRot();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        UUID myUUID = mc.player.getUUID();

        List<MemberState> forCompass = new ArrayList<>();

        for (MemberState m : members) {
            if (m.uuid().equals(myUUID)) continue; // skip self
            if (m.vanished()) continue;
            Vec3 mPos = new Vec3(m.x() + 0.5, m.y() + 1.7, m.z() + 0.5);
            double dist = myPos.distanceTo(mPos);
            if (dist > 500) continue;
            forCompass.add(m);
        }

        if (!forCompass.isEmpty())
            renderCompass(g, mc, forCompass.size() > 6 ? forCompass.subList(0, 6) : forCompass, myPos, yaw, sw, sh);
    }

    private static void renderCompass(GuiGraphics g, Minecraft mc, List<MemberState> far,
                                       Vec3 myPos, float yaw, int sw, int sh) {
        int hotbarY = sh - 22;
        int startX  = sw / 2 - (far.size() * 18) / 2;

        for (int i = 0; i < far.size(); i++) {
            MemberState m = far.get(i);
            Vec3 mPos = new Vec3(m.x() + 0.5, m.y(), m.z() + 0.5);

            double dx = mPos.x - myPos.x;
            double dz = mPos.z - myPos.z;
            double angleToTarget = Math.toDegrees(Math.atan2(-dx, dz));
            double relAngle = ((angleToTarget - yaw) % 360 + 360) % 360;

            int dirIdx = (int)((relAngle + 22.5) / 45) % 8;
            String arrow = ARROWS[dirIdx];
            int color = COLOR_ARGB[Math.max(0, Math.min(m.colorIdx(), COLOR_ARGB.length - 1))];

            int ax = startX + i * 18;
            int ay = hotbarY - 14;

            String label = COLOR_CODES[Math.max(0, Math.min(m.colorIdx(), COLOR_ARGB.length - 1))] + arrow;
            if (m.afk()) label = "§7" + arrow;

            g.fill(ax - 1, ay - 2, ax + 13, ay + 13, 0x66000000);
            g.drawString(mc.font, label, ax, ay, color, false);

            if (showNames) {
                String name = m.name();
                String displayName = name.isEmpty() ? "?" : (name.length() > 3 ? name.substring(0, 3) : name);
                while (displayName.length() > 1 && mc.font.width(displayName) > 16)
                    displayName = displayName.substring(0, displayName.length() - 1);
                String nameColor = m.afk() ? "§7" : COLOR_CODES[Math.max(0, Math.min(m.colorIdx(), COLOR_CODES.length - 1))];
                int nameX = ax + 7 - mc.font.width(displayName) / 2;
                g.drawString(mc.font, nameColor + displayName, nameX, ay + 10, color, false);
            } else {
                String initial = m.name().isEmpty() ? "?" : String.valueOf(m.name().charAt(0)).toUpperCase();
                g.drawString(mc.font, "§8" + initial, ax + 3, ay + 10, 0xFF777777, false);
            }
        }
    }

}

