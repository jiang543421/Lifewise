package com.lifewise.expense.config;

import com.lifewise.expense.web.CurrentUserArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册 {@link CurrentUserArgumentResolver} 供 expense 4 个 controller 使用。 */
@Configuration
public class ExpenseWebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver expenseCurrentUserArgumentResolver;

    public ExpenseWebMvcConfig(
            @org.springframework.beans.factory.annotation.Qualifier("expenseCurrentUserArgumentResolver")
            CurrentUserArgumentResolver expenseCurrentUserArgumentResolver) {
        this.expenseCurrentUserArgumentResolver = expenseCurrentUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(expenseCurrentUserArgumentResolver);
    }
}