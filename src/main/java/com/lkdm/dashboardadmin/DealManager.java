package com.lkdm.dashboardadmin;

import com.lkdm.dashboardadmin.networking.OpenDealPayload;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Échanges d'items entre joueurs (/deal) : sessions actives, demandes en attente, restitution.
 *
 * Extrait de la god-class {@link DashboardAdmin}. Le cache de noms reste dans {@link DashboardAdmin} ;
 * l'expiration des demandes et le nettoyage à la déconnexion sont déclenchés depuis {@link DashGameEvents}.
 */
public final class DealManager {

    private DealManager() {}

    // ─── Session d'échange ──────────────────────────────────────────────────────
    public static class DealSession {
        public final UUID player1, player2;
        public final NonNullList<ItemStack> offer1 = NonNullList.withSize(27, ItemStack.EMPTY);
        public final NonNullList<ItemStack> offer2 = NonNullList.withSize(27, ItemStack.EMPTY);
        public boolean accepted1 = false, accepted2 = false;
        DealSession(UUID p1, UUID p2) { player1 = p1; player2 = p2; }
        public boolean isPlayer1(UUID u)          { return player1.equals(u); }
        public NonNullList<ItemStack> myOffer(UUID u)    { return isPlayer1(u) ? offer1 : offer2; }
        public NonNullList<ItemStack> theirOffer(UUID u) { return isPlayer1(u) ? offer2 : offer1; }
        public boolean isAccepted(UUID u)          { return isPlayer1(u) ? accepted1 : accepted2; }
        public void setAccepted(UUID u, boolean v) { if (isPlayer1(u)) accepted1 = v; else accepted2 = v; }
        public void resetAccepted()                { accepted1 = false; accepted2 = false; }
        public UUID partner(UUID u)                { return isPlayer1(u) ? player2 : player1; }
        public boolean bothAccepted()              { return accepted1 && accepted2; }
    }

    // ─── État ───────────────────────────────────────────────────────────────────
    static final Map<UUID, DealSession> activeSessions        = new HashMap<>();
    private static final Map<UUID, UUID> pendingDeals          = new HashMap<>();
    private static final Map<UUID, Long> pendingDealTimestamps = new HashMap<>();

    public static Map<UUID, UUID> getPendingDeals()          { return pendingDeals; }
    public static Map<UUID, Long> getPendingDealTimestamps()  { return pendingDealTimestamps; }

    // ─── Helpers ────────────────────────────────────────────────────────────────
    static void sendDealPayload(ServerPlayer player, DealSession session) {
        String partnerName = DashboardAdmin.getPlayerNameCache().getOrDefault(session.partner(player.getUUID()), "???");
        boolean myAcc    = session.isAccepted(player.getUUID());
        boolean theirAcc = session.isAccepted(session.partner(player.getUUID()));
        List<ItemStack> myItems    = new ArrayList<>(session.myOffer(player.getUUID()));
        List<ItemStack> theirItems = new ArrayList<>(session.theirOffer(player.getUUID()));
        PacketDistributor.sendToPlayer(player, new OpenDealPayload(partnerName, false, "", myAcc, theirAcc, myItems, theirItems));
    }
    static void broadcastDealUpdate(DealSession session, MinecraftServer server) {
        ServerPlayer p1 = server.getPlayerList().getPlayer(session.player1);
        ServerPlayer p2 = server.getPlayerList().getPlayer(session.player2);
        if (p1 != null) sendDealPayload(p1, session);
        if (p2 != null) sendDealPayload(p2, session);
    }
    static void cancelDeal(DealSession session, MinecraftServer server, String reasonP1, String reasonP2) {
        activeSessions.remove(session.player1);
        activeSessions.remove(session.player2);
        for (boolean isP1 : new boolean[]{true, false}) {
            UUID uid = isP1 ? session.player1 : session.player2;
            ServerPlayer p    = server.getPlayerList().getPlayer(uid);
            NonNullList<ItemStack> offer = isP1 ? session.offer1 : session.offer2;
            for (ItemStack stack : offer) if (!stack.isEmpty() && p != null) p.getInventory().add(stack.copy());
            if (p != null) PacketDistributor.sendToPlayer(p, new OpenDealPayload("", true, isP1 ? reasonP1 : reasonP2, false, false, List.of(), List.of()));
        }
    }
    static void completeDeal(DealSession session, MinecraftServer server) {
        activeSessions.remove(session.player1);
        activeSessions.remove(session.player2);
        ServerPlayer p1 = server.getPlayerList().getPlayer(session.player1);
        ServerPlayer p2 = server.getPlayerList().getPlayer(session.player2);
        for (ItemStack stack : session.offer2) if (!stack.isEmpty() && p1 != null) p1.getInventory().add(stack.copy());
        for (ItemStack stack : session.offer1) if (!stack.isEmpty() && p2 != null) p2.getInventory().add(stack.copy());
        if (p1 != null) { PacketDistributor.sendToPlayer(p1, new OpenDealPayload("", true, "", false, false, List.of(), List.of())); p1.sendSystemMessage(Component.literal("§aÉchange complété !")); }
        if (p2 != null) { PacketDistributor.sendToPlayer(p2, new OpenDealPayload("", true, "", false, false, List.of(), List.of())); p2.sendSystemMessage(Component.literal("§aÉchange complété !")); }
    }
    static void handleDealDisconnect(ServerPlayer disconnecting, MinecraftServer server) {
        UUID uid = disconnecting.getUUID();
        pendingDealTimestamps.remove(uid);
        pendingDeals.entrySet().removeIf(e -> { if (e.getValue().equals(uid)) { pendingDealTimestamps.remove(e.getKey()); return true; } return false; });
        pendingDeals.remove(uid);
        DealSession session = activeSessions.remove(uid);
        if (session == null) return;
        activeSessions.remove(session.partner(uid));
        NonNullList<ItemStack> myOffer = session.myOffer(uid);
        for (ItemStack stack : myOffer) if (!stack.isEmpty()) disconnecting.getInventory().add(stack.copy());
        UUID partnerUUID = session.partner(uid);
        ServerPlayer partner = server.getPlayerList().getPlayer(partnerUUID);
        if (partner != null) {
            NonNullList<ItemStack> po = session.myOffer(partnerUUID);
            for (ItemStack stack : po) if (!stack.isEmpty()) partner.getInventory().add(stack.copy());
            PacketDistributor.sendToPlayer(partner, new OpenDealPayload("", true, disconnecting.getName().getString() + " s'est déconnecté. Échange annulé, items restitués.", false, false, List.of(), List.of()));
        }
    }
}
