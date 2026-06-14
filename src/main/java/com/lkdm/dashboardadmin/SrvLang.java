package com.lkdm.dashboardadmin;

import net.minecraft.server.level.ServerPlayer;

/**
 * i18n côté serveur — pendant de {@code client/Lang.java}, mais résolu selon la langue
 * du <b>destinataire</b> du message (et non du client local).
 *
 * Convention du projet : textes colocalisés FR/EN, pas de fichiers de langue JSON.
 * {@code Component.translatable} est volontairement évité car les codes couleur §
 * en amont d'un {@code %s} ne se propagent pas aux arguments substitués.
 *
 * Usage : {@code player.sendSystemMessage(Component.literal(SrvLang.t(player, "§cFR…", "§cEN…")));}
 * Pour un broadcast, boucler sur les joueurs et résoudre par destinataire (cf. {@link #each}).
 */
public final class SrvLang {

    private SrvLang() {}

    /** Vrai si le joueur est en français (ou destinataire inconnu → FR par défaut). */
    public static boolean isFr(ServerPlayer p) {
        if (p == null) return true;
        String l = p.clientInformation().language();
        return l == null || l.toLowerCase().startsWith("fr");
    }

    /** Renvoie {@code fr} ou {@code en} selon la langue du destinataire. */
    public static String t(ServerPlayer p, String fr, String en) {
        return isFr(p) ? fr : en;
    }
}
