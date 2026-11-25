package mg.bios.frigo.dto;

// statut : RECONNU ou INCONNU ; confiance : SUR ou A_VERIFIER (lignes reconnues seulement)
// categorieSuggeree et datePeremptionEstimee seront remplis par l'IA (étape 7b-2)
// note : information sur la quantité (article pesé, vendu au volume)
public record LigneTicketDto(
        String nomBrut,
        String statut,
        String confiance,
        String raison,
        Integer idDictionnaire,
        String nomReconnu,
        int quantite,
        Integer idCategorieSuggeree,
        String categorieSuggeree,
        String datePeremptionEstimee,
        String nomPropose,
        String note) {
}