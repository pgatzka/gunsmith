package io.github.pgatzka.gunsmith.batch.generate;

import io.github.pgatzka.gunsmith.batch.pojo.BuildResult;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.github.pgatzka.gunsmith.jooq.Tables.BUILD;

@Component
@StepScope
@RequiredArgsConstructor
public class GenerateWriter implements ItemWriter<BuildResult> {

    private final DSLContext dsl;

    @Override
    public void write(@NonNull Chunk<? extends BuildResult> chunk) throws Exception {
        if (chunk.isEmpty()) {
            return;
        }

        dsl.batch(chunk.getItems().stream()
                .collect(Collectors.toMap(BuildResult::jsonHash, Function.identity(), (a, b) -> a, LinkedHashMap::new)).values().stream()
                .map(buildResult -> dsl.insertInto(BUILD)
                        .set(BUILD.WEAPON_ID, buildResult.weaponId())
                        .set(BUILD.ERGONOMICS, buildResult.ergonomics())
                        .set(BUILD.RECOIL_HORIZONTAL, buildResult.recoilHorizontal())
                        .set(BUILD.RECOIL_VERTICAL, buildResult.recoilVertical())
                        .set(BUILD.JSON, JSONB.valueOf(buildResult.json()))
                        .set(BUILD.JSON_HASH, buildResult.jsonHash()))
                .toList()
        ).execute();
    }

}
