package io.github.pgatzka.gunsmith.data.entity;

import io.github.pgatzka.gunsmith.data.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "build")
public class BuildEntity extends AbstractEntity {

    @Column(name = "weapon_id", nullable = false)
    private Long weaponId;

    @Column(name = "ergonomics", nullable = false)
    private Double ergonomics;

    @Column(name = "recoil_horizontal", nullable = false)
    private Double recoilHorizontal;

    @Column(name = "recoil_vertical", nullable = false)
    private Double recoilVertical;

    @Column(name = "json_hash", nullable = false, unique = true)
    private Integer jsonHash;

    // @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String json;

}
