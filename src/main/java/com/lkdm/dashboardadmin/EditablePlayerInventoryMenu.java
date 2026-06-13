package com.lkdm.dashboardadmin;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public class EditablePlayerInventoryMenu extends ChestMenu {
    private final Inventory playerInventory;
    private final SimpleContainer container;

    public EditablePlayerInventoryMenu(int syncId, Inventory playerInv, Inventory targetInv) {
        super(MenuType.GENERIC_9x5, syncId, playerInv, createContainer(targetInv), 5);
        this.playerInventory = targetInv;
        this.container = (SimpleContainer) getContainer();
    }

    private static SimpleContainer createContainer(Inventory targetInv) {
        SimpleContainer container = new SimpleContainer(45); // 5 rows
        for (int i = 0; i < 41; i++) {
            container.setItem(i, targetInv.getItem(i).copy());
        }
        return container;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        for (int i = 0; i < 41; i++) {
            playerInventory.setItem(i, container.getItem(i));
        }
    }
}
