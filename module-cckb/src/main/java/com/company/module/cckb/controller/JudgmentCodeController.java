package com.company.module.cckb.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.cckb.dto.response.JudgmentCodeResponse;
import com.company.module.cckb.service.JudgmentCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 판정 코드(정상/경계/불량) 조회 API — 화면 뱃지 색상/라벨 렌더링용 */
@RestController
@RequestMapping("/cckb-api/judgment-codes")
@RequiredArgsConstructor
public class JudgmentCodeController {

    private final JudgmentCodeService judgmentCodeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<JudgmentCodeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(judgmentCodeService.getAll()));
    }
}
