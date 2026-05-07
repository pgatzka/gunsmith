package io.github.pgatzka.gunsmith.data;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;

@NoRepositoryBean
public interface CachedRepository<E extends Cached> extends JpaRepository<@NonNull E, Long> {

    boolean existsByTarkovId(String tarkovId);

    E findByTarkovId(String tarkovId);

    List<E> findByTarkovIdIn(Collection<String> tarkovIds);
}
