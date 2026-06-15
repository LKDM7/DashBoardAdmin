package com.lkdm.dashboardadmin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Lecture seule de l'inventaire / enderchest d'un joueur <b>hors ligne</b>, depuis
 * {@code <monde>/playerdata/<uuid>.dat}. Complète le ban offline : un admin peut inspecter un joueur
 * déconnecté sans qu'il se reconnecte. La vue est strictement consultative ({@link OfflineInventoryMenu}).
 */
public final class OfflinePlayerInventory {

    private OfflinePlayerInventory() {}

    public static void openInventory(ServerPlayer admin, String targetName) {
        CompoundTag data = load(admin, targetName);
        if (data == null) return;
        // 41 slots utiles (0-35 principal, 36-39 armure, 40 main secondaire) dans un conteneur 5 rangées.
        SimpleContainer container = new SimpleContainer(45);
        HolderLookup.Provider reg = admin.getServer().registryAccess();
        ListTag items = data.getList("Inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag it = items.getCompound(i);
            int nbtSlot = it.getByte("Slot") & 255;
            int slot = mapInventorySlot(nbtSlot);
            if (slot >= 0 && slot < 41) container.setItem(slot, ItemStack.parseOptional(reg, it));
        }
        admin.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> OfflineInventoryMenu.inventory(id, inv, container),
            Component.literal(SrvLang.t(admin, "Inv (hors ligne) : ", "Inv (offline): ") + targetName)));
    }

    public static void openEnderchest(ServerPlayer admin, String targetName) {
        CompoundTag data = load(admin, targetName);
        if (data == null) return;
        SimpleContainer container = new SimpleContainer(27);
        HolderLookup.Provider reg = admin.getServer().registryAccess();
        ListTag items = data.getList("EnderItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag it = items.getCompound(i);
            int slot = it.getByte("Slot") & 255;
            if (slot >= 0 && slot < 27) container.setItem(slot, ItemStack.parseOptional(reg, it));
        }
        admin.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> OfflineInventoryMenu.enderchest(id, inv, container),
            Component.literal(SrvLang.t(admin, "Ender (hors ligne) : ", "Ender (offline): ") + targetName)));
    }

    /** Mappe le n° de slot NBT vers l'index linéaire du conteneur (0-35 principal, 36-39 armure, 40 offhand). */
    private static int mapInventorySlot(int nbtSlot) {
        if (nbtSlot >= 0 && nbtSlot <= 35) return nbtSlot;          // principal + hotbar
        if (nbtSlot >= 100 && nbtSlot <= 103) return 36 + (nbtSlot - 100); // armure
        if (nbtSlot == 150) return 40;                              // main secondaire
        return -1;
    }

    /** Charge le NBT du joueur hors ligne ; notifie l'admin et renvoie null si introuvable/illisible. */
    private static CompoundTag load(ServerPlayer admin, String targetName) {
        MinecraftServer server = admin.getServer();
        UUID uuid = RoleManager.resolveUuid(targetName, server);
        if (uuid == null) {
            admin.sendSystemMessage(Component.literal(SrvLang.t(admin, "§cJoueur introuvable.", "§cPlayer not found.")));
            return null;
        }
        Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat");
        if (!Files.exists(file)) {
            admin.sendSystemMessage(Component.literal(SrvLang.t(admin,
                "§cAucune donnée enregistrée pour ce joueur.", "§cNo saved data for this player.")));
            return null;
        }
        try {
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            admin.sendSystemMessage(Component.literal(SrvLang.t(admin,
                "§cImpossible de lire les données du joueur.", "§cFailed to read player data.")));
            return null;
        }
    }
}
