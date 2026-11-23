package mg.bios.frigo.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import mg.bios.frigo.dto.CourseDto;
import mg.bios.frigo.dto.GpsDto;
import mg.bios.frigo.dto.MagasinProcheDto;
import mg.bios.frigo.entity.SupermarcheSimule;
import mg.bios.frigo.entity.TypeNotification;
import mg.bios.frigo.entity.Utilisateur;
import mg.bios.frigo.exception.ApiException;
import mg.bios.frigo.repository.SupermarcheSimuleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GpsService {

    private final SupermarcheSimuleRepository magasinRepository;
    private final CourseService courseService;
    private final NotificationService notificationService;
    private final int rayonRappel;
    private final int rayonRearmement;

    // Pour chaque utilisateur, le dernier magasin pour lequel un rappel a été envoyé.
    // Gardé en mémoire : il est perdu si le serveur redémarre, ce qui est acceptable ici.
    private final Map<Integer, Integer> dernierMagasinSignale = new ConcurrentHashMap<>();

    public GpsService(SupermarcheSimuleRepository magasinRepository,
                      CourseService courseService,
                      NotificationService notificationService,
                      @Value("${frigo.rayon-rappel-metres:500}") int rayonRappel,
                      @Value("${frigo.rayon-rearmement-metres:600}") int rayonRearmement) {
        this.magasinRepository = magasinRepository;
        this.courseService = courseService;
        this.notificationService = notificationService;
        this.rayonRappel = rayonRappel;
        this.rayonRearmement = rayonRearmement;
    }

    @Transactional
    public GpsDto evaluer(Utilisateur admin, Double latitude, Double longitude) {
        // Coordonnées valides
        if (latitude == null || longitude == null
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COORDONNEES_INVALIDES",
                    "Latitude entre -90 et 90, longitude entre -180 et 180");
        }

        // Le magasin enregistré le plus proche (formule de Haversine)
        SupermarcheSimule plusProche = null;
        double distanceMin = Double.MAX_VALUE;
        for (SupermarcheSimule magasin : magasinRepository.findAll()) {
            double distance = magasin.distanceVers(latitude, longitude);
            if (distance < distanceMin) {
                distanceMin = distance;
                plusProche = magasin;
            }
        }

        List<CourseDto> articles = courseService.aAcheter();
        if (plusProche == null) {
            return new GpsDto(false, false, null, articles, false);
        }

        long distanceArrondie = Math.round(distanceMin);
        boolean proche = distanceMin < rayonRappel;
        boolean rappel = proche && !articles.isEmpty();
        boolean notificationCreee = false;

        // Une seule alerte par approche
        if (distanceMin > rayonRearmement) {
            // Assez loin : l'alerte est réarmée
            dernierMagasinSignale.remove(admin.getId());
        } else if (rappel) {
            Integer dejaSignale = dernierMagasinSignale.get(admin.getId());
            if (!plusProche.getId().equals(dejaSignale)) {
                String message = construireMessage(plusProche, distanceArrondie, articles);
                notificationCreee = notificationService.notifier(admin, TypeNotification.RAPPEL_COURSES,
                        "Courses à faire", message, null, null, plusProche);
                dernierMagasinSignale.put(admin.getId(), plusProche.getId());
            }
        }

        return new GpsDto(proche, rappel,
                new MagasinProcheDto(plusProche.getId(), plusProche.getNomMagasin(), distanceArrondie),
                articles, notificationCreee);
    }

    private String construireMessage(SupermarcheSimule magasin, long distance, List<CourseDto> articles) {
        String noms = articles.stream().limit(8).map(CourseDto::nom).collect(Collectors.joining(", "));
        if (articles.size() > 8) {
            noms += "...";
        }
        String message = "Vous êtes à " + distance + " m de " + magasin.getNomMagasin()
                + ". À acheter : " + noms + ".";
        // La colonne de la base accepte 500 caractères au maximum
        return message.length() > 500 ? message.substring(0, 497) + "..." : message;
    }
}