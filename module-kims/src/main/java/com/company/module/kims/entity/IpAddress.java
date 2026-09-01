package com.company.module.kims.entity;

import com.company.module.kims.entity.enums.IpStatus;
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

import java.time.LocalDate;

/**
 * IP 주소 (마스터).
 * <p>신규 생성 / 정보·사용자 변경 / 회수 상태를 관리하며,
 * 변경 이력은 {@link IpHistory} 에 별도로 기록한다.
 */
@Entity
@Table(name = "ip_address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IpAddress extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IP_ID")
    private Long ipId;

    /** IP 주소 (별도관리 장비는 null) */
    @Column(name = "IP_ADDRESS", length = 45, unique = true)
    private String ipAddress;

    /** IP 그룹/대역 (예: 192.1.0, 또는 '별도관리') */
    @Column(name = "IP_GROUP", length = 30)
    private String ipGroup;

    /** 사업장 구분 (청주/서울) — PC 관리 분리용. 기본 '청주' */
    @Column(name = "SITE", nullable = false, length = 20)
    private String site;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private IpStatus status;

    /** 상주/임시/반납/예비 (관리대장 원본 상태) */
    @Column(name = "USAGE_TYPE", length = 20)
    private String usageType;

    /** 사용자명/장치명 */
    @Column(name = "USER_NAME", length = 100)
    private String userName;

    /** 사용자ID */
    @Column(name = "USER_ID", length = 50)
    private String userId;

    @Column(name = "DEPARTMENT", length = 50)
    private String department;

    @Column(name = "LOCATION", length = 100)
    private String location;

    /** 장치구분 (노트북/서버/데스크탑/프린터/키오스크 등) */
    @Column(name = "DEVICE", length = 100)
    private String device;

    /** 품의 여부 (현재 상태 기준) */
    @Column(name = "APPROVED", nullable = false)
    private boolean approved;

    /** 품의번호 */
    @Column(name = "APPROVAL_NO", length = 50)
    private String approvalNo;

    @Column(name = "REMARK", length = 500)
    private String remark;

    /** 비고 작성일 (관리대장의 비고작성일) */
    @Column(name = "NOTE_DATE")
    private LocalDate noteDate;

    /** 회수일 (회수 처리 시 입력) */
    @Column(name = "RECLAIMED_AT")
    private LocalDate reclaimedAt;

    // ----- PC/장비 상세 스펙 -----
    /** PC 모델명 */
    @Column(name = "MODEL", length = 100)
    private String model;
    /** 제조번호(S/N) */
    @Column(name = "SERIAL_NO", length = 100)
    private String serialNo;
    /** 구입업체 */
    @Column(name = "VENDOR", length = 100)
    private String vendor;
    /** 구입일 (관리대장 원본 문자열) */
    @Column(name = "PURCHASE_DATE", length = 30)
    private String purchaseDate;
    /** OS 버전 */
    @Column(name = "OS_VERSION", length = 60)
    private String osVersion;
    /** OS 시리얼 넘버 */
    @Column(name = "OS_SERIAL", length = 100)
    private String osSerial;
    /** OFFICE 버전 */
    @Column(name = "OFFICE_VERSION", length = 60)
    private String officeVersion;
    /** OFFICE 시리얼 넘버 */
    @Column(name = "OFFICE_SERIAL", length = 100)
    private String officeSerial;
    /** 한글 버전 */
    @Column(name = "HANGUL_VERSION", length = 60)
    private String hangulVersion;
    /** 한글 시리얼 넘버 */
    @Column(name = "HANGUL_SERIAL", length = 100)
    private String hangulSerial;

    /** 렌탈사명 (예: 롯데, AJ). null/빈값이면 자산(소유). */
    @Column(name = "RENTAL_COMPANY", length = 50)
    private String rentalCompany;

    /** PC 자산번호 */
    @Column(name = "PC_ASSET_NO", length = 60)
    private String pcAssetNo;
    /** 모니터 자산번호 */
    @Column(name = "MONITOR_ASSET_NO", length = 60)
    private String monitorAssetNo;

    @Builder
    private IpAddress(String ipAddress, IpStatus status, String userName, String department,
                      String location, String device, boolean approved, String approvalNo,
                      String remark, LocalDate noteDate, String site) {
        this.ipAddress = ipAddress;
        this.status = status;
        this.userName = userName;
        this.department = department;
        this.location = location;
        this.device = device;
        this.approved = approved;
        this.approvalNo = approvalNo;
        this.remark = remark;
        this.noteDate = noteDate;
        this.site = (site == null || site.isBlank()) ? "청주" : site;
    }

    // ----------------------------------------------------------------
    // 비즈니스 메서드
    // ----------------------------------------------------------------

    /** 정보/사용자 변경 (회수 상태였다면 다시 사용중으로 전환) */
    public void update(String userName, String department, String location, String device,
                       boolean approved, String approvalNo, String remark, LocalDate noteDate) {
        this.userName = userName;
        this.department = department;
        this.location = location;
        this.device = device;
        this.approved = approved;
        this.approvalNo = approvalNo;
        this.remark = remark;
        this.noteDate = noteDate;
        if (this.status == IpStatus.RECLAIMED) {
            this.status = IpStatus.IN_USE;
            this.reclaimedAt = null;
        } else {
            this.status = (userName != null && !userName.isBlank()) ? IpStatus.IN_USE : IpStatus.AVAILABLE;
        }
    }

    /** PC/장비 상세 스펙 갱신 (모델·제조번호·구입업체 + OS/OFFICE/한글/V3) */
    public void updateSpec(String model, String serialNo, String vendor,
                           String osVersion, String osSerial, String officeVersion, String officeSerial,
                           String hangulVersion, String hangulSerial, String rentalCompany,
                           String pcAssetNo, String monitorAssetNo) {
        this.model = model;
        this.serialNo = serialNo;
        this.vendor = vendor;
        this.osVersion = osVersion;
        this.osSerial = osSerial;
        this.officeVersion = officeVersion;
        this.officeSerial = officeSerial;
        this.hangulVersion = hangulVersion;
        this.hangulSerial = hangulSerial;
        this.rentalCompany = rentalCompany;
        this.pcAssetNo = pcAssetNo;
        this.monitorAssetNo = monitorAssetNo;
    }

    /**
     * PC+IP 반납 처리. PC/장비가 함께 반납되므로 사용자·장치·PC 스펙·자산정보를 모두 비운다.
     * <p>회수(IP만)와 달리 PC 관련 정보를 남기지 않는다. 상태는 회수(RECLAIMED)로 둔다.
     */
    public void returnPc(String remark) {
        this.userName = null;
        this.device = null;
        clearSpec();
        this.status = IpStatus.RECLAIMED;
        this.reclaimedAt = LocalDate.now();
        if (remark != null && !remark.isBlank()) {
            this.remark = remark;
            this.noteDate = LocalDate.now();
        }
    }

    /** IP 이동 시 원래 슬롯 비우기 (사용자·장치·스펙·자산 제거 → 가용) */
    public void vacate() {
        this.userName = null;
        this.device = null;
        clearSpec();
        this.status = IpStatus.AVAILABLE;
        this.reclaimedAt = null;
    }

    private void clearSpec() {
        this.model = null; this.serialNo = null; this.vendor = null;
        this.osVersion = null; this.osSerial = null;
        this.officeVersion = null; this.officeSerial = null;
        this.hangulVersion = null; this.hangulSerial = null;
        this.rentalCompany = null;
        this.pcAssetNo = null; this.monitorAssetNo = null;
    }

    /** IP 회수 처리 (비고가 있으면 비고/비고작성일도 갱신) */
    public void reclaim(String remark) {
        this.status = IpStatus.RECLAIMED;
        this.reclaimedAt = LocalDate.now();
        if (remark != null && !remark.isBlank()) {
            this.remark = remark;
            this.noteDate = LocalDate.now();
        }
    }

    // ----------------------------------------------------------------
    // 업무요청 자동 반영/원복 지원
    // ----------------------------------------------------------------

    /** IP 그룹(대역) 지정 — 신규 행 생성 시 대역 세팅용 */
    public void assignGroup(String ipGroup) {
        this.ipGroup = ipGroup;
    }

    /** 신규 부여/사용자 교체: 사용자 지정 후 사용중으로 전환 */
    public void assignTo(String userName, String department, String remark) {
        this.userName = userName;
        if (department != null && !department.isBlank()) {
            this.department = department;
        }
        if (remark != null && !remark.isBlank()) {
            this.remark = remark;
            this.noteDate = LocalDate.now();
        }
        this.status = IpStatus.IN_USE;
        this.reclaimedAt = null;
    }

    /** PC변경: 선택된 항목만 부분 수정 (부서/장치구분/렌탈사/제조번호/구입일/OS·OFFICE·한글 버전·시리얼) */
    public void applyPcFields(java.util.Map<String, String> f) {
        if (f == null) return;
        for (var e : f.entrySet()) {
            String v = e.getValue();
            switch (e.getKey()) {
                case "department" -> this.department = v;
                case "device" -> this.device = v;
                case "rentalCompany" -> this.rentalCompany = v;
                case "serialNo" -> this.serialNo = v;
                case "purchaseDate" -> this.purchaseDate = v;
                case "osVersion" -> this.osVersion = v;
                case "osSerial" -> this.osSerial = v;
                case "officeVersion" -> this.officeVersion = v;
                case "officeSerial" -> this.officeSerial = v;
                case "hangulVersion" -> this.hangulVersion = v;
                case "hangulSerial" -> this.hangulSerial = v;
                default -> { /* 무시 */ }
            }
        }
    }

    /** 현재 가변 상태 스냅샷 (원복용) */
    public IpRowSnapshot toSnapshot() {
        return new IpRowSnapshot(ipId,
                status == null ? null : status.name(), userName, department, location, device,
                approved, approvalNo, remark,
                noteDate == null ? null : noteDate.toString(),
                reclaimedAt == null ? null : reclaimedAt.toString(),
                model, serialNo, vendor, purchaseDate,
                osVersion, osSerial, officeVersion, officeSerial, hangulVersion, hangulSerial,
                rentalCompany, pcAssetNo, monitorAssetNo);
    }

    /** 스냅샷으로 전체 상태 원복 */
    public void restore(IpRowSnapshot s) {
        this.status = s.status() == null ? IpStatus.AVAILABLE : IpStatus.valueOf(s.status());
        this.userName = s.userName();
        this.department = s.department();
        this.location = s.location();
        this.device = s.device();
        this.approved = s.approved();
        this.approvalNo = s.approvalNo();
        this.remark = s.remark();
        this.noteDate = (s.noteDate() == null) ? null : LocalDate.parse(s.noteDate());
        this.reclaimedAt = (s.reclaimedAt() == null) ? null : LocalDate.parse(s.reclaimedAt());
        this.model = s.model();
        this.serialNo = s.serialNo();
        this.vendor = s.vendor();
        this.purchaseDate = s.purchaseDate();
        this.osVersion = s.osVersion();
        this.osSerial = s.osSerial();
        this.officeVersion = s.officeVersion();
        this.officeSerial = s.officeSerial();
        this.hangulVersion = s.hangulVersion();
        this.hangulSerial = s.hangulSerial();
        this.rentalCompany = s.rentalCompany();
        this.pcAssetNo = s.pcAssetNo();
        this.monitorAssetNo = s.monitorAssetNo();
    }
}
