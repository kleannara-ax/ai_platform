package com.company.module.personalinfo.repository;

import com.company.module.personalinfo.entity.FormTemplateType;
import com.company.module.personalinfo.entity.PiFormTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PiFormTemplateRepository extends JpaRepository<PiFormTemplate, Long> {

    List<PiFormTemplate> findByFormTypeAndDeletedYnOrderByVersionNoDesc(FormTemplateType formType, String deletedYn);

    Optional<PiFormTemplate> findFirstByFormTypeAndActiveYnAndDeletedYnOrderByVersionNoDesc(
            FormTemplateType formType, String activeYn, String deletedYn);

    List<PiFormTemplate> findByFormTypeAndActiveYn(FormTemplateType formType, String activeYn);

    Optional<PiFormTemplate> findByFormTemplateIdAndDeletedYn(Long formTemplateId, String deletedYn);

    /** 다음 버전 번호용. 삭제한 버전 번호도 다시 쓰지 않는다 */
    @Query("select coalesce(max(f.versionNo), 0) from PiFormTemplate f where f.formType = :formType")
    int maxVersionNo(@Param("formType") FormTemplateType formType);
}
