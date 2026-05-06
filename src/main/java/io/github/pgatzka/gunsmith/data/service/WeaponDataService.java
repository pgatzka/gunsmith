package io.github.pgatzka.gunsmith.data.service;

import com.apollographql.apollo.api.Optional;
import io.github.pgatzka.gunsmith.apollo.ApolloService;
import io.github.pgatzka.gunsmith.apollo.WeaponByIdQuery;
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
        WeaponByIdQuery.Data data = apolloService.query(new WeaponByIdQuery(Optional.present(tarkovId)));
        return weaponDataRepository.save(transform(data.item));
    }

    private WeaponData transform(WeaponByIdQuery.Item item) {
        WeaponData weaponData = new WeaponData();
        weaponData.setTarkovId(item.id);
        weaponData.setName(item.name);
        weaponData.setWeaponType(WeaponType.lookup(item.category.name));
        weaponData.setErgonomics(item.properties.onItemPropertiesWeapon.ergonomics);
        weaponData.setRecoilHorizontal(item.properties.onItemPropertiesWeapon.recoilHorizontal);
        weaponData.setRecoilVertical(item.properties.onItemPropertiesWeapon.recoilVertical);

        List<SlotData> slots = item.properties.onItemPropertiesWeapon.slots.stream().map(slot -> {
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
