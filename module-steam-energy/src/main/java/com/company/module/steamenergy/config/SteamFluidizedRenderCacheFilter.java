package com.company.module.steamenergy.config;

import com.company.module.steamenergy.legacy.service.FluidizedDetailWorkbookService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 스팀에너지 API 로 무언가 저장되면 세부 운영내역 계산 캐시(30초)를 비운다.
 *
 * <p>계산된 세부 운영내역은 여러 표(일별 입력 · 월별 단가 · 추가 행 · 스팀 송기량 · SRF 입고)를 읽는다.
 * 표마다 저장 뒤에 캐시를 비우는 코드를 넣는 대신, 읽기가 아닌 요청이 끝나면 한 번에 비운다.
 */
@Component
@RequiredArgsConstructor
public class SteamFluidizedRenderCacheFilter extends OncePerRequestFilter {

    private final FluidizedDetailWorkbookService detailWorkbookService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean steam = uri != null && uri.contains("/steam-energy-api/");
        return !steam || "GET".equalsIgnoreCase(request.getMethod()) || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            detailWorkbookService.invalidateRenderCache(null);
        }
    }
}
