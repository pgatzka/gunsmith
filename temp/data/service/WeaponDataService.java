package io.github.pgatzka.gunsmith.data.service;

import com.apollographql.apollo.api.Optional;
import io.github.pgatzka.gunsmith.apollo.ApolloService;
import io.github.pgatzka.gunsmith.apollo.WeaponsByIdQuery;
import io.github.pgatzka.gunsmith.data.embeddable.SlotData;
import io.github.pgatzka.gunsmith.data.entity.WeaponData;
import io.github.pgatzka.gunsmith.data.enumeration.WeaponType;
import io.github.pgatzka.gunsmith.data.repository.WeaponDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeaponDataService {

    private final WeaponDataRepository weaponDataRepository;
    private final ApolloService apolloService;

    public WeaponData getWeaponData(String tarkovId) {
        if (weaponDataRepository.existsByTarkovId(tarkovId)) {
            return weaponDataRepository.findByTarkovId(tarkovId);
        }
        WeaponsByIdQuery.Data data = apolloService.query(new WeaponsByIdQuery(List.of(tarkovId)));
        return weaponDataRepository.save(transform(data.items.getFirst()));
    }

    public WeaponData transform(WeaponsByIdQuery.Item item) {
        WeaponData weaponData = new WeaponData();
        weaponData.setTarkovId(item.id);
        weaponData.setName(item.name);
        weaponData.setWeaponType(WeaponType.lookup(item.category.name));
        weaponData.setErgonomics(item.properties.onItemPropertiesWeapon.ergonomics);
        weaponData.setRecoilHorizontal(item.properties.onItemPropertiesWeapon.recoilHorizontal);
        weaponData.setRecoilVertical(item.properties.onItemPropertiesWeapon.recoilVertical);

        List<SlotData> slots = item.properties.onItemPropertiesWeapon.slots.stream().filter(slot -> !slot.name.equals("Chamber")).map(slot -> {
            SlotData weaponSlotData = new SlotData();
            weaponSlotData.setTarkovId(slot.id);
            weaponSlotData.setName(slot.name);
            weaponSlotData.setRequired(slot.required);
            weaponSlotData.setAllowedAttachmentIds(slot.filters.allowedItems.stream().map(allowedItem -> allowedItem.id).collect(Collectors.toSet()));
            return weaponSlotData;
        }).toList();

        weaponData.setSlots(slots);

        return weaponData;
    }

}
