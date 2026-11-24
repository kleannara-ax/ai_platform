package com.company.module.personalinfo.service;

import com.company.module.personalinfo.dto.FileCheckResponse;
import com.company.module.personalinfo.entity.ContractFileType;
import com.company.module.personalinfo.entity.PiContract;
import com.company.module.personalinfo.entity.PiContractFile;
import com.company.module.personalinfo.repository.PiContractFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 첨부 업로드 전 파일 내용 확인 (계약 상세 첨부·계약만료시 점검·정기 파기확인 업로드 공통).
 * <ol>
 *   <li>중복 파일: 이 계약에 같은 내용(SHA-256)의 파일이 이미 있으면 막는다</li>
 *   <li>서류 종류: 문서 제목(앞부분)의 핵심 문구로 위수탁 계약서·재위탁 동의서·확약서·파기 확인서를 구분해 고른 구분과 다르면 막는다</li>
 *   <li>업체명: 문서의 '수탁업체명' 등 표기가 계약 업체와 다르면 막는다 (㈜·(주)·주식회사·띄어쓰기 무시)</li>
 *   <li>날짜: 입력한 서류 일자(파기 완료일·서명일·체결일)가 문서에 적힌 날짜 중에 없으면 막는다</li>
 *   <li>글자를 읽을 수 없거나(스캔 PDF·이미지·zip) 서류 종류를 알아볼 수 없으면 담당자 직접 확인이 필요하다</li>
 * </ol>
 * '기타' 구분은 중복 파일만 확인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PiFileContentChecker {

    private static final DateTimeFormatter DOT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    /** 서류 제목을 찾는 앞부분 (공백 제거 후 글자 수) */
    private static final int HEAD_CHARS = 400;

    /** 서류 종류별 제목 문구 (공백 없이 비교, 문서 앞부분에서 가장 먼저 나오는 문구의 종류로 판단) */
    static final Map<ContractFileType, List<String>> TYPE_WORDS = new LinkedHashMap<>();

    static {
        TYPE_WORDS.put(ContractFileType.DESTRUCTION_CONFIRM, List.of("파기확인서", "파기확인", "파기증명서", "파기결과서", "파기내역서"));
        TYPE_WORDS.put(ContractFileType.SECURITY_PLEDGE, List.of("비밀유지확약서", "확약서", "비밀유지서약서", "보안서약서", "서약서", "비밀유지각서"));
        TYPE_WORDS.put(ContractFileType.SUBCONTRACT_CONSENT, List.of("재위탁동의서", "재위탁동의", "재위탁승인", "재위탁에관한동의"));
        TYPE_WORDS.put(ContractFileType.CONTRACT, List.of("위수탁계약서", "위탁계약서", "업무위탁계약", "처리위탁계약", "위탁계약", "계약서"));
    }

    /** 수탁업체명 표기 (라벨 + 구분자 + 값) */
    private static final Pattern VENDOR_LABEL = Pattern.compile(
            "(?:수탁업체명|수탁업체|수탁자명|업체명|상호)\\s*[:：|\\t]?\\s*([^\\n\\t|]{2,40})");

    private final PiContractFileRepository fileRepository;
    private final PiFileStorage storage;
    private final PiPeriodicDestruction periodic;

    /**
     * 파일 내용 확인.
     * @param documentDate 입력한 서류 일자 (확약서 서명일 / 파기 확인서 파기 완료일)
     * @param signedDate   입력한 위수탁 계약서 체결일
     */
    public FileCheckResponse check(PiContract c, MultipartFile file, ContractFileType type,
                                   LocalDate documentDate, LocalDate signedDate) {
        return check(c, file, type, documentDate, signedDate, null);
    }

    /** @param destructionPeriod 정기 파기 확인서 대상 기간 (예: 2026-Q3) — 파기 일자가 수취 기한을 지났는지 확인 */
    public FileCheckResponse check(PiContract c, MultipartFile file, ContractFileType type,
                                   LocalDate documentDate, LocalDate signedDate, String destructionPeriod) {
        List<FileCheckResponse.Issue> errors = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        String name = PiFileStorage.sanitizeOriginalName(file == null ? null : file.getOriginalFilename());
        FileCheckResponse.FileCheckResponseBuilder res = FileCheckResponse.builder()
                .fileName(name).fileType(type.name()).fileTypeLabel(type.getLabel())
                .documentDates(List.of()).errors(errors).notes(notes);
        if (file == null || file.isEmpty()) {
            errors.add(issue("FILE", "파일이 비어 있습니다."));
            return res.blocked(true).build();
        }
        String ext = PiFileStorage.extensionOf(name);
        if (!PiFileStorage.CONTRACT_EXTENSIONS.contains(ext)) {
            errors.add(issue("FILE", "허용되지 않는 파일 형식입니다. (허용: " + String.join(", ", PiFileStorage.CONTRACT_EXTENSIONS) + ")"));
            return res.blocked(true).build();
        }
        if (file.getSize() > storage.getMaxContractFileBytes()) {
            errors.add(issue("FILE", "파일 크기는 " + (storage.getMaxContractFileBytes() / 1024 / 1024) + "MB 이하만 올릴 수 있습니다."));
            return res.blocked(true).build();
        }
        String periodKey = type == ContractFileType.DESTRUCTION_CONFIRM && destructionPeriod != null
                ? destructionPeriod.trim().toUpperCase(java.util.Locale.ROOT) : null;
        res.periodWarning(periodWarning(periodKey, documentDate));
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            errors.add(issue("FILE", "파일을 읽지 못했습니다."));
            return res.blocked(true).build();
        }

        // 1) 중복 파일
        String hash = sha256(data);
        PiContractFile dup = fileRepository.findByContractIdAndDeletedYnOrderByFileIdDesc(c.getContractId(), "N").stream()
                .filter(f -> hash.equalsIgnoreCase(f.getFileHash())).findFirst().orElse(null);
        if (dup != null) {
            String period = PiPeriodicDestruction.labelOf(dup.getDestructionPeriod());
            errors.add(issue("DUPLICATE", "같은 파일이 이미 등록되어 있습니다 — " + dup.getFileType().getLabel()
                    + (period == null ? "" : "(" + period + ")") + " · " + dup.getOriginalName()
                    + (dup.getCreatedAt() == null ? "" : " · " + dup.getCreatedAt().format(DT) + " 등록")
                    + ". 첨부 목록에서 확인하세요."));
            res.duplicate(FileCheckResponse.DuplicateFile.builder().fileId(dup.getFileId())
                    .fileTypeLabel(dup.getFileType().getLabel()).originalName(dup.getOriginalName())
                    .destructionPeriodLabel(period).createdAt(dup.getCreatedAt()).build());
        }
        if (type == ContractFileType.ETC) {
            notes.add("'기타' 파일은 중복 여부만 확인합니다.");
            return finish(res, errors, false, true);
        }

        // 2) 글자 읽기
        String text = null;
        if (PiContractDocAnalyzer.TEXT_EXTENSIONS.contains(ext)) {
            try {
                text = PiContractDocAnalyzer.extractText(data, ext);
            } catch (Exception e) {
                log.info("[personalinfo] 업로드 확인 글자 읽기 실패 ({}): {}", ext, e.toString());
            }
        }
        if (text == null || text.isBlank()) {
            notes.add(PiContractDocAnalyzer.TEXT_EXTENSIONS.contains(ext)
                    ? "파일에서 글자를 읽지 못했습니다(스캔본·암호·손상 파일일 수 있음). 서류 종류·업체명·날짜를 자동으로 확인할 수 없습니다."
                    : "이 파일 형식(" + ext + ")은 글자를 읽을 수 없어 서류 종류·업체명·날짜를 자동으로 확인할 수 없습니다.");
            return finish(res, errors, true, false);
        }
        String norm = PiContractDocAnalyzer.normalize(text);
        res.textReadable(true);

        // 3) 서류 종류
        ContractFileType detected = detectType(norm.replaceAll("\\s+", ""));
        boolean manual = false;
        if (detected == null) {
            notes.add("문서 제목에서 서류 종류를 알아볼 수 없습니다. 내용을 직접 확인해야 합니다.");
            manual = true;
        } else {
            res.detectedType(detected.name()).detectedTypeLabel(detected.getLabel());
            if (detected != type) {
                errors.add(issue("TYPE_MISMATCH", "이 파일은 '" + detected.getLabel() + "'(으)로 보입니다. 고른 구분은 '" + type.getLabel()
                        + "'입니다. 파일 구분을 '" + detected.getLabel() + "'(으)로 바꿔 올리세요."));
            } else {
                notes.add("서류 종류 일치: " + detected.getLabel());
            }
        }

        // 4) 업체명
        String vendorKey = PiVendorNames.key(c.getVendorName());
        if (!vendorKey.isEmpty()) {
            if (PiVendorNames.key(norm).contains(vendorKey)) {
                notes.add("업체명 일치: " + c.getVendorName());
            } else {
                String found = foundVendor(norm);
                res.foundVendorName(found);
                if (found != null) {
                    errors.add(issue("VENDOR_MISMATCH", "문서의 수탁업체명 '" + found + "'이(가) 계약 업체 '" + c.getVendorName() + "'와 다릅니다."));
                } else {
                    notes.add("문서에서 수탁업체명을 찾지 못해 업체명은 확인하지 않았습니다.");
                }
            }
        }

        // 5) 날짜
        LocalDate entered = type == ContractFileType.CONTRACT ? signedDate
                : type.hasDocumentDate() ? documentDate : null;
        if (type == ContractFileType.CONTRACT || type.hasDocumentDate()) {
            int maxYear = LocalDate.now().getYear() + 5;
            List<LocalDate> dates = PiContractDocAnalyzer.findDates(norm, maxYear).stream()
                    .filter(s -> type == ContractFileType.CONTRACT || !"PERIOD".equals(s.kind()))   // 서류 일자는 계약기간 날짜 제외
                    .map(PiContractDocAnalyzer.Scored::date).distinct().sorted().toList();
            res.documentDates(dates);
            if (entered == null && periodKey != null && !dates.isEmpty()) {
                res.periodWarning(periodWarning(periodKey, dates.get(dates.size() - 1)));   // 파기 완료일 미입력이면 문서의 마지막 날짜로
            }
            String label = type == ContractFileType.CONTRACT ? "체결일" : type.getDocumentDateLabel();
            if (entered == null) {
                if (!dates.isEmpty() && type != ContractFileType.CONTRACT) {
                    notes.add(label + "을(를) 입력하지 않았습니다. 문서의 날짜: " + join(dates));
                }
            } else if (dates.isEmpty()) {
                notes.add("문서에서 날짜를 찾지 못해 " + label + "은(는) 확인하지 않았습니다.");
            } else if (!dates.contains(entered)) {
                errors.add(issue("DATE_MISMATCH", "입력한 " + label + " " + entered.format(DOT) + "이(가) 문서에 없습니다. 문서의 날짜: "
                        + join(dates) + ". 문서에 적힌 날짜로 바꿔 올리세요."));
            } else {
                notes.add(label + " 일치: " + entered.format(DOT));
            }
        }
        return finish(res, errors, manual, false);
    }

    /** 정기 파기 확인서 파기 일자가 수취 기한(기간 종료 + 기한 일수)을 지났으면 확인 문구, 아니면 null */
    String periodWarning(String periodKey, LocalDate date) {
        LocalDate start = PiPeriodicDestruction.startOf(periodKey);
        if (start == null || date == null) {
            return null;
        }
        LocalDate end = PiPeriodicDestruction.periodEnd(PiPeriodicDestruction.cycleOf(periodKey), start);
        LocalDate due = end.plusDays(periodic.getReplyDays());
        if (!date.isAfter(due)) {
            return null;
        }
        String label = PiPeriodicDestruction.labelOf(periodKey);
        return "날짜가 " + periodic.getReplyDays() + "일 이상 경과했습니다. 파기 일자 " + date.format(DOT) + "은(는) " + label
                + " 종료일(" + end.format(DOT) + ")로부터 " + java.time.temporal.ChronoUnit.DAYS.between(end, date) + "일 지났습니다(수취 기한 "
                + due.format(DOT) + "). 해당 기간(" + label + ")의 파기확인서가 맞습니까?";
    }

    private static FileCheckResponse finish(FileCheckResponse.FileCheckResponseBuilder res, List<FileCheckResponse.Issue> errors,
                                            boolean manual, boolean etc) {
        boolean blocked = !errors.isEmpty();
        return res.blocked(blocked).needsManualConfirm(!blocked && manual).ok(!blocked && !manual).build();
    }

    /** 문서 앞부분에서 가장 먼저 나오는 서류 제목 문구의 종류 (같은 위치면 긴 문구 우선, 못 찾으면 null) */
    static ContractFileType detectType(String spaceless) {
        String head = spaceless.length() > HEAD_CHARS ? spaceless.substring(0, HEAD_CHARS) : spaceless;
        ContractFileType best = null;
        int bestAt = Integer.MAX_VALUE;
        int bestLen = 0;
        for (Map.Entry<ContractFileType, List<String>> e : TYPE_WORDS.entrySet()) {
            for (String w : e.getValue()) {
                int at = head.indexOf(w);
                if (at >= 0 && (at < bestAt || (at == bestAt && w.length() > bestLen))) {
                    best = e.getKey();
                    bestAt = at;
                    bestLen = w.length();
                }
            }
        }
        return best;
    }

    /** '수탁업체명' 등 라벨 옆 값 (자리표시자·서명란 문구 제외, 없으면 null) */
    static String foundVendor(String text) {
        Matcher m = VENDOR_LABEL.matcher(text);
        while (m.find()) {
            String v = m.group(1).trim();
            if (v.isEmpty() || v.startsWith("{{") || v.startsWith("(인)") || v.startsWith("(서명)")
                    || PiVendorNames.key(v).length() < 2) {
                continue;
            }
            return v;
        }
        return null;
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String join(List<LocalDate> dates) {
        return String.join(", ", dates.stream().filter(Objects::nonNull).map(d -> d.format(DOT)).toList());
    }

    private static FileCheckResponse.Issue issue(String code, String message) {
        return FileCheckResponse.Issue.builder().code(code).message(message).build();
    }
}
