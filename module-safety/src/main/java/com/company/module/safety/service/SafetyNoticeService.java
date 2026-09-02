package com.company.module.safety.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.safety.dto.request.NoticeSaveRequest;
import com.company.module.safety.dto.response.NoticeResponse;
import com.company.module.safety.entity.SafetyNotice;
import com.company.module.safety.repository.SafetyNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 안전작업방식 매뉴얼 화면의 공지사항 관련 비즈니스 로직.
 * <p>조회는 인증된 사용자 누구나, 등록/수정/삭제는 SAFETY 관리자만 가능하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyNoticeService {

    private final SafetyNoticeRepository noticeRepository;

    public List<NoticeResponse> getList() {
        return noticeRepository.findAllActive().stream().map(NoticeResponse::from).toList();
    }

    @Transactional
    public NoticeResponse create(NoticeSaveRequest request, String createdBy) {
        SafetyNotice notice = SafetyNotice.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .pinned(request.isPinned())
                .createdBy(createdBy)
                .build();
        return NoticeResponse.from(noticeRepository.save(notice));
    }

    @Transactional
    public NoticeResponse update(Long noticeId, NoticeSaveRequest request, String updatedBy) {
        SafetyNotice notice = findActive(noticeId);
        notice.update(request.getTitle(), request.getContent(), request.isPinned(), updatedBy);
        return NoticeResponse.from(notice);
    }

    @Transactional
    public void delete(Long noticeId, String deletedBy) {
        findActive(noticeId).delete(deletedBy);
    }

    private SafetyNotice findActive(Long noticeId) {
        return noticeRepository.findActiveById(noticeId)
                .orElseThrow(() -> new EntityNotFoundException("공지사항을 찾을 수 없습니다. id=" + noticeId));
    }
}
