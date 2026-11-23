package mg.bios.frigo.dto;

import java.util.List;

// proche : le magasin le plus proche est dans le rayon
// rappel : proche ET il y a des articles à acheter
public record GpsDto(
        boolean proche,
        boolean rappel,
        MagasinProcheDto magasin,
        List<CourseDto> articles,
        boolean notificationCreee) {
}