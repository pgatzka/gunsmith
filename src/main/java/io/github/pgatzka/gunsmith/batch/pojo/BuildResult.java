package io.github.pgatzka.gunsmith.batch.pojo;

public record BuildResult(Integer weaponId, double ergonomics, double recoilHorizontal, double recoilVertical, String json, String jsonHash) {

}
