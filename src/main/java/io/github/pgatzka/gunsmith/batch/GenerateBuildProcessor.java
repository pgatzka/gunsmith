package io.github.pgatzka.gunsmith.batch;

import io.github.pgatzka.gunsmith.batch.pojo.BuildResult;
import io.github.pgatzka.gunsmith.batch.pojo.BuildSettings;
import io.github.pgatzka.gunsmith.data.entity.AttachmentEntity;
import io.github.pgatzka.gunsmith.data.entity.SlotEntity;
import io.github.pgatzka.gunsmith.data.entity.WeaponEntity;
import io.github.pgatzka.gunsmith.data.pojo.Attachment;
import io.github.pgatzka.gunsmith.data.pojo.Build;
import io.github.pgatzka.gunsmith.data.pojo.Slot;
import io.github.pgatzka.gunsmith.data.repository.AttachmentRepository;
import io.github.pgatzka.gunsmith.data.repository.WeaponRepository;
import io.github.pgatzka.gunsmith.service.AttachmentService;
import io.github.pgatzka.gunsmith.service.WeaponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class GenerateBuildProcessor implements ItemProcessor<BuildSettings, BuildResult>, StepExecutionListener {

    private final ObjectMapper objectMapper;

    private final WeaponService weaponService;

    private final Map<String, WeaponEntity> weaponCache = new HashMap<>();

    private final Map<String, AttachmentEntity> attachmentCache = new HashMap<>();

    private final WeaponRepository weaponRepository;

    private final AttachmentRepository attachmentRepository;

    private final AttachmentService attachmentService;

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        log.info("Preparing step cache");
        weaponRepository.findAll().forEach(weapon -> weaponCache.put(weapon.getTarkovId(), weapon));
        log.info("Cached {} weapons", weaponCache.size());
        attachmentRepository.findAll().forEach(attachment -> attachmentCache.put(attachment.getTarkovId(), attachment));
        log.info("Cached {} attachments", attachmentCache.size());
    }

    @Override
    public BuildResult process(@NonNull BuildSettings item) {
        WeaponEntity weapon = getWeapon(item.id());
        try {
            List<AttachmentEntity> usedAttachments = new ArrayList<>();

            Build build = new Build();
            build.setSlots(weapon.getSlots().stream().map(weaponSlot -> populateSlot(weaponSlot, usedAttachments)).filter(Objects::nonNull).toList());

            List<Long> conflictingWeaponIds = usedAttachments.stream().map(AttachmentEntity::getConflictingWeaponIds).flatMap(Collection::stream).toList();

            if (conflictingWeaponIds.contains(weapon.getId())) {
                // Build is invalid. Skip
                return null;
            }

            List<Long> usedAttachmentIds = usedAttachments.stream().map(AttachmentEntity::getId).toList();
            List<Long> conflictingAttachmentIds = usedAttachments.stream().map(AttachmentEntity::getConflictingAttachmentIds).flatMap(Collection::stream).toList();

            if (!Collections.disjoint(usedAttachmentIds, conflictingAttachmentIds)) {
                // Build is invalid. Skip
                return null;
            }

            List<Double> ergonomics = usedAttachments.stream().map(AttachmentEntity::getErgonomics).toList();
            List<Double> recoilModifiers = usedAttachments.stream().map(AttachmentEntity::getRecoilModifier).toList();

            double finalErgonomics = weapon.getErgonomics() + ergonomics.stream().mapToDouble(f -> f).sum();

            double totalReduction = recoilModifiers.stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();

            double multiplier = 1 + totalReduction;  // e.g. 1 + (-0.84) = 0.16

            double finalRecoilHorizontal = weapon.getRecoilHorizontal().doubleValue() * multiplier;
            double finalRecoilVertical = weapon.getRecoilVertical().doubleValue() * multiplier;
            return new BuildResult(weapon.getId(), finalErgonomics, finalRecoilHorizontal, finalRecoilVertical, build);
        } catch (Exception e) {
            log.error("Failed to generate build for weapon: {}", weapon.getName(), e);
            throw e;
        }
    }

    private Slot populateSlot(SlotEntity slotEntity, List<AttachmentEntity> usedAttachments) {
        Slot slot = new Slot();
        slot.setId(slotEntity.getId());

        String attachmentTarkovId = getRandom(slotEntity.getAllowedAttachmentTarkovIds(), Set.of(), slotEntity.getRequired());

        if (attachmentTarkovId == null) {
            return null;
        }
        AttachmentEntity attachmentEntity = getAttachment(attachmentTarkovId);

        Attachment attachment = new Attachment();
        attachment.setId(attachmentEntity.getId());
        usedAttachments.add(attachmentEntity);
        attachment.setSlots(attachmentEntity.getSlots().stream().map(attachmentSlot -> populateSlot(attachmentSlot, usedAttachments)).filter(Objects::nonNull).toList());

        slot.setAttachment(attachment);

        return slot;
    }

    private WeaponEntity getWeapon(String id) {
        return weaponCache.computeIfAbsent(id, weaponService::getItem);
    }

    private AttachmentEntity getAttachment(String id) {
        return attachmentCache.computeIfAbsent(id, attachmentService::getItem);
    }

    private <V> V getRandom(Set<V> ids, Set<V> conflictingAttachments, boolean required) {
        Set<V> availableIds = ids.stream().filter(id -> !conflictingAttachments.contains(id)).collect(Collectors.toSet());

        if (availableIds.isEmpty() && !required) {
            return null;
        }

        if (availableIds.isEmpty()) {
            throw new IllegalStateException("No available ids found");
        }

        int bound = required ? availableIds.size() : availableIds.size() + 1;
        int index = ThreadLocalRandom.current().nextInt(bound);

        if (index == availableIds.size()) {
            return null;
        }

        return availableIds.stream().skip(index).findFirst().orElseThrow();
    }


}
