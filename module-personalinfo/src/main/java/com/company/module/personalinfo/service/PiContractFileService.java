package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.dto.ContractFileResponse;
import com.company.module.personalinfo.entity.ContractFileType;
import com.company.module.personalinfo.entity.PiContract;
import com.company.module.personalinfo.entity.PiContractFile;
import com.company.module.personalinfo.repository.PiContractFileRepository;
import com.company.module.personalinfo.repository.PiContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
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
    private final PiContractDocAnalyzer docAnalyzer;

    public List<ContractFileResponse> getList(Long contractId) {
        ensureContract(contractId);
        Long latest = latestContractDocId(contractId);
        return fileRepository.findByContractIdAndDeletedYnOrderByFileIdDesc(contractId, N).stream()
                .map(f -> ContractFileResponse.from(f, latest)).toList();
    }

    /** 체결일이 가장 최근인 위수탁 계약서 파일 ID (없으면 null) */
    public Long latestContractDocId(Long contractId) {
        return fileRepository
                .findFirstByContractIdAndFileTypeAndDeletedYnAndSignedDateIsNotNullOrderBySignedDateDescFileIdDesc(
                        contractId, ContractFileType.CONTRACT, N)
                .map(PiContractFile::getFileId).orElse(null);
    }

    @Transactional
    public ContractFileResponse upload(Long contractId, MultipartFile file, String fileType, String description, Long userId) {
        return upload(contractId, file, fileType, description, null, userId);
    }

    /**
     * 첨부 업로드. 위수탁 계약서(CONTRACT)는 체결일을 함께 저장한다.
     * <ul>
     *   <li>signedDate 를 보내면 그 날짜(화면에서 확인·수정한 값)를 쓴다.</li>
     *   <li>안 보내면 계약서 글자에서 계약일자를 읽는다(체결일 표기가 없으면 계약기간 시작일). 못 읽으면 비워 둔다.</li>
     *   <li>저장 후 계약의 개인정보처리 위탁계약 체결일을 최신 계약서 체결일로 다시 맞춘다.</li>
     * </ul>
     */
    @Transactional
    public ContractFileResponse upload(Long contractId, MultipartFile file, String fileType, String description,
                                       LocalDate signedDate, Long userId) {
        ensureContract(contractId);
        if (fileRepository.countByContractIdAndDeletedYn(contractId, N) >= MAX_FILES_PER_CONTRACT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "계약 1건당 첨부파일은 최대 " + MAX_FILES_PER_CONTRACT + "개까지 등록할 수 있습니다.");
        }
        ContractFileType type = parseType(fileType);
        PiFileStorage.StoredFile stored = storage.store(file, "contract", PiFileStorage.CONTRACT_EXTENSIONS,
                storage.getMaxContractFileBytes());
        LocalDate docDate = null;
        if (type == ContractFileType.CONTRACT) {
            docDate = signedDate != null ? signedDate : readSignedDate(file);
        }
        PiContractFile saved = fileRepository.save(PiContractFile.builder()
                .contractId(contractId)
                .fileType(type)
                .originalName(stored.originalName())
                .storedPath(stored.relativePath())
                .contentType(stored.contentType())
                .fileSize(stored.size())
                .fileHash(stored.sha256())
                .description(PiContractService.trim(description))
                .signedDate(docDate)
                .createdBy(userId)
                .build());
        if (type == ContractFileType.CONTRACT) {
            syncContractSignedDate(contractId, userId);
        }
        return ContractFileResponse.from(saved, latestContractDocId(contractId));
    }

    /** 위수탁 계약서의 체결일 변경 (직접 입력·수정). null 이면 비운다. */
    @Transactional
    public ContractFileResponse changeSignedDate(Long fileId, LocalDate signedDate, Long userId) {
        PiContractFile f = findActive(fileId);
        ensureContract(f.getContractId());
        if (!f.isContractDoc()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "체결일은 위수탁 계약서 파일에만 입력할 수 있습니다.");
        }
        f.changeSignedDate(signedDate, userId);
        fileRepository.flush();
        syncContractSignedDate(f.getContractId(), userId);
        return ContractFileResponse.from(f, latestContractDocId(f.getContractId()));
    }

    /**
     * 계약의 개인정보처리 위탁계약 체결일 = 삭제되지 않은 위수탁 계약서 중 가장 최근 체결일.
     * 체결일이 있는 계약서가 하나도 없으면 계약 값은 그대로 둔다(직접 입력한 값 보존).
     */
    @Transactional
    public void syncContractSignedDate(Long contractId, Long userId) {
        fileRepository.flush();
        fileRepository
                .findFirstByContractIdAndFileTypeAndDeletedYnAndSignedDateIsNotNullOrderBySignedDateDescFileIdDesc(
                        contractId, ContractFileType.CONTRACT, N)
                .ifPresent(latest -> contractRepository.findByContractIdAndDeletedYn(contractId, N)
                        .ifPresent((PiContract c) -> {
                            if (!latest.getSignedDate().equals(c.getContractSignedDate()) || !"Y".equals(c.getContractDocYn())) {
                                c.applyLatestContractDocDate(latest.getSignedDate(), userId);
                            }
                        }));
    }

    private LocalDate readSignedDate(MultipartFile file) {
        try {
            return docAnalyzer.analyze(file).getDetectedDate();
        } catch (RuntimeException e) {
            return null; // 날짜를 못 읽어도 업로드는 진행 (화면에서 직접 입력)
        }
    }

    public PiFileStorage.DownloadFile download(Long fileId) {
        PiContractFile f = findActive(fileId);
        ensureContract(f.getContractId());
        return new PiFileStorage.DownloadFile(f.getOriginalName(), f.getContentType(), storage.read(f.getStoredPath()));
    }

    /** 첨부파일 소프트 삭제 (관리자 전용). 실제 파일은 감사 증적으로 남긴다. */
    @Transactional
    public void delete(Long fileId, Long userId) {
        PiContractFile f = findActive(fileId);
        f.delete(userId);
        if (f.isContractDoc()) {
            syncContractSignedDate(f.getContractId(), userId); // 최신 계약서를 지우면 그다음 최신 날짜로
        }
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
