package io.github.pgatzka.gunsmith.data.repository;

import io.github.pgatzka.gunsmith.data.AbstractRepository;
import io.github.pgatzka.gunsmith.data.entity.AttachmentData;
import io.github.pgatzka.gunsmith.data.entity.WeaponData;
import org.springframework.stereotype.Repository;

@Repository
public interface AttachmentDataRepository extends AbstractRepository<AttachmentData> {

    boolean existsByTarkovId(String tarkovId);

    AttachmentData findByTarkovId(String tarkovId);

}