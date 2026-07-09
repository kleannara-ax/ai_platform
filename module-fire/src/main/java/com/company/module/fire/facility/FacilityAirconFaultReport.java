package com.company.module.fire.facility;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "facility_aircon_fault_report")
public class FacilityAirconFaultReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REPORT_ID")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EQUIPMENT_ID", nullable = false)
    private FacilityEquipment equipment;

    @Column(name = "REPORTER_NAME", length = 100)
    private String reporterName;

    @Column(name = "REPORTER_DEPARTMENT", length = 100)
    private String reporterDepartment;

    @Column(name = "FAULT_DESCRIPTION", nullable = false, length = 1000)
    private String faultDescription;

    @Column(name = "STATUS", nullable = false, length = 30)
    private String status;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (status == null || status.isBlank()) {
            status = "RECEIVED";
        }
        createdAt = LocalDateTime.now();
    }

    @Builder
    public FacilityAirconFaultReport(FacilityEquipment equipment, String reporterName, String reporterDepartment,
                                     String faultDescription, String status) {
        this.equipment = equipment;
        this.reporterName = reporterName;
        this.reporterDepartment = reporterDepartment;
        this.faultDescription = faultDescription;
        this.status = (status == null || status.isBlank()) ? "RECEIVED" : status;
    }
}
