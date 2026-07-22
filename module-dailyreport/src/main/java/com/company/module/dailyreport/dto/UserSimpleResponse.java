package com.company.module.dailyreport.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 사용자 간이 정보 응답 DTO
 * - 컬럼관리 페이지의 사용자 검색 드롭다운용
 * - core_user 테이블에서 EntityManager native query로 조회
 */
@Getter
@Builder
public class UserSimpleResponse {

    private Long userId;
    private String loginId;
    private String userName;
    private String department;
    private String position;
}
