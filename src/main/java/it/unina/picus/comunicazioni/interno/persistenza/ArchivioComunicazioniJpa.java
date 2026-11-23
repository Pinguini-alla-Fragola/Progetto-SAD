package it.unina.picus.comunicazioni.interno.persistenza;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.comunicazioni.interno.dominio.ArchivioComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.ComunicazioneSalvata;
import it.unina.picus.comunicazioni.interno.dominio.FileSalvato;
import it.unina.picus.comunicazioni.interno.dominio.FiltroComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.InfoAllegato;
import it.unina.picus.comunicazioni.interno.dominio.NuovaComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.NuovoAllegato;
import it.unina.picus.comunicazioni.interno.dominio.PaginaComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.TipoAllegato;
import jakarta.persistence.EntityManager;

/**
 * Salva e legge le comunicazioni, i destinatari e gli allegati nel database condiviso del monolite. Gli elenchi
 * leggono una pagina alla volta, già filtrata dal database, e gli allegati senza il loro contenuto, con poche query
 * per tutte le comunicazioni della pagina.
 */
@Repository
@Transactional
class ArchivioComunicazioniJpa implements ArchivioComunicazioni {

    /** Ordine stabile: a parità di istante decide l'identificativo, così una pagina non ripete né salta righe. */
    private static final String ORDINE = " order by c.inviataIl desc, c.id";

    private final EntityManager entityManager;

    ArchivioComunicazioniJpa(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void registra(NuovaComunicazione comunicazione) {
        entityManager.persist(new ComunicazioneJpa(comunicazione.id(), comunicazione.mittenteId(),
                comunicazione.titolo(), comunicazione.contenuto(), comunicazione.tipo(), comunicazione.inviataIl()));
        for (UUID destinatario : comunicazione.destinatari()) {
            entityManager.persist(new DestinatarioComunicazioneJpa(comunicazione.id(), destinatario));
        }
        for (NuovoAllegato allegato : comunicazione.allegati()) {
            entityManager.persist(new AllegatoComunicazioneJpa(allegato.id(), comunicazione.id(),
                    allegato.nomeFile(), allegato.tipo().tipoMime(), allegato.contenuto()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long contaDaConfermare(UUID destinatarioId) {
        return entityManager.createQuery("""
                select count(d) from DestinatarioComunicazioneJpa d
                where d.id.destinatarioId = :account and d.confermataIl is null
                """, Long.class).setParameter("account", destinatarioId).getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaComunicazioni elencoInviate(FiltroComunicazioni filtro) {
        CondizioniElenco condizioni = CondizioniElenco.perInviate(filtro);
        List<ComunicazioneJpa> comunicazioni = condizioni.parametri(entityManager.createQuery(
                "select c " + condizioni.daDove() + ORDINE, ComunicazioneJpa.class))
                .setFirstResult(filtro.primo()).setMaxResults(FiltroComunicazioni.DIMENSIONE_PAGINA)
                .getResultList();
        return pagina(filtro, componi(comunicazioni, Map.of()), condizioni);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaComunicazioni elencoRicevute(UUID destinatarioId, FiltroComunicazioni filtro) {
        CondizioniElenco condizioni = CondizioniElenco.perRicevute(destinatarioId, filtro);
        List<Object[]> righe = condizioni.parametri(entityManager.createQuery(
                "select c, d.confermataIl " + condizioni.daDove() + ORDINE, Object[].class))
                .setFirstResult(filtro.primo()).setMaxResults(FiltroComunicazioni.DIMENSIONE_PAGINA)
                .getResultList();
        List<ComunicazioneJpa> comunicazioni = new ArrayList<>();
        Map<UUID, Instant> conferme = new HashMap<>();
        for (Object[] riga : righe) {
            ComunicazioneJpa comunicazione = (ComunicazioneJpa) riga[0];
            comunicazioni.add(comunicazione);
            conferme.put(comunicazione.getId(), (Instant) riga[1]);
        }
        return pagina(filtro, componi(comunicazioni, conferme), condizioni);
    }

    /** Il totale serve alla pagina per sapere se ci sono altre comunicazioni oltre quelle mostrate. */
    private PaginaComunicazioni pagina(FiltroComunicazioni filtro, List<ComunicazioneSalvata> elementi,
            CondizioniElenco condizioni) {
        long totale = condizioni.parametri(entityManager.createQuery(
                "select count(c) " + condizioni.daDove(), Long.class)).getSingleResult();
        return new PaginaComunicazioni(elementi, filtro.pagina(), FiltroComunicazioni.DIMENSIONE_PAGINA, totale);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ComunicazioneSalvata> cerca(UUID comunicazioneId, UUID lettoreId) {
        ComunicazioneJpa comunicazione = entityManager.find(ComunicazioneJpa.class, comunicazioneId);
        if (comunicazione == null) {
            return Optional.empty();
        }
        DestinatarioComunicazioneJpa lettore = entityManager.find(DestinatarioComunicazioneJpa.class,
                new DestinatarioComunicazioneJpa.Chiave(comunicazioneId, lettoreId));
        Map<UUID, Instant> conferme = new HashMap<>();
        if (lettore != null) {
            conferme.put(comunicazioneId, lettore.getConfermataIl());
        }
        return componi(List.of(comunicazione), conferme).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> destinatari(UUID comunicazioneId) {
        return Set.copyOf(entityManager.createQuery("""
                select d.id.destinatarioId from DestinatarioComunicazioneJpa d
                where d.id.comunicazioneId = :comunicazione
                """, UUID.class).setParameter("comunicazione", comunicazioneId).getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> comunicazioneDellAllegato(UUID allegatoId) {
        return entityManager.createQuery(
                "select a.comunicazioneId from AllegatoComunicazioneJpa a where a.id = :allegato", UUID.class)
                .setParameter("allegato", allegatoId).getResultStream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FileSalvato> allegato(UUID allegatoId) {
        return Optional.ofNullable(entityManager.find(AllegatoComunicazioneJpa.class, allegatoId))
                .map(allegato -> new FileSalvato(allegato.getComunicazioneId(), allegato.getNomeFile(),
                        TipoAllegato.daTipoMime(allegato.getTipoMime()).orElseThrow(), allegato.getContenuto()));
    }

    /** Aggiunge a ogni comunicazione i suoi allegati e il numero di destinatari, con una query per tipo di dato. */
    private List<ComunicazioneSalvata> componi(List<ComunicazioneJpa> comunicazioni, Map<UUID, Instant> conferme) {
        if (comunicazioni.isEmpty()) {
            return List.of();
        }
        List<UUID> id = comunicazioni.stream().map(ComunicazioneJpa::getId).toList();
        Map<UUID, List<InfoAllegato>> allegati = allegatiDi(id);
        Map<UUID, Long> destinatari = numeroDestinatariDi(id);
        return comunicazioni.stream().map(c -> new ComunicazioneSalvata(c.getId(), c.getTitolo(), c.getTesto(),
                c.getTipoDestinatari(), c.getInviataIl(), allegati.getOrDefault(c.getId(), List.of()),
                destinatari.getOrDefault(c.getId(), 0L).intValue(), conferme.get(c.getId()))).toList();
    }

    private Map<UUID, List<InfoAllegato>> allegatiDi(List<UUID> comunicazioni) {
        List<Object[]> righe = entityManager.createQuery("""
                select a.comunicazioneId, a.id, a.nomeFile, a.tipoMime, a.dimensioneByte
                from AllegatoComunicazioneJpa a where a.comunicazioneId in :comunicazioni
                order by a.nomeFile, a.id
                """, Object[].class).setParameter("comunicazioni", comunicazioni).getResultList();
        Map<UUID, List<InfoAllegato>> allegati = new LinkedHashMap<>();
        for (Object[] riga : righe) {
            allegati.computeIfAbsent((UUID) riga[0], chiave -> new ArrayList<>()).add(new InfoAllegato(
                    (UUID) riga[1], (String) riga[2], TipoAllegato.daTipoMime((String) riga[3]).orElseThrow(),
                    (Long) riga[4]));
        }
        return allegati;
    }

    private Map<UUID, Long> numeroDestinatariDi(List<UUID> comunicazioni) {
        return entityManager.createQuery("""
                select d.id.comunicazioneId, count(d) from DestinatarioComunicazioneJpa d
                where d.id.comunicazioneId in :comunicazioni group by d.id.comunicazioneId
                """, Object[].class).setParameter("comunicazioni", comunicazioni).getResultList().stream()
                .collect(Collectors.toMap(riga -> (UUID) riga[0], riga -> (Long) riga[1]));
    }
}
