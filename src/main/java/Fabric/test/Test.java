package Fabric.test;

import Fabric.test.command.AdminCommand;
import Fabric.test.networking.AdminActionPayload;
import Fabric.test.networking.DealActionPayload;
import Fabric.test.networking.OpenDealPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Set;

@Mod("dashboardadmin")
public class Test {

    // ─── Feature flags ────────────────────────────────────────────────────────
    private static boolean pvpEnabled = true;
    private static boolean setblockInBuild = true; // /setblock autorisé aux non-OP en mode build
    private static boolean chatLocked = false;
    private static boolean weatherCycleEnabled = true;
    private static boolean cropTrampleEnabled = false;
    private static volatile boolean afkAutoEnabled           = false;
    private static volatile long    afkDelayMs               = 5 * 60 * 1000L;
    private static volatile boolean proportionalSleepEnabled = false;
    private static volatile boolean treeCapitatorEnabled     = false;
    private static volatile boolean fastLeafDecayEnabled     = false;
    private static volatile boolean doubleDoorEnabled        = false;
    private static volatile boolean rightClickHarvestEnabled = false;
    private static volatile boolean dispenserHarvestEnabled  = false;
    private static volatile boolean mailSpyEnabled           = false;

    // ─── State maps ───────────────────────────────────────────────────────────
    private static final java.util.Map<java.util.UUID, Long>     lastActivityTime = new java.util.HashMap<>();
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, java.util.Set<net.minecraft.core.BlockPos>> pendingLeafDecay = new java.util.HashMap<>();
    private static int leafDecayCounter = 0;
    static int clearLagTicks   = -1;
    static int removeMobsTicks = -1;
    /** Ticks restants avant l'arrêt programmé du serveur (-1 = aucun). */
    static volatile int restartTicks = -1;
    public static boolean isRestartScheduled() { return restartTicks > 0; }
    public static void scheduleRestart(int minutes) { restartTicks = Math.max(1, minutes) * 1200; }
    public static void cancelRestart() { restartTicks = -1; }
    private static final java.util.Map<java.util.UUID, java.util.Map<String, net.minecraft.core.BlockPos>> playerHomes    = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, java.util.Map<String, String>>                       playerHomesDim = new java.util.HashMap<>();
    static final java.util.Map<java.util.UUID, Boolean>                        frozenPlayers   = new java.util.HashMap<>();
    static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3>  frozenPositions = new java.util.HashMap<>();
    static final java.util.Set<java.util.UUID>                                 mutedPlayers    = new java.util.HashSet<>();
    static final java.util.Set<java.util.UUID>                                 vanishedPlayers = new java.util.HashSet<>();
    private static final java.util.Map<java.util.UUID, java.util.List<String>> playerLogs      = new java.util.HashMap<>();
    static final java.util.Map<String, String> pendingReports  = new java.util.LinkedHashMap<>();
    static final java.util.Map<String, String> acceptedReports = new java.util.LinkedHashMap<>();
    static final java.util.Map<String, String> closedReports   = new java.util.LinkedHashMap<>();
    static final java.util.Map<String, byte[]> reportImages         = new java.util.LinkedHashMap<>();
    static final java.util.Map<String, byte[]> acceptedReportImages = new java.util.LinkedHashMap<>();
    static final java.util.Map<String, byte[]> closedReportImages   = new java.util.LinkedHashMap<>();
    private static volatile int    maxHomes         = 3;
    private static volatile String webhookReports   = "";
    private static volatile String webhookSanctions = "";
    private static volatile String motd             = "";
    private static final java.util.List<String> chatHistory = new java.util.ArrayList<>();
    private static final java.util.Map<java.util.UUID, java.util.List<String>> adminNotes = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Integer>        hostileMobKills = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, PlayerSettings> playerSettings  = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long>           lastSeenTimestamps = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long>           lastHomeUse        = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long>           lastBackUse        = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long>           lastTpaUse         = new java.util.HashMap<>();
    private static volatile int cooldownHome = 30;
    private static volatile int cooldownBack = 10;
    private static volatile int cooldownTpa  = 60;
    private static volatile int cooldownWarp = 10;
    private static volatile int cooldownRtp  = 300;
    private static final java.util.Map<java.util.UUID, Long> lastRtpUse = new java.util.HashMap<>();
    private static final java.util.List<String>  scheduledMsgs      = new java.util.ArrayList<>();
    private static final java.util.List<Integer> scheduledIntervals = new java.util.ArrayList<>();
    static final java.util.List<Integer>         scheduledCounters  = new java.util.ArrayList<>();
    private static final java.util.Map<java.util.UUID, java.util.UUID> tpaRequests          = new java.util.HashMap<>();
    private static final java.util.Set<java.util.UUID>                tpaHere              = new java.util.HashSet<>(); // cibles dont la requête est un /tpahere
    private static final java.util.Map<java.util.UUID, Long>           pendingTpaTimestamps  = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long>           pendingDealTimestamps = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, java.util.UUID> lastMsg               = new java.util.HashMap<>();
    static final java.util.Map<java.util.UUID, Boolean>                         afkPlayers   = new java.util.HashMap<>();
    static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3>   lastPos      = new java.util.HashMap<>();
    private static final java.util.Map<net.minecraft.core.BlockPos, java.util.UUID> lockedBlocks  = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> trustedPlayers = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, String>  playerNameCache = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> lastPositions  = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> lastPositionDims = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, java.util.UUID>  pendingDeals = new java.util.HashMap<>();
    private static final java.util.Map<String, net.minecraft.core.BlockPos> warps    = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, String>                      warpsDim = new java.util.LinkedHashMap<>();
    private static final java.util.Map<java.util.UUID, Long>           lastWarpUse  = new java.util.HashMap<>();
    static final java.util.Map<java.util.UUID, DealSession>             activeSessions = new java.util.HashMap<>();
    private static final java.util.List<String[]> sanctionsLog = new java.util.ArrayList<>();
    private static final java.util.List<String[]> auditLog     = new java.util.ArrayList<>(); // [ts, admin, action, target, detail]

    private static final java.util.List<String[]> playerCommands = java.util.Arrays.asList(
        new String[]{"/menu",      "Ouvrir le menu paramètres & commandes"},
        new String[]{"/mail",      "Envoyer un message privé à un joueur"},
        new String[]{"/r",         "Répondre au dernier message reçu"},
        new String[]{"/tpa",       "Demander une téléportation vers un joueur"},
        new String[]{"/tpahere",   "Demander à un joueur de se téléporter à vous"},
        new String[]{"/tpaccept",  "Accepter une demande de téléportation"},
        new String[]{"/tpdeny",    "Refuser une demande de téléportation"},
        new String[]{"/sethome",   "Enregistrer un home (Overworld uniquement)"},
        new String[]{"/home",      "Se téléporter à un home enregistré"},
        new String[]{"/back",      "Retourner à la position précédente"},
        new String[]{"/lock",      "Verrouiller/déverrouiller un bloc regardé"},
        new String[]{"/trust",     "Donner accès à ses blocs verrouillés"},
        new String[]{"/untrust",   "Retirer l'accès à ses blocs verrouillés"},
        new String[]{"/lockinfo",  "Voir les infos d'un bloc verrouillé"},
        new String[]{"/chest",     "Ouvrir son coffre virtuel personnel"},
        new String[]{"/afk",       "Signaler / annuler son absence"},
        new String[]{"/report",    "Envoyer un signalement aux admins"},
        new String[]{"/stats",     "Voir vos statistiques de jeu"},
        new String[]{"/seen",        "Voir la dernière connexion d'un joueur"},
        new String[]{"/deal",        "Proposer un échange d'items à un joueur"},
        new String[]{"/dealaccept",  "Accepter une demande d'échange"},
        new String[]{"/dealdeny",    "Refuser une demande d'échange"},
        new String[]{"/groupaccept", "Accepter une invitation de groupe"},
        new String[]{"/groupdeny",   "Refuser une invitation de groupe"},
        new String[]{"/build",       "Mode construction dans une zone autorisée"},
        new String[]{"/warp",        "Se téléporter vers un warp"},
        new String[]{"/rtp",         "Téléportation aléatoire sécurisée"},
        new String[]{"/ignore",      "Ignorer les messages d'un joueur"},
        new String[]{"/unignore",    "Arrêter d'ignorer un joueur"}
    );

    private static final java.time.format.DateTimeFormatter LOG_FMT =
        java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    // ─── Inner types ──────────────────────────────────────────────────────────
    public static class DealSession {
        public final java.util.UUID player1, player2;
        public final NonNullList<ItemStack> offer1 = NonNullList.withSize(27, ItemStack.EMPTY);
        public final NonNullList<ItemStack> offer2 = NonNullList.withSize(27, ItemStack.EMPTY);
        public boolean accepted1 = false, accepted2 = false;
        DealSession(java.util.UUID p1, java.util.UUID p2) { player1 = p1; player2 = p2; }
        public boolean isPlayer1(java.util.UUID u)          { return player1.equals(u); }
        public NonNullList<ItemStack> myOffer(java.util.UUID u)    { return isPlayer1(u) ? offer1 : offer2; }
        public NonNullList<ItemStack> theirOffer(java.util.UUID u) { return isPlayer1(u) ? offer2 : offer1; }
        public boolean isAccepted(java.util.UUID u)          { return isPlayer1(u) ? accepted1 : accepted2; }
        public void setAccepted(java.util.UUID u, boolean v) { if (isPlayer1(u)) accepted1 = v; else accepted2 = v; }
        public void resetAccepted()                          { accepted1 = false; accepted2 = false; }
        public java.util.UUID partner(java.util.UUID u)      { return isPlayer1(u) ? player2 : player1; }
        public boolean bothAccepted()                        { return accepted1 && accepted2; }
    }

    // ─── Constructor (NeoForge entry point) ───────────────────────────────────
    public Test(IEventBus modEventBus) {
        HomePersistence.register();
        registerDispenserHoeBehavior();
        VirtualChestManager.register();
        SettingsPersistence.register();
        LockPersistence.register();
        MobKillsPersistence.register();
        SeenPersistence.register();
        ServerConfig.register();
        ZonePersistence.register();
        SanctionsPersistence.register();
        AuditPersistence.register();
        GroupPersistence.register();
        ModerationPersistence.register();
        RolePersistence.register();

        modEventBus.addListener(DashNetworking::onRegisterPayloads);
        NeoForge.EVENT_BUS.register(DashGameEvents.class);
        Fabric.test.command.ZoneCommand.registerEvents();
        // Client-only init — safe due to @EventBusSubscriber(value = Dist.CLIENT)
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            Fabric.test.client.TestClient.initClient(modEventBus);
        }
    }

    // ─── Accessors ────────────────────────────────────────────────────────────
    public static java.util.Map<java.util.UUID, PlayerSettings> getAllSettings() { return playerSettings; }
    public static PlayerSettings getPlayerSettings(java.util.UUID uuid) { return playerSettings.computeIfAbsent(uuid, k -> new PlayerSettings()); }
    public static java.util.Map<java.util.UUID, java.util.Map<String, net.minecraft.core.BlockPos>> getAllHomes() { return playerHomes; }
    public static java.util.Map<java.util.UUID, java.util.Map<String, String>> getAllHomesDim()                   { return playerHomesDim; }
    public static java.util.Map<String, net.minecraft.core.BlockPos> getPlayerHomes(java.util.UUID uuid)          { return playerHomes.computeIfAbsent(uuid, k -> new java.util.HashMap<>()); }
    public static java.util.Map<String, String> getPlayerHomesDim(java.util.UUID uuid)                            { return playerHomesDim.computeIfAbsent(uuid, k -> new java.util.HashMap<>()); }
    public static boolean isPvpEnabled()                  { return pvpEnabled; }
    public static void   setPvpEnabled(boolean v)         { pvpEnabled = v; }
    public static boolean isSetblockInBuild()             { return setblockInBuild; }
    public static void   setSetblockInBuild(boolean v)    { setblockInBuild = v; }
    public static boolean isChatLocked()                  { return chatLocked; }
    public static boolean isWeatherCycleEnabled()         { return weatherCycleEnabled; }
    public static boolean isCropTrampleEnabled()          { return cropTrampleEnabled; }
    public static boolean isAfkAutoEnabled()              { return afkAutoEnabled; }
    public static int     getAfkDelayMinutes()            { return (int)(afkDelayMs / 60000L); }
    public static void    setAfkDelayMinutes(int mins)    { afkDelayMs = Math.max(1, mins) * 60000L; }
    public static boolean isProportionalSleepEnabled()    { return proportionalSleepEnabled; }
    public static boolean isTreeCapitatorEnabled()        { return treeCapitatorEnabled; }
    public static boolean isFastLeafDecayEnabled()        { return fastLeafDecayEnabled; }
    public static boolean isDoubleDoorEnabled()           { return doubleDoorEnabled; }
    public static boolean isRightClickHarvestEnabled()    { return rightClickHarvestEnabled; }
    public static boolean isDispenserHarvestEnabled()     { return dispenserHarvestEnabled; }
    public static void setChatLocked(boolean v)               { chatLocked = v; }
    public static void setWeatherCycleEnabled(boolean v)      { weatherCycleEnabled = v; }
    public static void setCropTrampleEnabled(boolean v)       { cropTrampleEnabled = v; }
    public static void setAfkAutoEnabled(boolean v)           { afkAutoEnabled = v; }
    public static void setProportionalSleepEnabled(boolean v) { proportionalSleepEnabled = v; }
    public static void setTreeCapitatorEnabled(boolean v)     { treeCapitatorEnabled = v; }
    public static void setFastLeafDecayEnabled(boolean v)     { fastLeafDecayEnabled = v; }
    public static void setDoubleDoorEnabled(boolean v)        { doubleDoorEnabled = v; }
    public static void setRightClickHarvestEnabled(boolean v) { rightClickHarvestEnabled = v; }
    public static void setDispenserHarvestEnabled(boolean v)  { dispenserHarvestEnabled = v; }
    public static boolean isMailSpyEnabled()                   { return mailSpyEnabled; }
    public static void setMailSpyEnabled(boolean v)            { mailSpyEnabled = v; }
    /** Spy MP : relaie un message privé aux admins en ligne (hors expéditeur/destinataire). */
    public static void spyPrivateMessage(net.minecraft.server.MinecraftServer server,
                                         ServerPlayer sender, ServerPlayer target, String msg) {
        if (!mailSpyEnabled) return;
        Component spy = Component.literal("§8[SPY] §7" + sender.getName().getString()
            + " §8→ §7" + target.getName().getString() + " §8: §f" + msg);
        for (ServerPlayer op : server.getPlayerList().getPlayers())
            if (op.hasPermissions(2) && !op.getUUID().equals(sender.getUUID()) && !op.getUUID().equals(target.getUUID()))
                op.sendSystemMessage(spy);
    }
    public static int    getMaxHomes()                    { return maxHomes; }
    public static void   setMaxHomes(int v)               { maxHomes = Math.max(1, Math.min(10, v)); }
    public static String getWebhookReports()              { return webhookReports; }
    public static void   setWebhookReports(String v)      { webhookReports   = v == null ? "" : v; }
    public static String getWebhookSanctions()            { return webhookSanctions; }
    public static void   setWebhookSanctions(String v)    { webhookSanctions = v == null ? "" : v; }
    public static String getMotd()                        { return motd; }
    public static void   setMotd(String v)                { motd = v == null ? "" : v.replace("|", " ").trim(); }
    /** Historique du chat public (300 derniers messages), consultable depuis l'onglet LOGS. */
    public static void addChatHistory(String playerName, String message) {
        String time = java.time.LocalTime.now().format(LOG_FMT);
        synchronized (chatHistory) {
            chatHistory.add("[" + time + "] <" + playerName + "> " + message);
            while (chatHistory.size() > 300) chatHistory.remove(0);
        }
    }
    public static String getChatHistorySerialized() {
        synchronized (chatHistory) { return String.join("\n", chatHistory); }
    }
    static java.util.List<String> getChatHistoryRaw() { return chatHistory; }
    // ── Notes admin (modération, visibles uniquement des admins) ─────────────
    public static java.util.Map<java.util.UUID, java.util.List<String>> getAdminNotes() { return adminNotes; }
    private static String cleanNote(String note) {
        // On garde ':' (séparateur uniquement entre nom et notes, sur le 1er ':'). '' sépare les notes.
        return note == null ? "" : note.replace("\n", " ").replace("|", " ").replace("\t", " ").replace('', ' ').trim();
    }
    /** Ajoute une note à un joueur (max 15). Renvoie false si vide ou limite atteinte. */
    public static boolean addAdminNote(java.util.UUID uuid, String note) {
        String clean = cleanNote(note);
        if (clean.isEmpty()) return false;
        java.util.List<String> list = adminNotes.computeIfAbsent(uuid, k -> new java.util.ArrayList<>());
        if (list.size() >= 15) return false;
        list.add(clean);
        return true;
    }
    /** Supprime la note d'index donné. */
    public static boolean removeAdminNote(java.util.UUID uuid, int index) {
        java.util.List<String> list = adminNotes.get(uuid);
        if (list == null || index < 0 || index >= list.size()) return false;
        list.remove(index);
        if (list.isEmpty()) adminNotes.remove(uuid);
        return true;
    }
    /** "nom:note1note2…" par ligne (un joueur par ligne), pour le dashboard. */
    public static String getAdminNotesSerialized() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<java.util.UUID, java.util.List<String>> e : adminNotes.entrySet()) {
            String name = playerNameCache.get(e.getKey());
            if (name == null || e.getValue().isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(name).append(':').append(String.join("", e.getValue()));
        }
        return sb.toString();
    }
    public static boolean isFrozen(java.util.UUID uuid)   { return frozenPlayers.getOrDefault(uuid, false); }
    public static boolean isMuted(java.util.UUID uuid)    { return mutedPlayers.contains(uuid); }
    public static boolean isVanished(java.util.UUID uuid) { return vanishedPlayers.contains(uuid); }
    public static boolean isAfk(java.util.UUID uuid)      { return afkPlayers.getOrDefault(uuid, false); }
    public static java.util.Map<java.util.UUID, Long> getLastSeenTimestamps()              { return lastSeenTimestamps; }
    public static int  getCooldownHome()                   { return cooldownHome; }
    public static int  getCooldownBack()                   { return cooldownBack; }
    public static int  getCooldownTpa()                    { return cooldownTpa; }
    public static void setCooldownHome(int v)              { cooldownHome = v; }
    public static void setCooldownBack(int v)              { cooldownBack = v; }
    public static void setCooldownTpa(int v)               { cooldownTpa  = v; }
    public static int  getCooldownWarp()                   { return cooldownWarp; }
    public static void setCooldownWarp(int v)              { cooldownWarp = v; }
    public static int  getCooldownRtp()                    { return cooldownRtp; }
    public static void setCooldownRtp(int v)               { cooldownRtp = Math.max(0, v); }
    public static java.util.Map<java.util.UUID, Long> getLastRtpUse() { return lastRtpUse; }
    public static java.util.Map<String, net.minecraft.core.BlockPos> getWarps()    { return warps; }
    public static java.util.Map<String, String>                      getWarpsDim() { return warpsDim; }
    public static java.util.Map<java.util.UUID, Long>           getLastWarpUse()           { return lastWarpUse; }
    public static java.util.Map<java.util.UUID, Long>           getPendingTpaTimestamps()   { return pendingTpaTimestamps; }
    public static java.util.Map<java.util.UUID, Long>           getPendingDealTimestamps()  { return pendingDealTimestamps; }
    public static boolean isIgnoring(java.util.UUID ignorer, java.util.UUID target) {
        return getPlayerSettings(ignorer).ignoredPlayers.contains(target);
    }
    public static boolean isLocked(net.minecraft.core.BlockPos pos)  { return lockedBlocks.containsKey(pos); }
    public static java.util.UUID getOwner(net.minecraft.core.BlockPos pos) { return lockedBlocks.get(pos); }
    public static boolean isTrusted(java.util.UUID owner, java.util.UUID player) { return trustedPlayers.getOrDefault(owner, java.util.Collections.emptySet()).contains(player); }
    public static java.util.Set<java.util.UUID> getTrusted(java.util.UUID owner) { return trustedPlayers.computeIfAbsent(owner, k -> new java.util.HashSet<>()); }
    public static java.util.Map<net.minecraft.core.BlockPos, java.util.UUID> getAllLockedBlocks()          { return lockedBlocks; }
    public static java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> getAllTrustedPlayers()      { return trustedPlayers; }
    public static java.util.Map<java.util.UUID, String> getPlayerNameCache()                               { return playerNameCache; }
    public static java.util.Map<java.util.UUID, Integer> getAllHostileMobKills()                           { return hostileMobKills; }
    public static java.util.List<String[]> getSanctionsLog()                                               { return sanctionsLog; }
    public static String[] getScheduledMsgsArray()     { return scheduledMsgs.toArray(new String[0]); }
    public static int[]    getScheduledIntervalsArray() { return scheduledIntervals.stream().mapToInt(i -> i).toArray(); }
    public static String getGamemodeName(ServerPlayer player) { return player.gameMode.getGameModeForPlayer().getName(); }

    // ─── Serialization helpers ────────────────────────────────────────────────
    public static String getFeaturesSerialized() {
        return afkAutoEnabled + "|" + proportionalSleepEnabled + "|" + treeCapitatorEnabled
             + "|" + fastLeafDecayEnabled + "|" + doubleDoorEnabled + "|" + getAfkDelayMinutes()
             + "|" + rightClickHarvestEnabled + "|" + dispenserHarvestEnabled
             + "|" + cropTrampleEnabled + "|" + maxHomes
             + "|" + webhookReports + "|" + webhookSanctions + "|" + motd + "|" + mailSpyEnabled;
    }
    public static String getMutedPlayerNames(net.minecraft.server.MinecraftServer server) {
        return mutedPlayers.stream().map(uuid -> server.getPlayerList().getPlayer(uuid)).filter(p -> p != null).map(p -> p.getName().getString()).collect(java.util.stream.Collectors.joining(";"));
    }
    public static String getFrozenPlayerNames(net.minecraft.server.MinecraftServer server) {
        return frozenPlayers.entrySet().stream().filter(java.util.Map.Entry::getValue).map(e -> server.getPlayerList().getPlayer(e.getKey())).filter(p -> p != null).map(p -> p.getName().getString()).collect(java.util.stream.Collectors.joining(";"));
    }
    public static String getReportsSerialized()         { return serializeReports(pendingReports);  }
    public static String getAcceptedReportsSerialized() { return serializeReports(acceptedReports); }
    public static String getClosedReportsSerialized()   { return serializeReports(closedReports);   }
    public static String getCommandsSerialized()        { return playerCommands.stream().map(e -> e[0] + ":" + e[1]).collect(java.util.stream.Collectors.joining("|")); }
    public static String getSanctionsSerialized() {
        if (sanctionsLog.isEmpty()) return "";
        int start = Math.max(0, sanctionsLog.size() - 50);
        java.util.List<String[]> last = sanctionsLog.subList(start, sanctionsLog.size());
        StringBuilder sb = new StringBuilder();
        for (int i = last.size() - 1; i >= 0; i--) {
            if (sb.length() > 0) sb.append('|');
            String[] e = last.get(i);
            sb.append(e[0]).append('\t').append(e[1]).append('\t').append(e[2]).append('\t').append(e[3]).append('\t').append(e[4]);
        }
        return sb.toString();
    }
    public static String getAfkPlayerNames(net.minecraft.server.MinecraftServer server) {
        return afkPlayers.entrySet().stream().filter(java.util.Map.Entry::getValue).map(e -> server.getPlayerList().getPlayer(e.getKey())).filter(p -> p != null).map(p -> p.getName().getString()).collect(java.util.stream.Collectors.joining(";"));
    }
    /** Joueurs connus mais hors ligne : "nom:lastSeenMs;..." triés du plus récent au plus ancien (20 max). */
    public static String getOfflinePlayersSerialized(net.minecraft.server.MinecraftServer server) {
        java.util.Set<java.util.UUID> online = new java.util.HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) online.add(p.getUUID());
        return lastSeenTimestamps.entrySet().stream()
            .filter(e -> !online.contains(e.getKey()) && playerNameCache.containsKey(e.getKey()))
            .sorted(java.util.Map.Entry.<java.util.UUID, Long>comparingByValue().reversed())
            .limit(20)
            .map(e -> playerNameCache.get(e.getKey()) + ":" + e.getValue())
            .collect(java.util.stream.Collectors.joining(";"));
    }
    /** Santé serveur : "tps|mspt|ramUsedMo|ramMaxMo|entités|chunks|uptimeSec". */
    public static String getServerStatsSerialized(net.minecraft.server.MinecraftServer server) {
        double mspt = server.getAverageTickTimeNanos() / 1.0e6;
        double tps  = Math.min(20.0, 1000.0 / Math.max(mspt, 0.001));
        long ramUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        long ramMax  = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        int entities = 0, chunks = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity ignored : level.getAllEntities()) entities++;
            chunks += level.getChunkSource().getLoadedChunksCount();
        }
        long uptimeSec = server.getTickCount() / 20L;
        return String.format(java.util.Locale.ROOT, "%.1f|%.1f|%d|%d|%d|%d|%d",
            tps, mspt, ramUsed, ramMax, entities, chunks, uptimeSec);
    }
    /** Warps publics : "nom:x,y,z,dim;..." (le nom d'un warp ne contient ni ':' ni ';' — /setwarp prend un mot). */
    public static String getWarpsSerialized() {
        java.util.StringJoiner sj = new java.util.StringJoiner(";");
        for (java.util.Map.Entry<String, net.minecraft.core.BlockPos> e : warps.entrySet()) {
            net.minecraft.core.BlockPos p = e.getValue();
            sj.add(e.getKey() + ":" + p.getX() + "," + p.getY() + "," + p.getZ() + ","
                + warpsDim.getOrDefault(e.getKey(), "minecraft:overworld"));
        }
        return sj.toString();
    }
    public static String getKeepInventoryPlayerNames(net.minecraft.server.MinecraftServer server) {
        return playerSettings.entrySet().stream().filter(e -> e.getValue().keepInventory).map(e -> server.getPlayerList().getPlayer(e.getKey())).filter(p -> p != null).map(p -> p.getName().getString()).collect(java.util.stream.Collectors.joining(";"));
    }
    public static String getCooldownsSerialized()   { return cooldownHome + "|" + cooldownBack + "|" + cooldownTpa + "|" + cooldownWarp; }
    public static String getScheduledBroadcastsSerialized() {
        if (scheduledMsgs.isEmpty()) return "";
        java.util.StringJoiner sj = new java.util.StringJoiner("|");
        for (int i = 0; i < scheduledMsgs.size(); i++) sj.add(scheduledMsgs.get(i) + "\t" + (scheduledIntervals.get(i) / 1200));
        return sj.toString();
    }
    public static String getBannedPlayersSerialized(net.minecraft.server.MinecraftServer server) {
        java.util.StringJoiner sj = new java.util.StringJoiner("|");
        for (net.minecraft.server.players.UserBanListEntry entry : server.getPlayerList().getBans().getEntries()) {
            String name   = entry.getDisplayName().getString();
            String reason = entry.getReason() != null ? entry.getReason().replace("|", " ").replace(":", " ") : "";
            sj.add(name + ":" + reason);
        }
        return sj.toString();
    }
    private static String serializeReports(java.util.Map<String, String> map) {
        return map.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue().replace("|", " ")).collect(java.util.stream.Collectors.joining("|"));
    }

    // ─── Mutation helpers ─────────────────────────────────────────────────────
    public static void addScheduledBroadcast(String msg, int intervalTicks) {
        scheduledMsgs.add(msg);
        scheduledIntervals.add(intervalTicks);
        scheduledCounters.add(intervalTicks);
    }
    public static boolean removeScheduledBroadcast(int idx) {
        if (idx < 0 || idx >= scheduledMsgs.size()) return false;
        scheduledMsgs.remove(idx);
        scheduledIntervals.remove(idx);
        scheduledCounters.remove(idx);
        return true;
    }
    private static final int MAX_LOGS_PER_PLAYER = 200;
    static void addLog(java.util.UUID uuid, String entry) {
        String time = java.time.LocalTime.now().format(LOG_FMT);
        java.util.List<String> logs = playerLogs.computeIfAbsent(uuid, k -> new java.util.ArrayList<>());
        logs.add("[" + time + "] " + entry);
        // Cap the per-player log to avoid unbounded growth over a long-running server session.
        while (logs.size() > MAX_LOGS_PER_PLAYER) logs.remove(0);
    }
    static void addSanction(String type, String player, String admin, String reason) {
        String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
        if (sanctionsLog.size() >= 200) sanctionsLog.remove(0);
        sanctionsLog.add(new String[]{ ts, type, player, admin, reason.isEmpty() ? "—" : reason });
        SanctionsPersistence.save();
    }

    // ─── Journal d'actions admin (onglet AUDIT) ───────────────────────────────
    public static java.util.List<String[]> getAuditLog() { return auditLog; }
    /** Actions du dashboard à NE PAS journaliser (lectures / rafraîchissements). */
    private static final java.util.Set<String> NON_AUDITED = java.util.Set.of(
        "REFRESH_ADMIN", "GET_LOGS", "GET_CHAT", "GET_SANCTIONS", "GET_AUDIT",
        "FETCH_REPORT_IMAGE", "OPEN_ZONES");
    public static boolean isAuditable(String action) { return !NON_AUDITED.contains(action); }
    public static void addAudit(String admin, String action, String target, String detail) {
        String ts  = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
        String tgt = (target == null || target.isBlank()) ? "—" : target;
        String det = detail == null ? "" : detail.replace('\t', ' ').replace('|', ' ').replace('\n', ' ').trim();
        if (det.length() > 60) det = det.substring(0, 60) + "…";
        if (det.isEmpty()) det = "—";
        if (auditLog.size() >= 200) auditLog.remove(0);
        auditLog.add(new String[]{ ts, admin, action, tgt, det });
        AuditPersistence.save();
    }
    public static String getAuditSerialized() {
        if (auditLog.isEmpty()) return "";
        int start = Math.max(0, auditLog.size() - 60);
        java.util.List<String[]> last = auditLog.subList(start, auditLog.size());
        StringBuilder sb = new StringBuilder();
        for (int i = last.size() - 1; i >= 0; i--) {
            if (sb.length() > 0) sb.append('|');
            String[] e = last.get(i);
            sb.append(e[0]).append('\t').append(e[1]).append('\t').append(e[2]).append('\t').append(e[3]).append('\t').append(e[4]);
        }
        return sb.toString();
    }
    static void savePosition(ServerPlayer player)       { lastPositions.put(player.getUUID(), player.position()); lastPositionDims.put(player.getUUID(), player.level().dimension()); }
    static java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> getLastPositions() { return lastPositions; }
    static java.util.Map<java.util.UUID, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> getLastPositionDims() { return lastPositionDims; }
    static java.util.Map<java.util.UUID, Long> getLastActivityTime()       { return lastActivityTime; }
    static java.util.Map<java.util.UUID, Long> getLastHomeUse()             { return lastHomeUse; }
    static java.util.Map<java.util.UUID, Long> getLastBackUse()             { return lastBackUse; }
    static java.util.Map<java.util.UUID, Long> getLastTpaUse()              { return lastTpaUse; }
    static java.util.Map<java.util.UUID, java.util.UUID> getTpaRequests()   { return tpaRequests; }
    static java.util.Set<java.util.UUID> getTpaHere()                       { return tpaHere; }
    static java.util.Map<java.util.UUID, java.util.UUID> getLastMsg()       { return lastMsg; }
    static java.util.Map<java.util.UUID, java.util.UUID> getPendingDeals()  { return pendingDeals; }
    static int getLeafDecayCounter()                   { return leafDecayCounter; }
    static void setLeafDecayCounter(int v)             { leafDecayCounter = v; }
    static java.util.Map<java.util.UUID, java.util.List<String>> getPlayerLogs() { return playerLogs; }

    public static boolean checkCooldown(java.util.Map<java.util.UUID, Long> map, java.util.UUID uuid,
                                        int seconds, ServerPlayer player, String label) {
        long now  = System.currentTimeMillis();
        Long last = map.get(uuid);
        if (last != null) {
            long remaining = seconds - (now - last) / 1000;
            if (remaining > 0) {
                player.sendSystemMessage(Component.literal("§cAttendez encore §e" + remaining + "s §cavant de réutiliser §6" + label + "§c."));
                return false;
            }
        }
        map.put(uuid, now);
        return true;
    }

    /** Durée compacte : "3j 4h", "2h 30m", "45m", "30s"… (2 unités max). */
    public static String formatDurationShort(long seconds) {
        long d = seconds / 86400, h = (seconds % 86400) / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (d > 0) return d + "j" + (h > 0 ? " " + h + "h" : "");
        if (h > 0) return h + "h" + (m > 0 ? " " + m + "m" : "");
        if (m > 0) return m + "m" + (s > 0 ? " " + s + "s" : "");
        return s + "s";
    }

    public static String formatTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long sec  = diff / 1000;
        if (sec < 60)  return sec + " seconde"  + (sec  > 1 ? "s" : "");
        long min  = sec / 60;
        if (min < 60)  return min + " minute"   + (min  > 1 ? "s" : "");
        long hr   = min / 60; long m = min % 60;
        if (hr  < 24)  return hr  + "h" + (m > 0 ? m + "m" : "");
        long day  = hr  / 24; long h = hr  % 24;
        return day + " jour" + (day > 1 ? "s" : "") + (h > 0 ? " " + h + "h" : "");
    }

    // ─── Deal helpers ─────────────────────────────────────────────────────────
    static void sendDealPayload(ServerPlayer player, DealSession session) {
        String partnerName = playerNameCache.getOrDefault(session.partner(player.getUUID()), "???");
        boolean myAcc    = session.isAccepted(player.getUUID());
        boolean theirAcc = session.isAccepted(session.partner(player.getUUID()));
        java.util.List<ItemStack> myItems    = new java.util.ArrayList<>(session.myOffer(player.getUUID()));
        java.util.List<ItemStack> theirItems = new java.util.ArrayList<>(session.theirOffer(player.getUUID()));
        PacketDistributor.sendToPlayer(player, new OpenDealPayload(partnerName, false, "", myAcc, theirAcc, myItems, theirItems));
    }
    static void broadcastDealUpdate(DealSession session, net.minecraft.server.MinecraftServer server) {
        ServerPlayer p1 = server.getPlayerList().getPlayer(session.player1);
        ServerPlayer p2 = server.getPlayerList().getPlayer(session.player2);
        if (p1 != null) sendDealPayload(p1, session);
        if (p2 != null) sendDealPayload(p2, session);
    }
    static void cancelDeal(DealSession session, net.minecraft.server.MinecraftServer server, String reasonP1, String reasonP2) {
        activeSessions.remove(session.player1);
        activeSessions.remove(session.player2);
        for (boolean isP1 : new boolean[]{true, false}) {
            java.util.UUID uid = isP1 ? session.player1 : session.player2;
            ServerPlayer p    = server.getPlayerList().getPlayer(uid);
            NonNullList<ItemStack> offer = isP1 ? session.offer1 : session.offer2;
            for (ItemStack stack : offer) if (!stack.isEmpty() && p != null) p.getInventory().add(stack.copy());
            if (p != null) PacketDistributor.sendToPlayer(p, new OpenDealPayload("", true, isP1 ? reasonP1 : reasonP2, false, false, java.util.List.of(), java.util.List.of()));
        }
    }
    static void completeDeal(DealSession session, net.minecraft.server.MinecraftServer server) {
        activeSessions.remove(session.player1);
        activeSessions.remove(session.player2);
        ServerPlayer p1 = server.getPlayerList().getPlayer(session.player1);
        ServerPlayer p2 = server.getPlayerList().getPlayer(session.player2);
        for (ItemStack stack : session.offer2) if (!stack.isEmpty() && p1 != null) p1.getInventory().add(stack.copy());
        for (ItemStack stack : session.offer1) if (!stack.isEmpty() && p2 != null) p2.getInventory().add(stack.copy());
        if (p1 != null) { PacketDistributor.sendToPlayer(p1, new OpenDealPayload("", true, "", false, false, java.util.List.of(), java.util.List.of())); p1.sendSystemMessage(Component.literal("§aÉchange complété !")); }
        if (p2 != null) { PacketDistributor.sendToPlayer(p2, new OpenDealPayload("", true, "", false, false, java.util.List.of(), java.util.List.of())); p2.sendSystemMessage(Component.literal("§aÉchange complété !")); }
    }
    static void handleDealDisconnect(ServerPlayer disconnecting, net.minecraft.server.MinecraftServer server) {
        java.util.UUID uid = disconnecting.getUUID();
        pendingDealTimestamps.remove(uid);
        pendingDeals.entrySet().removeIf(e -> { if (e.getValue().equals(uid)) { pendingDealTimestamps.remove(e.getKey()); return true; } return false; });
        pendingDeals.remove(uid);
        DealSession session = activeSessions.remove(uid);
        if (session == null) return;
        activeSessions.remove(session.partner(uid));
        NonNullList<ItemStack> myOffer = session.myOffer(uid);
        for (ItemStack stack : myOffer) if (!stack.isEmpty()) disconnecting.getInventory().add(stack.copy());
        java.util.UUID partnerUUID = session.partner(uid);
        ServerPlayer partner = server.getPlayerList().getPlayer(partnerUUID);
        if (partner != null) {
            NonNullList<ItemStack> po = session.myOffer(partnerUUID);
            for (ItemStack stack : po) if (!stack.isEmpty()) partner.getInventory().add(stack.copy());
            PacketDistributor.sendToPlayer(partner, new OpenDealPayload("", true, disconnecting.getName().getString() + " s'est déconnecté. Échange annulé, items restitués.", false, false, java.util.List.of(), java.util.List.of()));
        }
    }

    // ─── Tree capitator helpers ───────────────────────────────────────────────
    static void scheduleNearbyLeaves(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos pos) {
        var set = pendingLeafDecay.computeIfAbsent(world.dimension(), k -> new java.util.LinkedHashSet<>());
        for (int dx = -4; dx <= 4; dx++) for (int dy = -1; dy <= 5; dy++) for (int dz = -4; dz <= 4; dz++) {
            net.minecraft.core.BlockPos lp = pos.offset(dx, dy, dz);
            if (world.getBlockState(lp).is(net.minecraft.tags.BlockTags.LEAVES)) set.add(lp.immutable());
        }
    }
    static java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, java.util.Set<net.minecraft.core.BlockPos>> getPendingLeafDecayMap() { return pendingLeafDecay; }
    static void addLogNeighbors(net.minecraft.world.level.LevelAccessor world, net.minecraft.core.BlockPos pos,
                                  java.util.Set<net.minecraft.core.BlockPos> visited, java.util.Queue<net.minecraft.core.BlockPos> queue) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            net.minecraft.core.BlockPos nb = pos.relative(dir);
            if (visited.add(nb) && world.getBlockState(nb).is(net.minecraft.tags.BlockTags.LOGS)) queue.add(nb);
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            net.minecraft.core.BlockPos nb = pos.offset(dx, 0, dz);
            if (visited.add(nb) && world.getBlockState(nb).is(net.minecraft.tags.BlockTags.LOGS)) queue.add(nb);
            nb = pos.offset(dx, 1, dz);
            if (visited.add(nb) && world.getBlockState(nb).is(net.minecraft.tags.BlockTags.LOGS)) queue.add(nb);
        }
    }

    // ─── Dispenser hoe harvest ────────────────────────────────────────────────
    private static void registerDispenserHoeBehavior() {
        net.minecraft.core.dispenser.DispenseItemBehavior behavior = (source, stack) -> {
            if (!dispenserHarvestEnabled) return stack;
            net.minecraft.server.level.ServerLevel level = source.level();
            net.minecraft.core.BlockPos pos     = source.pos();
            net.minecraft.core.Direction facing = source.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
            int effLevel = 0, fortuneLevel = 0;
            for (var entry : stack.getEnchantments().entrySet()) {
                var optKey = entry.getKey().unwrapKey();
                if (optKey.isEmpty()) continue;
                String path = optKey.get().location().getPath();
                int lvl = entry.getIntValue();
                if ("efficiency".equals(path)) effLevel = lvl;
                else if ("fortune".equals(path)) fortuneLevel = lvl;
            }
            int range  = 5 + effLevel * 4;
            int radius = (range - 1) / 2;
            net.minecraft.core.BlockPos center = pos.relative(facing);
            boolean isVertical = (facing.getAxis() == net.minecraft.core.Direction.Axis.Y);
            for (int a = -radius; a <= radius; a++) for (int b = -radius; b <= radius; b++) {
                dispenserHarvestBlock(level, center.offset(a, 0, b), stack, fortuneLevel);
                if (!isVertical) dispenserHarvestBlock(level, center.offset(a, -1, b), stack, fortuneLevel);
            }
            return stack;
        };
        for (net.minecraft.world.item.Item hoe : new net.minecraft.world.item.Item[]{
            net.minecraft.world.item.Items.WOODEN_HOE, net.minecraft.world.item.Items.STONE_HOE,
            net.minecraft.world.item.Items.IRON_HOE,   net.minecraft.world.item.Items.GOLDEN_HOE,
            net.minecraft.world.item.Items.DIAMOND_HOE, net.minecraft.world.item.Items.NETHERITE_HOE
        }) net.minecraft.world.level.block.DispenserBlock.registerBehavior(hoe, behavior);
    }
    private static void dispenserHarvestBlock(net.minecraft.server.level.ServerLevel level,
                                               net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack hoe, int fortuneLevel) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();
        java.util.List<net.minecraft.world.item.ItemStack> drops = null;
        net.minecraft.world.level.block.state.BlockState resetState = null;
        if (block instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(state)) {
            drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, null, hoe);
            resetState = crop.getStateForAge(0);
        } else if (block instanceof net.minecraft.world.level.block.NetherWartBlock && state.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) == 3) {
            drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, null, hoe);
            resetState = state.setValue(net.minecraft.world.level.block.NetherWartBlock.AGE, 0);
        } else if (block instanceof net.minecraft.world.level.block.CocoaBlock && state.getValue(net.minecraft.world.level.block.CocoaBlock.AGE) == 2) {
            drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, null, hoe);
            resetState = state.setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 0);
        } else if (state.is(net.minecraft.world.level.block.Blocks.SUGAR_CANE) && level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.SUGAR_CANE)) {
            drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, null, hoe);
            resetState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        } else if (state.is(net.minecraft.world.level.block.Blocks.CACTUS) && level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.CACTUS)) {
            drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, null, hoe);
            resetState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        if (drops == null) return;
        // Block.getDrops already applies fortune via the tool parameter — no manual multiplication
        level.setBlock(pos, resetState, 3);
        drops.forEach(d -> net.minecraft.world.level.block.Block.popResource(level, pos, d));
    }

    // ─── Command registration ─────────────────────────────────────────────────
    public static void registerTpaCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
            .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
            .executes(context -> {
                ServerPlayer sender = context.getSource().getPlayerOrException();
                ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                if (sender == target) return 0;
                if (Fabric.test.command.ZoneCommand.isInBuildMode(sender.getUUID())) {
                    sender.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/tpa §cen mode construction."));
                    return 0;
                }
                if (!checkCooldown(lastTpaUse, sender.getUUID(), cooldownTpa, sender, "/tpa")) return 0;
                if (!getPlayerSettings(target.getUUID()).allowTpaRequests) {
                    sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'accepte pas les demandes de téléportation."));
                    return 0;
                }
                tpaRequests.put(target.getUUID(), sender.getUUID());
                pendingTpaTimestamps.put(target.getUUID(), System.currentTimeMillis());
                Component msg = Component.literal(sender.getName().getString() + " veut se tp à vous. ")
                    .append(Component.literal("[OUI]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/tpaccept"))))
                    .append(Component.literal(" "))
                    .append(Component.literal("[NON]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.RED).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/tpdeny"))));
                target.sendSystemMessage(msg);
                sender.sendSystemMessage(Component.literal("§aRequête envoyée à " + target.getName().getString()));
                return 1;
            })));
        dispatcher.register(Commands.literal("tpahere")
            .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
            .executes(context -> {
                ServerPlayer sender = context.getSource().getPlayerOrException();
                ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target");
                if (sender == target) return 0;
                if (Fabric.test.command.ZoneCommand.isInBuildMode(sender.getUUID())) {
                    sender.sendSystemMessage(Component.literal("§cImpossible d'utiliser §6/tpahere §cen mode construction."));
                    return 0;
                }
                if (!checkCooldown(lastTpaUse, sender.getUUID(), cooldownTpa, sender, "/tpahere")) return 0;
                if (!getPlayerSettings(target.getUUID()).allowTpaRequests) {
                    sender.sendSystemMessage(Component.literal("§c" + target.getName().getString() + " n'accepte pas les demandes de téléportation."));
                    return 0;
                }
                tpaRequests.put(target.getUUID(), sender.getUUID());
                tpaHere.add(target.getUUID());
                pendingTpaTimestamps.put(target.getUUID(), System.currentTimeMillis());
                Component msg = Component.literal(sender.getName().getString() + " veut que vous vous tp à lui. ")
                    .append(Component.literal("[OUI]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/tpaccept"))))
                    .append(Component.literal(" "))
                    .append(Component.literal("[NON]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.RED).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/tpdeny"))));
                target.sendSystemMessage(msg);
                sender.sendSystemMessage(Component.literal("§aRequête envoyée à " + target.getName().getString()));
                return 1;
            })));
        dispatcher.register(Commands.literal("tpaccept").executes(context -> {
            ServerPlayer target = context.getSource().getPlayerOrException();
            java.util.UUID senderUUID = tpaRequests.remove(target.getUUID());
            pendingTpaTimestamps.remove(target.getUUID());
            boolean here = tpaHere.remove(target.getUUID());
            if (senderUUID == null) { target.sendSystemMessage(Component.literal("§cAucune demande en attente.")); return 0; }
            String senderName = playerNameCache.getOrDefault(senderUUID, "?");
            ServerPlayer sender = context.getSource().getServer().getPlayerList().getPlayer(senderUUID);
            if (sender == null) {
                target.sendSystemMessage(Component.literal("§c" + senderName + " n'est plus connecté."));
                return 0;
            }
            if (here) {
                // /tpahere : c'est l'accepteur (target) qui rejoint le demandeur (sender).
                target.teleportTo((ServerLevel)sender.level(), sender.getX(), sender.getY(), sender.getZ(), Set.of(), target.getYRot(), target.getXRot());
            } else {
                sender.teleportTo((ServerLevel)target.level(), target.getX(), target.getY(), target.getZ(), Set.of(), sender.getYRot(), sender.getXRot());
            }
            target.sendSystemMessage(Component.literal("§a✔ Vous avez accepté la demande de §e" + sender.getName().getString() + "§a."));
            sender.sendSystemMessage(Component.literal("§a✔ §e" + target.getName().getString() + "§a a accepté — téléporté !"));
            return 1;
        }));
        dispatcher.register(Commands.literal("tpdeny").executes(context -> {
            ServerPlayer target = context.getSource().getPlayerOrException();
            pendingTpaTimestamps.remove(target.getUUID());
            tpaHere.remove(target.getUUID());
            java.util.UUID senderUUID = tpaRequests.remove(target.getUUID());
            if (senderUUID == null) { target.sendSystemMessage(Component.literal("§cAucune demande en attente.")); return 0; }
            String senderName = playerNameCache.getOrDefault(senderUUID, "?");
            ServerPlayer sender = context.getSource().getServer().getPlayerList().getPlayer(senderUUID);
            target.sendSystemMessage(Component.literal("§c✘ Vous avez refusé la demande de §e" + senderName + "§c."));
            if (sender != null) sender.sendSystemMessage(Component.literal("§c✘ §e" + target.getName().getString() + "§c a refusé votre demande de téléportation."));
            return 1;
        }));
    }
}
