package com.company.module.steamenergy.legacy;

import jakarta.annotation.PostConstruct;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.util.IOUtils;
import org.springframework.context.annotation.Configuration;

/**
 * Apache POI 전역 한도 설정 (레거시 스팀 대시보드 이관).
 *
 * <p>원본 SteamApplication 의 static 블록을 플랫폼 환경으로 이관.
 * <p>원본 에너지 회계비용 엑셀은 시트 18+ / drawings·styles 포함으로 1000 엔트리 초과 가능하여
 * zip-bomb 방어 한도를 넉넉히 상향한다.
 */
@Configuration
public class SteamPoiConfig {

    @PostConstruct
    public void configurePoiLimits() {
        ZipSecureFile.setMaxFileCount(100_000);
        ZipSecureFile.setMaxEntrySize(1_024L * 1024 * 1024); // 1 GiB
        ZipSecureFile.setMaxTextSize(1_024L * 1024 * 1024);  // 1 GiB
        ZipSecureFile.setMinInflateRatio(0.001);             // 압축률 매우 낮은 파일도 허용
        IOUtils.setByteArrayMaxOverride(512 * 1024 * 1024);  // 512 MiB
    }
}
