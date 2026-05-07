package io.github.pgatzka.gunsmith.data.repository;

import io.github.pgatzka.gunsmith.data.AbstractRepository;
import io.github.pgatzka.gunsmith.data.entity.AttachmentData;
import io.github.pgatzka.gunsmith.data.entity.WeaponData;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachmentDataRepository extends AbstractRepository<AttachmentData> {

    boolean existsByTarkovId(String tarkovId);

    AttachmentData findByTarkovId(String tarkovId);
    @Query("select w.tarkovId from AttachmentData w")
    List<String> findAllTarkovIds();
}