package com.lkdm.dashboardadmin;

/**
 * Permission/behaviour flags attached to a {@link Zone}.
 *
 * <p>Each flag stores whether an action is <b>allowed</b> inside the zone (default {@code true}).
 * Setting a flag to {@code false} turns the corresponding protection on.</p>
 *
 * <ul>
 *   <li><b>Access flags</b> ({@code areaRule == false}) only restrict players who are not
 *       authorized on the zone (non-members). Members, builders and ops bypass them.</li>
 *   <li><b>Area flags</b> ({@code areaRule == true}) apply to everyone inside the zone, since
 *       they describe world behaviour (PvP, spawns, explosions) rather than per-player access.</li>
 * </ul>
 */
public enum ZoneFlag {
    BUILD       ("Construction",   "Building",      true,  false),
    INTERACT    ("Interaction",    "Interaction",   true,  false),
    CONTAINER   ("Conteneurs",     "Containers",    true,  false),
    ENTRY       ("Entrée",         "Entry",         true,  false),
    ITEM_DROP   ("Jeter items",    "Item drop",     true,  false),
    ITEM_PICKUP ("Ramasser items", "Item pickup",   true,  false),
    PVP         ("PvP",            "PvP",           true,  true),
    MOB_SPAWN   ("Spawn mobs",     "Mob spawning",  true,  true),
    EXPLOSIONS  ("Explosions",     "Explosions",    true,  true),
    CROP_TRAMPLE("Piétinement",    "Crop trample",  true,  true);

    /** Short label shown in the admin GUI (French). */
    public final String  label;
    /** Short label shown in the admin GUI (English). */
    public final String  labelEn;
    /** Default value when a zone has never set this flag explicitly. */
    public final boolean defaultAllowed;
    /** {@code true} = applies to everyone; {@code false} = only non-authorized players. */
    public final boolean areaRule;

    ZoneFlag(String label, String labelEn, boolean defaultAllowed, boolean areaRule) {
        this.label          = label;
        this.labelEn        = labelEn;
        this.defaultAllowed = defaultAllowed;
        this.areaRule       = areaRule;
    }

    /** Parses a flag by its enum name, returning {@code null} if unknown (no exception). */
    public static ZoneFlag byName(String name) {
        if (name == null) return null;
        try { return valueOf(name); } catch (IllegalArgumentException e) { return null; }
    }
}
