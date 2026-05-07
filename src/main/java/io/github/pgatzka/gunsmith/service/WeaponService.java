package io.github.pgatzka.gunsmith.service;

import io.github.pgatzka.gunsmith.AbstractTarkovItemService;
import io.github.pgatzka.gunsmith.ApplicationProperties;
import io.github.pgatzka.gunsmith.apollo.ApolloService;
import io.github.pgatzka.gunsmith.apollo.ItemsByIdQuery;
import io.github.pgatzka.gunsmith.data.entity.AttachmentEntity;
import io.github.pgatzka.gunsmith.data.entity.SlotEntity;
import io.github.pgatzka.gunsmith.data.entity.WeaponEntity;
import io.github.pgatzka.gunsmith.data.repository.WeaponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WeaponService extends AbstractTarkovItemService<WeaponEntity> {

    private AttachmentService attachmentService;

    @Lazy
    @Autowired
    public void setAttachmentService(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    public WeaponService(ApplicationProperties properties, ApolloService apolloService, WeaponRepository repository) {
        super(properties, apolloService, repository);
    }

    @Override
    public WeaponEntity transform(ItemsByIdQuery.Item weapon, WeaponEntity existing) {
        if (existing == null) {
            existing = repository.findByTarkovId(weapon.id);
        }

        WeaponEntity weaponEntity = new WeaponEntity();
        if (existing != null) {
            weaponEntity.setId(existing.getId());
        }

        weaponEntity.setTarkovId(weapon.id);
        weaponEntity.setName(weapon.name);
        weaponEntity.setCategory(weapon.category.name);
        weaponEntity.setErgonomics(weapon.properties.onItemPropertiesWeapon.ergonomics);
        weaponEntity.setRecoilHorizontal(weapon.properties.onItemPropertiesWeapon.recoilHorizontal);
        weaponEntity.setRecoilVertical(weapon.properties.onItemPropertiesWeapon.recoilVertical);
        weaponEntity = repository.save(weaponEntity);

        Set<String> totalAllowedAttachments = weapon.properties.onItemPropertiesWeapon.slots.stream()
                .filter(slot -> !"Chamber".equals(slot.name)).map(slot -> slot.filters.allowedItems.stream().map(item -> item.id).toList()).flatMap(List::stream).collect(Collectors.toSet());
        Set<AttachmentEntity> allowedAttachments = attachmentService.getItems(totalAllowedAttachments);

        List<SlotEntity> slots = weapon.properties.onItemPropertiesWeapon.slots.stream().map(slot -> {
            SlotEntity slotEntity = new SlotEntity();
            slotEntity.setTarkovId(slot.id);
            slotEntity.setName(slot.name);
            slotEntity.setRequired(slot.required);
            Set<String> allowedAttachmentIds = slot.filters.allowedItems.stream().map(item -> item.id).collect(Collectors.toSet());
            slotEntity.setAllowedAttachmentTarkovIds(allowedAttachments.stream().map(AttachmentEntity::getTarkovId).filter(allowedAttachmentIds::contains).collect(Collectors.toSet()));
            return slotEntity;
        }).toList();

        /**
         *
         slotEntity.setAllowedAttachmentIds(attachmentService.getItems(slot.filters.allowedItems.stream().map(item -> item.id).collect(Collectors.toSet()))
         .stream().map(AttachmentEntity::getId).collect(Collectors.toSet()));
         */


        weaponEntity.setSlots(slots);

        return repository.save(weaponEntity);
    }

}