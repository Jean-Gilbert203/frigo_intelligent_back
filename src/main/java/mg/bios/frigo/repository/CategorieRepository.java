package mg.bios.frigo.repository;

import java.util.List;
import java.util.Optional;
import mg.bios.frigo.entity.Categorie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategorieRepository extends JpaRepository<Categorie, Integer> {

    // Toutes les catégories avec leur compartiment par défaut, triées par nom
    @Query("SELECT c FROM Categorie c JOIN FETCH c.compartimentDefaut ORDER BY c.nom")
    List<Categorie> findAllAvecCompartiment();

    // Insensible à la casse et aux accents
    Optional<Categorie> findByNom(String nom);
}