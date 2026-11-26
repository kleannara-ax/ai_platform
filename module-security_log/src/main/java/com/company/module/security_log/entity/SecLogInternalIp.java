package com.company.module.security_log.entity;

import com.company.module.security_log.support.SecLogTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 내부 IP 대역 (관리 > 내부 IP 설정)
 * - 접속 행위 분석에서 사용 중인 대역에 속하지 않는 IP 를 '외부 IP' 로 표시한다.
 */
@Entity
@Table(name = "sec_log_internal_ip")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogInternalIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INTERNAL_IP_ID")
    private Long internalIpId;

    @Column(name = "IP_RANGE", nullable = false, length = 50)
    private String ipRange;

    @Column(name = "START_IP_NUM", nullable = false)
    private Long startIpNum;

    @Column(name = "END_IP_NUM", nullable = false)
    private Long endIpNum;

    @Column(name = "IP_NAME", nullable = false, length = 100)
    private String ipName;

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
    private SecLogInternalIp(String ipRange, Long startIpNum, Long endIpNum, String ipName, String description,
                             String useYn, Integer sortOrder, Long createdBy) {
        this.ipRange = ipRange;
        this.startIpNum = startIpNum;
        this.endIpNum = endIpNum;
        this.ipName = ipName;
        this.description = description;
        this.useYn = "N".equals(useYn) ? "N" : "Y";
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.createdBy = createdBy;
        this.deletedYn = "N";
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

    public void update(String ipRange, Long startIpNum, Long endIpNum, String ipName, String description,
                       String useYn, Integer sortOrder, Long updatedBy) {
        this.ipRange = ipRange;
        this.startIpNum = startIpNum;
        this.endIpNum = endIpNum;
        this.ipName = ipName;
        this.description = description;
        this.useYn = "N".equals(useYn) ? "N" : "Y";
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.updatedBy = updatedBy;
    }

    public void changeUseYn(String useYn, Long updatedBy) {
        this.useYn = "N".equals(useYn) ? "N" : "Y";
        this.updatedBy = updatedBy;
    }

    /** 삭제했던 같은 대역을 다시 등록할 때 */
    public void restore(Long updatedBy) {
        this.deletedYn = "N";
        this.deletedAt = null;
        this.deletedBy = null;
        this.updatedBy = updatedBy;
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.useYn = "N";
        this.deletedBy = deletedBy;
        this.deletedAt = SecLogTime.now();
    }

    public boolean contains(long ip) {
        return ip >= startIpNum && ip <= endIpNum;
    }
}
