package io.github.pgatzka.gunsmith.batch.generate;

import io.github.pgatzka.gunsmith.batch.pojo.*;
import io.github.pgatzka.gunsmith.jooq.tables.records.AttachmentRecord;
import io.github.pgatzka.gunsmith.jooq.tables.records.SlotRecord;
import io.github.pgatzka.gunsmith.jooq.tables.records.WeaponRecord;
import io.github.pgatzka.gunsmith.service.AttachmentService;
import io.github.pgatzka.gunsmith.service.WeaponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.Result;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static io.github.pgatzka.gunsmith.jooq.Tables.*;

@Slf4j
@StepScope
@Component
@RequiredArgsConstructor
public class GenerateProcessor implements ItemProcessor<BuildSettings, BuildResult>, StepExecutionListener {

    private final ObjectMapper objectMapper;

    private final DSLContext dsl;

    private final WeaponService weaponService;

    private final AttachmentService attachmentService;

    private final Set<String> seenHashes = new HashSet<>();

    private int skippedForIllegalWeapon = 0;
    private int skippedForIllegalAttachment = 0;
    private int skippedForDuplicate = 0;

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        seenHashes.addAll(loadHashes());
    }

    @Override
    public @Nullable ExitStatus afterStep(StepExecution stepExecution) {
        log.info("Skipped {} builds due to attachment conflicts", skippedForIllegalAttachment);
        log.info("Skipped {} builds due to duplication", skippedForDuplicate);
        log.info("Skipped {} builds due to weapon conflict", skippedForIllegalWeapon);

        return StepExecutionListener.super.afterStep(stepExecution);
    }

    @Override
    public @Nullable BuildResult process(@NonNull BuildSettings settings) {
        Map<Integer, AttachmentRecord> usedAttachmentRecords = new HashMap<>();
        Set<Integer> blockedAttachmentIds = new HashSet<>();

        WeaponRecord weapon = getWeaponByTarkovId(settings.id());

        List<SlotRecord> weaponSlots = getSlotsForWeapon(weapon);

        List<Slot> slots = populateSlots(weaponSlots, weapon, usedAttachmentRecords, blockedAttachmentIds);

        Build build = new Build(weapon.getId(), slots);

        Set<Integer> usedAttachmentIds = build.collectUsedAttachmentIds();

        // Backstop: pre-filter is best-effort (it doesn't catch one-way conflicts where
        // only the already-chosen attachment lists the candidate, not vice-versa).
        if (!usedAttachmentIds.isEmpty()) {
            List<Integer> conflictingAttachmentIds = getConflictingAttachmentIds(usedAttachmentIds);

            if (!Collections.disjoint(usedAttachmentIds, conflictingAttachmentIds)) {
                skippedForIllegalAttachment++;
                return null;
            }

            List<Integer> conflictingWeaponIds = getConflictingWeaponIds(usedAttachmentIds);

            if (conflictingWeaponIds.contains(weapon.getId())) {
                skippedForIllegalWeapon++;
                return null;
            }
        }

        String buildJson;
        try {
            buildJson = objectMapper.writeValueAsString(build);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize build", e);
        }

        String buildHash = DigestUtils.md5DigestAsHex(buildJson.getBytes(StandardCharsets.UTF_8));

        if (!seenHashes.add(buildHash)) {
            skippedForDuplicate++;
            return null;
        }

        Collection<AttachmentRecord> usedAttachments = usedAttachmentRecords.values();

        double totalErgonomics = weapon.getErgonomics() + usedAttachments.stream().mapToDouble(AttachmentRecord::getErgonomics).sum();
        double totalRecoilModifier = usedAttachments.stream().mapToDouble(AttachmentRecord::getRecoilModifier).sum();
        double totalRecoilHorizontal = weapon.getRecoilHorizontal() * (1 + totalRecoilModifier);
        double totalRecoilVertical = weapon.getRecoilVertical() * (1 + totalRecoilModifier);

        return new BuildResult(weapon.getId(), totalErgonomics, totalRecoilHorizontal, totalRecoilVertical, buildJson, buildHash);
    }

    private List<Slot> populateSlots(List<SlotRecord> slots, WeaponRecord weapon, Map<Integer, AttachmentRecord> usedAttachmentRecords, Set<Integer> blockedAttachmentIds) {
        return slots.stream().map(weaponSlot -> {
            List<String> allowedAttachmentTarkovIds = getAllowedAttachmentsForSlot(weaponSlot);

            List<String> viable = allowedAttachmentTarkovIds.stream()
                    .filter(tarkovId -> {
                        Integer id = attachmentIdByTarkovId.get(tarkovId);
                        if (id == null) {
                            return true;
                        }
                        if (blockedAttachmentIds.contains(id)) {
                            return false;
                        }
                        if (getConflictingWeaponIdsByAttachmentId(id).contains(weapon.getId())) {
                            return false;
                        }
                        List<Integer> candidateConflicts = getConflictingAttachmentIdsByAttachmentId(id);
                        return Collections.disjoint(candidateConflicts, usedAttachmentRecords.keySet());
                    })
                    .toList();

            String chosenAttachmentTarkovId = randomElement(viable, weaponSlot.getRequired());

            if (chosenAttachmentTarkovId == null) {
                return null;
            }

            AttachmentRecord attachment = getAttachment(chosenAttachmentTarkovId);

            usedAttachmentRecords.put(attachment.getId(), attachment);
            blockedAttachmentIds.addAll(getConflictingAttachmentIdsByAttachmentId(attachment.getId()));

            List<SlotRecord> attachmentSlots = getSlotsForAttachment(attachment);

            return new Slot(
                    weaponSlot.getId(),
                    new Attachment(
                            attachment.getId(),
                            populateSlots(attachmentSlots, weapon, usedAttachmentRecords, blockedAttachmentIds)
                    )
            );
        }).filter(Objects::nonNull).toList();
    }

    private List<String> loadHashes() {
        return dsl.select(BUILD.JSON_HASH).from(BUILD).fetchInto(String.class);
    }

    private final Map<String, WeaponRecord> weaponByTarkovId = new HashMap<>();

    private WeaponRecord getWeaponByTarkovId(String weaponTarkovId) {
        return weaponByTarkovId.computeIfAbsent(weaponTarkovId, weaponService::getWeapon);
    }

    private List<Integer> getConflictingWeaponIds(Set<Integer> attachmentIds) {
        return attachmentIds.stream().map(this::getConflictingWeaponIdsByAttachmentId).flatMap(Collection::stream).toList();
    }

    private final Map<Integer, List<Integer>> conflictingWeaponIdsByAttachmentId = new HashMap<>();

    private List<Integer> getConflictingWeaponIdsByAttachmentId(Integer attachmentId) {
        return conflictingWeaponIdsByAttachmentId.computeIfAbsent(attachmentId, id -> dsl.selectDistinct(ATTACHMENT_CONFLICTING_WEAPON.CONFLICTING_WEAPON_ID)
                .from(ATTACHMENT_CONFLICTING_WEAPON)
                .where(ATTACHMENT_CONFLICTING_WEAPON.ATTACHMENT_ID.eq(id))
                .fetchInto(Integer.class));
    }

    private List<Integer> getConflictingAttachmentIds(Set<Integer> attachmentIds) {
        return attachmentIds.stream().map(this::getConflictingAttachmentIdsByAttachmentId).flatMap(Collection::stream).toList();
    }

    private final Map<Integer, List<Integer>> conflictingAttachmentIdsByAttachmentId = new HashMap<>();

    private List<Integer> getConflictingAttachmentIdsByAttachmentId(Integer attachmentId) {
        return conflictingAttachmentIdsByAttachmentId.computeIfAbsent(attachmentId, id -> dsl.selectDistinct(ATTACHMENT_CONFLICTING_ATTACHMENT.CONFLICTING_ATTACHMENT_ID)
                .from(ATTACHMENT_CONFLICTING_ATTACHMENT)
                .where(ATTACHMENT_CONFLICTING_ATTACHMENT.ATTACHMENT_ID.eq(id))
                .fetchInto(Integer.class));
    }

    private final Map<String, AttachmentRecord> attachmentByTarkovId = new HashMap<>();

    private AttachmentRecord getAttachment(String attachmentTarkovId) {
        return attachmentByTarkovId.computeIfAbsent(attachmentTarkovId, tarkovId -> {
            AttachmentRecord rec = attachmentService.getAttachment(tarkovId);
            attachmentIdByTarkovId.put(tarkovId, rec.getId());
            return rec;
        });
    }

    private final Map<Integer, List<SlotRecord>> slotsByAttachmentId = new HashMap<>();

    private List<SlotRecord> getSlotsForAttachment(AttachmentRecord attachment) {
        return slotsByAttachmentId.computeIfAbsent(attachment.getId(), attachmentId -> dsl.select(SLOT.fields())
                .from(SLOT)
                .join(ATTACHMENT_SLOT).on(ATTACHMENT_SLOT.SLOT_ID.eq(SLOT.ID))
                .where(ATTACHMENT_SLOT.ATTACHMENT_ID.eq(attachment.getId()))
                .fetchInto(SLOT));
    }

    private final Map<Integer, List<SlotRecord>> slotsByWeaponId = new HashMap<>();

    private List<SlotRecord> getSlotsForWeapon(WeaponRecord weapon) {
        return slotsByWeaponId.computeIfAbsent(weapon.getId(), id ->
                dsl.select(SLOT.fields())
                        .from(SLOT)
                        .join(WEAPON_SLOT).on(WEAPON_SLOT.SLOT_ID.eq(SLOT.ID))
                        .where(WEAPON_SLOT.WEAPON_ID.eq(id))
                        .fetchInto(SLOT));
    }

    private final Map<Integer, List<String>> allowedAttachmentsBySlotId = new HashMap<>();
    private final Map<String, Integer> attachmentIdByTarkovId = new HashMap<>();

    private List<String> getAllowedAttachmentsForSlot(SlotRecord slot) {
        return allowedAttachmentsBySlotId.computeIfAbsent(slot.getId(), slotId -> {
            Result<Record2<String, Integer>> rows = dsl.selectDistinct(ATTACHMENT.TARKOV_ID, ATTACHMENT.ID)
                    .from(SLOT_ALLOWED_ATTACHMENT)
                    .join(ATTACHMENT).on(ATTACHMENT.ID.eq(SLOT_ALLOWED_ATTACHMENT.ALLOWED_ATTACHMENT_ID))
                    .where(SLOT_ALLOWED_ATTACHMENT.SLOT_ID.eq(slotId))
                    .fetch();
            rows.forEach(r -> attachmentIdByTarkovId.put(r.value1(), r.value2()));
            return rows.map(r -> r.value1());
        });
    }

    public static <T> T randomElement(List<T> list, boolean required) {
        if (list == null || list.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException("List must be non-empty when required=true");
            }
            return null;
        }

        int size = list.size();
        int bound = required ? size : size + 1;
        int index = ThreadLocalRandom.current().nextInt(bound);

        if (!required && index == size) {
            return null;
        }

        return list.get(index);
    }
}