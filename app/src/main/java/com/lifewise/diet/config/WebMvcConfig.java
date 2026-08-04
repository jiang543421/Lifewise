package com.lifewise.diet.config;

import com.lifewise.diet.web.CurrentUserArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册 {@link CurrentUserArgumentResolver}。 */
@Configuration("dietWebMvcConfig")
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver resolver;

    public WebMvcConfig(CurrentUserArgumentResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
    }
}