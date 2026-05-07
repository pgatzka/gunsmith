package io.github.pgatzka.gunsmith;

import io.github.pgatzka.gunsmith.apollo.ApolloService;
import io.github.pgatzka.gunsmith.apollo.ItemsByIdQuery;
import io.github.pgatzka.gunsmith.data.Cached;
import io.github.pgatzka.gunsmith.data.CachedRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractTarkovItemService<E extends Cached> {

    protected final ApplicationProperties properties;

    protected final ApolloService apolloService;

    protected final CachedRepository<E> repository;

    protected E findByTarkovId(String tarkovId) {
        return repository.findByTarkovId(tarkovId);
    }

    protected List<E> findByTarkovIdIn(Collection<String> tarkovIds) {
        return repository.findByTarkovIdIn(tarkovIds);
    }

    public abstract E transform(ItemsByIdQuery.Item item, E existing);

    public E getItem(String tarkovId) {
        log.debug("Fetching single item with tarkovId={}", tarkovId);
        return getItems(Set.of(tarkovId)).stream()
                .findFirst()
                .orElse(null);
    }

    public Set<E> getItems(Set<String> tarkovIds) {
        if (tarkovIds == null || tarkovIds.isEmpty()) {
            log.debug("getItem called with empty or null id list, returning empty result");
            return Set.of();
        }

        log.debug("Fetching {} items with tarkovIds={}", tarkovIds.size(), tarkovIds);

        Map<String, E> cached = findByTarkovIdIn(tarkovIds).stream()
                .collect(Collectors.toMap(Cached::getTarkovId, Function.identity()));

        log.debug("Found {} of {} requested items in repository cache", cached.size(), tarkovIds.size());

        List<String> idsToFetch = tarkovIds.stream().filter(id -> {
            E item = cached.get(id);
            return item == null || !isFresh(item);
        }).toList();

        if (idsToFetch.isEmpty()) {
            log.debug("All {} requested items are fresh in cache, no API call needed", tarkovIds.size());
        } else {
            log.debug("Refreshing {} of {} items from Apollo API (missing or stale)", idsToFetch.size(), tarkovIds.size());
        }

        Map<String, E> refreshed = idsToFetch.isEmpty() ? Map.of() : fetchAndSaveAll(idsToFetch, cached);

        Set<E> result = tarkovIds.stream()
                .map(id -> refreshed.getOrDefault(id, cached.get(id)))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (result.size() < tarkovIds.size()) {
            log.warn("Returning {} items but {} were requested; {} could not be resolved",
                    result.size(), tarkovIds.size(), tarkovIds.size() - result.size());
        }

        return result;
    }

    private Map<String, E> fetchAndSaveAll(List<String> tarkovIds, Map<String, E> existing) {
        Map<String, E> result = new HashMap<>();
        int batchSize = properties.getMaxQueryBatchSize();
        int totalBatches = (tarkovIds.size() + batchSize - 1) / batchSize;

        log.debug("Fetching {} items in {} batch(es) of up to {}", tarkovIds.size(), totalBatches, batchSize);

        for (int i = 0; i < tarkovIds.size(); i += batchSize) {
            List<String> batch = tarkovIds.subList(i, Math.min(i + batchSize, tarkovIds.size()));
            int batchNumber = (i / batchSize) + 1;
            log.debug("Processing batch {}/{} with {} items", batchNumber, totalBatches, batch.size());
            result.putAll(fetchAndSaveBatch(batch, existing));
        }

        log.debug("Completed all batches, persisted {} items total", result.size());
        return result;
    }

    private Map<String, E> fetchAndSaveBatch(List<String> tarkovIds, Map<String, E> existing) {
        log.debug("Querying Apollo for {} items", tarkovIds.size());
        ItemsByIdQuery.Data data = apolloService.query(new ItemsByIdQuery(tarkovIds));

        if (data.items.size() != tarkovIds.size()) {
            log.warn("Apollo returned {} items for {} requested ids", data.items.size(), tarkovIds.size());
        }

        Map<String, E> persisted = data.items.stream()
                .map(item -> {

                    E prior = existing.get(item.id);
                    if (prior != null) {
                        log.trace("Updating existing entity for tarkovId={}", item.id);
                    } else {
                        log.trace("Creating new entity for tarkovId={}", item.id);
                    }
                    return transform(item, prior);
                })
                .collect(Collectors.toMap(Cached::getTarkovId, Function.identity()));

        log.debug("Persisted {} items from Apollo response", persisted.size());
        return persisted;
    }

    protected boolean isFresh(E item) {
        if (item.getUpdatedAt() == null) {
            log.trace("Item tarkovId={} has no updatedAt, treating as stale", item.getTarkovId());
            return false;
        }
        LocalDateTime validAfter = LocalDateTime.now().minusMinutes(properties.getCacheTimeToLiveMinutes());
        boolean fresh = item.getUpdatedAt().isAfter(validAfter);
        if (!fresh) {
            log.trace("Item tarkovId={} is stale (updated at {}, cutoff {})",
                    item.getTarkovId(), item.getUpdatedAt(), validAfter);
        }
        return fresh;
    }

}