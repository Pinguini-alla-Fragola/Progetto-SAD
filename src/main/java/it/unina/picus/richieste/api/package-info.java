/**
 * Contratti per l'invio, la consultazione e il coordinamento delle richieste di misura.
 * I tipi pubblici rappresentano il ciclo della richiesta e i soli dati ammessi per ciascun attore.
 *
 * <p>Nel flusso di risposta a un contatto di disponibilità, un adattatore HTTP delega al
 * <em>Service Layer</em> i controlli di accesso e la gestione delle transazioni. I
 * <em>Repository</em> incapsulano l'accesso ai contatti e alle richieste di misura.
 *
 * @see it.unina.picus.richieste.api.RichiesteApi
 */
package it.unina.picus.richieste.api;
