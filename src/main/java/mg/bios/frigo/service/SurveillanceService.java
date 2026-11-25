package mg.bios.frigo.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mg.bios.frigo.dto.ResultatSurveillance;
import mg.bios.frigo.entity.AlimentEnStock;
import mg.bios.frigo.entity.DictionnaireAliment;
import mg.bios.frigo.entity.TypeNotification;
import mg.bios.frigo.repository.AlimentEnStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Les contrôles « du jour » : stock, liste de courses, péremption.
// Appelé par le voyage dans le temps, puis par la tâche planifiée quotidienne.
@Service
public class SurveillanceService {

    private final AlimentEnStockRepository stockRepository;
    private final CourseService courseService;
    private final NotificationService notificationService;
    private final StockService stockService;

    public SurveillanceService(AlimentEnStockRepository stockRepository,
                               CourseService courseService,
                               NotificationService notificationService,
                               StockService stockService) {
        this.stockRepository = stockRepository;
        this.courseService = courseService;
        this.notificationService = notificationService;
        this.stockService = stockService;
    }

    @Transactional
    public ResultatSurveillance verifier() {
        LocalDate aujourdhui = LocalDate.now();

        // Les lots, regroupés par aliment suivi (un aliment est suivi s'il a au moins un lot)
        Map<Integer, List<AlimentEnStock>> parAliment = new LinkedHashMap<>();
        for (AlimentEnStock lot : stockRepository.findAllAvecDetails()) {
            parAliment.computeIfAbsent(lot.getDictionnaire().getId(), cle -> new ArrayList<>()).add(lot);
        }

        int notifications = 0;
        boolean coursesModifiees = false;

        for (List<AlimentEnStock> groupe : parAliment.values()) {
            DictionnaireAliment aliment = groupe.get(0).getDictionnaire();

            // 1. Stock disponible (lots périmés exclus) et liste de courses
            int disponible = 0;
            for (AlimentEnStock lot : groupe) {
                if (!lot.estPerime(aujourdhui)) {
                    disponible += lot.getQuantite();
                }
            }
            boolean courses = courseService.synchroniser(aliment, disponible);
            if (courses) {
                coursesModifiees = true;
            }

            // 2. Péremption : seuls les lots non vides comptent
            boolean perime = false;
            boolean bientot = false;
            for (AlimentEnStock lot : groupe) {
                if (lot.getQuantite() == 0) {
                    continue;
                }
                String etat = stockService.etatPeremption(lot.joursAvantPeremption(aujourdhui));
                if ("PERIME".equals(etat)) {
                    perime = true;
                } else if ("BIENTOT_PERIME".equals(etat)) {
                    bientot = true;
                }
            }

            // 3. Notifications
            if (perime && disponible == 0) {
                // Un seul message : le produit est périmé ET il n'en reste plus de consommable
                String suite = courses ? " Ajouté à la liste de courses." : "";
                notifications += notificationService.notifierAdmins(TypeNotification.PEREMPTION,
                        "Produit périmé",
                        aliment.getNom() + " : lot périmé, plus aucune unité consommable." + suite,
                        aliment, null);
            } else {
                notifications += notificationService.notifierStock(aliment, disponible, courses);
                if (perime) {
                    notifications += notificationService.notifierAdmins(TypeNotification.PEREMPTION,
                            "Produit périmé",
                            aliment.getNom() + " : au moins un lot est périmé, à jeter.", aliment, null);
                } else if (bientot) {
                    notifications += notificationService.notifierAdmins(TypeNotification.PEREMPTION,
                            "Péremption proche",
                            aliment.getNom() + " : un lot arrive bientôt à péremption.", aliment, null);
                }
            }
        }
        return new ResultatSurveillance(notifications, coursesModifiees);
    }
}