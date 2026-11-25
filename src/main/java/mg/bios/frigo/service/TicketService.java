package mg.bios.frigo.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mg.bios.frigo.dto.LigneTicketDto;
import mg.bios.frigo.dto.TicketDto;
import mg.bios.frigo.entity.DictionnaireAliment;
import mg.bios.frigo.exception.ApiException;
import mg.bios.frigo.repository.DictionnaireAlimentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Map;

// Interprète le TEXTE d'un ticket : reconnaît les aliments du dictionnaire, sans IA.
// Les règles sont fixes : le même ticket donne toujours le même résultat.
@Service
public class TicketService {

    private static final int MAX_LIGNES = 200;

    // « 4x125g » : le premier nombre est la quantité ; « x12 » : la quantité suit le x
    private static final Pattern QUANTITE_MULTIPLE = Pattern.compile("\\b(\\d{1,2})\\s?x\\s?\\d");
    private static final Pattern QUANTITE_SUFFIXE = Pattern.compile("\\bx\\s?(\\d{1,2})\\b");
    private static final Pattern NOMBRE = Pattern.compile("\\d+(?:[.,]\\d+)?");

    // Mots qui annoncent le début de la zone des articles, et mot qui annonce sa fin
    private static final Set<String> ENTETES_ARTICLES = Set.of("designation", "description", "article", "libelle");
    private static final Set<String> FINS_ARTICLES = Set.of("total");

    // Titres de colonnes (Qty, SellPrice, Montant...)
    private static final Set<String> COLONNES = Set.of(
            "qty", "quantite", "qte", "prix", "unit", "unite", "sellprice", "disc",
            "netvalue", "net", "value", "montant", "uom", "amount");

    // Lignes qui ne sont pas des produits
    private static final Set<String> MOTS_IGNORES = Set.of(
            "total", "tva", "sous", "especes", "espece", "carte", "cb", "rendu", "monnaie", "merci",
            "ticket", "caisse", "client", "facture", "remise", "payer", "bienvenue", "telephone",
            "tel", "adresse", "siret", "fidelite", "point", "points", "avoir",
            "bill", "items", "item", "qty", "tax", "amt", "amount", "roundoff", "net", "value",
            "received", "savings", "tender", "reference", "bank", "payment", "mpesa", "cash",
            "memo", "invoice", "sales", "counter", "visite", "bientot");

    // Mots d'emballage ou d'unité : ils n'empêchent pas une reconnaissance sûre
    private static final Set<String> MOTS_BRUIT = Set.of(
            "bio", "uht", "nature", "nat", "pack", "lot", "sachet", "barquette", "bouteille",
            "boite", "bte", "pce", "piece", "kg", "g", "gr", "l", "ml", "cl", "x", "promo",
            "pc", "pcs", "ltr", "pkt", "paquet", "vrac");

    // Unités de poids ou de volume : l'article est pesé ou vendu au volume
    private static final Set<String> UNITES_POIDS = Set.of("kg", "g", "gr", "l", "ltr", "ml", "cl");

    private record Candidat(DictionnaireAliment aliment, List<String> jetons, int longueur) {
    }

    private final DictionnaireAlimentRepository repository;

    public TicketService(DictionnaireAlimentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public TicketDto analyserTexte(String texte) {
        if (texte == null || texte.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TEXTE_VIDE", "Le texte du ticket est vide");
        }
        List<String> propres = Arrays.stream(texte.split("\\R"))
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();
        if (propres.size() > MAX_LIGNES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TEXTE_TROP_LONG",
                    "Un ticket ne peut pas dépasser " + MAX_LIGNES + " lignes");
        }

        // La zone des articles : après la ligne de titre, jusqu'à la ligne « Total ».
        // Sans ligne de titre, tout le texte est analysé.
        int debut = -1;
        for (int i = 0; i < propres.size(); i++) {
            if (contientMot(propres.get(i), ENTETES_ARTICLES)) {
                debut = i;
                break;
            }
        }
        int premiere = debut + 1;
        int fin = propres.size();
        if (debut >= 0) {
            for (int j = premiere; j < propres.size(); j++) {
                if (contientMot(propres.get(j), FINS_ARTICLES)) {
                    fin = j;
                    break;
                }
            }
        }
        int ignorees = premiere + (propres.size() - fin);

        List<Candidat> candidats = repository.findAll().stream().map(this::versCandidat).toList();
        List<LigneTicketDto> resultat = new ArrayList<>();
        for (int i = premiere; i < fin; i++) {
            String ligne = propres.get(i);
            if (sansLettre(ligne) || estEnTeteColonnes(ligne) || estIgnoree(ligne)) {
                ignorees++;
                continue;
            }
            // Certains tickets mettent quantité et prix sur la ligne qui suit le nom
            String suivante = (i + 1 < fin && sansLettre(propres.get(i + 1))) ? propres.get(i + 1) : null;
            resultat.add(analyserLigne(ligne, suivante, candidats));
        }
        return new TicketDto(resultat, ignorees);
    }

    private LigneTicketDto analyserLigne(String brute, String suivante, List<Candidat> candidats) {
        String normalise = TexteTicket.normaliser(brute);

        // Quantité : dans la ligne (4x125g, x12), sinon sur la ligne suivante
        String unitePoids = Arrays.stream(normalise.split(" "))
                .filter(UNITES_POIDS::contains)
                .findFirst()
                .orElse(null);
        Integer dansLaLigne = extraireQuantite(normalise);
        int quantite = 1;
        String note = null;
        if (dansLaLigne != null) {
            quantite = dansLaLigne;
        } else if (suivante != null) {
            Matcher m = NOMBRE.matcher(suivante);
            if (m.find()) {
                String lu = m.group().replace(',', '.');
                double valeur = Double.parseDouble(lu);
                boolean entier = valeur == Math.rint(valeur);
                if (unitePoids != null || !entier) {
                    // Article pesé : une unité, la valeur du ticket est gardée dans la note
                    note = "Quantité sur le ticket : " + lu
                            + (unitePoids != null ? " " + unitePoids : "") + " (comptée comme 1 unité)";
                } else {
                    quantite = borner((int) Math.round(valeur));
                }
            }
        }
        if (note == null && unitePoids != null) {
            note = "Vendu au poids ou au volume (" + unitePoids + ") : compté comme 1 unité";
        }

        Set<String> significatifs = new LinkedHashSet<>(TexteTicket.jetonsSignificatifs(brute));

        // Les aliments dont TOUS les mots figurent dans la ligne, du plus précis au moins précis
        List<Candidat> trouves = candidats.stream()
                .filter(c -> !c.jetons().isEmpty() && significatifs.containsAll(c.jetons()))
                .sorted(Comparator.comparingInt((Candidat c) -> c.jetons().size())
                        .thenComparingInt(Candidat::longueur).reversed())
                .toList();

        // Aucun aliment connu : l'admin pourra le rattacher, l'ajouter au dictionnaire ou l'ignorer
        if (trouves.isEmpty()) {
            return new LigneTicketDto(brute, "INCONNU", null, null, null, null, quantite,
                    null, null, null, proposerNom(brute), note);
        }

        Candidat meilleur = trouves.get(0);
        List<String> egalites = trouves.stream().skip(1)
                .filter(c -> c.jetons().size() == meilleur.jetons().size()
                        && c.longueur() == meilleur.longueur())
                .map(c -> c.aliment().getNom())
                .toList();
        // Les mots de la ligne que l'aliment trouvé n'explique pas (hors emballages et unités)
        List<String> restants = significatifs.stream()
                .filter(t -> !meilleur.jetons().contains(t))
                .filter(t -> !MOTS_BRUIT.contains(t))
                .toList();
        
        Map<String, String> originaux = motsOriginaux(brute);

        String confiance = "SUR";
        String raison = null;
        if (!egalites.isEmpty()) {
            confiance = "A_VERIFIER";
            raison = "Plusieurs aliments possibles : " + meilleur.aliment().getNom()
                    + ", " + String.join(", ", egalites);
        } else if (!restants.isEmpty()) {
            confiance = "A_VERIFIER";
            raison = "Mots non expliqués : "
                    + String.join(", ", restants.stream().map(t -> originaux.getOrDefault(t, t)).toList());
        }
        
        return new LigneTicketDto(brute, "RECONNU", confiance, raison,
                meilleur.aliment().getId(), meilleur.aliment().getNom(), quantite,
                null, null, null, null, note);
    }

    // « doux » devient « dou » au singulier : pour l'affichage, on retrouve le mot tel qu'écrit sur le ticket
    private Map<String, String> motsOriginaux(String brute) {
        Map<String, String> originaux = new HashMap<>();
        for (String mot : TexteTicket.normaliser(brute).split(" ")) {
            if (!mot.isEmpty()) {
                originaux.putIfAbsent(TexteTicket.singulier(mot), mot);
            }
        }
        return originaux;
    }

    private Candidat versCandidat(DictionnaireAliment aliment) {
        return new Candidat(aliment, TexteTicket.jetonsSignificatifs(aliment.getNom()),
                TexteTicket.normaliser(aliment.getNom()).length());
    }

    // Vrai si un des mots de la ligne figure dans l'ensemble
    private boolean contientMot(String ligne, Set<String> mots) {
        for (String jeton : TexteTicket.normaliser(ligne).split(" ")) {
            if (mots.contains(jeton)) {
                return true;
            }
        }
        return false;
    }

    private boolean sansLettre(String ligne) {
        return ligne.chars().noneMatch(Character::isLetter);
    }

    // « Qty SellPrice Disc NetValue » : uniquement des titres de colonnes
    private boolean estEnTeteColonnes(String ligne) {
        List<String> mots = Arrays.stream(TexteTicket.normaliser(ligne).split(" "))
                .filter(t -> !t.isEmpty() && t.chars().allMatch(Character::isLetter))
                .toList();
        return !mots.isEmpty() && COLONNES.containsAll(mots);
    }

    // Une ligne sans vrai mot, ou qui contient TOTAL, TVA, CARTE, MERCI... n'est pas un produit
    private boolean estIgnoree(String brute) {
        List<String> jetons = Arrays.asList(TexteTicket.normaliser(brute).split(" "));
        boolean aUnMot = jetons.stream()
                .anyMatch(t -> t.length() >= 2 && t.chars().allMatch(Character::isLetter));
        return !aUnMot || jetons.stream().anyMatch(MOTS_IGNORES::contains);
    }

    // Retourne null si la ligne n'indique aucune quantité
    private Integer extraireQuantite(String normalise) {
        Matcher m = QUANTITE_MULTIPLE.matcher(normalise);
        if (m.find()) {
            return borner(Integer.parseInt(m.group(1)));
        }
        m = QUANTITE_SUFFIXE.matcher(normalise);
        if (m.find()) {
            return borner(Integer.parseInt(m.group(1)));
        }
        return null;
    }

    private int borner(int quantite) {
        return Math.max(1, Math.min(quantite, 99));
    }

    // Nom proposé pour le dictionnaire : la ligne sans prix, poids ni emballage, première lettre en majuscule
    private String proposerNom(String brute) {
        List<String> mots = new ArrayList<>();
        for (String mot : brute.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (mot.isEmpty() || mot.chars().anyMatch(Character::isDigit)) {
                continue;
            }
            if (MOTS_BRUIT.contains(TexteTicket.singulier(TexteTicket.normaliser(mot)))) {
                continue;
            }
            mots.add(mot);
        }
        return NomsAliments.nettoyer(String.join(" ", mots));
    }
}