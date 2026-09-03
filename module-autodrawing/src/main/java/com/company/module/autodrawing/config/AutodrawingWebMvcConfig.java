package com.company.module.autodrawing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * module-autodrawing 전용 WebMvcConfigurer.
 * 자동도면 관련 정적 리소스(HTML, JS, CSS)에 대한 캐시 제어를 담당.
 */
@Configuration
public class AutodrawingWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 자동도면 모듈 관련 HTML/JS/CSS 캐시 방지
        registry.addResourceHandler(
                        "/autodrawing/**")
                .addResourceLocations("classpath:/static/autodrawing/")
                .setCacheControl(CacheControl.noStore().mustRevalidate());
    }
}
