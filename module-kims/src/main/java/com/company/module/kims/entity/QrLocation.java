package com.company.module.kims.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * QR 구역 (사무실 위치/부서별 QR 코드).
 * <p>관리자가 위치·부서를 입력해 생성하면 고유 토큰이 발급되고,
 * 그 토큰을 담은 URL 이 QR 코드로 변환된다. 휴대폰으로 스캔하면
 * 해당 위치/부서가 채워진 업무 요청 페이지(향후)가 열린다.
 */
@Entity
@Table(name = "qr_location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QrLocation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QR_ID")
    private Long qrId;

    /** QR URL 에 담기는 고유 토큰 (UUID) */
    @Column(name = "TOKEN", nullable = false, length = 36, unique = true)
    private String token;

    /** 구역명/표시명 (예: 제지생산팀 사무실) */
    @Column(name = "NAME", length = 100)
    private String name;

    /** 위치 (예: 본관 3층 301호) */
    @Column(name = "LOCATION", nullable = false, length = 100)
    private String location;

    /** 부서 */
    @Column(name = "DEPARTMENT", length = 50)
    private String department;

    /** 사용 여부 (비활성 QR 은 스캔 시 무효) */
    @Column(name = "ACTIVE", nullable = false)
    private boolean active;

    @Column(name = "REMARK", length = 255)
    private String remark;

    @Builder
    private QrLocation(String token, String name, String location, String department,
                       Boolean active, String remark) {
        this.token = token;
        this.name = name;
        this.location = location;
        this.department = department;
        this.active = (active == null) || active;
        this.remark = remark;
    }

    /** 구역 정보 수정 */
    public void update(String name, String location, String department, boolean active, String remark) {
        this.name = name;
        this.location = location;
        this.department = department;
        this.active = active;
        this.remark = remark;
    }

    /** 소프트 삭제. 물리 DELETE 대신 DELETED_YN='Y' 로만 처리한다. */
    public void delete(String deletedBy) {
        markDeleted(deletedBy);
    }
}
