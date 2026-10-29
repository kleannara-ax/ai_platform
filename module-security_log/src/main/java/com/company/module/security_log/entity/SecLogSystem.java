package com.company.module.security_log.entity;

import com.company.module.security_log.support.SecLogTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 점검 대상 시스템 마스터 (홈페이지, 자사몰, UTM, SSL VPN, DBMS 등)
 * - 월별 점검 현황(접속 행위로그 / 장기 미접속 계정)의 기준 목록
 */
@Entity
@Table(name = "sec_log_system")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SYSTEM_ID")
    private Long systemId;

    @Column(name = "SYSTEM_CODE", nullable = false, length = 50, updatable = false)
    private String systemCode;

    @Column(name = "SYSTEM_NAME", nullable = false, length = 100)
    private String systemName;

    /** 시스템 구분: 서비스 / 정보보호시스템 / 네트워크 / DBMS */
    @Column(name = "CATEGORY", nullable = false, length = 50)
    private String category;

    /** 접속 행위로그 기본 로그 유형 (업로드 시 기본 선택값) */
    @Enumerated(EnumType.STRING)
    @Column(name = "DEFAULT_LOG_TYPE", nullable = false, length = 20)
    private LogType defaultLogType;

    @Column(name = "MANAGER_NAME", length = 100)
    private String managerName;

    /** 공통 룰셋 적용 여부 */
    @Column(name = "COMMON_RULE_YN", nullable = false, length = 1)
    private String commonRuleYn;

    /** 시스템 전용 룰셋 적용 여부 */
    @Column(name = "SYSTEM_RULE_YN", nullable = false, length = 1)
    private String systemRuleYn;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "USE_YN", nullable = false, length = 1)
    private String useYn;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", updatable = false)
    private Long createdBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "DELETED_YN", nullable = false, length = 1)
    private String deletedYn;

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    @Column(name = "DELETED_BY")
    private Long deletedBy;

    @Builder
    private SecLogSystem(String systemCode, String systemName, String category, LogType defaultLogType,
                         String managerName, String description, String useYn,
                         Integer sortOrder, Long createdBy) {
        this.systemCode = systemCode;
        this.systemName = systemName;
        this.category = category;
        this.defaultLogType = defaultLogType == null ? LogType.ETC : defaultLogType;
        this.managerName = managerName;
        this.description = description;
        this.useYn = useYn == null ? "Y" : useYn;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.createdBy = createdBy;
        this.deletedYn = "N";
        this.commonRuleYn = "Y";
        this.systemRuleYn = "Y";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = SecLogTime.now();
        if (this.deletedYn == null) {
            this.deletedYn = "N";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = SecLogTime.now();
    }

    public void update(String systemName, String category, LogType defaultLogType, String managerName,
                       String description, String useYn, Integer sortOrder, Long updatedBy) {
        this.systemName = systemName;
        this.category = category;
        this.defaultLogType = defaultLogType == null ? LogType.ETC : defaultLogType;
        this.managerName = managerName;
        this.description = description;
        this.useYn = useYn == null ? "Y" : useYn;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.updatedBy = updatedBy;
    }

    /** 사용여부 토글 (월별 점검 현황·대시보드 대상 포함 여부) */
    public void changeUseYn(String useYn, Long updatedBy) {
        this.useYn = "N".equals(useYn) ? "N" : "Y";
        this.updatedBy = updatedBy;
    }

    /** 룰셋 사용/미사용 (공통 룰셋, 시스템 전용 룰셋) */
    public void changeRuleset(String commonRuleYn, String systemRuleYn, Long updatedBy) {
        if (commonRuleYn != null) {
            this.commonRuleYn = "N".equals(commonRuleYn) ? "N" : "Y";
        }
        if (systemRuleYn != null) {
            this.systemRuleYn = "N".equals(systemRuleYn) ? "N" : "Y";
        }
        this.updatedBy = updatedBy;
    }

    public boolean useCommonRules() {
        return !"N".equals(this.commonRuleYn);
    }

    public boolean useSystemRules() {
        return !"N".equals(this.systemRuleYn);
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.useYn = "N";
        this.deletedBy = deletedBy;
        this.deletedAt = SecLogTime.now();
    }

    public void restore(Long updatedBy) {
        this.deletedYn = "N";
        this.deletedBy = null;
        this.deletedAt = null;
        this.updatedBy = updatedBy;
    }
}
