package mg.bios.frigo.service;

import java.util.List;
import mg.bios.frigo.dto.CategorieDto;
import mg.bios.frigo.dto.CategorieRequest;
import mg.bios.frigo.entity.Categorie;
import mg.bios.frigo.entity.Compartiment;
import mg.bios.frigo.exception.ApiException;
import mg.bios.frigo.repository.CategorieRepository;
import mg.bios.frigo.repository.CompartimentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategorieService {

    private final CategorieRepository repository;
    private final CompartimentRepository compartimentRepository;

    public CategorieService(CategorieRepository repository, CompartimentRepository compartimentRepository) {
        this.repository = repository;
        this.compartimentRepository = compartimentRepository;
    }

    @Transactional(readOnly = true)
    public List<CategorieDto> lire() {
        return repository.findAllAvecCompartiment().stream().map(this::versDto).toList();
    }

    @Transactional
    public CategorieDto creer(CategorieRequest requete) {
        String nom = NomsAliments.nettoyer(requete.nom());
        if (nom.isEmpty() || nom.length() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOM_INVALIDE",
                    "Le nom de la catégorie est obligatoire");
        }
        if (requete.idCompartimentDefaut() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPARTIMENT_REQUIS",
                    "Indiquer le compartiment par défaut de la catégorie");
        }
        Compartiment compartiment = compartimentRepository.findById(requete.idCompartimentDefaut())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPARTIMENT_INCONNU",
                        "Compartiment inconnu : " + requete.idCompartimentDefaut()));
        if (repository.findByNom(nom).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "NOM_DEJA_EXISTANT",
                    "Une catégorie porte déjà ce nom : " + nom);
        }
        Categorie categorie = new Categorie();
        categorie.setNom(nom);
        categorie.setCompartimentDefaut(compartiment);
        return versDto(repository.save(categorie));
    }

    private CategorieDto versDto(Categorie c) {
        return new CategorieDto(c.getId(), c.getNom(),
                c.getCompartimentDefaut().getId(), c.getCompartimentDefaut().getNom());
    }
}