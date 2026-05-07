package io.github.pgatzka.gunsmith.batch;

import io.github.pgatzka.gunsmith.ApplicationProperties;
import io.github.pgatzka.gunsmith.apollo.ApolloService;
import io.github.pgatzka.gunsmith.apollo.WeaponIdsQuery;
import io.github.pgatzka.gunsmith.batch.pojo.BuildSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateBuildReader implements ItemReader<BuildSettings>, StepExecutionListener {

    private final ApplicationProperties properties;

    private final ApolloService apolloService;

    private Iterator<BuildSettings> iterator;

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        String id = stepExecution.getJobExecution().getJobParameters().getString("id", null);
        List<String> ids = id == null
                ? apolloService.query(new WeaponIdsQuery()).items.stream().map(item -> item.id).filter(weaponId -> !properties.getIgnoredWeapons().contains(weaponId)).toList()
                : List.of();

        if (id == null && ids.isEmpty()) {
            throw new IllegalStateException("No weapons returned from API");
        }

        long count = Objects.requireNonNull(stepExecution.getJobExecution().getJobParameters().getLong("count", 50L));

        List<BuildSettings> buildSettings = new ArrayList<>();
        if (id == null) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (long i = 0; i < count; i++) {
                buildSettings.add(new BuildSettings(ids.get(random.nextInt(ids.size()))));
            }
        } else {
            for (long i = 0; i < count; i++) {
                buildSettings.add(new BuildSettings(id));
            }
        }

        iterator = buildSettings.iterator();
    }

    @Override
    public @Nullable BuildSettings read() throws Exception {
        return iterator.hasNext() ? iterator.next() : null;
    }

}
