package com.lifewise.plan.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册 plan 模块的 {@link CurrentUserArgumentResolver}。 */
@Configuration
public class PlanWebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver planCurrentUserArgumentResolver;

    public PlanWebMvcConfig(
            @Qualifier("planCurrentUserArgumentResolver")
            CurrentUserArgumentResolver planCurrentUserArgumentResolver) {
        this.planCurrentUserArgumentResolver = planCurrentUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(planCurrentUserArgumentResolver);
    }
}