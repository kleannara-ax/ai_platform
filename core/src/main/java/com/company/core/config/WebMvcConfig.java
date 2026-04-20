package com.company.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 공통 설정
 * 내부망 환경이므로 CORS는 제한적으로 설정
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${ps-insp.upload.dir:/data/upload/ps_cov_ins}")
    private String psInspUploadDir;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")         // 내부망 환경 — 필요 시 특정 origin으로 제한
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 정적 리소스 캐시 비활성화 — 항상 최신 버전 제공
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache());

        // PS-INSP 업로드 이미지 서빙: /uploads/** → 파일시스템 업로드 디렉토리
        String uploadLocation = psInspUploadDir.endsWith("/") ? psInspUploadDir : psInspUploadDir + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadLocation)
                .setCacheControl(CacheControl.noCache());
    }
}
