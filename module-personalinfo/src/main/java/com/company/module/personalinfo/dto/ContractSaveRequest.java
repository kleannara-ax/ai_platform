package com.company.module.personalinfo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 위수탁 계약 등록/수정 요청.
 * 항목은 "외부위탁 현황 리스트" 엑셀 컬럼을 기준으로 한다.
 */
@Getter
@Setter
public class ContractSaveRequest {

    private static final String YN = "^[YN]$";
    private static final String EMAILS = "^$|^[^@\\s,;]+@[^@\\s,;]+(\\s*[,;]\\s*[^@\\s,;]+@[^@\\s,;]+)*$";

    /** ISMS 범위 여부 (Y/N) */
    @Pattern(regexp = YN, message = "ISMS 범위 여부는 Y 또는 N 입니다.")
    private String ismsScopeYn = "Y";

    /** 수탁업체명 */
    @NotBlank(message = "수탁업체명은 필수입니다.")
    @Size(max = 200)
    private String vendorName;

    /** 관련 서비스 (예: 자사몰, 임직원몰, OMS) */
    @Size(max = 200)
    private String serviceName;

    /** 위탁업무 내용 */
    @NotBlank(message = "위탁업무 내용은 필수입니다.")
    @Size(max = 1000)
    private String entrustedTask;

    /** 당사 담당부서 */
    @Size(max = 100)
    private String deptName;

    /** 당사 계약 담당자 */
    @Size(max = 100)
    private String managerName;

    /** 당사 계약 담당자 이메일 (만료 알림 수신) */
    @Size(max = 200)
    @Pattern(regexp = EMAILS, message = "담당자 이메일 형식이 올바르지 않습니다.")
    private String managerEmail;

    /** 수탁사 담당자 */
    @Size(max = 100)
    private String vendorContactName;

    @Size(max = 200)
    @Pattern(regexp = EMAILS, message = "수탁사 담당자 이메일 형식이 올바르지 않습니다.")
    private String vendorContactEmail;

    @Size(max = 50)
    private String vendorContactPhone;

    /** 원계약 시작일 */
    private LocalDate contractStartDate;

    /** 원계약 종료일 (만료 알림 기준) */
    private LocalDate contractEndDate;

    /** 계약기간 비고 (예: 체결일로부터 1년, 이용약관) */
    @Size(max = 500)
    private String contractPeriodNote;

    /** 자동연장 여부 */
    @Pattern(regexp = YN)
    private String autoRenewYn = "N";

    /** 자동연장 비고 (예: 1개월 단위) */
    @Size(max = 200)
    private String autoRenewNote;

    /** 개인정보처리 위탁계약서 작성 여부 */
    @Pattern(regexp = YN)
    private String contractDocYn = "N";

    /** 개인정보처리 위탁계약 체결일 */
    private LocalDate contractSignedDate;

    /** 재위탁 여부 */
    @Pattern(regexp = YN)
    private String subcontractYn = "N";

    /** 재위탁 업체명 */
    @Size(max = 500)
    private String subcontractorName;

    /** 재위탁 업무 */
    @Size(max = 1000)
    private String subcontractTask;

    /** 재위탁 동의서 작성 여부 */
    @Pattern(regexp = "^$|^[YN]$")
    private String subcontractConsentYn;

    /** 만료 알림 사용 여부 */
    @Pattern(regexp = YN)
    private String alertYn = "Y";

    /** 만료 N일 전부터 알림 */
    @Min(value = 1, message = "알림 기준일은 1일 이상입니다.")
    @Max(value = 365, message = "알림 기준일은 365일 이하입니다.")
    private Integer alertDaysBefore = 30;

    /** 추가 알림 수신자 (쉼표/세미콜론 구분) */
    @Size(max = 1000)
    @Pattern(regexp = EMAILS, message = "추가 수신자 이메일 형식이 올바르지 않습니다.")
    private String alertExtraEmails;

    @Size(max = 2000)
    private String remark;
}
