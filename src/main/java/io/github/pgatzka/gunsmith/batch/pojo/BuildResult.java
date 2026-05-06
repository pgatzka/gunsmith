package io.github.pgatzka.gunsmith.batch.pojo;

import io.github.pgatzka.gunsmith.data.enumeration.WeaponType;

import java.util.List;
import java.util.Set;

public record BuildResult(String weaponId, WeaponType weaponType, String json, Set<String> usedAttachments, Set<String> conflictingAttachments,
                          List<Double> ergonomicsModifiers, List<Double> recoilModifiers, Double baseErgonomics,
                          Integer baseRecoilHorizontal, Integer baseRecoilVertical) {

    public int jsonHash() {
        return json.hashCode();
    }

}
