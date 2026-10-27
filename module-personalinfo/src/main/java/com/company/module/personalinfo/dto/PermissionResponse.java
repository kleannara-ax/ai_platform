package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

/** 화면 버튼 노출 제어용 현재 사용자 권한 */
@Getter
@Builder
public class PermissionResponse {

    private boolean canRead;
    private boolean canWrite;
    private boolean canDelete;
    private boolean canSendMail;
}
