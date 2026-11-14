package it.unina.picus.catalogo.interno.persistenza;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalogo_adiacenze")
public class AdiacenzaJpa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "comune_a")
    private String comuneA;
    @Column(name = "comune_b")
    private String comuneB;

    protected AdiacenzaJpa() {
    }
}
