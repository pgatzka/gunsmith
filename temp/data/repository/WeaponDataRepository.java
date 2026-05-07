package io.github.pgatzka.gunsmith.data.repository;

import io.github.pgatzka.gunsmith.data.AbstractRepository;
import io.github.pgatzka.gunsmith.data.entity.WeaponData;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeaponDataRepository extends AbstractRepository<WeaponData> {

    boolean existsByTarkovId(String tarkovId);

    WeaponData findByTarkovId(String tarkovId);

    @Query("select w.tarkovId from WeaponData w")
    List<String> findAllTarkovIds();
}