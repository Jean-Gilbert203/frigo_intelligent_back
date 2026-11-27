package mg.bios.frigo.config;

import mg.bios.frigo.service.ImageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ImagesConfig implements WebMvcConfigurer {

    private final ImageService imageService;

    public ImagesConfig(ImageService imageService) {
        this.imageService = imageService;
    }

    // /images/aliments/... : d'abord le dossier des images ajoutées, puis celles fournies avec le serveur
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/aliments/**")
                .addResourceLocations(
                        imageService.getDossier().toUri().toString(),
                        "classpath:/static/images/aliments/");
    }
}