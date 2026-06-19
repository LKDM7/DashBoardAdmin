package com.lkdm.dashboardadmin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Logique d'auto-manger côté CLIENT (tick).
 *
 * <p>Quand le toggle est ON, qu'aucun écran n'est ouvert et que la faim
 * descend au seuil choisi, on cherche un aliment configuré dans
 * l'inventaire (hotbar d'abord, sinon on le remonte dans un slot de hotbar
 * via un clic SWAP), on le sélectionne et on le mange en maintenant la touche
 * « utiliser ». Le slot d'origine est restauré après.</p>
 */
public final class AutoEatHandler {

    private enum State { IDLE, MOVING, EATING }

    private static State state    = State.IDLE;
    private static int   prevSlot = -1;
    private static int   eatTicks = 0;
    private static int   moveTicks = 0;
    private static int   cooldown = 0;

    private AutoEatHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AutoEatHandler::onTick);
    }

    private static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) { reset(mc); return; }

        // Pause auto-manger dès qu'un écran est ouvert (inventaire de config, etc.)
        if (!AutoEatConfig.enabled || mc.screen != null) { abort(mc, player); return; }

        if (cooldown > 0) {
            cooldown--;
            if (state == State.IDLE) return;
        }

        int food = player.getFoodData().getFoodLevel();

        switch (state) {
            case IDLE -> {
                if (food > AutoEatConfig.threshold() || player.isUsingItem()) return;
                if (!AutoEatConfig.hasAnyFood()) { cooldown = 20; return; }

                int hot = findInHotbar(player);
                if (hot >= 0) { beginEat(mc, player, hot); return; }

                int inv = findInInventory(player);
                if (inv >= 0) {
                    int target = firstEmptyHotbar(player);
                    if (target < 0) target = player.getInventory().selected;
                    // Remonte l'aliment de l'inventaire principal vers la hotbar.
                    mc.gameMode.handleInventoryMouseClick(
                        player.inventoryMenu.containerId, inv, target, ClickType.SWAP, player);
                    moveTicks = 0;
                    state = State.MOVING;
                } else {
                    cooldown = 20; // aucun aliment configuré présent
                }
            }
            case MOVING -> {
                moveTicks++;
                int hot = findInHotbar(player);
                if (hot >= 0) beginEat(mc, player, hot);
                else if (moveTicks > 5) { state = State.IDLE; cooldown = 10; }
            }
            case EATING -> {
                mc.options.keyUse.setDown(true); // maintient la consommation
                if (player.isUsingItem()) { eatTicks = 0; return; }
                eatTicks++;
                if (food > AutoEatConfig.threshold() || eatTicks > 8) finishEat(mc, player);
            }
        }
    }

    private static void beginEat(Minecraft mc, LocalPlayer player, int slot) {
        ItemStack st = player.getInventory().getItem(slot);
        if (!AutoEatConfig.isFood(st)) { state = State.IDLE; cooldown = 10; return; }
        prevSlot = player.getInventory().selected;
        if (slot != prevSlot) {
            player.getInventory().selected = slot;
            send(mc, new ServerboundSetCarriedItemPacket(slot));
        }
        mc.options.keyUse.setDown(true);
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        eatTicks = 0;
        state = State.EATING;
    }

    private static void finishEat(Minecraft mc, LocalPlayer player) {
        mc.options.keyUse.setDown(false);
        restoreSlot(mc, player);
        state = State.IDLE;
        cooldown = 10;
    }

    private static void abort(Minecraft mc, LocalPlayer player) {
        if (state == State.EATING) {
            mc.options.keyUse.setDown(false);
            restoreSlot(mc, player);
        }
        state = State.IDLE;
    }

    private static void reset(Minecraft mc) {
        if (state == State.EATING && mc != null) mc.options.keyUse.setDown(false);
        state = State.IDLE;
        prevSlot = -1;
    }

    private static void restoreSlot(Minecraft mc, LocalPlayer player) {
        if (prevSlot >= 0 && prevSlot != player.getInventory().selected) {
            player.getInventory().selected = prevSlot;
            send(mc, new ServerboundSetCarriedItemPacket(prevSlot));
        }
        prevSlot = -1;
    }

    private static void send(Minecraft mc, ServerboundSetCarriedItemPacket pkt) {
        ClientPacketListener conn = mc.getConnection();
        if (conn != null) conn.send(pkt);
    }

    // Recherche dans l'ordre de priorité de la config (slot 0 = priorité max).

    private static int findInHotbar(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int c = 0; c < AutoEatConfig.SLOTS; c++) {
            Item want = AutoEatConfig.food(c);
            if (want == null) continue;
            for (int i = 0; i <= 8; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && s.is(want)) return i;
            }
        }
        return -1;
    }

    private static int findInInventory(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int c = 0; c < AutoEatConfig.SLOTS; c++) {
            Item want = AutoEatConfig.food(c);
            if (want == null) continue;
            for (int i = 9; i <= 35; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && s.is(want)) return i;
            }
        }
        return -1;
    }

    private static int firstEmptyHotbar(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i <= 8; i++) if (inv.getItem(i).isEmpty()) return i;
        return -1;
    }
}
