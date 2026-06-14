package com.lkdm.dashboardadmin;

import com.lkdm.dashboardadmin.networking.NotifPayload;
import com.lkdm.dashboardadmin.networking.ReportImagePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Gestion centralisée des signalements joueurs (/report, GUI admin, commandes /reportaccept…).
 *
 * Extrait de {@link DashNetworking} et {@link DashGameEvents} qui dupliquaient la logique de
 * soumission/notification/accept/refus. L'état reste pour l'instant dans {@link DashboardAdmin}
 * (pendingReports / acceptedReports / closedReports + images) ; ce manager n'en est que la façade.
 */
public final class ReportManager {

    private ReportManager() {}

    /** Soumet un signalement, prévient les admins en ligne et relaie au webhook Discord. */
    public static void submit(ServerPlayer player, String message, byte[] img) {
        String name    = player.getName().getString();
        boolean hasImg = img != null && img.length > 0;
        DashboardAdmin.pendingReports.put(name, message);
        if (hasImg) DashboardAdmin.reportImages.put(name, img);
        player.sendSystemMessage(Component.literal(SrvLang.t(player, "§aVotre rapport a été envoyé aux administrateurs.", "§aYour report has been sent to the staff.")));
        notifyAdmins(player.getServer(), name, message, hasImg);
        DiscordWebhook.sendReport(DashboardAdmin.getWebhookReports(), name, message, hasImg ? img : null);
    }

    /** Prévient chaque admin en ligne (chat cliquable + toast) d'un nouveau report. */
    private static void notifyAdmins(MinecraftServer server, String name, String message, boolean hasImg) {
        for (ServerPlayer op : server.getPlayerList().getPlayers())
            if (op.hasPermissions(2)) {
                Component notif = Component.literal("§c§l[REPORT] §r§e" + name + " §7» §f" + message + "  ")
                    .append(Component.literal(SrvLang.t(op, "[ACCEPTER]", "[ACCEPT]")).withStyle(s -> s.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportaccept " + name))))
                    .append(Component.literal("  "))
                    .append(Component.literal(SrvLang.t(op, "[REFUSER]", "[DENY]")).withStyle(s -> s.withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reportdeny " + name))));
                op.sendSystemMessage(notif);
                PacketDistributor.sendToPlayer(op, new NotifPayload("REPORT",
                    SrvLang.t(op, "§c⚑ Report de §f" + name + (hasImg ? " §8[IMG]" : ""), "§c⚑ Report from §f" + name + (hasImg ? " §8[IMG]" : ""))));
            }
    }

    /** Prend en charge un report en attente. Renvoie false (avec message) s'il n'existe pas. */
    public static boolean accept(ServerPlayer admin, String name) {
        if (!DashboardAdmin.pendingReports.containsKey(name)) {
            admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cAucun rapport en attente de §e" + name + "§c.", "§cNo pending report from §e" + name + "§c.")));
            return false;
        }
        DashboardAdmin.acceptedReports.put(name, DashboardAdmin.pendingReports.remove(name));
        byte[] img = DashboardAdmin.reportImages.remove(name);
        if (img != null) DashboardAdmin.acceptedReportImages.put(name, img);
        ServerPlayer reporter = admin.getServer().getPlayerList().getPlayerByName(name);
        if (reporter != null) reporter.sendSystemMessage(Component.literal(SrvLang.t(reporter, "§aVotre signalement a été pris en charge.", "§aYour report is now being handled.")));
        admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aRapport de §e" + name + " §apris en charge.", "§aReport from §e" + name + " §ataken in charge.")));
        return true;
    }

    /** Refuse (supprime) un report en attente. Renvoie false (avec message) s'il n'existe pas. */
    public static boolean refuse(ServerPlayer admin, String name) {
        if (DashboardAdmin.pendingReports.remove(name) == null) {
            admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cAucun rapport de §e" + name + "§c.", "§cNo report from §e" + name + "§c.")));
            return false;
        }
        DashboardAdmin.reportImages.remove(name);
        admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cRapport de §e" + name + " §crefusé.", "§cReport from §e" + name + " §cdenied.")));
        return true;
    }

    /** Clôture un report en cours (déplace vers l'historique, plafonné à 15). */
    public static boolean close(ServerPlayer admin, String name) {
        String msg = DashboardAdmin.acceptedReports.remove(name);
        if (msg == null) {
            admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cAucun rapport en cours pour §e" + name + "§c.", "§cNo ongoing report for §e" + name + "§c.")));
            return false;
        }
        if (DashboardAdmin.closedReports.size() >= 15) {
            String oldest = DashboardAdmin.closedReports.keySet().iterator().next();
            DashboardAdmin.closedReports.remove(oldest);
            DashboardAdmin.closedReportImages.remove(oldest);
        }
        DashboardAdmin.closedReports.put(name, msg);
        byte[] img = DashboardAdmin.acceptedReportImages.remove(name);
        if (img != null) DashboardAdmin.closedReportImages.put(name, img);
        ServerPlayer reporter = admin.getServer().getPlayerList().getPlayerByName(name);
        if (reporter != null) reporter.sendSystemMessage(Component.literal(SrvLang.t(reporter, "§aVotre signalement a été résolu.", "§aYour report has been resolved.")));
        admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§aSignalement de §e" + name + " §aclôturé.", "§aReport from §e" + name + " §aclosed.")));
        return true;
    }

    /** Envoie l'image associée à un report (en attente / accepté / clôturé) à l'admin demandeur. */
    public static void fetchImage(ServerPlayer admin, String name) {
        byte[] img = DashboardAdmin.reportImages.containsKey(name) ? DashboardAdmin.reportImages.get(name)
            : DashboardAdmin.acceptedReportImages.containsKey(name) ? DashboardAdmin.acceptedReportImages.get(name)
            : DashboardAdmin.closedReportImages.get(name);
        if (img != null) PacketDistributor.sendToPlayer(admin, new ReportImagePayload(name, img));
    }
}
