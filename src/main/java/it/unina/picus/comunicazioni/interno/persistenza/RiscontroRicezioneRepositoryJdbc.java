package it.unina.picus.comunicazioni.interno.persistenza;

import it.unina.picus.comunicazioni.interno.dominio.RiscontroRicezioneRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Adattatore JDBC per i destinatari e le conferme delle comunicazioni del Banco. */
@Repository
public class RiscontroRicezioneRepositoryJdbc implements RiscontroRicezioneRepository {

    private final JdbcTemplate jdbc;

    /**
     * Collega la porta di persistenza al database condiviso dall'applicazione.
     *
     * @param jdbc accesso JDBC alla connessione gestita da Spring
     */
    public RiscontroRicezioneRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Set<UUID>> cercaDestinatari(UUID comunicazioneId) {
        var righe = jdbc.query("""
                select d.destinatario_id
                from comunicazioni m
                left join comunicazioni_destinatari d on d.comunicazione_id = m.id
                where m.id = ?
                """, (rs, numeroRiga) -> rs.getObject("destinatario_id", UUID.class), comunicazioneId);
        if (righe.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(righe.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>A isolamento {@code READ COMMITTED} un aggiornamento concorrente rilegge la condizione
     * dopo il commit di chi ha scritto per primo. La query successiva restituisce il valore
     * memorizzato con la precisione del database, anche per la prima conferma.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Instant> registraPrimaConferma(UUID comunicazioneId, UUID destinatarioId, Instant istante) {
        jdbc.update("""
                update comunicazioni_destinatari
                set confermata_il = ?
                where comunicazione_id = ? and destinatario_id = ? and confermata_il is null
                """, istante.atOffset(ZoneOffset.UTC), comunicazioneId, destinatarioId);
        return jdbc.query("""
                select confermata_il
                from comunicazioni_destinatari
                where comunicazione_id = ? and destinatario_id = ?
                """, rs -> rs.next()
                ? Optional.of(rs.getObject("confermata_il", OffsetDateTime.class).toInstant())
                : Optional.empty(), comunicazioneId, destinatarioId);
    }
}
