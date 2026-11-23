package mg.bios.frigo.dto;

// seuilMinimum est facultatif : 1 par défaut
public record DictionnaireRequest(String nom, Integer idCategorie, Integer seuilMinimum) {
}