package io.github.pgatzka.gunsmith.batch.pojo;


import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record Build(Integer weaponId, List<Slot> slots) {

    public  Set<Integer> collectUsedAttachmentIds() {
        return slots.stream().filter(s -> s.attachment() != null)
                .flatMap(s -> Stream.concat(Stream.of(s.attachment().attachmentId()), s.attachment().collectUsedAttachmentTarkovIds().stream()))
                .collect(Collectors.toSet());
    }

}
