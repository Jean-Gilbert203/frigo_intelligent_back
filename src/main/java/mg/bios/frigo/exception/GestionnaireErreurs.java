package mg.bios.frigo.exception;

import java.util.Map;
import mg.bios.frigo.dto.ErreurDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GestionnaireErreurs {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErreurDto> surApiException(ApiException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(new ErreurDto(e.getCode(), e.getMessage(), e.getDetails()));
    }

    // Corps JSON absent ou mal formé
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurDto> surCorpsIllisible(HttpMessageNotReadableException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErreurDto("CORPS_INVALIDE", "Corps de la requête absent ou invalide", Map.of()));
    }

    // Fichier plus gros que la limite de spring.servlet.multipart.max-file-size
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErreurDto> surFichierTropGros(MaxUploadSizeExceededException e) {
        return ResponseEntity
                .status(HttpStatus.valueOf(413))
                .body(new ErreurDto("IMAGE_TROP_GROSSE",
                        "L'image est trop volumineuse (5 Mo au maximum)", Map.of()));
    }
}