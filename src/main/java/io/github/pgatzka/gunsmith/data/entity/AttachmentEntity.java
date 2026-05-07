package io.github.pgatzka.gunsmith.data.entity;

import io.github.pgatzka.gunsmith.data.AbstractEntity;
import io.github.pgatzka.gunsmith.data.Cached;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "attachment")
public class AttachmentEntity extends AbstractEntity implements Cached {

    @Column(name = "tarkov_id", nullable = false, unique = true)
    private String tarkovId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "ergonomics", nullable = false)
    private Double ergonomics;

    @Column(name = "recoil_modifier", nullable = false)
    private Double recoilModifier;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch =  FetchType.EAGER)
    @CollectionTable(name = "attachment_slot")
    private List<SlotEntity> slots = new ArrayList<>();

    private Set<Long> conflictingAttachmentIds = new HashSet<>();

    private Set<Long> conflictingWeaponIds = new HashSet<>();

}