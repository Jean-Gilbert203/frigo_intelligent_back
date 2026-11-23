package mg.bios.frigo.service;

import java.time.LocalDate;
import java.util.regex.Pattern;
import mg.bios.frigo.dto.DictionnaireDto;
import mg.bios.frigo.dto.DictionnaireModification;
import mg.bios.frigo.dto.DictionnaireRequest;
import mg.bios.frigo.entity.Categorie;
import mg.bios.frigo.entity.DictionnaireAliment;
import mg.bios.frigo.exception.ApiException;
import mg.bios.frigo.repository.AlimentEnStockRepository;
import mg.bios.frigo.repository.CategorieRepository;
import mg.bios.frigo.repository.DictionnaireAlimentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DictionnaireService {

    // Nom de fichier simple : minuscules, chiffres et tirets, avec une extension d'image
    private static final Pattern IMAGE = Pattern.compile("^[a-z0-9-]+\\.(svg|png|jpg|jpeg|webp)$");

    private final DictionnaireAlimentRepository repository;
    private final CategorieRepository categorieRepository;
    private final AlimentEnStockRepository stockRepository;
    private final CourseService courseService;
    private final NotificationService notificationService;
    private final DiffusionService diffusion;

    public DictionnaireService(DictionnaireAlimentRepository repository,
                               CategorieRepository categorieRepository,
                               AlimentEnStockRepository stockRepository,
                               CourseService courseService,
                               NotificationService notificationService,
                               DiffusionService diffusion) {
        this.repository = repository;
        this.categorieRepository = categorieRepository;
        this.stockRepository = stockRepository;
        this.courseService = courseService;
        this.notificationService = notificationService;
        this.diffusion = diffusion;
    }

    @Transactional
    public DictionnaireDto creer(DictionnaireRequest requete) {
        // Nom valide et pas déjà présent 
        String nom = NomsAliments.nettoyer(requete.nom());
        if (nom.isEmpty() || nom.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOM_INVALIDE",
                    "Le nom de l'aliment est obligatoire");
        }
        String singulier = NomsAliments.singulier(nom);
        if (repository.findByNom(nom).isPresent()
                || (singulier != null && repository.findByNom(singulier).isPresent())) {
            throw new ApiException(HttpStatus.CONFLICT, "NOM_DEJA_EXISTANT",
                    "Cet aliment existe déjà dans le dictionnaire : " + nom);
        }

        // Seuil : 1 par défaut, jamais négatif
        int seuil = requete.seuilMinimum() == null ? 1 : requete.seuilMinimum();
        if (seuil < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SEUIL_INVALIDE",
                    "Le seuil minimum ne peut pas être négatif");
        }

        // Catégorie obligatoire : le compartiment par défaut s'en déduit
        if (requete.idCategorie() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORIE_REQUISE",
                    "Indiquer la catégorie de l'aliment");
        }
        Categorie categorie = trouverCategorie(requete.idCategorie());

        DictionnaireAliment aliment = new DictionnaireAliment();
        aliment.setNom(nom);
        aliment.setCategorie(categorie);
        aliment.setSeuilMinimum(seuil);
        aliment.setImage("defaut.svg");
        return versDto(repository.save(aliment));
    }

    @Transactional
    public DictionnaireDto modifier(Integer id, DictionnaireModification requete) {
        DictionnaireAliment aliment = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ALIMENT_INCONNU",
                        "Aliment inconnu : " + id));

        boolean seuilChange = false;
        if (requete.seuilMinimum() != null) {
            if (requete.seuilMinimum() < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SEUIL_INVALIDE",
                        "Le seuil minimum ne peut pas être négatif");
            }
            seuilChange = !requete.seuilMinimum().equals(aliment.getSeuilMinimum());
            aliment.setSeuilMinimum(requete.seuilMinimum());
        }
        if (requete.idCategorie() != null) {
            aliment.setCategorie(trouverCategorie(requete.idCategorie()));
        }
        if (requete.image() != null) {
            if (!IMAGE.matcher(requete.image()).matches()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_INVALIDE",
                        "Nom de fichier attendu : minuscules, chiffres et tirets, avec .svg, .png, .jpg ou .webp");
            }
            aliment.setImage(requete.image());
        }
        aliment = repository.save(aliment);

        // Un nouveau seuil peut changer la liste de courses et déclencher une alerte 
        if (seuilChange && stockRepository.existsByDictionnaireId(id)) {
            int disponible = (int) stockRepository.sommeDisponible(id, LocalDate.now());
            boolean coursesModifiees = courseService.synchroniser(aliment, disponible);
            notificationService.notifierStock(aliment, disponible, coursesModifiees);
            if (coursesModifiees) {
                diffusion.evenement("/topic/courses", "COURSES_MODIFIEES");
            }
        }
        diffusion.evenement("/topic/stocks", "STOCK_MODIFIE");
        return versDto(aliment);
    }

    private Categorie trouverCategorie(Integer idCategorie) {
        return categorieRepository.findById(idCategorie)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORIE_INCONNUE",
                        "Catégorie inconnue : " + idCategorie));
    }

    private DictionnaireDto versDto(DictionnaireAliment d) {
        return new DictionnaireDto(
                d.getId(),
                d.getNom(),
                "/images/aliments/" + d.getImage(),
                d.getSeuilMinimum(),
                d.getCategorie().getNom(),
                d.getCategorie().getCompartimentDefaut().getNom());
    }
}