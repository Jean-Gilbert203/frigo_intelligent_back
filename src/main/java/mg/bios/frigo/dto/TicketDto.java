package mg.bios.frigo.dto;

import java.util.List;

public record TicketDto(List<LigneTicketDto> lignes, int lignesIgnorees) {
}