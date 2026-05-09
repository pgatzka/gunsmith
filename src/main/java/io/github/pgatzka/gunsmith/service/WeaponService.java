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
public class WeaponService {

    private final DSLContext dsl;

    private final ApolloService apolloService;


    private AttachmentService attachmentService;

    @Lazy
    @Autowired
    public void setAttachmentService(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @Transactional
    public List<WeaponRecord> getWeapons(List<String> tarkovIds) {
        return getWeaponsWithinTransaction(tarkovIds);
    }

    @Transactional
    public WeaponRecord getWeapon(String tarkovId) {
        return getWeaponsWithinTransaction(List.of(tarkovId)).stream().findAny().orElseThrow();
    }

    private List<WeaponRecord> getWeaponsWithinTransaction(List<String> tarkovIds) {
        if (tarkovIds.isEmpty()) {
            return List.of();
        }

        Set<String> uniqueIds = new HashSet<>(tarkovIds);
        log.debug("Resolving {} weapon(s): {}", uniqueIds.size(), uniqueIds);

        List<String> idsToFetch = new ArrayList<>();
        uniqueIds.forEach(tarkovId -> {
            if (dsl.fetchExists(WEAPON, WEAPON.TARKOV_ID.eq(tarkovId))
                    && dsl.select(WEAPON.UPDATED_AT)
                    .from(WEAPON)
                    .where(WEAPON.TARKOV_ID.eq(tarkovId))
                    .fetchSingleInto(OffsetDateTime.class)
                    .isAfter(OffsetDateTime.now().minusDays(2))) {
                return;
            }
            dsl.deleteFrom(WEAPON).where(WEAPON.TARKOV_ID.eq(tarkovId)).execute();
            idsToFetch.add(tarkovId);
        });

        if (idsToFetch.isEmpty()) {
            log.debug("All {} weapon(s) are fresh; nothing to fetch", uniqueIds.size());
            return dsl.fetch(WEAPON, WEAPON.TARKOV_ID.in(tarkovIds));
        }

        log.debug("Fetching {} stale weapon(s) from Apollo: {}", idsToFetch.size(), idsToFetch);
        List<ItemsByIdsQuery.Item> items = apolloService.query(new ItemsByIdsQuery(idsToFetch)).items;

        if (items.size() != idsToFetch.size()) {
            throw new IllegalStateException("Found " + items.size() + " weapons, but requested " + idsToFetch.size());
        }

        for (ItemsByIdsQuery.Item item : items) {
            if (dsl.fetchExists(WEAPON, WEAPON.TARKOV_ID.eq(item.id))) {
                log.debug("Weapon {} ({}) already inserted via recursion, skipping", item.id, item.name);
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

            Integer weaponId = dsl.insertInto(WEAPON)
                    .set(WEAPON.TARKOV_ID, item.id)
                    .set(WEAPON.NAME, item.name)
                    .set(WEAPON.ICON_LINK, item.iconLink)
                    .set(WEAPON.CATEGORY_ID, categoryId)
                    .set(WEAPON.ERGONOMICS, item.properties.onItemPropertiesWeapon.ergonomics)
                    .set(WEAPON.RECOIL_HORIZONTAL, item.properties.onItemPropertiesWeapon.recoilHorizontal)
                    .set(WEAPON.RECOIL_VERTICAL, item.properties.onItemPropertiesWeapon.recoilVertical)
                    .returningResult(WEAPON.ID).fetchSingleInto(Integer.class);
            log.info("Inserted weapon {} ({}) [id={}]", item.name, item.id, weaponId);

            List<SlotFields> slots = item.properties.onItemPropertiesWeapon.slots.stream()
                    .map(slot -> slot.slotFields)
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

                List<AttachmentRecord> allowedAttachments = attachmentService.getAttachments(allowedItemTarkovIds);
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

                for (Integer attachmentId : toAdd) {
                    dsl.insertInto(SLOT_ALLOWED_ATTACHMENT)
                            .set(SLOT_ALLOWED_ATTACHMENT.SLOT_ID, slotId)
                            .set(SLOT_ALLOWED_ATTACHMENT.ALLOWED_ATTACHMENT_ID, attachmentId)
                            .execute();
                }

                dsl.insertInto(WEAPON_SLOT)
                        .set(WEAPON_SLOT.WEAPON_ID, weaponId)
                        .set(WEAPON_SLOT.SLOT_ID, slotId)
                        .execute();
            }
        }

        return dsl.fetch(WEAPON, WEAPON.TARKOV_ID.in(tarkovIds));
    }
}