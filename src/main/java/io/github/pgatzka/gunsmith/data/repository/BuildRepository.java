package io.github.pgatzka.gunsmith.data.repository;

import io.github.pgatzka.gunsmith.data.entity.BuildEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface BuildRepository extends JpaRepository<BuildEntity, Long> {

    @Query("select b.jsonHash from BuildEntity b")
    Set<Integer> getJsonHashes();

}
