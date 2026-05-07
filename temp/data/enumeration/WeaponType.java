package io.github.pgatzka.gunsmith.data.enumeration;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public enum WeaponType {
    ASSAULT_RIFLE("Assault rifle"),
    HANDGUN("Handgun"),
    SHOTGUN("Shotgun"),
    SNIPER_RIFLE("Sniper rifle"),
    ASSAULT_CARBINE("Assault carbine"),
    MARKSMAN_RIFLE("Marksman rifle"),
    SMG("SMG"),
    MACHINEGUN("Machinegun"),
    GRENADE_LAUNCHER("Grenade launcher"),
    REVOLVER("Revolver"),
    ROCKET_LAUNCHER("Rocket Launcher");


    private static final Map<String, WeaponType> INDEX =
            Arrays.stream(values())
                    .collect(Collectors.toUnmodifiableMap(w -> w.name, Function.identity()));

    private final String name;

    public static WeaponType lookup(String name) {
        WeaponType type = INDEX.get(name);
        if (type == null) {
            throw new IllegalArgumentException("Unknown weapon type: " + name);
        }
        return type;
    }

}
