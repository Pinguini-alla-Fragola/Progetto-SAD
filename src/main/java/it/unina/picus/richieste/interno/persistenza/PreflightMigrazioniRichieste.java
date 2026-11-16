package it.unina.picus.richieste.interno.persistenza;

import java.sql.SQLException;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

/** Ferma l'upgrade prima dei vincoli se lo storico richiede una decisione operativa. */
@Component
class PreflightMigrazioniRichieste implements Callback {
    @Override
    public boolean supports(Event evento, Context contesto) {
        if (evento != Event.BEFORE_EACH_MIGRATE || contesto.getMigrationInfo() == null
                || contesto.getMigrationInfo().getVersion() == null) {
            return false;
        }
        String versione = contesto.getMigrationInfo().getVersion().getVersion();
        return versione.equals("11") || versione.equals("13");
    }

    @Override
    public boolean canHandleInTransaction(Event evento, Context contesto) {
        return true;
    }

    @Override
    public void handle(Event evento, Context contesto) {
        String versione = contesto.getMigrationInfo().getVersion().getVersion();
        String sql = versione.equals("11") ? """
                select richiesta_id from richieste_contatti_disponibilita
                where esito = 'IN_ATTESA' group by richiesta_id having count(*) > 1
                """ : """
                select a.contatto_id from comunicazioni_avvisi_operativi a
                left join richieste_contatti_disponibilita c on c.id = a.contatto_id
                where c.id is null or c.richiesta_id <> a.richiesta_id or c.erogatore_id <> a.destinatario_id
                union all
                select contatto_id from comunicazioni_avvisi_operativi
                group by contatto_id, tipo having count(*) > 1
                """;
        try (var statement = contesto.getConnection().createStatement(); var righe = statement.executeQuery(sql)) {
            if (righe.next()) {
                throw new FlywayException("Preflight PICUS V" + versione + ": storico da riconciliare. "
                        + "Eseguire scripts/db/preflight-richieste.sql e seguire la procedura in "
                        + "docs/architecture/api-coordinamento-richieste.md prima di ripetere l'upgrade. "
                        + "Nessun contatto o avviso è stato modificato dal controllo.");
            }
        } catch (SQLException ex) {
            throw new FlywayException("Impossibile eseguire il preflight PICUS V" + versione, ex);
        }
    }

    @Override
    public String getCallbackName() {
        return "preflight-storico-richieste";
    }
}
