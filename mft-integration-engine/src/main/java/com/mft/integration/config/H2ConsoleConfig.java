package com.mft.integration.config;

import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Custom H2 Web Console registration for Spring Boot 4+.
 * Enables browser-based SQL inspection at http://localhost:8080/h2-console.
 */
@Configuration
@ConditionalOnClass(JakartaWebServlet.class)
@ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true", matchIfMissing = true)
public class H2ConsoleConfig {

    @Bean
    public ServletRegistrationBean<JakartaWebServlet> h2ConsoleServletRegistration() {
        ServletRegistrationBean<JakartaWebServlet> registration =
                new ServletRegistrationBean<>(new JakartaWebServlet(), "/h2-console/*");
        registration.setName("H2Console");
        registration.addInitParameter("webAllowOthers", "true");
        return registration;
    }

    @Controller
    static class H2ConsoleRedirectController {
        @GetMapping("/h2-console")
        public String redirect() {
            return "redirect:/h2-console/";
        }
    }
}
