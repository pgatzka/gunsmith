package io.github.pgatzka.gunsmith.data.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Build {

    private String id;

    private String name;

    private Double ergonomics;

    private Integer recoilHorizontal;

    private Integer recoilVertical;

    private Set<Slot> slots;

}
