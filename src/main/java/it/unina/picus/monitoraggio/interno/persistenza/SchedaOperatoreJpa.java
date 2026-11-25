package it.unina.picus.monitoraggio.interno.persistenza;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.DatiSchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeRendicontabili.VersioneInviata;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoSchedaOperatore;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Scheda personale inviata. I conteggi sono conservati per singola categoria dichiarata. */
@Entity
@Table(name = "monitoraggio_schede_operatore")
public class SchedaOperatoreJpa {

    @Id
    private UUID id;
    private UUID operatoreId;
    private int anno;
    private int trimestre;
    private Instant inviataIl;
    @Column(length = 16)
    private String versioneTracciato;
    private String nome;
    private String cognome;
    private String luogoNascita;
    private LocalDate dataNascita;
    private String codiceFiscale;
    private String qualifica;
    private String tipologiaContratto;
    private String areaLivello;
    @Column(precision = 19, scale = 6)
    private BigDecimal ore;
    // La colonna numerica ha scala fissa. Questi campi conservano la precisione dichiarata.
    private int scalaOre;
    @Column(precision = 19, scale = 6)
    private BigDecimal costoOrarioLordo;
    private int scalaCostoOrarioLordo;

    @ElementCollection
    @CollectionTable(name = "monitoraggio_conteggi_scheda", joinColumns = @JoinColumn(name = "scheda_id"))
    @MapKeyColumn(name = "categoria", length = 64)
    @Column(name = "valore", nullable = false)
    private Map<String, Long> conteggi = new HashMap<>();

    /** Costruttore riservato a JPA. */
    protected SchedaOperatoreJpa() {}

    /** Converte l'invio già validato in un'entità da inserire. */
    SchedaOperatoreJpa(SchedaOperatore scheda) {
        SchedaSintesi sintesi = scheda.sintesi();
        DatiSchedaOperatore dati = scheda.dati();
        id = sintesi.id();
        operatoreId = sintesi.operatoreId();
        anno = sintesi.trimestre().anno();
        trimestre = sintesi.trimestre().numero();
        inviataIl = sintesi.inviataIl();
        versioneTracciato = TracciatoSchedaOperatore.VERSIONE;
        nome = dati.nome();
        cognome = dati.cognome();
        luogoNascita = dati.luogoNascita();
        dataNascita = dati.dataNascita();
        codiceFiscale = dati.codiceFiscale();
        qualifica = dati.qualifica();
        tipologiaContratto = dati.tipologiaContratto();
        areaLivello = dati.areaLivello();
        ore = dati.ore();
        scalaOre = ore.scale();
        costoOrarioLordo = dati.costoOrarioLordo();
        scalaCostoOrarioLordo = costoOrarioLordo.scale();
        conteggi.putAll(dati.conteggi());
    }

    /** Identifica il titolare senza caricare la collezione dei conteggi. */
    UUID operatoreId() {
        return operatoreId;
    }

    /** Ricompone la scheda completa dopo il controllo della titolarità. */
    SchedaOperatore aScheda() {
        var dati = new DatiSchedaOperatore(nome, cognome, luogoNascita, dataNascita, codiceFiscale,
                qualifica, tipologiaContratto, areaLivello, ore.setScale(scalaOre),
                costoOrarioLordo.setScale(scalaCostoOrarioLordo), conteggi);
        return new SchedaOperatore(sintesi(), dati);
    }

    /** Proietta il primo invio conservando tutti i conteggi originariamente dichiarati. */
    VersioneInviata aVersioneInviata() {
        return new VersioneInviata(sintesi(), 1, conteggi);
    }

    private SchedaSintesi sintesi() {
        return new SchedaSintesi(id, operatoreId, new Trimestre(anno, trimestre), inviataIl);
    }
}
