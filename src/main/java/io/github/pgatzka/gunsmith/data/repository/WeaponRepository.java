package io.github.pgatzka.gunsmith.data.repository;

import io.github.pgatzka.gunsmith.data.CachedRepository;
import io.github.pgatzka.gunsmith.data.entity.WeaponEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface WeaponRepository extends CachedRepository<WeaponEntity> {


}