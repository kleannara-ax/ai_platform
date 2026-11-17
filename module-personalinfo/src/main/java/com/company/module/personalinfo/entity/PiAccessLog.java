package com.company.module.personalinfo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 접근 기록 (계약·점검 상세 조회, 계약서·점검 결과·엑셀 다운로드).
 * 추가만 하고 수정·삭제하지 않는다(변경 메서드 없음).
 */
@Entity
@Table(name = "pi_access_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PiAccessLog extends PiBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ACCESS_ID")
    private Long accessId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ACTION_TYPE", nullable = false, length = 30)
    private AccessActionType actionType;

    @Column(name = "CONTRACT_ID")
    private Long contractId;

    @Column(name = "VENDOR_NAME", length = 200)
    private String vendorName;

    @Column(name = "CONTRACT_NAME", length = 200)
    private String contractName;

    @Column(name = "TARGET_ID")
    private Long targetId;

    @Column(name = "TARGET_NAME", length = 500)
    private String targetName;

    @Column(name = "RECORD_COUNT")
    private Integer recordCount;

    @Column(name = "REASON", length = 500)
    private String reason;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "USER_LOGIN_ID", length = 100)
    private String userLoginId;

    @Column(name = "USER_NAME", length = 100)
    private String userName;

    @Column(name = "CLIENT_IP", length = 64)
    private String clientIp;

    @Column(name = "USER_AGENT", length = 300)
    private String userAgent;

    @Builder
    private PiAccessLog(AccessActionType actionType, Long contractId, String vendorName, String contractName,
                        Long targetId, String targetName, Integer recordCount, String reason,
                        Long userId, String userLoginId, String userName, String clientIp, String userAgent) {
        this.actionType = actionType;
        this.contractId = contractId;
        this.vendorName = cut(vendorName, 200);
        this.contractName = cut(contractName, 200);
        this.targetId = targetId;
        this.targetName = cut(targetName, 500);
        this.recordCount = recordCount;
        this.reason = cut(reason, 500);
        this.userId = userId;
        this.userLoginId = cut(userLoginId, 100);
        this.userName = cut(userName, 100);
        this.clientIp = cut(clientIp, 64);
        this.userAgent = cut(userAgent, 300);
        markCreatedBy(userId);
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
