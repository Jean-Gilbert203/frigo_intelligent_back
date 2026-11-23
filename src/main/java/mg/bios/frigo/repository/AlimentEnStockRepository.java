package mg.bios.frigo.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import mg.bios.frigo.entity.AlimentEnStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlimentEnStockRepository extends JpaRepository<AlimentEnStock, Integer> {

    // Tous les lots, avec l'aliment, sa catégorie et le compartiment, dans un ordre lisible
    @Query("SELECT s FROM AlimentEnStock s "
         + "JOIN FETCH s.dictionnaire d "
         + "JOIN FETCH d.categorie "
         + "JOIN FETCH s.compartiment p "
         + "ORDER BY p.id, d.nom, s.datePeremption")
    List<AlimentEnStock> findAllAvecDetails();

    // Lots consommables d'un aliment : non périmés, non vides, le plus proche de la péremption d'abord
    @Query("SELECT s FROM AlimentEnStock s "
         + "WHERE s.dictionnaire.id = :idAliment "
         + "AND s.datePeremption >= :aujourdhui "
         + "AND s.quantite > 0 "
         + "ORDER BY s.datePeremption, s.id")
    List<AlimentEnStock> findLotsConsommables(@Param("idAliment") Integer idAliment,
                                              @Param("aujourdhui") LocalDate aujourdhui);

    // Total d'un compartiment (les lots périmés comptent : ils occupent la place)
    @Query("SELECT COALESCE(SUM(s.quantite), 0) FROM AlimentEnStock s "
         + "WHERE s.compartiment.id = :idCompartiment")
    long sommeQuantitesCompartiment(@Param("idCompartiment") Integer idCompartiment);

    // Stock disponible d'un aliment : somme des lots non périmés
    @Query("SELECT COALESCE(SUM(s.quantite), 0) FROM AlimentEnStock s "
         + "WHERE s.dictionnaire.id = :idAliment AND s.datePeremption >= :aujourdhui")
    long sommeDisponible(@Param("idAliment") Integer idAliment,
                         @Param("aujourdhui") LocalDate aujourdhui);

    // Un lot identique : même aliment, même compartiment, même date de péremption
    Optional<AlimentEnStock> findFirstByDictionnaireIdAndCompartimentIdAndDatePeremption(
            Integer idAliment, Integer idCompartiment, LocalDate datePeremption);

    // Suppression des lots vides d'un aliment 
    long deleteByDictionnaireIdAndQuantite(Integer idAliment, Integer quantite);

     // Vrai si l'aliment a au moins un lot (il est alors « suivi »)
    boolean existsByDictionnaireId(Integer idAliment);
}