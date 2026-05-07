package io.github.pgatzka.gunsmith.data.entity;

import io.github.pgatzka.gunsmith.data.AbstractEntity;
import io.github.pgatzka.gunsmith.data.Cached;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "weapon")
public class WeaponEntity extends AbstractEntity implements Cached {

    @Column(name = "tarkov_id", nullable = false, unique = true)
    private String tarkovId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "ergonomics", nullable = false)
    private Double ergonomics;

    @Column(name = "recoil_horizontal", nullable = false)
    private Integer recoilHorizontal;

    @Column(name = "recoil_vertical", nullable = false)
    private Integer recoilVertical;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @CollectionTable(name = "weapon_slot")
    private List<SlotEntity> slots = new ArrayList<>();

}