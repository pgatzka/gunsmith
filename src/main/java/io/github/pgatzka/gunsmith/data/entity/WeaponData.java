package io.github.pgatzka.gunsmith.data.entity;

import io.github.pgatzka.gunsmith.data.AbstractEntity;
import io.github.pgatzka.gunsmith.data.embeddable.SlotData;
import io.github.pgatzka.gunsmith.data.enumeration.WeaponType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "weapon_data")
public class WeaponData extends AbstractEntity {

    @Column(name = "tarkov_id", nullable = false, unique = true)
    private String tarkovId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "weapon_type", nullable = false)
    private WeaponType weaponType;

    @Column(name = "ergonomics", nullable = false)
    private Double ergonomics;

    @Column(name = "recoil_horizontal", nullable = false)
    private Integer recoilHorizontal;

    @Column(name = "recoil_vertical", nullable = false)
    private Integer recoilVertical;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "weapon_slot_data", joinColumns = @JoinColumn(name = "weapon_id"))
    private List<SlotData> slots = new ArrayList<>();

}