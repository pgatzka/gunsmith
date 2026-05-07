package io.github.pgatzka.gunsmith.cache;

import io.github.pgatzka.gunsmith.apollo.ApolloService;
import io.github.pgatzka.gunsmith.apollo.AttachmentsByIdQuery;
import io.github.pgatzka.gunsmith.apollo.WeaponIdsQuery;
import io.github.pgatzka.gunsmith.apollo.WeaponsByIdQuery;
import io.github.pgatzka.gunsmith.data.embeddable.SlotData;
import io.github.pgatzka.gunsmith.data.entity.AttachmentData;
import io.github.pgatzka.gunsmith.data.entity.WeaponData;
import io.github.pgatzka.gunsmith.data.repository.AttachmentDataRepository;
import io.github.pgatzka.gunsmith.data.repository.WeaponDataRepository;
import io.github.pgatzka.gunsmith.data.service.AttachmentDataService;
import io.github.pgatzka.gunsmith.data.service.WeaponDataService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CachePreloader {

    private static final int BATCH_SIZE = 100;

    private final ApolloService apolloService;
    private final WeaponDataRepository weaponDataRepository;
    private final WeaponDataService weaponDataService;
    private final AttachmentDataRepository attachmentDataRepository;
    private final AttachmentDataService attachmentDataService;

    @Getter
    private boolean cacheLoaded = false;

    @EventListener(ApplicationReadyEvent.class)
    public void preloadCache() {
        // Track all known IDs in memory to avoid repeated existsByTarkovId() DB hits
        Set<String> knownWeaponIds = new HashSet<>(weaponDataRepository.findAllTarkovIds());
        Set<String> knownAttachmentIds = new HashSet<>(attachmentDataRepository.findAllTarkovIds());

        // ---- Weapons ----
        List<String> missingWeaponIds = apolloService.query(new WeaponIdsQuery()).items.stream()
                .map(item -> item.id)
                .filter(id -> !knownWeaponIds.contains(id))
                .distinct()
                .toList();

        log.info("Fetching {} missing weapons", missingWeaponIds.size());

        List<WeaponData> newWeapons = new ArrayList<>();
        for (List<String> batch : partition(missingWeaponIds, BATCH_SIZE)) {
            for (WeaponsByIdQuery.Item item : apolloService.query(new WeaponsByIdQuery(batch)).items) {
                newWeapons.add(weaponDataService.transform(item));
            }
        }
        if (!newWeapons.isEmpty()) {
            weaponDataRepository.saveAll(newWeapons);
            newWeapons.forEach(w -> knownWeaponIds.add(w.getTarkovId()));
        }

        // ---- Attachments: collect all IDs from weapons first, fetch in one batched pass ----
        List<WeaponData> allWeapons = weaponDataRepository.findAll();

        Set<String> attachmentIdsToFetch = allWeapons.stream()
                .flatMap(w -> w.getSlots().stream())
                .flatMap(s -> s.getAllowedAttachmentIds().stream())
                .filter(id -> !knownAttachmentIds.contains(id))
                .collect(Collectors.toCollection(HashSet::new));

        // ---- Iteratively resolve attachment-of-attachment references ----
        // Each pass: fetch everything currently pending, then collect newly discovered IDs from the results.
        while (!attachmentIdsToFetch.isEmpty()) {
            log.info("Fetching {} attachments", attachmentIdsToFetch.size());

            List<AttachmentData> fetchedThisPass = new ArrayList<>();
            for (List<String> batch : partition(new ArrayList<>(attachmentIdsToFetch), BATCH_SIZE)) {
                for (AttachmentsByIdQuery.Item item : apolloService.query(new AttachmentsByIdQuery(batch)).items) {
                    fetchedThisPass.add(attachmentDataService.transform(item));
                }
            }

            attachmentDataRepository.saveAll(fetchedThisPass);
            fetchedThisPass.forEach(a -> knownAttachmentIds.add(a.getTarkovId()));

            // Discover next-level IDs only from what we just fetched
            attachmentIdsToFetch = fetchedThisPass.stream()
                    .flatMap(a -> a.getSlots().stream())
                    .flatMap(s -> s.getAllowedAttachmentIds().stream())
                    .filter(id -> !knownAttachmentIds.contains(id))
                    .collect(Collectors.toCollection(HashSet::new));
        }

        cacheLoaded = true;
        log.info("Cache preload complete");
    }

    private static <T> List<List<T>> partition(List<T> source, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i += size) {
            result.add(source.subList(i, Math.min(i + size, source.size())));
        }
        return result;
    }
}