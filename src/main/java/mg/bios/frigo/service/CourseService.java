package mg.bios.frigo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mg.bios.frigo.dto.CourseDto;
import mg.bios.frigo.entity.DictionnaireAliment;
import mg.bios.frigo.entity.LigneCourse;
import mg.bios.frigo.entity.StatutCourse;
import mg.bios.frigo.exception.ApiException;
import mg.bios.frigo.repository.AlimentEnStockRepository;
import mg.bios.frigo.repository.LigneCourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

    private final LigneCourseRepository repository;
    private final AlimentEnStockRepository stockRepository;
    private final DiffusionService diffusion;

    public CourseService(LigneCourseRepository repository,
                         AlimentEnStockRepository stockRepository,
                         DiffusionService diffusion) {
        this.repository = repository;
        this.stockRepository = stockRepository;
        this.diffusion = diffusion;
    }

    @Transactional
    public boolean synchroniser(DictionnaireAliment aliment, int disponible) {
        Optional<LigneCourse> existante = repository.findByDictionnaireId(aliment.getId());

        if (disponible <= aliment.getSeuilMinimum()) {
            if (existante.isEmpty()) {
                LigneCourse ligne = new LigneCourse();
                ligne.setDictionnaire(aliment);
                repository.save(ligne);
                return true;
            }
            LigneCourse ligne = existante.get();
            if (ligne.getStatut() == StatutCourse.A_ACHETER) {
                return false;
            }
            ligne.remettreAAcheter();
            repository.save(ligne);
            return true;
        }

        if (existante.isPresent() && existante.get().getStatut() == StatutCourse.A_ACHETER) {
            LigneCourse ligne = existante.get();
            ligne.marquerAchete();
            repository.save(ligne);
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<CourseDto> lire() {
        List<CourseDto> liste = new ArrayList<>(
                repository.findAllAvecAliment().stream().map(this::versDto).toList());
        liste.sort(Comparator.comparing((CourseDto c) -> !"A_ACHETER".equals(c.statut())));
        return liste;
    }

    // Les lignes encore « à acheter »
    @Transactional(readOnly = true)
    public List<CourseDto> aAcheter() {
        return lire().stream().filter(c -> "A_ACHETER".equals(c.statut())).toList();
    }

    @Transactional
    public CourseDto marquerAchete(Integer id) {
        LigneCourse ligne = trouver(id);
        ligne.marquerAchete();
        ligne = repository.save(ligne);
        diffusion.evenement("/topic/courses", "COURSES_MODIFIEES");
        return versDto(ligne);
    }

    // « Ne plus suivre » (R7) : supprime la ligne et les lots à 0 de l'aliment
    @Transactional
    public void nePlusSuivre(Integer id) {
        LigneCourse ligne = trouver(id);
        Integer idAliment = ligne.getDictionnaire().getId();
        stockRepository.deleteByDictionnaireIdAndQuantite(idAliment, 0);
        repository.delete(ligne);
        diffusion.evenement("/topic/courses", "COURSES_MODIFIEES");
        diffusion.evenement("/topic/stocks", "STOCK_MODIFIE");
    }

    private LigneCourse trouver(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COURSE_INCONNUE",
                        "Ligne de courses inconnue : " + id));
    }

    private CourseDto versDto(LigneCourse l) {
        DictionnaireAliment aliment = l.getDictionnaire();
        return new CourseDto(
                l.getId(),
                aliment.getId(),
                aliment.getNom(),
                "/images/aliments/" + aliment.getImage(),
                l.getQuantiteRecommandee(),
                l.getStatut().name());
    }
}