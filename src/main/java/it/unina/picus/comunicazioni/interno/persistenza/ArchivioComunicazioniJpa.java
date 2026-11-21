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
import it.unina.picus.comunicazioni.interno.dominio.InfoAllegato;
import it.unina.picus.comunicazioni.interno.dominio.NuovaComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.NuovoAllegato;
import it.unina.picus.comunicazioni.interno.dominio.TipoAllegato;
import jakarta.persistence.EntityManager;

/**
 * Salva e legge le comunicazioni, i destinatari e gli allegati nel database condiviso del monolite. Gli elenchi
 * leggono gli allegati senza il loro contenuto e con poche query per tutte le comunicazioni insieme.
 */
@Repository
@Transactional
class ArchivioComunicazioniJpa implements ArchivioComunicazioni {

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
    public List<ComunicazioneSalvata> elencoInviate() {
        List<ComunicazioneJpa> comunicazioni = entityManager.createQuery(
                "select c from ComunicazioneJpa c order by c.inviataIl desc, c.id", ComunicazioneJpa.class)
                .getResultList();
        return componi(comunicazioni, Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComunicazioneSalvata> elencoRicevute(UUID destinatarioId) {
        List<Object[]> righe = entityManager.createQuery("""
                select c, d.confermataIl from ComunicazioneJpa c, DestinatarioComunicazioneJpa d
                where d.id.comunicazioneId = c.id and d.id.destinatarioId = :account
                order by c.inviataIl desc, c.id
                """, Object[].class).setParameter("account", destinatarioId).getResultList();
        List<ComunicazioneJpa> comunicazioni = new ArrayList<>();
        Map<UUID, Instant> conferme = new HashMap<>();
        for (Object[] riga : righe) {
            ComunicazioneJpa comunicazione = (ComunicazioneJpa) riga[0];
            comunicazioni.add(comunicazione);
            conferme.put(comunicazione.getId(), (Instant) riga[1]);
        }
        return componi(comunicazioni, conferme);
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
