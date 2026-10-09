package com.Reservation.Hotel.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Makes files saved under ./uploads/ (hotel & room photos, payment receipts)
 * reachable in the browser at /uploads/&lt;folder&gt;/&lt;filename&gt;.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploads = Paths.get("uploads").toAbsolutePath();
        try {
            // Create it up front: Path.toUri() only ends with "/" for an existing directory, and without
            // the trailing slash Spring would resolve /uploads/hotels/x.jpg against the wrong folder.
            Files.createDirectories(uploads);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the uploads folder " + uploads, e);
        }
        String location = uploads.toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
