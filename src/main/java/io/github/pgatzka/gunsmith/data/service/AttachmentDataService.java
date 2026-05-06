package io.github.pgatzka.gunsmith.data.service;

import com.apollographql.apollo.api.Optional;
import io.github.pgatzka.gunsmith.apollo.ApolloService;
import io.github.pgatzka.gunsmith.apollo.AttachmentByIdQuery;
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
        AttachmentByIdQuery.Data data = apolloService.query(new AttachmentByIdQuery(Optional.present(tarkovId)));
        return attachmentDataRepository.save(transform(data.item));
    }

    private AttachmentData transform(AttachmentByIdQuery.Item item) {
        AttachmentData attachmentData = new AttachmentData();
        attachmentData.setTarkovId(item.id);
        attachmentData.setName(item.name);
        attachmentData.setAttachmentType(AttachmentType.lookup(item.category.name));

        switch (item.properties.__typename) {
            case "ItemPropertiesScope": {
                attachmentData.setErgonomics(item.properties.onItemPropertiesScope.ergonomics);
                attachmentData.setRecoilModifier(item.properties.onItemPropertiesScope.recoilModifier);

                List<SlotData> slots = item.properties.onItemPropertiesScope.slots.stream().map(slot -> {
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

                List<SlotData> slots = item.properties.onItemPropertiesWeaponMod.slots.stream().map(slot -> {
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

                List<SlotData> slots = item.properties.onItemPropertiesMagazine.slots.stream().map(slot -> {
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

                List<SlotData> slots = item.properties.onItemPropertiesBarrel.slots.stream().map(slot -> {
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
