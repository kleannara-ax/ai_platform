package com.company.module.epr.dto;

import java.util.List;

/** 엑셀 시트 해석 결과 — 읽은 행과 읽지 못한 행 */
public record ParsedSheet<T>(List<T> rows, List<UploadIssue> issues) {
}
