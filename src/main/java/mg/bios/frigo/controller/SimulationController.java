package mg.bios.frigo.controller;

import mg.bios.frigo.dto.AvanceeTempsDto;
import mg.bios.frigo.dto.AvanceeTempsRequest;
import mg.bios.frigo.dto.GpsDto;
import mg.bios.frigo.dto.PositionRequest;
import mg.bios.frigo.entity.Utilisateur;
import mg.bios.frigo.service.GpsService;
import mg.bios.frigo.service.IdentiteService;
import mg.bios.frigo.service.SimulationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService simulationService;
    private final GpsService gpsService;
    private final IdentiteService identite;

    public SimulationController(SimulationService simulationService,
                                GpsService gpsService,
                                IdentiteService identite) {
        this.simulationService = simulationService;
        this.gpsService = gpsService;
        this.identite = identite;
    }

    // Outil de démonstration : tout utilisateur actif
    @PostMapping("/avancer-temps")
    public AvanceeTempsDto avancerTemps(@RequestBody AvanceeTempsRequest requete) {
        identite.utilisateurAppelant();
        return simulationService.avancerTemps(requete.jours());
    }

    // Position de l'administrateur (fictive via le curseur, réelle plus tard) : réservé à l'admin
    @PostMapping("/gps")
    public GpsDto gps(@RequestBody PositionRequest requete) {
        Utilisateur admin = identite.exigerAdmin();
        return gpsService.evaluer(admin, requete.latitude(), requete.longitude());
    }
}