package io.github.pgatzka.gunsmith.batch;

import io.github.pgatzka.gunsmith.batch.pojo.BuildResult;
import io.github.pgatzka.gunsmith.data.entity.BuildEntity;
import io.github.pgatzka.gunsmith.data.repository.BuildRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@StepScope
public class GenerateBuildWriter implements ItemWriter<BuildResult>, StepExecutionListener {

    private final ObjectMapper objectMapper;

    private final BuildRepository buildRepository;

    private final Set<Integer> jsonHashes = new HashSet<>();

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        jsonHashes.addAll(buildRepository.getJsonHashes());
    }

    @Override
    public void write(@NonNull Chunk<? extends BuildResult> chunk) {
        List<BuildEntity> builds = chunk.getItems().stream()
                .map(buildResult -> {
                    BuildEntity buildEntity = new BuildEntity();
                    buildEntity.setWeaponId(buildResult.weaponId());
                    buildEntity.setErgonomics(buildResult.ergonomics());
                    buildEntity.setRecoilHorizontal(buildResult.recoilHorizontal());
                    buildEntity.setRecoilVertical(buildResult.recoilVertical());
                    String json = objectMapper.writeValueAsString(buildResult.build());
                    buildEntity.setJson(json);
                    buildEntity.setJsonHash(json.hashCode());
                    return buildEntity;
                })
                .filter(build -> jsonHashes.add(build.getJsonHash()))
                .toList();

        buildRepository.saveAll(builds);
    }

}
