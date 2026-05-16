package Fabric.test.networking;

import net.minecraft.resources.ResourceLocation;

public class ModMessages {
    public static final ResourceLocation OPEN_ADMIN_GUI  = ResourceLocation.fromNamespaceAndPath("test", "open_admin_gui");
    public static final ResourceLocation ADMIN_ACTION    = ResourceLocation.fromNamespaceAndPath("test", "admin_action");
    public static final ResourceLocation OPEN_SETTINGS   = ResourceLocation.fromNamespaceAndPath("test", "open_settings");
    public static final ResourceLocation UPDATE_SETTINGS = ResourceLocation.fromNamespaceAndPath("test", "update_settings");

    public static void registerC2SPackets() {}
    public static void registerS2CPackets() {}
}
