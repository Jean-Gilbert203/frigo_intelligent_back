package mg.bios.frigo.dto;

public record CategorieDto(
        Integer id,
        String nom,
        Integer idCompartimentDefaut,
        String compartimentDefaut) {
}