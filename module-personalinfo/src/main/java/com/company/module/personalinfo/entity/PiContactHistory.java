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
 * 위수탁 계약 담당자 변경 이력 (위탁사 = 당사 담당부서·담당자·이메일).
 *
 * <p>계약 등록 시 1건, 이후 위탁사 담당자 항목이 바뀔 때마다 변경 후 값과 그 시점 계약기간을 스냅샷으로 1건 남긴다.
 * 수탁사 담당자·연락처 변경은 이력을 남기지 않는다.
 * 변경된 항목 코드는 {@code CHANGED_FIELDS}(쉼표 구분)에 기록한다.
 * 이력은 수정하지 않는다(추가만). 변경자 이름은 업무 모듈이 core_user 를 조회하지 않도록
 * 로그인 사용자 표시명을 함께 저장한다.
 */
@Entity
@Table(name = "pi_contact_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PiContactHistory extends PiBaseEntity {

    /** 담당자 항목 코드 (CHANGED_FIELDS 에 저장) */
    public static final String F_DEPT = "DEPT_NAME";
    public static final String F_MANAGER = "MANAGER_NAME";
    public static final String F_MANAGER_EMAIL = "MANAGER_EMAIL";
    /** 이력 대상 항목 (위탁사 담당자) */
    public static final java.util.List<String> TRACKED_FIELDS = java.util.List.of(F_DEPT, F_MANAGER, F_MANAGER_EMAIL);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HISTORY_ID")
    private Long historyId;

    @Column(name = "CONTRACT_ID", nullable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "CHANGE_TYPE", nullable = false, length = 20)
    private ContactChangeType changeType;

    @Column(name = "VENDOR_NAME", length = 200)
    private String vendorName;

    @Column(name = "SERVICE_NAME", length = 200)
    private String serviceName;

    @Column(name = "DEPT_NAME", length = 100)
    private String deptName;

    @Column(name = "MANAGER_NAME", length = 100)
    private String managerName;

    @Column(name = "MANAGER_EMAIL", length = 200)
    private String managerEmail;

    @Column(name = "CONTRACT_START_DATE")
    private java.time.LocalDate contractStartDate;

    @Column(name = "CONTRACT_END_DATE")
    private java.time.LocalDate contractEndDate;

    @Column(name = "CONTRACT_PERIOD_NOTE", length = 500)
    private String contractPeriodNote;

    @Column(name = "CHANGED_FIELDS", length = 300)
    private String changedFields;

    @Column(name = "CHANGED_BY_NAME", length = 100)
    private String changedByName;

    @Builder
    private PiContactHistory(Long contractId, ContactChangeType changeType, String vendorName, String serviceName,
                             String deptName, String managerName, String managerEmail,
                             java.time.LocalDate contractStartDate, java.time.LocalDate contractEndDate,
                             String contractPeriodNote, String changedFields, String changedByName, Long createdBy) {
        this.contractId = contractId;
        this.changeType = changeType == null ? ContactChangeType.UPDATE : changeType;
        this.vendorName = vendorName;
        this.serviceName = serviceName;
        this.deptName = deptName;
        this.managerName = managerName;
        this.managerEmail = managerEmail;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.contractPeriodNote = contractPeriodNote;
        this.changedFields = changedFields;
        this.changedByName = changedByName;
        markCreatedBy(createdBy);
    }

    /** 계약의 현재 담당자 값으로 스냅샷 생성 */
    public static PiContactHistory snapshot(PiContract c, ContactChangeType type, String changedFields,
                                            String changedByName, Long userId) {
        return PiContactHistory.builder()
                .contractId(c.getContractId())
                .changeType(type)
                .vendorName(c.getVendorName())
                .serviceName(c.getServiceName())
                .deptName(c.getDeptName())
                .managerName(c.getManagerName())
                .managerEmail(c.getManagerEmail())
                .contractStartDate(c.getContractStartDate())
                .contractEndDate(c.getContractEndDate())
                .contractPeriodNote(c.getContractPeriodNote())
                .changedFields(changedFields)
                .changedByName(changedByName)
                .createdBy(userId)
                .build();
    }
}
