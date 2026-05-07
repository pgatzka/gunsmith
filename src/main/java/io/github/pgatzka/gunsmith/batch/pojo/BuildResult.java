package io.github.pgatzka.gunsmith.batch.pojo;

import io.github.pgatzka.gunsmith.data.pojo.Build;

public record BuildResult(Long weaponId, double ergonomics, double recoilHorizontal, double recoilVertical, Build build) {

}
