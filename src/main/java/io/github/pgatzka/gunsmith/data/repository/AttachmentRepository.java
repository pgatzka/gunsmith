package io.github.pgatzka.gunsmith.data.repository;

import io.github.pgatzka.gunsmith.data.CachedRepository;
import io.github.pgatzka.gunsmith.data.entity.AttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttachmentRepository extends CachedRepository<AttachmentEntity> {
    
}