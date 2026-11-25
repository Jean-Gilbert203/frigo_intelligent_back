package mg.bios.frigo.repository;

import java.util.List;
import mg.bios.frigo.entity.Notification;
import mg.bios.frigo.entity.TypeNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    // Les plus récentes d'abord ; Pageable limite le nombre de résultats
    @Query("SELECT n FROM Notification n LEFT JOIN FETCH n.aliment "
         + "WHERE n.destinataire.id = :idUtilisateur "
         + "ORDER BY n.dateEnvoi DESC, n.id DESC")
    List<Notification> findRecentes(@Param("idUtilisateur") Integer idUtilisateur, Pageable pageable);

    @Query("SELECT n FROM Notification n LEFT JOIN FETCH n.aliment "
         + "WHERE n.destinataire.id = :idUtilisateur AND n.lu = false "
         + "ORDER BY n.dateEnvoi DESC, n.id DESC")
    List<Notification> findNonLues(@Param("idUtilisateur") Integer idUtilisateur);

     // Anti-doublon : une notification identique (même type, même aliment, même titre) n'est pas encore lue
    boolean existsByDestinataireIdAndTypeAndAlimentIdAndTitreAndLuFalse(
            Integer idUtilisateur, TypeNotification type, Integer idAliment, String titre);

    // Anti-doublon : une notification du même type existe déjà pour cette session de porte
    boolean existsByDestinataireIdAndTypeAndOuvertureId(
            Integer idUtilisateur, TypeNotification type, Integer idOuverture);

    @Modifying
    @Query("UPDATE Notification n SET n.lu = true "
         + "WHERE n.destinataire.id = :idUtilisateur AND n.lu = false")
    int marquerToutesLues(@Param("idUtilisateur") Integer idUtilisateur);
}