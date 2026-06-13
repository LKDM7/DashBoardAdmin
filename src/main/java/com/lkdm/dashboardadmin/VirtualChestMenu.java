package com.lkdm.dashboardadmin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class VirtualChestMenu extends ChestMenu {
    public VirtualChestMenu(int syncId, Inventory playerInv, Container container) {
        super(MenuType.GENERIC_9x1, syncId, playerInv, container, 1);
        
        // Bloquer les slots 0, 1 (début) et 7, 8 (fin)
        for (int i = 0; i < 9; i++) {
            if (i < 2 || i > 6) {
                this.slots.set(i, new Slot(container, i, 0, 0) {
                    @Override
                    public boolean mayPlace(ItemStack stack) { return false; }
                    @Override
                    public boolean mayPickup(Player player) { return false; }
                    @Override
                    public ItemStack getItem() { return new ItemStack(Items.BLACK_STAINED_GLASS_PANE); }
                });
            }
        }
    }
}
