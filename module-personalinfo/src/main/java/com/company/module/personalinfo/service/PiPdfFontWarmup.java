package com.company.module.personalinfo.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * PDFBox 글꼴 캐시 미리 만들기.
 * <p>PDFBox 는 글꼴이 포함되지 않은 PDF 를 처음 읽을 때 서버의 시스템 글꼴을 모두 훑어 캐시를 만든다(수 초~수십 초).
 * 계약서 분석을 처음 쓰는 사용자가 기다리지 않도록, 서버 기동이 끝난 뒤 낮은 우선순위 백그라운드 스레드에서 한 번 만들어 둔다.
 * 실패해도 기능에는 영향이 없다(첫 분석 때 다시 만들어짐).</p>
 */
@Slf4j
@Component
public class PiPdfFontWarmup {

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread t = new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                FontMappers.instance().getFontBoxFont("Helvetica", null);
                log.info("[personalinfo] PDF 글꼴 캐시 준비 완료 ({}ms)", System.currentTimeMillis() - start);
            } catch (Throwable e) {
                log.info("[personalinfo] PDF 글꼴 캐시 미리 만들기 건너뜀: {}", e.toString());
            }
        }, "pi-pdf-font-warmup");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
