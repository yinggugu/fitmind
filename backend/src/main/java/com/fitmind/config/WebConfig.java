package com.fitmind.config;
import org.springframework.context.annotation.Configuration; import org.springframework.web.servlet.config.annotation.*;
@Configuration public class WebConfig implements WebMvcConfigurer { @Override public void addCorsMappings(CorsRegistry r){r.addMapping("/api/**").allowedOrigins("http://127.0.0.1:5299","http://localhost:5299").allowedMethods("GET","POST","PUT","DELETE","OPTIONS").allowedHeaders("*");} }
