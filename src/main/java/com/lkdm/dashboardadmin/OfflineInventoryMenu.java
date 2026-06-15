package com.lkdm.dashboardadmin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;

/**
 * Vue <b>lecture seule</b> de l'inventaire / enderchest d'un joueur hors ligne.
 *
 * Les items sont chargés depuis le fichier {@code playerdata/<uuid>.dat} dans un conteneur temporaire
 * ({@link OfflinePlayerInventory}). Comme le joueur est hors ligne, toute modification serait perdue à
 * la reconnexion — pire, déplacer un item vers son propre inventaire le dupliquerait. On bloque donc
 * toute interaction de slot ({@link #clicked} neutralisé), ce qui rend la vue strictement consultative.
 */
public class OfflineInventoryMenu extends ChestMenu {

    private OfflineInventoryMenu(MenuType<?> type, int syncId, Inventory playerInv, Container container, int rows) {
        super(type, syncId, playerInv, container, rows);
    }

    public static OfflineInventoryMenu inventory(int syncId, Inventory playerInv, Container container) {
        return new OfflineInventoryMenu(MenuType.GENERIC_9x5, syncId, playerInv, container, 5);
    }

    public static OfflineInventoryMenu enderchest(int syncId, Inventory playerInv, Container container) {
        return new OfflineInventoryMenu(MenuType.GENERIC_9x3, syncId, playerInv, container, 3);
    }

    /** Lecture seule : on neutralise tous les clics (déplacement, shift-clic, drag, hotbar). */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // no-op
    }

    /** Rien à réécrire : le conteneur est temporaire et le joueur cible est hors ligne. */
    @Override
    public void removed(Player player) {
        super.removed(player);
    }
}
