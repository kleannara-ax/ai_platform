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
    private final PiAccessScopeResolver accessScope;
    private final PiAccessLogService accessLog;

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
        return upload(contractId, file, fileType, description, signedDate, null, null, userId);
    }

    @Transactional
    public ContractFileResponse upload(Long contractId, MultipartFile file, String fileType, String description,
                                       LocalDate signedDate, LocalDate documentDate, Long userId) {
        return upload(contractId, file, fileType, description, signedDate, documentDate, null, userId);
    }

    @Transactional
    public ContractFileResponse upload(Long contractId, MultipartFile file, String fileType, String description,
                                       LocalDate signedDate, LocalDate documentDate, String documentNote, Long userId) {
        return upload(contractId, file, fileType, description, signedDate, documentDate, documentNote, null, userId);
    }

    /**
     * 첨부 업로드 + 서류 일자(확약서 서명일 / 파기 확인서 파기 완료일) + 파기 지연 사유 + 정기 파기 대상 기간.
     * destructionPeriod(예: 2026-Q3)를 보내면 정기 파기 확인서로 저장한다(계약 종료 만료 서류로 세지 않음).
     * 관리 시작 전에 끝난 지난 기간도 올릴 수 있다.
     */
    @Transactional
    public ContractFileResponse upload(Long contractId, MultipartFile file, String fileType, String description,
                                       LocalDate signedDate, LocalDate documentDate, String documentNote,
                                       String destructionPeriod, Long userId) {
        PiContract contract = ensureContract(contractId);
        if (fileRepository.countByContractIdAndDeletedYn(contractId, N) >= MAX_FILES_PER_CONTRACT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "계약 1건당 첨부파일은 최대 " + MAX_FILES_PER_CONTRACT + "개까지 등록할 수 있습니다.");
        }
        ContractFileType type = parseType(fileType);
        String period = checkPeriod(contract, type, destructionPeriod);
        if (period != null && StringUtils.hasText(documentNote)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파기 지연 사유는 계약 종료 파기 확인서에만 입력합니다.");
        }
        if (documentDate != null && documentDate.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "서류 일자는 오늘보다 늦을 수 없습니다.");
        }
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
                .documentDate(documentDate)
                .documentNote(checkNote(type, documentNote))
                .destructionPeriod(period)
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

    /** 서류 일자 변경 (정보보호 및 비밀유지 확약서 서명일 / 개인정보 파기 확인서 파기 완료일). null 이면 비운다. */
    @Transactional
    public ContractFileResponse changeDocumentDate(Long fileId, LocalDate documentDate, Long userId) {
        return changeDocumentDate(fileId, documentDate, null, false, userId);
    }

    /**
     * 서류 일자 + 파기 지연 사유 변경. noteGiven=false 면 기존 사유를 그대로 둔다.
     */
    @Transactional
    public ContractFileResponse changeDocumentDate(Long fileId, LocalDate documentDate, String documentNote,
                                                   boolean noteGiven, Long userId) {
        PiContractFile f = findActive(fileId);
        ensureContract(f.getContractId());
        if (!f.getFileType().hasDocumentDate()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "서류 일자는 정보보호 및 비밀유지 확약서(서명일)와 개인정보 파기 확인서(파기 완료일)에만 입력할 수 있습니다.");
        }
        if (documentDate != null && documentDate.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, f.getFileType().getDocumentDateLabel() + "은(는) 오늘보다 늦을 수 없습니다.");
        }
        if (noteGiven) {
            f.changeDocumentDate(documentDate, checkNote(f.getFileType(), documentNote), userId);
        } else {
            f.changeDocumentDate(documentDate, userId);
        }
        return ContractFileResponse.from(f, latestContractDocId(f.getContractId()));
    }

    /** 파기 확인서 대상 기간 변경 (null/빈 값이면 계약 종료 파기 확인서로) */
    @Transactional
    public ContractFileResponse changeDestructionPeriod(Long fileId, String destructionPeriod, Long userId) {
        PiContractFile f = findActive(fileId);
        PiContract c = ensureContract(f.getContractId());
        if (f.getFileType() != ContractFileType.DESTRUCTION_CONFIRM) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "대상 기간은 개인정보 파기 확인서에만 지정할 수 있습니다.");
        }
        f.changeDestructionPeriod(checkPeriod(c, f.getFileType(), destructionPeriod), userId);
        return ContractFileResponse.from(f, latestContractDocId(f.getContractId()));
    }

    /** 정기 파기 대상 기간 검증 (파기 확인서 + 정기 파기확인 계약만, 주기·계약기간 확인). 비었으면 null */
    private static String checkPeriod(PiContract c, ContractFileType type, String period) {
        String p = PiContractService.trim(period);
        if (p == null) {
            return null;
        }
        p = p.toUpperCase(Locale.ROOT);
        if (type != ContractFileType.DESTRUCTION_CONFIRM) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "대상 기간은 개인정보 파기 확인서에만 지정할 수 있습니다.");
        }
        if (!c.isPeriodicDestruction()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "정기 파기확인 대상 계약이 아닙니다. 계약 수정에서 [정기 파기확인]을 먼저 체크하세요.");
        }
        String err = PiPeriodicDestruction.validatePeriod(c, p, LocalDate.now());
        if (err != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, err);
        }
        return p;
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
        PiContract c = ensureContract(f.getContractId());
        accessLog.record(com.company.module.personalinfo.entity.AccessActionType.DOWNLOAD_FILE, c, f.getFileId(),
                f.getFileType().getLabel() + " · " + f.getOriginalName());
        return new PiFileStorage.DownloadFile(f.getOriginalName(), f.getContentType(), storage.read(f.getStoredPath()));
    }

    /** 첨부파일 소프트 삭제 (관리자 전용). 실제 파일은 감사 증적으로 남긴다. */
    @Transactional
    public void delete(Long fileId, Long userId) {
        PiContractFile f = findActive(fileId);
        ensureContract(f.getContractId());
        f.delete(userId);
        if (f.isContractDoc()) {
            syncContractSignedDate(f.getContractId(), userId); // 최신 계약서를 지우면 그다음 최신 날짜로
        }
    }

    private PiContractFile findActive(Long fileId) {
        return fileRepository.findByFileIdAndDeletedYn(fileId, N)
                .orElseThrow(() -> new EntityNotFoundException("계약 첨부파일", fileId));
    }

    /** 계약 존재 + 현재 사용자 부서 범위 확인 */
    /** 파기 지연 사유: 파기 확인서에만, 최대 500자 */
    private static String checkNote(ContractFileType type, String note) {
        String n = PiContractService.trim(note);
        if (n == null) {
            return null;
        }
        if (type != ContractFileType.DESTRUCTION_CONFIRM) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파기 지연 사유는 개인정보 파기 확인서에만 입력할 수 있습니다.");
        }
        if (n.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파기 지연 사유는 500자 이내로 입력하세요.");
        }
        return n;
    }

    private PiContract ensureContract(Long contractId) {
        return accessScope.check(contractRepository.findByContractIdAndDeletedYn(contractId, N)
                .orElseThrow(() -> new EntityNotFoundException("위수탁 계약", contractId)));
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
