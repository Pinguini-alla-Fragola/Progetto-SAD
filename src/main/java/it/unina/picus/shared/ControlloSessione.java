package it.unina.picus.shared;

import jakarta.servlet.Filter;

/**
 * Filtro che chiude le sessioni non più valide. Il modulo Utenze lo implementa; {@link SecurityConfig}
 * lo inserisce nella catena di Spring Security subito dopo il caricamento della sessione, quindi
 * prima di CSRF e dei permessi di ruolo: una sessione revocata riceve lo stesso esito qualunque
 * area o metodo la prima richiesta usi. Non importa nulla dai moduli applicativi.
 */
public interface ControlloSessione extends Filter {
}
