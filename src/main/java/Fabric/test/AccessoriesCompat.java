package Fabric.test;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Saves and restores Accessories mod slots on player death (keepInventory).
 * Uses reflection so the mod compiles and runs without Accessories installed.
 */
public final class AccessoriesCompat {

    private static Boolean present = null;
    private static Method GET_CAP, GET_CONTAINERS, GET_ACCESSORIES, GET_SIZE, GET_ITEM, SET_ITEM;

    private AccessoriesCompat() {}

    private static boolean init() {
        if (present != null) return present;
        try {
            Class<?> capClass       = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            Class<?> containerClass = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
            Class<?> handlerClass   = Class.forName("net.minecraft.world.Container");

            GET_CAP        = capClass.getMethod("get", net.minecraft.world.entity.LivingEntity.class);
            GET_CONTAINERS = capClass.getMethod("getContainers");
            GET_ACCESSORIES = containerClass.getMethod("getAccessories");
            GET_SIZE       = handlerClass.getMethod("getContainerSize");
            GET_ITEM       = handlerClass.getMethod("getItem", int.class);
            SET_ITEM       = handlerClass.getMethod("setItem", int.class, ItemStack.class);
            present = true;
        } catch (Exception e) {
            present = false;
        }
        return present;
    }

    /** Copies all accessories to a map, then clears the slots so items don't drop. */
    @SuppressWarnings("unchecked")
    public static Map<String, NonNullList<ItemStack>> saveAndClear(ServerPlayer player) {
        Map<String, NonNullList<ItemStack>> result = new HashMap<>();
        if (!init()) return result;
        try {
            Object cap = GET_CAP.invoke(null, player);
            if (cap == null) return result;
            Map<String, Object> containers = (Map<String, Object>) GET_CONTAINERS.invoke(cap);
            for (Map.Entry<String, Object> entry : containers.entrySet()) {
                Object handler = GET_ACCESSORIES.invoke(entry.getValue());
                int size = (int) GET_SIZE.invoke(handler);
                NonNullList<ItemStack> copy = NonNullList.withSize(size, ItemStack.EMPTY);
                for (int i = 0; i < size; i++) {
                    copy.set(i, ((ItemStack) GET_ITEM.invoke(handler, i)).copy());
                    SET_ITEM.invoke(handler, i, ItemStack.EMPTY);
                }
                result.put(entry.getKey(), copy);
            }
        } catch (Exception ignored) {}
        return result;
    }

    /** Puts saved accessories back into the player's slots. */
    @SuppressWarnings("unchecked")
    public static void restore(ServerPlayer player, Map<String, NonNullList<ItemStack>> saved) {
        if (saved.isEmpty() || !init()) return;
        try {
            Object cap = GET_CAP.invoke(null, player);
            if (cap == null) return;
            Map<String, Object> containers = (Map<String, Object>) GET_CONTAINERS.invoke(cap);
            for (Map.Entry<String, NonNullList<ItemStack>> entry : saved.entrySet()) {
                Object container = containers.get(entry.getKey());
                if (container == null) continue;
                Object handler = GET_ACCESSORIES.invoke(container);
                NonNullList<ItemStack> stacks = entry.getValue();
                int size = (int) GET_SIZE.invoke(handler);
                for (int i = 0; i < stacks.size() && i < size; i++)
                    SET_ITEM.invoke(handler, i, stacks.get(i));
            }
        } catch (Exception ignored) {}
    }
}
