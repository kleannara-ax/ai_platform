package com.company.module.cckb.repository;

import com.company.module.cckb.entity.CckbImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CckbImageRepository extends JpaRepository<CckbImage, Long> {

    List<CckbImage> findByInspectionRecord_InspectionIdOrderByImageIdAsc(String inspectionId);

    Optional<CckbImage> findByImageIdAndInspectionRecord_InspectionId(Long imageId, String inspectionId);

    /** 특정 검사에 속한 이미지인지 파일 경로(FILE_URL) 로 검증할 때 사용 (ImageController 소유권 체크) */
    List<CckbImage> findByFileUrlEndingWith(String suffix);
}
