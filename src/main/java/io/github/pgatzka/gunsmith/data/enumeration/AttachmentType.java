package io.github.pgatzka.gunsmith.data.enumeration;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public enum AttachmentType {
    BARREL("Barrel"),
    SILENCER("Silencer"),
    PISTOL_GRIP("Pistol grip"),
    MOUNT("Mount"),
    CYLINDER_MAGAZINE("Cylinder Magazine"),
    COMPACT_REFLEX_SIGHT("Compact reflex sight"),
    SPRING_DRIVEN_CYLINDER("Spring Driven Cylinder"),
    FLASH_HIDER("Flashhider"),
    SPECIAL_SCOPE("Special scope"),
    REFLEX_SIGHT("Reflex sight"),
    GAS_BLOCK("Gas block"),
    STOCK("Stock"),
    HANDGUARD("Handguard"),
    FLASHLIGHT("Flashlight"),
    SCOPE("Scope"),
    AUXILIARY_MOD("Auxiliary Mod"),
    BIPOD("Bipod"),
    ASSAULT_SCOPE("Assault scope"),
    FOREGRIP("Foregrip"),
    CHARGING_HANDLE("Charging handle"),
    UNDERBARREL_GRENADE_LAUNCHER("UBGL"),
    RECEIVER("Receiver"),
    IRON_SIGHT("Ironsight"),
    COMBINED_TACTICAL_DEVICE("Comb. tact. device"),
    COMBINED_MUZZLE_DEVICE("Comb. muzzle device"),
    MAGAZINE("Magazine"),


    ;

    private static final Map<String, AttachmentType> INDEX =
            Arrays.stream(values())
                    .collect(Collectors.toUnmodifiableMap(w -> w.name, Function.identity()));

    private final String name;

    public static AttachmentType lookup(String name) {
        AttachmentType type = INDEX.get(name);
        if (type == null) {
            throw new IllegalArgumentException("Unknown attachment type: " + name);
        }
        return type;
    }
}
