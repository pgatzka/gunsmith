package io.github.pgatzka.gunsmith.data.repository;

import io.github.pgatzka.gunsmith.data.entity.SlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SlotRepository extends JpaRepository<SlotEntity, Long> {

}