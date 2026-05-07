package io.github.pgatzka.gunsmith.service;

import io.github.pgatzka.gunsmith.AbstractTarkovItemService;
import io.github.pgatzka.gunsmith.ApplicationProperties;
import io.github.pgatzka.gunsmith.apollo.ApolloService;
import io.github.pgatzka.gunsmith.apollo.ItemsByIdQuery;
import io.github.pgatzka.gunsmith.data.entity.AttachmentEntity;
import io.github.pgatzka.gunsmith.data.entity.SlotEntity;
import io.github.pgatzka.gunsmith.data.entity.WeaponEntity;
import io.github.pgatzka.gunsmith.data.repository.AttachmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AttachmentService extends AbstractTarkovItemService<AttachmentEntity> {

    private WeaponService weaponService;

    @Lazy
    @Autowired
    public void setWeaponService(WeaponService weaponService) {
        this.weaponService = weaponService;
    }

    public AttachmentService(ApplicationProperties properties, ApolloService apolloService, AttachmentRepository repository) {
        super(properties, apolloService, repository);
    }

    @Override
    public AttachmentEntity transform(ItemsByIdQuery.Item attachment, AttachmentEntity existing) {
        if (existing == null) {
            existing = repository.findByTarkovId(attachment.id);
        }

        AttachmentEntity attachmentEntity = new AttachmentEntity();
        if (existing != null) {
            attachmentEntity.setId(existing.getId());
        }

        attachmentEntity.setTarkovId(attachment.id);
        attachmentEntity.setName(attachment.name);
        attachmentEntity.setCategory(attachment.category.name);

        PropertyData properties = extractProperties(attachment.properties);
        attachmentEntity.setErgonomics(properties.ergonomics());
        attachmentEntity.setRecoilModifier(properties.recoilModifier());

        attachmentEntity = repository.save(attachmentEntity);

        Set<String> conflictingAttachmentTarkovIds = new HashSet<>();
        Set<String> conflictingWeaponTarkovIds = new HashSet<>();

        for (var conflict : attachment.conflictingItems) {
            Set<String> typeNames = conflict.types.stream()
                    .map(t -> t.rawValue)
                    .collect(Collectors.toSet());

            if (typeNames.contains("mods")) {
                conflictingAttachmentTarkovIds.add(conflict.id);
            } else if (typeNames.contains("gun")) {
                conflictingWeaponTarkovIds.add(conflict.id);
            } else if (typeNames.contains("ammo")) {
                log.debug("Skipping ammo conflict {} on attachment {}", conflict.id, attachment.id);
            } else {
                throw new IllegalStateException(
                        "Conflict item " + conflict.id + " on attachment " + attachment.id
                                + " has unrecognized types: " + typeNames
                );
            }
        }

        Set<String> attachmentIdsToResolve = new HashSet<>();
        properties.slots().forEach(slot -> attachmentIdsToResolve.addAll(slot.allowedItemIds()));
        attachmentIdsToResolve.addAll(conflictingAttachmentTarkovIds);

        Set<AttachmentEntity> resolvedAttachments = getItems(attachmentIdsToResolve);

        Set<WeaponEntity> resolvedWeapons = conflictingWeaponTarkovIds.isEmpty()
                ? Set.of()
                : weaponService.getItems(conflictingWeaponTarkovIds);

        attachmentEntity.setSlots(properties.slots().stream()
                .map(slot -> toSlotEntity(slot, resolvedAttachments))
                .toList());

        attachmentEntity.setConflictingAttachmentIds(
                resolvedAttachments.stream()
                        .filter(a -> conflictingAttachmentTarkovIds.contains(a.getTarkovId()))
                        .map(AttachmentEntity::getId)
                        .collect(Collectors.toSet())
        );

        attachmentEntity.setConflictingWeaponIds(
                resolvedWeapons.stream()
                        .map(WeaponEntity::getId)
                        .collect(Collectors.toSet())
        );

        return repository.save(attachmentEntity);
    }

    private PropertyData extractProperties(ItemsByIdQuery.Properties properties) {
        return switch (properties.__typename) {
            case "ItemPropertiesScope" -> new PropertyData(
                    properties.onItemPropertiesScope.ergonomics,
                    properties.onItemPropertiesScope.recoilModifier,
                    properties.onItemPropertiesScope.slots.stream()
                            .filter(slot -> !"Chamber".equals(slot.name))
                            .map(slot -> new SlotData(slot.id, slot.name, slot.required,
                                    slot.filters.allowedItems.stream().map(item -> item.id).collect(Collectors.toSet())))
                            .toList()
            );
            case "ItemPropertiesWeaponMod" -> new PropertyData(
                    properties.onItemPropertiesWeaponMod.ergonomics,
                    properties.onItemPropertiesWeaponMod.recoilModifier,
                    properties.onItemPropertiesWeaponMod.slots.stream()
                            .filter(slot -> !"Chamber".equals(slot.name))
                            .map(slot -> new SlotData(slot.id, slot.name, slot.required,
                                    slot.filters.allowedItems.stream().map(item -> item.id).collect(Collectors.toSet())))
                            .toList()
            );
            case "ItemPropertiesMagazine" -> new PropertyData(
                    properties.onItemPropertiesMagazine.ergonomics,
                    properties.onItemPropertiesMagazine.recoilModifier,
                    properties.onItemPropertiesMagazine.slots.stream()
                            .filter(slot -> !"Chamber".equals(slot.name))
                            .map(slot -> new SlotData(slot.id, slot.name, slot.required,
                                    slot.filters.allowedItems.stream().map(item -> item.id).collect(Collectors.toSet())))
                            .toList()
            );
            case "ItemPropertiesBarrel" -> new PropertyData(
                    properties.onItemPropertiesBarrel.ergonomics,
                    properties.onItemPropertiesBarrel.recoilModifier,
                    properties.onItemPropertiesBarrel.slots.stream()
                            .filter(slot -> !"Chamber".equals(slot.name))
                            .map(slot -> new SlotData(slot.id, slot.name, slot.required,
                                    slot.filters.allowedItems.stream().map(item -> item.id).collect(Collectors.toSet())))
                            .toList()
            );
            default -> throw new IllegalArgumentException("Invalid item properties: " + properties.__typename);
        };
    }

    private SlotEntity toSlotEntity(SlotData slot, Set<AttachmentEntity> allowedAttachments) {
        SlotEntity slotEntity = new SlotEntity();
        slotEntity.setTarkovId(slot.tarkovId());
        slotEntity.setName(slot.name());
        slotEntity.setRequired(slot.required());
        slotEntity.setAllowedAttachmentTarkovIds(
                allowedAttachments.stream()
                        .map(AttachmentEntity::getTarkovId)
                        .filter(tarkovId -> slot.allowedItemIds().contains(tarkovId))
                        .collect(Collectors.toSet())
        );
        return slotEntity;
    }

    private record PropertyData(double ergonomics, double recoilModifier, List<SlotData> slots) {
    }

    private record SlotData(String tarkovId, String name, boolean required, Set<String> allowedItemIds) {
    }


}
