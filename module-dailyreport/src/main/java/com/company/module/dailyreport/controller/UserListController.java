package com.company.module.dailyreport.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.dailyreport.dto.UserSimpleResponse;
import com.company.module.dailyreport.service.UserListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사용자 목록 조회 REST Controller
 * - '세부공장일보 컬럼관리' 페이지의 사용자 검색 드롭다운용
 * - Architecture Rule #4: core_user를 EntityManager native query로 조회
 *
 * 엔드포인트:
 *   GET /dailyreport-api/users → 활성 사용자 전체 목록
 */
@RestController
@RequestMapping("/dailyreport-api/users")
@RequiredArgsConstructor
public class UserListController {

    private final UserListService userListService;

    /**
     * 활성 사용자 전체 목록 조회 (컬럼관리 페이지 드롭다운용)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserSimpleResponse>>> getUsers() {
        return ResponseEntity.ok(
                ApiResponse.success(userListService.getActiveUsers()));
    }
}
