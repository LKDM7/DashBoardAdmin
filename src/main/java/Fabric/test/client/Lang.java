package Fabric.test.client;

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
}
