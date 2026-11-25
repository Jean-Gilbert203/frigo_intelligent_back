package mg.bios.frigo.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Outils de comparaison de textes : minuscules, sans accents, mots entiers
public final class TexteTicket {

    // Petits mots sans valeur pour reconnaître un aliment
    private static final Set<String> MOTS_VIDES = Set.of(
            "de", "du", "des", "d", "la", "le", "les", "l", "et", "au", "aux", "en", "a", "un", "une");

    private TexteTicket() {
    }

    // « Crème Fraîche d'Isigny » donne « creme fraiche d isigny » ; œ devient oe
    public static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        String s = texte.toLowerCase(Locale.ROOT).replace("œ", "oe").replace("æ", "ae");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return s.replaceAll("[^a-z0-9]+", " ").trim();
    }

    // Forme singulière simple d'un mot : « yaourts » donne « yaourt »
    public static String singulier(String jeton) {
        if (jeton.length() > 3 && (jeton.endsWith("s") || jeton.endsWith("x"))) {
            return jeton.substring(0, jeton.length() - 1);
        }
        return jeton;
    }

    // Les mots qui comptent : sans chiffres (poids, prix, quantités), sans petits mots, au singulier
    public static List<String> jetonsSignificatifs(String texte) {
        String norme = normaliser(texte);
        Set<String> resultat = new LinkedHashSet<>();
        if (!norme.isEmpty()) {
            for (String jeton : norme.split(" ")) {
                if (jeton.isEmpty() || contientChiffre(jeton) || MOTS_VIDES.contains(jeton)) {
                    continue;
                }
                resultat.add(singulier(jeton));
            }
        }
        return new ArrayList<>(resultat);
    }

    private static boolean contientChiffre(String jeton) {
        return jeton.chars().anyMatch(Character::isDigit);
    }
}