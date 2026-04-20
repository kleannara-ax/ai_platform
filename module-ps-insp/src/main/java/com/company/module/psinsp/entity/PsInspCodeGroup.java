package com.company.module.psinsp.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 공통코드 그룹 테이블 (code_group) 접근용 경량 Entity (읽기 전용)
 *
 * <p>JPQL 서브쿼리에서 GROUP_CODE → GROUP_ID 변환 시 사용합니다.
 */
@Entity
@Table(name = "code_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PsInspCodeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "GROUP_ID")
    private Long groupId;

    @Column(name = "GROUP_CODE", nullable = false, unique = true, length = 50)
    private String groupCode;

    @Column(name = "GROUP_NAME", nullable = false, length = 100)
    private String groupName;
}
