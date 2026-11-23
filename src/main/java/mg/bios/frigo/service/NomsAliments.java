package mg.bios.frigo.service;

// Règles de nettoyage des noms (aliments, catégories)
public final class NomsAliments {

    private NomsAliments() {
    }

    // Espaces superflus retirés, première lettre en majuscule : « thon  frais » donne « Thon frais »
    public static String nettoyer(String saisie) {
        if (saisie == null) {
            return "";
        }
        String nom = saisie.trim().replaceAll("\\s+", " ");
        if (nom.isEmpty()) {
            return nom;
        }
        return Character.toUpperCase(nom.charAt(0)) + nom.substring(1);
    }

    // Forme singulière simple : « Yaourts » donne « Yaourt » (null si le mot est trop court)
    public static String singulier(String nom) {
        if (nom.length() > 3 && (nom.endsWith("s") || nom.endsWith("x"))) {
            return nom.substring(0, nom.length() - 1);
        }
        return null;
    }
}