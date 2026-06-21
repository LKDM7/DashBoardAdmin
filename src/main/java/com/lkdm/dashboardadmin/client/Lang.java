package com.lkdm.dashboardadmin.client;

import net.minecraft.client.Minecraft;

/**
 * Localisation des GUI : français si la langue du jeu est le français,
 * anglais sinon. Les textes restent colocalisés avec le code
 * ({@code Lang.t("Fermer", "Close")}) plutôt que dans des fichiers de
 * langue — plus simple à maintenir pour deux langues.
 */
public final class Lang {

    private Lang() {}

    /** true si la langue sélectionnée du client est le français (fr_fr, fr_ca…). */
    public static boolean fr() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getLanguageManager() == null) return true;
        return mc.getLanguageManager().getSelected().startsWith("fr");
    }

    /** Sélectionne la variante selon la langue du jeu. */
    public static String t(String french, String english) {
        return fr() ? french : english;
    }

    /**
     * Badge d'état canonique pour TOUS les toggles ON/OFF de l'interface
     * (§a✔ ON / §c✘ OFF). À préfixer du libellé, ex. {@code "AFK AUTO: " + Lang.onOff(on)}.
     * Centralisé ici pour garder un langage visuel unique sur tous les écrans.
     */
    public static String onOff(boolean on) {
        return on ? "§a✔ ON" : "§c✘ OFF";
    }

    /**
     * Bulle d'aide d'un flag de zone, expliquant sa portée selon {@code areaRule} :
     * règle de MONDE (tout le monde) ou règle d'ACCÈS (non-autorisés seulement).
     * Partagée par ZoneScreen et ZonesTab pour un texte identique.
     */
    public static net.minecraft.network.chat.Component flagTooltip(boolean areaRule) {
        String txt = areaRule
            ? t("§b● Règle de MONDE\n§7S'applique à §ftout le monde§7 dans la zone\n§7(membres et op compris).",
                "§b● WORLD rule\n§7Applies to §feveryone§7 inside the zone\n§7(members and ops included).")
            : t("§e● Règle d'ACCÈS\n§7Ne bloque que les joueurs §fnon autorisés§7.\n§7Membres, builders et op passent outre.",
                "§e● ACCESS rule\n§7Only restricts §funauthorized§7 players.\n§7Members, builders and ops bypass it.");
        return net.minecraft.network.chat.Component.literal(txt);
    }
}
