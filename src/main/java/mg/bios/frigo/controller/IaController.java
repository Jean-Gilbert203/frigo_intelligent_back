package mg.bios.frigo.controller;

import mg.bios.frigo.dto.TicketDto;
import mg.bios.frigo.dto.TicketRequest;
import mg.bios.frigo.service.IdentiteService;
import mg.bios.frigo.service.TicketService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ia")
public class IaController {

    private final TicketService ticketService;
    private final IdentiteService identite;

    public IaController(TicketService ticketService, IdentiteService identite) {
        this.ticketService = ticketService;
        this.identite = identite;
    }

    // Mode « texte brut » : sans caméra ni internet (mock de secours pour la démonstration)
    @PostMapping("/analyser-texte")
    public TicketDto analyserTexte(@RequestBody TicketRequest requete) {
        identite.exigerAdmin();
        return ticketService.analyserTexte(requete.texte());
    }
}