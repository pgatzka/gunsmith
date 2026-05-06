package io.github.pgatzka.gunsmith.batch;

import io.github.pgatzka.gunsmith.batch.pojo.BuildResult;
import io.github.pgatzka.gunsmith.data.entity.BuildData;
import io.github.pgatzka.gunsmith.data.repository.BuildDataRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@StepScope
public class GenerateBuildWriter implements ItemWriter<BuildResult>, StepExecutionListener {

    private final BuildDataRepository buildDataRepository;

    private Set<Integer> existingJson;

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        existingJson = buildDataRepository.fetchExistingJsonHashes();
    }

    @Override
    public void write(@NonNull Chunk<? extends BuildResult> chunk) {
        List<BuildResult> builds = chunk.getItems().stream().map(BuildResult.class::cast)
                .filter(buildResult -> Collections.disjoint(buildResult.conflictingAttachments(), buildResult.usedAttachments())).toList();

        Set<BuildResult> json = builds.stream()
                .filter(build -> !existingJson.contains(build.jsonHash()))
                .collect(Collectors.toSet());

        existingJson.addAll(builds.stream().map(BuildResult::jsonHash).collect(Collectors.toSet()));

        List<BuildData> newBuildData = json.stream().map(buildResult -> {
            BuildData buildData = new BuildData();
            buildData.setJsonHash(buildResult.jsonHash());
            buildData.setJson(buildResult.json());
            buildData.setUsedAttachments(buildResult.usedAttachments());

            double ergonomics = buildResult.baseErgonomics() + buildResult.ergonomicsModifiers().stream().mapToDouble(Double::doubleValue).sum();

            double recoilMultiplier = buildResult.recoilModifiers().stream()
                    .mapToDouble(mod -> 1 + mod)
                    .reduce(1.0, (a, b) -> a * b);

            double recoilHorizontal = buildResult.baseRecoilHorizontal() * recoilMultiplier;
            double recoilVertical = buildResult.baseRecoilVertical() * recoilMultiplier;

            buildData.setErgonomics(ergonomics);
            buildData.setRecoilHorizontal(recoilHorizontal);
            buildData.setRecoilVertical(recoilVertical);
            buildData.setWeaponId(buildResult.weaponId());

            return buildData;
        }).toList();

        buildDataRepository.saveAll(newBuildData);
    }

}
