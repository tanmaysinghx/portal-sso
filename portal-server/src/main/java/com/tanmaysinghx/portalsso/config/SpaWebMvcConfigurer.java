package com.tanmaysinghx.portalsso.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves static assets from classpath:/static/ and routes client-side deep links (e.g. /users,
 * /clients/new, /dashboard) to index.html without interfering with backend endpoints (/api/**,
 * /oauth2/**, /login, /logout, /userinfo, /.well-known/**, /actuator/**).
 */
@Configuration
public class SpaWebMvcConfigurer implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }

                        // Exclude backend endpoints from SPA fallback
                        if (resourcePath.startsWith("api")
                                || resourcePath.startsWith("oauth2")
                                || resourcePath.startsWith(".well-known")
                                || resourcePath.equals("userinfo")
                                || resourcePath.equals("login")
                                || resourcePath.equals("logout")
                                || resourcePath.startsWith("actuator")) {
                            return null;
                        }

                        Resource index = location.createRelative("index.html");
                        if (index.exists() && index.isReadable()) {
                            return index;
                        }
                        return null;
                    }
                });
    }
}
