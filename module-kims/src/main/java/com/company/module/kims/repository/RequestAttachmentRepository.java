package com.company.module.kims.repository;

import com.company.module.kims.entity.RequestAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestAttachmentRepository extends JpaRepository<RequestAttachment, Long> {

    /** 특정 요청의 첨부파일 목록 (오래된 순, 삭제되지 않은 것만) */
    List<RequestAttachment> findByServiceRequest_RequestIdAndDeletedYnOrderByCreatedAtAsc(
            Long requestId, String deletedYn);
}
