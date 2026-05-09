package io.github.pgatzka.gunsmith.service;

import io.github.pgatzka.gunsmith.apollo.ApolloService;
import io.github.pgatzka.gunsmith.apollo.ItemsByIdsQuery;
import io.github.pgatzka.gunsmith.apollo.fragment.SlotFields;
import io.github.pgatzka.gunsmith.jooq.tables.records.AttachmentRecord;
import io.github.pgatzka.gunsmith.jooq.tables.records.WeaponRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.pgatzka.gunsmith.jooq.Tables.*;
import static io.github.pgatzka.gunsmith.jooq.tables.SlotTable.SLOT;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final DSLContext dsl;

    private WeaponService weaponService;

    @Lazy
    @Autowired
    public void setWeaponService(WeaponService weaponService) {
        this.weaponService = weaponService;
    }

    private final ApolloService apolloService;

    @Transactional
    public List<AttachmentRecord> getAttachments(List<String> tarkovIds) {
        return getAttachmentsWithinTransaction(tarkovIds);
    }

    @Transactional
    public AttachmentRecord getAttachment(String tarkovId) {
        return getAttachmentsWithinTransaction(List.of(tarkovId)).stream().findFirst().orElse(null);
    }

    private List<AttachmentRecord> getAttachmentsWithinTransaction(List<String> tarkovIds) {
        if (tarkovIds.isEmpty()) {
            return List.of();
        }

        Set<String> uniqueIds = new HashSet<>(tarkovIds);
        log.debug("Resolving {} attachment(s): {}", uniqueIds.size(), uniqueIds);

        List<String> idsToFetch = new ArrayList<>();
        uniqueIds.forEach(tarkovId -> {
            if (dsl.fetchExists(ATTACHMENT, ATTACHMENT.TARKOV_ID.eq(tarkovId))
                    && dsl.select(ATTACHMENT.UPDATED_AT)
                    .from(ATTACHMENT)
                    .where(ATTACHMENT.TARKOV_ID.eq(tarkovId))
                    .fetchSingleInto(OffsetDateTime.class)
                    .isAfter(OffsetDateTime.now().minusDays(2))) {
                return;
            }
            dsl.deleteFrom(ATTACHMENT).where(ATTACHMENT.TARKOV_ID.eq(tarkovId)).execute();
            idsToFetch.add(tarkovId);
        });

        if (idsToFetch.isEmpty()) {
            log.debug("All {} attachment(s) are fresh; nothing to fetch", uniqueIds.size());
            return dsl.fetch(ATTACHMENT, ATTACHMENT.TARKOV_ID.in(tarkovIds));
        }

        log.debug("Fetching {} stale attachment(s) from Apollo: {}", idsToFetch.size(), idsToFetch);
        List<ItemsByIdsQuery.Item> items = apolloService.query(new ItemsByIdsQuery(idsToFetch)).items;

        if (items.size() != idsToFetch.size()) {
            throw new IllegalStateException("Found " + items.size() + " attachments, but requested " + idsToFetch.size());
        }

        for (ItemsByIdsQuery.Item item : items) {
            if (dsl.fetchExists(ATTACHMENT, ATTACHMENT.TARKOV_ID.eq(item.id))) {
                log.debug("Attachment {} ({}) already inserted via recursion, skipping", item.id, item.name);
                continue;
            }

            Integer categoryId;
            var existingCategory = dsl.select(CATEGORY.ID, CATEGORY.UPDATED_AT)
                    .from(CATEGORY)
                    .where(CATEGORY.TARKOV_ID.eq(item.category.id))
                    .fetchOptional();

            if (existingCategory.isPresent()) {
                categoryId = existingCategory.get().get(CATEGORY.ID);
                if (existingCategory.get().get(CATEGORY.UPDATED_AT).isBefore(OffsetDateTime.now().minusDays(2))) {
                    dsl.update(CATEGORY)
                            .set(CATEGORY.NAME, item.category.name)
                            .where(CATEGORY.ID.eq(categoryId))
                            .execute();
                }
            } else {
                categoryId = dsl.insertInto(CATEGORY)
                        .set(CATEGORY.TARKOV_ID, item.category.id)
                        .set(CATEGORY.NAME, item.category.name)
                        .returningResult(CATEGORY.ID)
                        .fetchSingle()
                        .value1();
            }

            record AttachmentStats(Double ergonomics, Double recoilModifier, List<SlotFields> slots) {
            }

            AttachmentStats stats = switch (item.properties.__typename) {
                case "ItemPropertiesBarrel" -> {
                    var p = item.properties.onItemPropertiesBarrel;
                    yield new AttachmentStats(p.ergonomics, p.recoilModifier,
                            p.slots.stream().map(s -> s.slotFields).toList());
                }
                case "ItemPropertiesMagazine" -> {
                    var p = item.properties.onItemPropertiesMagazine;
                    yield new AttachmentStats(p.ergonomics, p.recoilModifier,
                            p.slots.stream().map(s -> s.slotFields).toList());
                }
                case "ItemPropertiesScope" -> {
                    var p = item.properties.onItemPropertiesScope;
                    yield new AttachmentStats(p.ergonomics, p.recoilModifier,
                            p.slots.stream().map(s -> s.slotFields).toList());
                }
                case "ItemPropertiesWeaponMod" -> {
                    var p = item.properties.onItemPropertiesWeaponMod;
                    yield new AttachmentStats(p.ergonomics, p.recoilModifier,
                            p.slots.stream().map(s -> s.slotFields).toList());
                }
                default -> throw new IllegalStateException("Unexpected properties: " + item.properties.__typename);
            };

            Integer attachmentId = dsl.insertInto(ATTACHMENT)
                    .set(ATTACHMENT.TARKOV_ID, item.id)
                    .set(ATTACHMENT.NAME, item.name)
                    .set(ATTACHMENT.ICON_LINK, item.iconLink)
                    .set(ATTACHMENT.CATEGORY_ID, categoryId)
                    .set(ATTACHMENT.ERGONOMICS, stats.ergonomics())
                    .set(ATTACHMENT.RECOIL_MODIFIER, stats.recoilModifier())
                    .returningResult(ATTACHMENT.ID).fetchSingleInto(Integer.class);
            log.info("Inserted attachment {} ({}) [id={}]", item.name, item.id, attachmentId);

            Set<String> conflictingAttachmentTarkovIds = new HashSet<>();
            Set<String> conflictingWeaponTarkovIds = new HashSet<>();
            for (var conflict : item.conflictingItems) {
                Set<String> typeNames = conflict.types.stream()
                        .map(t -> t.rawValue)
                        .collect(Collectors.toSet());

                if (typeNames.contains("mods")) {
                    conflictingAttachmentTarkovIds.add(conflict.id);
                } else if (typeNames.contains("gun")) {
                    conflictingWeaponTarkovIds.add(conflict.id);
                } else if (typeNames.contains("ammo")) {
                    log.debug("Skipping ammo conflict {} on attachment {}", conflict.id, item.id);
                } else {
                    throw new IllegalStateException(
                            "Conflict item " + conflict.id + " on attachment " + item.id
                                    + " has unrecognized types: " + typeNames
                    );
                }
            }

            // --- attachment conflicts ---
            List<AttachmentRecord> conflictingAttachments =
                    getAttachmentsWithinTransaction(new ArrayList<>(conflictingAttachmentTarkovIds));
            Set<Integer> desiredConflictingAttachmentIds = conflictingAttachments.stream()
                    .map(AttachmentRecord::getId)
                    .collect(Collectors.toSet());

            Set<Integer> currentConflictingAttachmentIds = dsl.select(ATTACHMENT_CONFLICTING_ATTACHMENT.CONFLICTING_ATTACHMENT_ID)
                    .from(ATTACHMENT_CONFLICTING_ATTACHMENT)
                    .where(ATTACHMENT_CONFLICTING_ATTACHMENT.ATTACHMENT_ID.eq(attachmentId))
                    .fetchSet(ATTACHMENT_CONFLICTING_ATTACHMENT.CONFLICTING_ATTACHMENT_ID);

            Set<Integer> attachmentConflictsToAdd = new HashSet<>(desiredConflictingAttachmentIds);
            attachmentConflictsToAdd.removeAll(currentConflictingAttachmentIds);

            Set<Integer> attachmentConflictsToRemove = new HashSet<>(currentConflictingAttachmentIds);
            attachmentConflictsToRemove.removeAll(desiredConflictingAttachmentIds);

            if (!attachmentConflictsToRemove.isEmpty()) {
                dsl.deleteFrom(ATTACHMENT_CONFLICTING_ATTACHMENT)
                        .where(ATTACHMENT_CONFLICTING_ATTACHMENT.ATTACHMENT_ID.eq(attachmentId))
                        .and(ATTACHMENT_CONFLICTING_ATTACHMENT.CONFLICTING_ATTACHMENT_ID.in(attachmentConflictsToRemove))
                        .execute();
            }

            for (Integer conflictAttachmentId : attachmentConflictsToAdd) {
                dsl.insertInto(ATTACHMENT_CONFLICTING_ATTACHMENT)
                        .set(ATTACHMENT_CONFLICTING_ATTACHMENT.ATTACHMENT_ID, attachmentId)
                        .set(ATTACHMENT_CONFLICTING_ATTACHMENT.CONFLICTING_ATTACHMENT_ID, conflictAttachmentId)
                        .execute();
            }

            if (!attachmentConflictsToAdd.isEmpty() || !attachmentConflictsToRemove.isEmpty()) {
                log.debug("Attachment {} conflicts: +{} -{}", item.id,
                        attachmentConflictsToAdd.size(), attachmentConflictsToRemove.size());
            }

            // --- weapon conflicts ---
            List<WeaponRecord> conflictingWeapons =
                    weaponService.getWeapons(new ArrayList<>(conflictingWeaponTarkovIds));
            Set<Integer> desiredConflictingWeaponIds = conflictingWeapons.stream()
                    .map(WeaponRecord::getId)
                    .collect(Collectors.toSet());

            Set<Integer> currentConflictingWeaponIds = dsl.select(ATTACHMENT_CONFLICTING_WEAPON.CONFLICTING_WEAPON_ID)
                    .from(ATTACHMENT_CONFLICTING_WEAPON)
                    .where(ATTACHMENT_CONFLICTING_WEAPON.ATTACHMENT_ID.eq(attachmentId))
                    .fetchSet(ATTACHMENT_CONFLICTING_WEAPON.CONFLICTING_WEAPON_ID);

            Set<Integer> weaponConflictsToAdd = new HashSet<>(desiredConflictingWeaponIds);
            weaponConflictsToAdd.removeAll(currentConflictingWeaponIds);

            Set<Integer> weaponConflictsToRemove = new HashSet<>(currentConflictingWeaponIds);
            weaponConflictsToRemove.removeAll(desiredConflictingWeaponIds);

            if (!weaponConflictsToRemove.isEmpty()) {
                dsl.deleteFrom(ATTACHMENT_CONFLICTING_WEAPON)
                        .where(ATTACHMENT_CONFLICTING_WEAPON.ATTACHMENT_ID.eq(attachmentId))
                        .and(ATTACHMENT_CONFLICTING_WEAPON.CONFLICTING_WEAPON_ID.in(weaponConflictsToRemove))
                        .execute();
            }

            for (Integer conflictWeaponId : weaponConflictsToAdd) {
                dsl.insertInto(ATTACHMENT_CONFLICTING_WEAPON)
                        .set(ATTACHMENT_CONFLICTING_WEAPON.ATTACHMENT_ID, attachmentId)
                        .set(ATTACHMENT_CONFLICTING_WEAPON.CONFLICTING_WEAPON_ID, conflictWeaponId)
                        .execute();
            }

            if (!weaponConflictsToAdd.isEmpty() || !weaponConflictsToRemove.isEmpty()) {
                log.debug("Attachment {} weapon conflicts: +{} -{}", item.id,
                        weaponConflictsToAdd.size(), weaponConflictsToRemove.size());
            }
            List<SlotFields> slots = stats.slots.stream()
                    .filter(slot -> !"Chamber".equals(slot.name))
                    .toList();
            for (SlotFields slot : slots) {
                var existingSlot = dsl.select(SLOT.ID, SLOT.UPDATED_AT)
                        .from(SLOT)
                        .where(SLOT.TARKOV_ID.eq(slot.id))
                        .fetchOptional();

                Integer slotId;
                if (existingSlot.isPresent()) {
                    slotId = existingSlot.get().get(SLOT.ID);

                    if (existingSlot.get().get(SLOT.UPDATED_AT).isBefore(OffsetDateTime.now().minusDays(2))) {
                        dsl.update(SLOT)
                                .set(SLOT.NAME, slot.name)
                                .set(SLOT.REQUIRED, slot.required)
                                .where(SLOT.ID.eq(slotId))
                                .execute();
                    }
                } else {
                    slotId = dsl.insertInto(SLOT)
                            .set(SLOT.TARKOV_ID, slot.id)
                            .set(SLOT.NAME, slot.name)
                            .set(SLOT.REQUIRED, slot.required)
                            .returningResult(SLOT.ID).fetchSingleInto(Integer.class);
                }

                List<String> allowedItemTarkovIds = slot.filters.allowedItems.stream()
                        .map(allowedItem -> allowedItem.id)
                        .toList();

                List<AttachmentRecord> allowedAttachments = getAttachmentsWithinTransaction(allowedItemTarkovIds);
                Set<Integer> desiredAttachmentIds = allowedAttachments.stream()
                        .map(AttachmentRecord::getId)
                        .collect(Collectors.toSet());

                Set<Integer> currentAttachmentIds = dsl.select(SLOT_ALLOWED_ATTACHMENT.ALLOWED_ATTACHMENT_ID)
                        .from(SLOT_ALLOWED_ATTACHMENT)
                        .where(SLOT_ALLOWED_ATTACHMENT.SLOT_ID.eq(slotId))
                        .fetchSet(SLOT_ALLOWED_ATTACHMENT.ALLOWED_ATTACHMENT_ID);

                Set<Integer> toAdd = new HashSet<>(desiredAttachmentIds);
                toAdd.removeAll(currentAttachmentIds);

                Set<Integer> toRemove = new HashSet<>(currentAttachmentIds);
                toRemove.removeAll(desiredAttachmentIds);

                if (!toRemove.isEmpty()) {
                    dsl.deleteFrom(SLOT_ALLOWED_ATTACHMENT)
                            .where(SLOT_ALLOWED_ATTACHMENT.SLOT_ID.eq(slotId))
                            .and(SLOT_ALLOWED_ATTACHMENT.ALLOWED_ATTACHMENT_ID.in(toRemove))
                            .execute();
                }

                for (Integer allowedAttachmentId : toAdd) {
                    dsl.insertInto(SLOT_ALLOWED_ATTACHMENT)
                            .set(SLOT_ALLOWED_ATTACHMENT.SLOT_ID, slotId)
                            .set(SLOT_ALLOWED_ATTACHMENT.ALLOWED_ATTACHMENT_ID, allowedAttachmentId)
                            .execute();
                }

                dsl.insertInto(ATTACHMENT_SLOT)
                        .set(ATTACHMENT_SLOT.ATTACHMENT_ID, attachmentId)
                        .set(ATTACHMENT_SLOT.SLOT_ID, slotId)
                        .execute();
            }
        }

        return dsl.fetch(ATTACHMENT, ATTACHMENT.TARKOV_ID.in(tarkovIds));
    }
}