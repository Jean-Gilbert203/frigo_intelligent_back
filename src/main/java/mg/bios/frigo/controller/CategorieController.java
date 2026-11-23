package mg.bios.frigo.controller;

import java.util.List;
import mg.bios.frigo.dto.CategorieDto;
import mg.bios.frigo.dto.CategorieRequest;
import mg.bios.frigo.service.CategorieService;
import mg.bios.frigo.service.IdentiteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategorieController {

    private final CategorieService categorieService;
    private final IdentiteService identite;

    public CategorieController(CategorieService categorieService, IdentiteService identite) {
        this.categorieService = categorieService;
        this.identite = identite;
    }

    @GetMapping
    public List<CategorieDto> lire() {
        identite.exigerAdmin();
        return categorieService.lire();
    }

    @PostMapping
    public ResponseEntity<CategorieDto> creer(@RequestBody CategorieRequest requete) {
        identite.exigerAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(categorieService.creer(requete));
    }
}