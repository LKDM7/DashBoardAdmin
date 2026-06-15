package com.lkdm.dashboardadmin.mixin;

import com.lkdm.dashboardadmin.DashboardAdmin;
import com.lkdm.dashboardadmin.RoleManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Contournement de l'anti-spam chat vanilla pour les joueurs exemptés (OP + rôles de modération,
 * uniquement si le toggle est activé — voir {@link DashboardAdmin#isAntiSpamBypassed}).
 *
 * Vanilla appelle {@code detectRateSpam()} après chaque message : on annule l'appel pour les
 * joueurs exemptés → compteur jamais incrémenté, aucun kick possible.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerSpamMixin {

    private static final org.slf4j.Logger DASH_LOG = com.mojang.logging.LogUtils.getLogger();

    @Shadow public ServerPlayer player;

    @Inject(method = "detectRateSpam", at = @At("HEAD"), cancellable = true)
    private void dashboardadmin$bypassChatSpam(CallbackInfo ci) {
        boolean toggle = DashboardAdmin.isAntiSpamBypassEnabled();
        boolean op     = player != null && player.hasPermissions(2);
        boolean role   = player != null && RoleManager.hasAnyRole(player.getUUID());
        boolean bypass = DashboardAdmin.isAntiSpamBypassed(player);
        // DEBUG temporaire : à retirer une fois la cause identifiée.
        DASH_LOG.info("[DashAntiSpam] joueur={} bypass={} (toggle={} op={} role={})",
            player != null ? player.getName().getString() : "?", bypass, toggle, op, role);
        if (bypass) ci.cancel();
    }
}
