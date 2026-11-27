package mg.bios.frigo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import mg.bios.frigo.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// Enregistre les images d'aliments envoyées depuis l'application
@Service
public class ImageService {

    private static final Logger LOG = LoggerFactory.getLogger(ImageService.class);
    private static final long TAILLE_MAX = 5L * 1024 * 1024;

    // Les noms que cette classe crée : minuscules, chiffres et tirets, avec une extension d'image
    private static final Pattern NOM_CREE = Pattern.compile("^[a-z0-9-]+\\.(png|jpg|webp)$");

    private final Path dossier;

    public ImageService(@Value("${frigo.images.dossier:./images-aliments}") String dossier)
            throws IOException {
        this.dossier = Path.of(dossier).toAbsolutePath().normalize();
        Files.createDirectories(this.dossier);
        LOG.info("Images des aliments ajoutées depuis l'application : {}", this.dossier);
    }

    public Path getDossier() {
        return dossier;
    }

    // Vérifie l'image, l'enregistre sous un nom unique et retourne ce nom
    public String enregistrer(String nomAliment, MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_VIDE", "Aucune image reçue");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            // 413 : contenu trop volumineux
            throw new ApiException(HttpStatus.valueOf(413), "IMAGE_TROP_GROSSE",
                    "L'image est trop volumineuse (5 Mo au maximum)");
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_ILLISIBLE",
                    "L'image n'a pas pu être lue");
        }
        String extension = detecterExtension(contenu);
        if (extension == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "IMAGE_INVALIDE",
                    "Formats acceptés : PNG, JPEG ou WebP");
        }
        String nom = slug(nomAliment) + "-" + System.currentTimeMillis() + "." + extension;
        try {
            Files.write(dossier.resolve(nom), contenu);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_NON_ENREGISTREE",
                    "L'image n'a pas pu être enregistrée");
        }
        return nom;
    }

    // Supprime une image ajoutée depuis l'application. Les images fournies avec le serveur
    // ne se trouvent pas dans ce dossier : elles ne sont jamais touchées.
    public void supprimer(String nom) {
        if (nom == null || !NOM_CREE.matcher(nom).matches()) {
            return;
        }
        Path cible = dossier.resolve(nom).normalize();
        if (!cible.startsWith(dossier)) {
            return;
        }
        try {
            Files.deleteIfExists(cible);
        } catch (IOException e) {
            LOG.warn("Ancienne image non supprimée : {}", nom);
        }
    }

    // Le format se reconnaît aux premiers octets du fichier, pas au nom ni au type annoncé
    private String detecterExtension(byte[] b) {
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    // « Thon frais » donne « thon-frais »
    private String slug(String nom) {
        String base = TexteTicket.normaliser(nom).replace(' ', '-');
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        base = base.replaceAll("^-+|-+$", "");
        return base.isEmpty() ? "aliment" : base;
    }
}