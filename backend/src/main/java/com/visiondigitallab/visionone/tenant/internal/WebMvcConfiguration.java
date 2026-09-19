package com.visiondigitallab.visionone.tenant.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final OrganizationContextInterceptor organizationContextInterceptor;

    public WebMvcConfiguration(OrganizationContextInterceptor organizationContextInterceptor) {
        this.organizationContextInterceptor = organizationContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(organizationContextInterceptor).addPathPatterns("/api/v1/orgs/**");
    }
}
