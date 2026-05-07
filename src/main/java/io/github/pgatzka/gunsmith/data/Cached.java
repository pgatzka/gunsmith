package io.github.pgatzka.gunsmith.data;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public interface Cached {

    String getTarkovId();

    LocalDateTime getUpdatedAt();

}
