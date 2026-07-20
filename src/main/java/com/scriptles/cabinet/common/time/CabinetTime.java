package com.scriptles.cabinet.common.time;

import java.time.LocalDate;
import java.time.ZoneId;

public final class CabinetTime {
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private CabinetTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
