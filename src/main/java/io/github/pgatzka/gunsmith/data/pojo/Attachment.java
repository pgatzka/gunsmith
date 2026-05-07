package io.github.pgatzka.gunsmith.data.pojo;

import lombok.Data;

import java.util.List;

@Data
public class Attachment {

    private Long id;

    private List<Slot> slots;

}
