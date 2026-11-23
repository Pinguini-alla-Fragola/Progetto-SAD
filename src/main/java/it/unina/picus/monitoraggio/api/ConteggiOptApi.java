package it.unina.picus.monitoraggio.api;

import java.util.List;
import java.util.UUID;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.ConteggioOpt;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.VoceMisura;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

public interface ConteggiOptApi {

    void registraConteggiOpt(IdentitaAutenticata attore, Trimestre trimestre, List<VoceMisura> conteggi);

    List<ConteggioOpt> consultaConteggiOpt(IdentitaAutenticata attore, UUID optId, Trimestre da, Trimestre a);
}