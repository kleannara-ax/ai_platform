package com.company.module.epr.dto;

/** 엑셀 한 행의 확인 필요 사항 (행 번호는 엑셀 화면 기준 1부터) */
public record UploadIssue(int rowNumber, String productCode, String message) {
}
