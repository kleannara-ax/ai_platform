package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.dto.ContractFileResponse;
import com.company.module.personalinfo.entity.ContractFileType;
import com.company.module.personalinfo.entity.PiContractFile;
import com.company.module.personalinfo.repository.PiContractFileRepository;
import com.company.module.personalinfo.repository.PiContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

/**
 * 위수탁 계약서 및 관련 증빙 파일 업로드/다운로드 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PiContractFileService {

    private static final String N = "N";
    private static final int MAX_FILES_PER_CONTRACT = 50;

    private final PiContractFileRepository fileRepository;
    private final PiContractRepository contractRepository;
    private final PiFileStorage storage;

    public List<ContractFileResponse> getList(Long contractId) {
        ensureContract(contractId);
        return fileRepository.findByContractIdAndDeletedYnOrderByFileIdDesc(contractId, N).stream()
                .map(ContractFileResponse::from).toList();
    }

    @Transactional
    public ContractFileResponse upload(Long contractId, MultipartFile file, String fileType, String description, Long userId) {
        ensureContract(contractId);
        if (fileRepository.countByContractIdAndDeletedYn(contractId, N) >= MAX_FILES_PER_CONTRACT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "계약 1건당 첨부파일은 최대 " + MAX_FILES_PER_CONTRACT + "개까지 등록할 수 있습니다.");
        }
        ContractFileType type = parseType(fileType);
        PiFileStorage.StoredFile stored = storage.store(file, "contract", PiFileStorage.CONTRACT_EXTENSIONS,
                storage.getMaxContractFileBytes());
        PiContractFile saved = fileRepository.save(PiContractFile.builder()
                .contractId(contractId)
                .fileType(type)
                .originalName(stored.originalName())
                .storedPath(stored.relativePath())
                .contentType(stored.contentType())
                .fileSize(stored.size())
                .fileHash(stored.sha256())
                .description(PiContractService.trim(description))
                .createdBy(userId)
                .build());
        return ContractFileResponse.from(saved);
    }

    public PiFileStorage.DownloadFile download(Long fileId) {
        PiContractFile f = findActive(fileId);
        ensureContract(f.getContractId());
        return new PiFileStorage.DownloadFile(f.getOriginalName(), f.getContentType(), storage.read(f.getStoredPath()));
    }

    /** 첨부파일 소프트 삭제 (관리자 전용). 실제 파일은 감사 증적으로 남긴다. */
    @Transactional
    public void delete(Long fileId, Long userId) {
        findActive(fileId).delete(userId);
    }

    private PiContractFile findActive(Long fileId) {
        return fileRepository.findByFileIdAndDeletedYn(fileId, N)
                .orElseThrow(() -> new EntityNotFoundException("계약 첨부파일", fileId));
    }

    private void ensureContract(Long contractId) {
        if (contractRepository.findByContractIdAndDeletedYn(contractId, N).isEmpty()) {
            throw new EntityNotFoundException("위수탁 계약", contractId);
        }
    }

    private static ContractFileType parseType(String fileType) {
        if (!StringUtils.hasText(fileType)) {
            return ContractFileType.CONTRACT;
        }
        try {
            return ContractFileType.valueOf(fileType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파일 구분 값이 올바르지 않습니다: " + fileType);
        }
    }
}
