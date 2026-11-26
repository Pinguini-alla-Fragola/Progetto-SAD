package it.unina.picus.monitoraggio.interno.persistenza;

import it.unina.picus.monitoraggio.interno.dominio.StatoVersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoMonitoraggio;
import it.unina.picus.monitoraggio.interno.dominio.VersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.VoceCategoriaVersione;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versione delle categorie di un tracciato di monitoraggio (#41, #181). */
@Entity
@Table(name = "monitoraggio_versioni_categorie")
public class VersioneCategorieJpa {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String tracciato;

    @Column(name = "codice_versione", nullable = false, length = 64)
    private String codiceVersione;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StatoVersioneCategorie stato;

    @Column(nullable = false, length = 255)
    private String descrizione;

    @Column(name = "creata_il", nullable = false)
    private Instant creataIl;

    @Column(name = "pubblicata_il")
    private Instant pubblicataIl;

    @Version
    @Column(name = "versione_record", nullable = false)
    private long versioneRecord;

    @OneToMany(mappedBy = "versione", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordine asc, codice asc")
    private List<VoceVersioneCategorieJpa> voci = new ArrayList<>();

    /** Costruttore riservato a JPA. */
    protected VersioneCategorieJpa() {}

    VersioneCategorie aDominio() {
        List<VoceCategoriaVersione> vociDominio = voci.stream()
                .map(VoceVersioneCategorieJpa::aDominio)
                .toList();
        return new VersioneCategorie(
                id,
                TracciatoMonitoraggio.daCodice(tracciato),
                codiceVersione,
                stato,
                descrizione,
                creataIl,
                pubblicataIl,
                vociDominio);
    }
}
