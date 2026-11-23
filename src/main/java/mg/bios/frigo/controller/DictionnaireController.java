package mg.bios.frigo.controller;

import java.util.List;
import mg.bios.frigo.dto.DictionnaireDto;
import mg.bios.frigo.dto.DictionnaireModification;
import mg.bios.frigo.dto.DictionnaireRequest;
import mg.bios.frigo.repository.DictionnaireAlimentRepository;
import mg.bios.frigo.service.DictionnaireService;
import mg.bios.frigo.service.IdentiteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dictionnaire")
public class DictionnaireController {

    private final DictionnaireAlimentRepository repository;
    private final DictionnaireService dictionnaireService;
    private final IdentiteService identite;

    public DictionnaireController(DictionnaireAlimentRepository repository,
                                  DictionnaireService dictionnaireService,
                                  IdentiteService identite) {
        this.repository = repository;
        this.dictionnaireService = dictionnaireService;
        this.identite = identite;
    }

    @GetMapping
    public List<DictionnaireDto> lister() {
        return repository.findAllAvecCategorie().stream()
                .map(d -> new DictionnaireDto(
                        d.getId(),
                        d.getNom(),
                        "/images/aliments/" + d.getImage(),
                        d.getSeuilMinimum(),
                        d.getCategorie().getNom(),
                        d.getCategorie().getCompartimentDefaut().getNom()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<DictionnaireDto> creer(@RequestBody DictionnaireRequest requete) {
        identite.exigerAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(dictionnaireService.creer(requete));
    }

    @PutMapping("/{id}")
    public DictionnaireDto modifier(@PathVariable Integer id, @RequestBody DictionnaireModification requete) {
        identite.exigerAdmin();
        return dictionnaireService.modifier(id, requete);
    }
}