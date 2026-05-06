package io.github.pgatzka.gunsmith.data.pojo;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Attachment {

    private String id;

    private String name;

    private Double ergonomics;

    private Double recoilModifier;

    private Set<Slot> slots;

}
