package io.github.pgatzka.gunsmith.data.entity;

import io.github.pgatzka.gunsmith.data.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "slot")
public class SlotEntity extends AbstractEntity {

    @Column(name = "tarkov_id", nullable = false, unique = true)
    private String tarkovId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "required", nullable = false)
    private Boolean required = false;

    @Column(name = "allowed_attachment_ids")
    private Set<String> allowedAttachmentTarkovIds = new LinkedHashSet<>();

}