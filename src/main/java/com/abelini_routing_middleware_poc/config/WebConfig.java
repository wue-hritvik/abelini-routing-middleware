package com.abelini_routing_middleware_poc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Exclude /internal/ paths from Spring Boot, so Nginx handles them
        registry
                .addResourceHandler("/internal/**")
                .addResourceLocations("none:");
    }
}
