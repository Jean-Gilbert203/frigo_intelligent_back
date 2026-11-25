package mg.bios.frigo.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import mg.bios.frigo.dto.MarquageDto;
import mg.bios.frigo.dto.NotificationDto;
import mg.bios.frigo.entity.DictionnaireAliment;
import mg.bios.frigo.entity.Notification;
import mg.bios.frigo.entity.OuverturePorte;
import mg.bios.frigo.entity.Role;
import mg.bios.frigo.entity.SupermarcheSimule;
import mg.bios.frigo.entity.TypeNotification;
import mg.bios.frigo.entity.Utilisateur;
import mg.bios.frigo.exception.ApiException;
import mg.bios.frigo.repository.NotificationRepository;
import mg.bios.frigo.repository.UtilisateurRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final UtilisateurRepository utilisateurRepository;
    private final DiffusionService diffusion;

    public NotificationService(NotificationRepository repository,
                               UtilisateurRepository utilisateurRepository,
                               DiffusionService diffusion) {
        this.repository = repository;
        this.utilisateurRepository = utilisateurRepository;
        this.diffusion = diffusion;
    }

    // ---------------------------------------------------------------- envoi

    // Enregistre la notification puis la diffuse (après validation de la transaction).
    // Retourne faux si un doublon a été évité (règle R13).
    @Transactional
    public boolean notifier(Utilisateur destinataire, TypeNotification type, String titre,
                            String message, DictionnaireAliment aliment,
                            OuverturePorte ouverture, SupermarcheSimule magasin) {
        if (aliment != null && repository.existsByDestinataireIdAndTypeAndAlimentIdAndTitreAndLuFalse(
            destinataire.getId(), type, aliment.getId(), titre)) {
            return false;
        }
        if (ouverture != null && repository.existsByDestinataireIdAndTypeAndOuvertureId(
                destinataire.getId(), type, ouverture.getId())) {
            return false;
        }
        Notification notification = new Notification();
        notification.setDestinataire(destinataire);
        notification.setType(type);
        notification.setTitre(titre);
        notification.setMessage(message);
        notification.setAliment(aliment);
        notification.setOuverture(ouverture);
        notification.setMagasin(magasin);
        notification = repository.save(notification);

        diffusion.diffuser("/topic/notifications/" + destinataire.getId(), versDto(notification));
        return true;
    }

    // Une notification pour chaque administrateur actif. Retourne le nombre créées.
    @Transactional
    public int notifierAdmins(TypeNotification type, String titre, String message,
                              DictionnaireAliment aliment, OuverturePorte ouverture) {
        int creees = 0;
        for (Utilisateur admin : utilisateurRepository.findByRoleAndActifTrue(Role.ADMIN)) {
            if (notifier(admin, type, titre, message, aliment, ouverture, null)) {
                creees++;
            }
        }
        return creees;
    }

    // Notifications de stock : RUPTURE_STOCK à 0, STOCK_FAIBLE sous le seuil. Retourne le nombre créées.
    @Transactional
    public int notifierStock(DictionnaireAliment aliment, int disponible, boolean ajouteAuxCourses) {
        if (disponible == 0) {
            return notifierAdmins(TypeNotification.RUPTURE_STOCK, "Rupture de stock",
                    aliment.getNom() + " : plus aucune unité disponible.", aliment, null);
        }
        if (disponible <= aliment.getSeuilMinimum()) {
            String suite = ajouteAuxCourses ? " Ajouté à la liste de courses." : "";
            return notifierAdmins(TypeNotification.STOCK_FAIBLE, "Stock faible",
                    aliment.getNom() + " : " + disponible + " restant(s), seuil "
                            + aliment.getSeuilMinimum() + "." + suite, aliment, null);
        }
        return 0;
    }

    // ---------------------------------------------------------------- lecture

    @Transactional(readOnly = true)
    public List<NotificationDto> lire(Integer idUtilisateur, int limite) {
        int taille = Math.max(1, Math.min(limite, 200));
        return repository.findRecentes(idUtilisateur, PageRequest.of(0, taille)).stream()
                .map(this::versDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> nonLues(Integer idUtilisateur) {
        return repository.findNonLues(idUtilisateur).stream().map(this::versDto).toList();
    }

    @Transactional
    public NotificationDto marquerLue(Integer idNotification, Utilisateur appelant) {
        Notification notification = repository.findById(idNotification)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_INCONNUE",
                        "Notification inconnue : " + idNotification));
        if (!notification.getDestinataire().getId().equals(appelant.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOTIFICATION_INTERDITE",
                    "Cette notification appartient à un autre utilisateur");
        }
        notification.marquerCommeLue();
        return versDto(repository.save(notification));
    }

    @Transactional
    public MarquageDto marquerToutesLues(Utilisateur appelant) {
        return new MarquageDto(repository.marquerToutesLues(appelant.getId()));
    }

    // ---------------------------------------------------------------- conversion

    private NotificationDto versDto(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getTitre(),
                n.getMessage(),
                n.getType().name(),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(n.getDateEnvoi()),
                Boolean.TRUE.equals(n.getLu()),
                n.getAliment() != null ? n.getAliment().getId() : null,
                n.getAliment() != null ? n.getAliment().getNom() : null,
                n.getOuverture() != null ? n.getOuverture().getId() : null,
                n.getMagasin() != null ? n.getMagasin().getId() : null);
    }
}