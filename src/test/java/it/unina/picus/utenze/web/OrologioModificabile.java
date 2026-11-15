package it.unina.picus.utenze.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Orologio di prova che avanza solo quando il test lo decide. */
class OrologioModificabile extends Clock {

    private volatile Instant ora = Instant.parse("2026-11-15T10:00:00Z");

    void avanza(Duration durata) {
        ora = ora.plus(durata);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return ora;
    }
}
