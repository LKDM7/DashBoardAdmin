package com.lkdm.dashboardadmin.client;

import com.lkdm.dashboardadmin.networking.AutoEatPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Logique d'auto-manger côté CLIENT (tick).
 *
 * <p>Quand le toggle est ON, qu'aucun écran n'est ouvert et que la faim descend
 * au seuil choisi, on cherche un aliment configuré n'importe où dans l'inventaire
 * (hotbar, inventaire principal, main secondaire) et on envoie un
 * {@link AutoEatPayload} au serveur, qui le consomme instantanément. Pas
 * d'animation, pas de manipulation de la hotbar.</p>
 */
public final class AutoEatHandler {

    private static int cooldown = 0;

    private AutoEatHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AutoEatHandler::onTick);
    }

    private static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!AutoEatConfig.enabled || mc.screen != null) return;

        if (cooldown > 0) { cooldown--; return; }

        if (player.getFoodData().getFoodLevel() > AutoEatConfig.threshold()) return;
        if (player.isUsingItem()) return;
        if (!AutoEatConfig.hasAnyFood()) { cooldown = 20; return; }

        int slot = findFoodSlot(player);
        if (slot >= 0) {
            PacketDistributor.sendToServer(new AutoEatPayload(slot));
            cooldown = 10; // laisse le serveur appliquer et resync la faim
        } else {
            cooldown = 20; // aucun aliment configuré présent
        }
    }

    /** Premier slot contenant un aliment configuré, dans l'ordre de priorité de la config. */
    private static int findFoodSlot(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int c = 0; c < AutoEatConfig.SLOTS; c++) {
            Item want = AutoEatConfig.food(c);
            if (want == null) continue;
            for (int i = 0; i <= 35; i++) { // hotbar (0-8) + inventaire principal (9-35)
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && s.is(want)) return i;
            }
            ItemStack off = inv.getItem(40); // main secondaire
            if (!off.isEmpty() && off.is(want)) return 40;
        }
        return -1;
    }
}
