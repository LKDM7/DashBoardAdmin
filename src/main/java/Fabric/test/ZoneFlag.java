package Fabric.test;

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
    BUILD       ("Construction",   true,  false),
    INTERACT    ("Interaction",    true,  false),
    CONTAINER   ("Conteneurs",     true,  false),
    ENTRY       ("Entrée",         true,  false),
    ITEM_DROP   ("Jeter items",    true,  false),
    ITEM_PICKUP ("Ramasser items", true,  false),
    PVP         ("PvP",            true,  true),
    MOB_SPAWN   ("Spawn mobs",     true,  true),
    EXPLOSIONS  ("Explosions",     true,  true),
    CROP_TRAMPLE("Piétinement",    true,  true);

    /** Short label shown in the admin GUI. */
    public final String  label;
    /** Default value when a zone has never set this flag explicitly. */
    public final boolean defaultAllowed;
    /** {@code true} = applies to everyone; {@code false} = only non-authorized players. */
    public final boolean areaRule;

    ZoneFlag(String label, boolean defaultAllowed, boolean areaRule) {
        this.label          = label;
        this.defaultAllowed = defaultAllowed;
        this.areaRule       = areaRule;
    }

    /** Parses a flag by its enum name, returning {@code null} if unknown (no exception). */
    public static ZoneFlag byName(String name) {
        if (name == null) return null;
        try { return valueOf(name); } catch (IllegalArgumentException e) { return null; }
    }
}
