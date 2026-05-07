package io.github.pgatzka.gunsmith.data.entity;

import io.github.pgatzka.gunsmith.data.AbstractEntity;
import io.github.pgatzka.gunsmith.data.enumeration.WeaponType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "build_data")
public class BuildData extends AbstractEntity {

    @Column(name = "json", nullable = false, columnDefinition = "text")
    private String json;

    private Set<String> usedAttachments;

    @Column(name = "ergonomics", nullable = false)
    private Double ergonomics;

    @Column(name = "recoil_horizontal", nullable = false)
    private Double recoilHorizontal;

    @Column(name = "recoil_vertical", nullable = false)
    private Double recoilVertical;

    @Column(name = "weapon_id", nullable = false)
    private String weaponId;

    @Column(name = "json_hash", nullable = false, unique = true)
    private Integer jsonHash;

}
