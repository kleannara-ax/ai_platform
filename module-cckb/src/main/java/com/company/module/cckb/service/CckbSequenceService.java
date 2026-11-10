package com.company.module.cckb.service;

import com.company.module.cckb.repository.InspectionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 검사ID(INSPECTION_ID) 채번 서비스.
 * <p>원본 V4 HTML 의 {@code generateSeqNo()} 를 DB 카운트 기반으로 포팅.
 * localStorage 버전은 메모리 카운터 맵을 썼지만, DB 버전은 동시성을 고려해
 * "오늘 날짜 prefix 로 시작하는 기존 레코드 수 + 1" 을 조회해 채번한다.
 * 형식: YYMMDD + 4자리 순번 (예: 2611060001).
 *
 * <p>※ 동시 다발 요청 시 경합 가능성이 있으나, 현재 검사소 특성상(1회 1검사 순차 진행)
 * 실무 위험은 낮다. 추후 동시성 이슈가 발견되면 DB 시퀀스 테이블 + 비관적 락으로 전환한다.
 */
@Service
@RequiredArgsConstructor
public class CckbSequenceService {

    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyMMdd", Locale.KOREA);

    private final InspectionRecordRepository inspectionRecordRepository;

    @Transactional
    public synchronized String generateInspectionId() {
        String datePrefix = LocalDate.now().format(DATE_PREFIX);
        long countToday = inspectionRecordRepository.countByDatePrefix(datePrefix);
        long next = countToday + 1;
        String seq = String.format("%04d", next);
        String candidate = datePrefix + seq;

        // 혹시 중복(이미 존재)되면 순번을 계속 증가시켜 회피한다.
        while (inspectionRecordRepository.findByInspectionIdAndDeletedYn(candidate, "N").isPresent()
                || inspectionRecordRepository.findByInspectionIdAndDeletedYn(candidate, "Y").isPresent()) {
            next++;
            candidate = datePrefix + String.format("%04d", next);
        }
        return candidate;
    }
}
