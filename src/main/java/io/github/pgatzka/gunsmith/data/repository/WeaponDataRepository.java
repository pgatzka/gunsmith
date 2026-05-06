package io.github.pgatzka.gunsmith.data.repository;

import io.github.pgatzka.gunsmith.data.AbstractRepository;
import io.github.pgatzka.gunsmith.data.entity.WeaponData;
import org.springframework.stereotype.Repository;

@Repository
public interface WeaponDataRepository extends AbstractRepository<WeaponData> {

    boolean existsByTarkovId(String tarkovId);

    WeaponData findByTarkovId(String tarkovId);


}