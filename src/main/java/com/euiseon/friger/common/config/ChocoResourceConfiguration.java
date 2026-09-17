package com.euiseon.friger.common.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Only versioned public image derivatives receive a long-lived cache policy. */
@Configuration(proxyBeanMethods = false)
@Profile("!ui-dev")
public class ChocoResourceConfiguration implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/choco/web/**")
                .addResourceLocations("classpath:/static/assets/choco/web/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        // Unversioned tab icons stay replaceable, so they only get a short public cache.
        registry.addResourceHandler("/favicon.ico", "/apple-touch-icon.png")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
    }
}
