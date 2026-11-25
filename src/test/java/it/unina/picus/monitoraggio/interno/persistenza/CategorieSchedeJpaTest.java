package it.unina.picus.monitoraggio.interno.persistenza;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.monitoraggio.interno.dominio.CategorieSchedeRendicontabili;
import it.unina.picus.shared.IntegrazionePostgresql;

/** Verifica la lettura delle categorie TM-OP-01 e il vincolo sul codice su PostgreSQL. */
@IntegrazionePostgresql
@Transactional
class CategorieSchedeJpaTest {

    @Autowired
    private CategorieSchedeRendicontabili categorie;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void svuotaCategorie() {
        jdbc.update("delete from monitoraggio_categorie_schede");
    }

    @Test
    void distingueConfigurazioneAssenteAttivaEDisattivata() {
        assertThat(categorie.codiciAmmessi()).isEmpty();

        String codiceAlLimite = "A" + "_.-1".repeat(15) + "XYZ";
        inserisci("TM-OP_01.2", true);
        inserisci(codiceAlLimite, true);
        inserisci("archiviata", false);

        assertThat(categorie.codiciAmmessi()).hasValue(Set.of("TM-OP_01.2", codiceAlLimite));

        jdbc.update("update monitoraggio_categorie_schede set attiva = false");
        assertThat(categorie.codiciAmmessi()).hasValue(Set.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "_prefisso", "Fuori catalogo", "spazio ", "linea\n", "caffè"})
    void vincoloRifiutaUnCodiceFuoriFormato(String codice) {
        assertThatThrownBy(() -> inserisci(codice, true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, errore -> {
                    assertThat(errore.getSQLState()).isEqualTo("23514");
                    assertThat(errore.getMessage()).contains("chk_categoria_scheda_codice");
                });
    }

    private void inserisci(String codice, boolean attiva) {
        jdbc.update("""
                insert into monitoraggio_categorie_schede (codice, descrizione, attiva)
                values (?, 'Categoria di prova', ?)
                """, codice, attiva);
    }
}
