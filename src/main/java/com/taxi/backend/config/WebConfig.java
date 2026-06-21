package com.taxi.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * /uploads/** URL orqali yuklangan fayllarni serve qilish.
 * Masalan: /uploads/drivers/15/driver_face_xxx.jpg
 *
 * XAVFSIZLIK: Fayllar UUID bilan nomlanadi (predictable emas).
 * Cache-Control header orqali brauzer keshi boshqariladi.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCachePeriod(3600); // 1 soat brauzer keshi
    }
}
