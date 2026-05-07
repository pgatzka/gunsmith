package io.github.pgatzka.gunsmith.data.repository;

import io.github.pgatzka.gunsmith.data.AbstractRepository;
import io.github.pgatzka.gunsmith.data.entity.BuildData;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface BuildDataRepository extends AbstractRepository<BuildData> {

    @Query("select buildData.jsonHash from BuildData buildData group by buildData.jsonHash")
    Set<Integer> fetchExistingJsonHashes();

}