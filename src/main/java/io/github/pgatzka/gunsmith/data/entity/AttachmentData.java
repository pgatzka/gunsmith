package io.github.pgatzka.gunsmith.data.entity;

import io.github.pgatzka.gunsmith.data.AbstractEntity;
import io.github.pgatzka.gunsmith.data.embeddable.SlotData;
import io.github.pgatzka.gunsmith.data.enumeration.AttachmentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "attachment_data")
public class AttachmentData extends AbstractEntity {

    @Column(name = "tarkov_id", nullable = false, unique = true)
    private String tarkovId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false)
    private AttachmentType attachmentType;

    @Column(name = "ergonomics", nullable = false)
    private Double ergonomics;

    @Column(name = "recoil_modifier", nullable = false)
    private Double recoilModifier;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "attachment_slot_data", joinColumns = @JoinColumn(name = "attachment_id"))
    private List<SlotData> slots = new ArrayList<>();

    private Set<String> conflictingAttachmentIds = new LinkedHashSet<>();

}