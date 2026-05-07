package io.github.pgatzka.gunsmith.data.service;

import com.apollographql.apollo.api.Optional;
import io.github.pgatzka.gunsmith.apollo.ApolloService;
import io.github.pgatzka.gunsmith.apollo.AttachmentsByIdQuery;
import io.github.pgatzka.gunsmith.data.entity.AttachmentData;
import io.github.pgatzka.gunsmith.data.embeddable.SlotData;
import io.github.pgatzka.gunsmith.data.enumeration.AttachmentType;
import io.github.pgatzka.gunsmith.data.repository.AttachmentDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentDataService {

    private final AttachmentDataRepository attachmentDataRepository;
    private final ApolloService apolloService;

    public AttachmentData getAttachmentData(String tarkovId) {
        if (attachmentDataRepository.existsByTarkovId(tarkovId)) {
            return attachmentDataRepository.findByTarkovId(tarkovId);
        }
        AttachmentsByIdQuery.Data data = apolloService.query(new AttachmentsByIdQuery(List.of(tarkovId)));
        return attachmentDataRepository.save(transform(data.items.getFirst()));
    }

    public AttachmentData transform(AttachmentsByIdQuery.Item item) {
        AttachmentData attachmentData = new AttachmentData();
        attachmentData.setTarkovId(item.id);
        attachmentData.setName(item.name);
        attachmentData.setAttachmentType(AttachmentType.lookup(item.category.name));
        attachmentData.setConflictingAttachmentIds(item.conflictingItems.stream().map(a -> a.id).collect(Collectors.toSet()));

        switch (item.properties.__typename) {
            case "ItemPropertiesScope": {
                attachmentData.setErgonomics(item.properties.onItemPropertiesScope.ergonomics);
                attachmentData.setRecoilModifier(item.properties.onItemPropertiesScope.recoilModifier);

                List<SlotData> slots = item.properties.onItemPropertiesScope.slots.stream().filter(slot -> !slot.name.equals("Chamber")).map(slot -> {
                    SlotData attachmentSlotData = new SlotData();
                    attachmentSlotData.setTarkovId(slot.id);
                    attachmentSlotData.setName(slot.name);
                    attachmentSlotData.setRequired(slot.required);
                    attachmentSlotData.setAllowedAttachmentIds(slot.filters.allowedItems.stream().map(allowedItem -> allowedItem.id).collect(Collectors.toSet()));
                    return attachmentSlotData;
                }).toList();

                attachmentData.setSlots(slots);
                break;
            }
            case "ItemPropertiesWeaponMod": {
                attachmentData.setErgonomics(item.properties.onItemPropertiesWeaponMod.ergonomics);
                attachmentData.setRecoilModifier(item.properties.onItemPropertiesWeaponMod.recoilModifier);

                List<SlotData> slots = item.properties.onItemPropertiesWeaponMod.slots.stream().filter(slot -> !slot.name.equals("Chamber")).map(slot -> {
                    SlotData attachmentSlotData = new SlotData();
                    attachmentSlotData.setTarkovId(slot.id);
                    attachmentSlotData.setName(slot.name);
                    attachmentSlotData.setRequired(slot.required);
                    attachmentSlotData.setAllowedAttachmentIds(slot.filters.allowedItems.stream().map(allowedItem -> allowedItem.id).collect(Collectors.toSet()));
                    return attachmentSlotData;
                }).toList();

                attachmentData.setSlots(slots);
                break;
            }
            case "ItemPropertiesMagazine": {
                attachmentData.setErgonomics(item.properties.onItemPropertiesMagazine.ergonomics);
                attachmentData.setRecoilModifier(item.properties.onItemPropertiesMagazine.recoilModifier);

                List<SlotData> slots = item.properties.onItemPropertiesMagazine.slots.stream().filter(slot -> !slot.name.equals("Chamber")).map(slot -> {
                    SlotData attachmentSlotData = new SlotData();
                    attachmentSlotData.setTarkovId(slot.id);
                    attachmentSlotData.setName(slot.name);
                    attachmentSlotData.setRequired(slot.required);
                    attachmentSlotData.setAllowedAttachmentIds(slot.filters.allowedItems.stream().map(allowedItem -> allowedItem.id).collect(Collectors.toSet()));
                    return attachmentSlotData;
                }).toList();

                attachmentData.setSlots(slots);
                break;
            }
            case "ItemPropertiesBarrel": {
                attachmentData.setErgonomics(item.properties.onItemPropertiesBarrel.ergonomics);
                attachmentData.setRecoilModifier(item.properties.onItemPropertiesBarrel.recoilModifier);

                List<SlotData> slots = item.properties.onItemPropertiesBarrel.slots.stream().filter(slot -> !slot.name.equals("Chamber")).map(slot -> {
                    SlotData attachmentSlotData = new SlotData();
                    attachmentSlotData.setTarkovId(slot.id);
                    attachmentSlotData.setName(slot.name);
                    attachmentSlotData.setRequired(slot.required);
                    attachmentSlotData.setAllowedAttachmentIds(slot.filters.allowedItems.stream().map(allowedItem -> allowedItem.id).collect(Collectors.toSet()));
                    return attachmentSlotData;
                }).toList();

                attachmentData.setSlots(slots);
                break;
            }
            default: {
                throw new IllegalArgumentException("Unknown properties: " + item.properties.__typename);
            }
        }

        return attachmentData;
    }

}
