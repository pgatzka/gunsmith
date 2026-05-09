package io.github.pgatzka.gunsmith.batch.generate;

import io.github.pgatzka.gunsmith.ApplicationProperties;
import io.github.pgatzka.gunsmith.apollo.ApolloService;
import io.github.pgatzka.gunsmith.apollo.WeaponIdsQuery;
import io.github.pgatzka.gunsmith.batch.pojo.BuildSettings;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@StepScope
@Component
@RequiredArgsConstructor
public class GenerateReader implements ItemReader<BuildSettings>, StepExecutionListener {

    private final ApplicationProperties properties;

    private final ApolloService apolloService;

    private Iterator<BuildSettings> iterator;

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        List<String> weaponTarkovIds = apolloService.query(new WeaponIdsQuery()).items.stream().map(item -> item.id)
                .filter(id -> !properties.getJob().getIgnoredWeapons().contains(id)).toList();

        long countPerWeapon = properties.getJob().getTarget() / weaponTarkovIds.size();

        List<BuildSettings> settings = new ArrayList<>();
        for (String weaponTarkovId : weaponTarkovIds) {
            for (long i = 0; i < countPerWeapon; i++) {
                settings.add(new BuildSettings(weaponTarkovId));
            }
        }

        Collections.shuffle(settings);

        iterator = settings.iterator();
    }

    @Override
    public @Nullable BuildSettings read() throws Exception {
        return iterator.hasNext() ? iterator.next() : null;
    }

}
