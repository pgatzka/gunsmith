package io.github.pgatzka.gunsmith.data.embeddable;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Embeddable
public class SlotData {

    private String tarkovId;

    private String name;

    private Boolean required;

    private Set<String> allowedAttachmentIds;

}
