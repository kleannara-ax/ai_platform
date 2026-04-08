package com.company.module.fire.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * module-fire 전용 WebMvcConfigurer.
 * 소방 관련 정적 리소스(HTML, JS, 이미지)에 대한 캐시 제어를 담당.
 */
@Configuration
public class FireWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 소방 모듈 이미지/JS 리소스 명시 매핑 (nested JAR에서 확실히 서빙)
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/")
                .setCacheControl(CacheControl.maxAge(7, java.util.concurrent.TimeUnit.DAYS));

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(CacheControl.noStore().mustRevalidate());

        // 소방 모듈 관련 HTML 페이지 캐시 방지
        registry.addResourceHandler(
                        "/index.html",
                        "/extinguishers.html",
                        "/hydrants.html",
                        "/receivers.html",
                        "/pumps.html",
                        "/maps/**",
                        "/qr/**",
                        "/minspection/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore().mustRevalidate());
    }
}
