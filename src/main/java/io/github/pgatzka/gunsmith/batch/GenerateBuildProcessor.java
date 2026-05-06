package io.github.pgatzka.gunsmith.batch;

import io.github.pgatzka.gunsmith.batch.pojo.BuildResult;
import io.github.pgatzka.gunsmith.batch.pojo.BuildSettings;
import io.github.pgatzka.gunsmith.data.embeddable.SlotData;
import io.github.pgatzka.gunsmith.data.entity.AttachmentData;
import io.github.pgatzka.gunsmith.data.entity.WeaponData;
import io.github.pgatzka.gunsmith.data.pojo.Attachment;
import io.github.pgatzka.gunsmith.data.pojo.Build;
import io.github.pgatzka.gunsmith.data.pojo.Slot;
import io.github.pgatzka.gunsmith.data.repository.AttachmentDataRepository;
import io.github.pgatzka.gunsmith.data.repository.WeaponDataRepository;
import io.github.pgatzka.gunsmith.data.service.AttachmentDataService;
import io.github.pgatzka.gunsmith.data.service.WeaponDataService;
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

    private final Map<String, AttachmentData> attachmentCache = new HashMap<>();

    private final Map<String, WeaponData> weaponCache = new HashMap<>();

    private final WeaponDataService weaponDataService;

    private final AttachmentDataService attachmentDataService;

    private final ObjectMapper objectMapper;
    private final WeaponDataRepository weaponDataRepository;
    private final AttachmentDataRepository attachmentDataRepository;

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        log.info("Preparing cache");
        weaponDataRepository.findAll().forEach(weaponData -> weaponCache.put(weaponData.getTarkovId(), weaponData));
        log.info("Prepared weapon cache with {} entries", weaponCache.size());
        attachmentDataRepository.findAll().forEach(attachmentData -> attachmentCache.put(attachmentData.getTarkovId(), attachmentData));
        log.info("Prepared attachment cache with {} entries", attachmentCache.size());
    }

    @Override
    public BuildResult process(@NonNull BuildSettings item) {
        long startTime = System.nanoTime();
        WeaponData weaponData = getWeaponData(item.id());
        try {


            Set<String> conflictingAttachments = new HashSet<>();
            Set<String> usedAttachments = new HashSet<>();
            List<Double> ergonomicsModifiers = new ArrayList<>();
            List<Double> recoilModifiers = new ArrayList<>();

            Build build = new Build();
            build.setId(weaponData.getTarkovId());
            build.setName(weaponData.getName());
            build.setErgonomics(weaponData.getErgonomics());
            build.setRecoilHorizontal(weaponData.getRecoilHorizontal());
            build.setRecoilVertical(weaponData.getRecoilVertical());
            build.setSlots(weaponData.getSlots().stream().filter(slot -> !slot.getName().equals("Chamber")).map(slot -> populateSlot(slot, conflictingAttachments, usedAttachments, ergonomicsModifiers, recoilModifiers, 1)).collect(Collectors.toSet()));

            log.debug("Build took {}ns, WeaponCache({}), AttachmentCache({})", System.nanoTime() - startTime, weaponCache.size(), attachmentCache.size());

            return new BuildResult(weaponData.getTarkovId(), weaponData.getWeaponType(), objectMapper.writeValueAsString(build), usedAttachments, conflictingAttachments, ergonomicsModifiers, recoilModifiers, weaponData.getErgonomics(), weaponData.getRecoilHorizontal(), weaponData.getRecoilVertical());
        } catch (Exception e) {
            log.error("Failed to generate build for weapon: {}", weaponData.getName(), e);
            throw e;
        }
    }

    private WeaponData getWeaponData(String id) {
        return weaponCache.computeIfAbsent(id, weaponDataService::getWeaponData);
    }

    private AttachmentData getAttachmentData(String id) {
        return attachmentCache.computeIfAbsent(id, attachmentDataService::getAttachmentData);
    }

    private Attachment populateAttachment(AttachmentData attachmentData, Set<String> conflictingAttachments, Set<String> usedAttachments, List<Double> ergonomicsModifiers, List<Double> recoilModifiers, int level) {
        Attachment attachment = new Attachment();
        attachment.setId(attachmentData.getTarkovId());
        attachment.setName(attachmentData.getName());
        attachment.setErgonomics(attachmentData.getErgonomics());
        attachment.setRecoilModifier(attachmentData.getRecoilModifier());
        attachment.setSlots(attachmentData.getSlots().stream().filter(slot -> !slot.getName().equals("Chamber")).map(slot -> populateSlot(slot, conflictingAttachments, usedAttachments, ergonomicsModifiers, recoilModifiers, level)).collect(Collectors.toSet()));

        return attachment;
    }

    private Slot populateSlot(SlotData slot, Set<String> conflictingAttachments, Set<String> usedAttachments, List<Double> ergonomicsModifiers, List<Double> recoilModifiers, int level) {
        Slot attachmentSlot = new Slot();
        attachmentSlot.setId(slot.getTarkovId());
        attachmentSlot.setName(slot.getName());

        String attachmentId = getRandom(slot.getAllowedAttachmentIds(), conflictingAttachments, slot.getRequired());
        if (attachmentId != null) {
            AttachmentData attachment = getAttachmentData(attachmentId);

            log.debug("{}{}: {}", "\t".repeat(level), slot.getName(), attachment.getName());

            usedAttachments.add(attachmentId);
            conflictingAttachments.addAll(attachment.getConflictingAttachmentIds());
            ergonomicsModifiers.add(attachment.getErgonomics());
            recoilModifiers.add(attachment.getRecoilModifier());

            attachmentSlot.setAttachment(populateAttachment(attachment, conflictingAttachments, usedAttachments, ergonomicsModifiers, recoilModifiers, level + 1));
        }
        return attachmentSlot;
    }

    private String getRandom(Set<String> ids, Set<String> conflictingAttachments, boolean required) {
        Set<String> availableIds = ids.stream().filter(id -> !conflictingAttachments.contains(id)).collect(Collectors.toSet());

        int bound = required ? availableIds.size() : availableIds.size() + 1;
        int index = ThreadLocalRandom.current().nextInt(bound);

        if (index == availableIds.size()) {
            return null;
        }

        return availableIds.stream().skip(index).findFirst().orElseThrow();
    }


}
