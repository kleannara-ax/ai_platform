package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.DlpMailRuleResponse;
import com.company.module.security_log.dto.DlpMailRuleSaveRequest;
import com.company.module.security_log.dto.DlpMailSendLogResponse;
import com.company.module.security_log.entity.SecLogDlpKakaoApproval;
import com.company.module.security_log.entity.SecLogDlpMailRule;
import com.company.module.security_log.entity.SecLogDlpMailSendLog;
import com.company.module.security_log.repository.SecLogDlpMailRuleRepository;
import com.company.module.security_log.repository.SecLogDlpMailSendLogRepository;
import com.company.module.security_log.support.SecLogTime;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * DLP 관리 &gt; 메일 알림 관리 - 카카오톡 승인 대장 신청 종료일 기준 메일 알림 (규칙 여러 개)
 * <ul>
 *   <li>규칙마다: 보내는 날 = 신청 종료일 + N일, 매일 보내는 시각, 받는 사람·참조, 제목·본문(자리표시)</li>
 *   <li>대상: 같은 사람(부서+이름)의 최신 '신청중' 건 중 오늘 ≥ 보내는 날 이고, 보내는 날 ≥ 규칙의 보내기 시작일</li>
 *   <li>신청 종료일이 같은 사람은 메일 1통에 모두 담아 보냄 (본문의 '사람마다' 자리표시가 들어간 줄은 사람 수만큼 반복)</li>
 *   <li>규칙 1개 x 승인 건 1개당 한 번만 보냄 (SENT), 실패(FAIL)는 다음 실행 때 다시 시도</li>
 *   <li>메일 서버(SMTP)는 서버 환경 변수 SPRING_MAIL_HOST 등으로 넣음 - 없으면 보내지 않고 '대기'로 둠</li>
 *   <li>받는 사람·참조는 회사 메일만 (서버 환경 변수 SECLOG_MAIL_ALLOWED_DOMAINS, 쉼표 구분) - 저장할 때 막고, 보낼 때 한 번 더 막음.
 *       도메인을 넣지 않으면 규칙은 저장되지만 아무 데도 보내지 않음('대기')</li>
 *   <li>받는 사람이 없으면 규칙을 켤 수 없음 (꺼진 상태로만 저장)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecLogDlpMailRuleService {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final int MAX_ADDRS = 20;
    private static final int MAX_RULES = 20;

    /** 제목·본문 자리표시 (화면 안내 순서) */
    private static final Map<String, String> PLACEHOLDERS = new LinkedHashMap<>();

    static {
        PLACEHOLDERS.put("{번호}", "순번 (사람마다)");
        PLACEHOLDERS.put("{사업부}", "사업부 (사람마다)");
        PLACEHOLDERS.put("{부서}", "부서 (사람마다)");
        PLACEHOLDERS.put("{이름}", "이름 (사람마다)");
        PLACEHOLDERS.put("{신청시작일}", "신청 시작일 (사람마다)");
        PLACEHOLDERS.put("{문서번호}", "전자결재 문서번호 (사람마다)");
        PLACEHOLDERS.put("{사유}", "사유 (사람마다)");
        PLACEHOLDERS.put("{신청종료일}", "신청 종료일");
        PLACEHOLDERS.put("{보내는날}", "보내는 날 (신청 종료일 + N일)");
        PLACEHOLDERS.put("{인원수}", "이 메일에 담긴 인원 수");
    }

    private final SecLogDlpKakaoApprovalService approvalService;
    private final SecLogDlpMailRuleRepository ruleRepository;
    private final SecLogDlpMailSendLogRepository logRepository;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final ReentrantLock runLock = new ReentrantLock();

    /** 보내는 주소 (없으면 SMTP 계정) */
    @Value("${SECLOG_MAIL_FROM:${spring.mail.username:}}")
    private String fromAddr;

    /** 받을 수 있는 회사 메일 도메인 (쉼표 구분, 예: company.co.kr) - 없으면 아무 데도 보내지 않음 */
    @Value("${SECLOG_MAIL_ALLOWED_DOMAINS:}")
    private String allowedDomainsRaw;

    /** 회사 메일 도메인 목록 (소문자, 앞의 @ 뺌) */
    public List<String> allowedDomains() {
        List<String> out = new ArrayList<>();
        if (StringUtils.hasText(allowedDomainsRaw)) {
            for (String p : allowedDomainsRaw.split("[,;\\s]+")) {
                String d = p.trim().toLowerCase(Locale.ROOT).replaceFirst("^@", "");
                if (!d.isEmpty() && !out.contains(d)) {
                    out.add(d);
                }
            }
        }
        return out;
    }

    /** 회사 밖 주소 (주소의 @ 뒤가 회사 도메인과 똑같지 않으면 회사 밖, 도메인 미설정이면 전부) */
    List<String> outsideAddrs(String... lists) {
        List<String> domains = allowedDomains();
        List<String> out = new ArrayList<>();
        for (String v : lists) {
            if (!StringUtils.hasText(v)) {
                continue;
            }
            for (String a : split(v)) {
                if (!domains.contains(a.substring(a.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT))) {
                    out.add(a);
                }
            }
        }
        return out;
    }

    public boolean smtpReady() {
        return mailSender.getIfAvailable() != null;
    }

    // ================================================================== 조회

    @Transactional(readOnly = true)
    public DlpMailRuleResponse getRules() {
        LocalDate today = SecLogTime.today();
        List<SecLogDlpKakaoApproval> latest = latestApprovals();
        List<DlpMailRuleResponse.Rule> rules = new ArrayList<>();
        for (SecLogDlpMailRule r : activeRules()) {
            Map<Long, SecLogDlpMailSendLog> logs = logsByApproval(r.getMailRuleId());
            long pending = due(r, latest, today).stream().filter(a -> !isSent(logs.get(a.getKakaoApprovalId()))).count();
            long sent = logs.values().stream().filter(this::isSent).count();
            long fail = logs.values().stream().filter(l -> SecLogDlpMailSendLog.FAIL.equals(l.getSendStatus())).count();
            rules.add(DlpMailRuleResponse.Rule.builder()
                    .mailRuleId(r.getMailRuleId()).basic(r.getRuleKey() != null).ruleName(r.getRuleName())
                    .offsetDays(r.getOffsetDays()).offsetText(offsetText(r.getOffsetDays())).sendTime(r.getSendTime())
                    .toAddrs(r.getToAddrs()).ccAddrs(r.getCcAddrs()).subjectTpl(r.getSubjectTpl()).bodyTpl(r.getBodyTpl())
                    .useYn(r.getUseYn()).startDate(r.getStartDate()).lastRunAt(r.getLastRunAt()).lastRunResult(r.getLastRunResult())
                    .pendingCount(pending).sentCount(sent).failCount(fail).build());
        }
        List<DlpMailRuleResponse.Placeholder> ph = new ArrayList<>();
        PLACEHOLDERS.forEach((k, v) -> ph.add(DlpMailRuleResponse.Placeholder.builder().key(k).desc(v).build()));
        return DlpMailRuleResponse.builder().smtpReady(smtpReady()).allowedDomains(allowedDomains())
                .fromAddr(StringUtils.hasText(fromAddr) ? fromAddr : null).placeholders(ph).rules(rules).build();
    }

    /** 보낸 기록 (최근 300건, ruleId 가 있으면 그 규칙만) */
    @Transactional(readOnly = true)
    public List<DlpMailSendLogResponse> getLogs(Long ruleId) {
        Map<Long, String> names = new HashMap<>();
        ruleRepository.findAll().forEach(r -> names.put(r.getMailRuleId(), r.getRuleName()));
        List<SecLogDlpMailSendLog> list = ruleId == null
                ? logRepository.findTop300ByDeletedYnOrderByLastTryAtDescMailSendLogIdDesc(SecLogConstants.DELETED_N)
                : logRepository.findTop300ByMailRuleIdAndDeletedYnOrderByLastTryAtDescMailSendLogIdDesc(ruleId, SecLogConstants.DELETED_N);
        return list.stream().map(l -> DlpMailSendLogResponse.from(l, names.get(l.getMailRuleId()))).toList();
    }

    static String offsetText(int d) {
        return d == 0 ? "신청 종료일 당일" : d > 0 ? "신청 종료일 + " + d + "일" : "신청 종료일 " + (-d) + "일 전";
    }

    // ================================================================== 등록 · 수정 · 삭제

    @Transactional
    public Long create(DlpMailRuleSaveRequest q, Long userId) {
        if (activeRules().size() >= MAX_RULES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "메일 알림 규칙은 " + MAX_RULES + "개까지 만들 수 있습니다.");
        }
        String use = "N".equals(q.getUseYn()) ? "N" : "Y";
        SecLogDlpMailRule r = SecLogDlpMailRule.builder()
                .ruleName(q.getRuleName().trim()).offsetDays(q.getOffsetDays()).sendTime(q.getSendTime())
                .toAddrs(nb(addrs(q.getToAddrs(), "받는 사람", "Y".equals(use)))).ccAddrs(addrs(q.getCcAddrs(), "참조", false))
                .subjectTpl(q.getSubjectTpl().trim()).bodyTpl(q.getBodyTpl()).useYn(use)
                .startDate(SecLogTime.today()).createdBy(userId).build();
        return ruleRepository.save(r).getMailRuleId();
    }

    @Transactional
    public void update(Long id, DlpMailRuleSaveRequest q, Long userId) {
        String use = "N".equals(q.getUseYn()) ? "N" : "Y";
        rule(id).update(q.getRuleName().trim(), q.getOffsetDays(), q.getSendTime(),
                nb(addrs(q.getToAddrs(), "받는 사람", "Y".equals(use))), addrs(q.getCcAddrs(), "참조", false),
                q.getSubjectTpl().trim(), q.getBodyTpl(), use, userId);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        rule(id).delete(userId);
    }

    private SecLogDlpMailRule rule(Long id) {
        return ruleRepository.findByMailRuleIdAndDeletedYn(id, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private List<SecLogDlpMailRule> activeRules() {
        return ruleRepository.findByDeletedYnOrderByMailRuleIdAsc(SecLogConstants.DELETED_N);
    }

    /** 받는 사람 칸은 비어 있어도 NOT NULL 이라 빈 글자로 저장 */
    private static String nb(String v) {
        return v == null ? "" : v;
    }

    /**
     * 주소 목록 검사 (형식 · 개수 · 회사 메일만)
     * @param required true = 비어 있으면 안 됨 (켜진 규칙의 받는 사람)
     */
    String addrs(String v, String label, boolean required) {
        List<String> list = new ArrayList<>();
        if (StringUtils.hasText(v)) {
            for (String p : v.split("[,;\\s]+")) {
                String t = p.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (!EMAIL.matcher(t).matches()) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, label + " 메일 주소 형식이 올바르지 않습니다: " + t);
                }
                if (!list.contains(t)) {
                    list.add(t);
                }
            }
        }
        if (required && list.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    label + "이(가) 없으면 규칙을 켤 수 없습니다. " + label + "을(를) 넣거나 규칙을 끈 뒤 저장해 주세요.");
        }
        List<String> domains = allowedDomains();
        if (!domains.isEmpty()) {
            for (String t : list) {
                if (!domains.contains(t.substring(t.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT))) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                            label + "에 회사 밖 주소는 넣을 수 없습니다: " + t + " (회사 메일 @" + String.join(", @", domains) + " 만 가능)");
                }
            }
        }
        if (list.size() > MAX_ADDRS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, label + "은(는) " + MAX_ADDRS + "개까지 입력할 수 있습니다.");
        }
        return list.isEmpty() ? null : String.join(", ", list);
    }

    // ================================================================== 대상

    /** 같은 사람(부서+이름) 중 최신 '신청중' 건 (종료일이 가장 늦은 건, 같으면 나중 등록) */
    private List<SecLogDlpKakaoApproval> latestApprovals() {
        Map<String, SecLogDlpKakaoApproval> latest = new HashMap<>();
        for (SecLogDlpKakaoApproval a : approvalService.active()) {
            latest.merge(SecLogDlpKakaoApprovalService.personKey(a.getDeptName(), a.getUserName()), a, (x, y) -> {
                int c = x.getEndDate().compareTo(y.getEndDate());
                return c > 0 || (c == 0 && x.getKakaoApprovalId() > y.getKakaoApprovalId()) ? x : y;
            });
        }
        return new ArrayList<>(latest.values());
    }

    /** 규칙 기준 오늘 보낼 차례인 건 (이미 보낸 건 포함 - 호출하는 쪽에서 거름) */
    private static List<SecLogDlpKakaoApproval> due(SecLogDlpMailRule r, List<SecLogDlpKakaoApproval> latest, LocalDate today) {
        return latest.stream()
                .filter(a -> {
                    LocalDate send = a.getEndDate().plusDays(r.getOffsetDays());
                    return !today.isBefore(send) && !send.isBefore(r.getStartDate());
                })
                .sorted((x, y) -> x.getEndDate().compareTo(y.getEndDate()) != 0 ? x.getEndDate().compareTo(y.getEndDate())
                        : x.getKakaoApprovalId().compareTo(y.getKakaoApprovalId()))
                .toList();
    }

    private Map<Long, SecLogDlpMailSendLog> logsByApproval(Long ruleId) {
        Map<Long, SecLogDlpMailSendLog> m = new HashMap<>();
        logRepository.findByMailRuleIdAndDeletedYn(ruleId, SecLogConstants.DELETED_N).forEach(l -> m.put(l.getKakaoApprovalId(), l));
        return m;
    }

    private boolean isSent(SecLogDlpMailSendLog l) {
        return l != null && SecLogDlpMailSendLog.SENT.equals(l.getSendStatus());
    }

    /** 사람마다 값이 다른 자리표시 (이 자리표시가 들어간 줄은 사람 수만큼 반복) */
    private static final List<String> PERSON_KEYS = List.of("{번호}", "{사업부}", "{부서}", "{이름}", "{신청시작일}", "{문서번호}", "{사유}");

    private static String nz(String v) {
        return StringUtils.hasText(v) ? v : "-";
    }

    private static Map<String, String> personValues(SecLogDlpKakaoApproval a, int no) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("{번호}", String.valueOf(no));
        v.put("{사업부}", nz(a.getDivisionName()));
        v.put("{부서}", nz(a.getDeptName()));
        v.put("{이름}", nz(a.getUserName()));
        v.put("{신청시작일}", String.valueOf(a.getStartDate()));
        v.put("{문서번호}", nz(a.getApprovalDocNo()));
        v.put("{사유}", nz(a.getReason()));
        return v;
    }

    private static String fillGroupKeys(String t, List<SecLogDlpKakaoApproval> group, int offset) {
        LocalDate end = group.get(0).getEndDate();
        return t.replace("{신청종료일}", String.valueOf(end))
                .replace("{보내는날}", String.valueOf(end.plusDays(offset)))
                .replace("{인원수}", String.valueOf(group.size()));
    }

    private static String fillMap(String t, Map<String, String> v) {
        String out = t;
        for (Map.Entry<String, String> e : v.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * 메일 본문 채우기 (신청 종료일이 같은 사람 묶음)
     * - '사람마다' 자리표시가 들어간 줄(이어진 줄은 한 덩어리)은 사람 수만큼 반복, 여러 줄 덩어리는 사람 사이에 빈 줄
     * - 나머지 줄은 한 번만 ({신청종료일} {보내는날} {인원수} 채움)
     */
    static String fillBody(String tpl, List<SecLogDlpKakaoApproval> group, int offset) {
        String[] lines = (tpl == null ? "" : tpl.replace("\r\n", "\n")).split("\n", -1);
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            if (PERSON_KEYS.stream().noneMatch(lines[i]::contains)) {
                out.add(fillGroupKeys(lines[i], group, offset));
                i++;
                continue;
            }
            int j = i;
            while (j < lines.length && PERSON_KEYS.stream().anyMatch(lines[j]::contains)) {
                j++;
            }
            List<String> block = Arrays.asList(lines).subList(i, j);
            for (int k = 0; k < group.size(); k++) {
                if (k > 0 && block.size() > 1) {
                    out.add("");
                }
                Map<String, String> v = personValues(group.get(k), k + 1);
                for (String b : block) {
                    out.add(fillMap(fillGroupKeys(b, group, offset), v));
                }
            }
            i = j;
        }
        return String.join("\n", out);
    }

    /** 메일 제목 채우기 - '사람마다' 자리표시는 첫 사람 값, {이름} 은 여러 명이면 '김철수 외 2명' */
    static String fillSubject(String tpl, List<SecLogDlpKakaoApproval> group, int offset) {
        Map<String, String> v = personValues(group.get(0), 1);
        if (group.size() > 1) {
            v.put("{이름}", v.get("{이름}") + " 외 " + (group.size() - 1) + "명");
        }
        return fillMap(fillGroupKeys(tpl == null ? "" : tpl, group, offset), v).replaceAll("[\\r\\n]+", " ");
    }

    // ================================================================== 실행

    /** 자동 실행 대상 규칙 ID (사용 중 + 오늘 보내는 시각이 지났고 그 시각 이후 실행 기록 없음) */
    @Transactional(readOnly = true)
    public List<Long> dueRuleIds() {
        LocalDateTime now = SecLogTime.now();
        List<Long> ids = new ArrayList<>();
        for (SecLogDlpMailRule r : activeRules()) {
            if (!"Y".equals(r.getUseYn())) {
                continue;
            }
            LocalDateTime at = now.toLocalDate().atTime(LocalTime.parse(r.getSendTime()));
            if (!now.isBefore(at) && (r.getLastRunAt() == null || r.getLastRunAt().isBefore(at))) {
                ids.add(r.getMailRuleId());
            }
        }
        return ids;
    }

    /**
     * 규칙 1개 실행 (자동 + 관리자 [지금 보내기])
     * @param manual true = 관리자가 누름 (사용 안 함이어도 실행)
     * @return 실행 결과 한 줄
     */
    @Transactional
    public String run(Long ruleId, boolean manual, Long userId) {
        if (!runLock.tryLock()) {
            return "이미 실행 중";
        }
        try {
            SecLogDlpMailRule r = rule(ruleId);
            if (!manual && !"Y".equals(r.getUseYn())) {
                return "사용 안 함 - 실행 안 함";
            }
            Map<Long, SecLogDlpMailSendLog> logs = logsByApproval(ruleId);
            List<SecLogDlpKakaoApproval> targets = due(r, latestApprovals(), SecLogTime.today()).stream()
                    .filter(a -> !isSent(logs.get(a.getKakaoApprovalId()))).toList();
            JavaMailSender sender = mailSender.getIfAvailable();
            String result;
            if (targets.isEmpty()) {
                result = "보낼 대상 없음";
            } else if (!StringUtils.hasText(r.getToAddrs())) {
                result = "받는 사람 없음 - " + targets.size() + "명 대기 (규칙 수정에서 받는 사람을 넣어야 보냄)";
            } else if (allowedDomains().isEmpty()) {
                result = "회사 도메인 미설정 - " + targets.size() + "명 대기 (서버 환경 변수 SECLOG_MAIL_ALLOWED_DOMAINS 설정 필요)";
            } else if (sender == null) {
                result = "메일 서버 미설정 - " + targets.size() + "명 대기 (서버 환경 변수 SPRING_MAIL_HOST 등 설정 필요)";
            } else {
                // 신청 종료일이 같은 사람 = 메일 1통
                Map<LocalDate, List<SecLogDlpKakaoApproval>> groups = new java.util.TreeMap<>();
                targets.forEach(a -> groups.computeIfAbsent(a.getEndDate(), k -> new ArrayList<>()).add(a));
                int okMail = 0;
                int okPeople = 0;
                int failMail = 0;
                int failPeople = 0;
                String lastErr = null;
                // 보낼 때 한 번 더: 회사 밖 주소가 하나라도 있으면 그 메일은 보내지 않음 (실패로 기록)
                List<String> outside = outsideAddrs(r.getToAddrs(), r.getCcAddrs());
                for (List<SecLogDlpKakaoApproval> group : groups.values()) {
                    group.sort(java.util.Comparator.comparing((SecLogDlpKakaoApproval a) -> nz(a.getDeptName()))
                            .thenComparing(a -> nz(a.getUserName())).thenComparing(SecLogDlpKakaoApproval::getKakaoApprovalId));
                    String subject = cut(fillSubject(r.getSubjectTpl(), group, r.getOffsetDays()), 500);
                    String body = cut(fillBody(r.getBodyTpl(), group, r.getOffsetDays()), 4000);
                    String err = outside.isEmpty() ? send(sender, r.getToAddrs(), r.getCcAddrs(), subject, body)
                            : cut("회사 밖 주소라 보내지 않음: " + String.join(", ", outside), 1000);
                    for (SecLogDlpKakaoApproval a : group) {
                        SecLogDlpMailSendLog l = logs.get(a.getKakaoApprovalId());
                        boolean isNew = l == null;
                        if (isNew) {
                            l = SecLogDlpMailSendLog.builder().mailRuleId(ruleId).kakaoApprovalId(a.getKakaoApprovalId()).createdBy(userId).build();
                        }
                        // 제목·본문·받는 사람을 채운 뒤 저장 (NOT NULL 칸), 같은 메일에 담긴 사람은 같은 제목·본문
                        l.recordTry(a.getDeptName(), a.getUserName(), a.getEndDate(), r.getToAddrs(), r.getCcAddrs(), subject, body, err, userId);
                        if (isNew) {
                            logRepository.save(l);
                        }
                    }
                    if (err == null) {
                        okMail++;
                        okPeople += group.size();
                    } else {
                        failMail++;
                        failPeople += group.size();
                        lastErr = err;
                    }
                }
                result = "보냄 " + okMail + "통 (" + okPeople + "명)"
                        + (failMail > 0 ? " · 실패 " + failMail + "통 (" + failPeople + "명, 다음 실행 때 다시 시도: " + cut(lastErr, 200) + ")" : "");
            }
            r.recordRun(cut((manual ? "[지금 보내기] " : "[자동] ") + result, 500));
            log.info("[security_log] 메일 알림 규칙 '{}' {}: {}", r.getRuleName(), manual ? "수동" : "자동", result);
            return r.getLastRunResult();
        } finally {
            runLock.unlock();
        }
    }

    private String send(JavaMailSender sender, String to, String cc, String subject, String body) {
        try {
            MimeMessage m = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, false, StandardCharsets.UTF_8.name());
            if (StringUtils.hasText(fromAddr)) {
                h.setFrom(new InternetAddress(fromAddr, "DLP 모니터링", StandardCharsets.UTF_8.name()));
            }
            h.setTo(split(to));
            if (StringUtils.hasText(cc)) {
                h.setCc(split(cc));
            }
            h.setSubject(subject);
            h.setText(body, false);
            sender.send(m);
            return null;
        } catch (Exception e) {
            log.warn("[security_log] 메일 알림 실패: {}", e.getMessage());
            Throwable c = e;
            while (c.getCause() != null && c.getCause() != c) {
                c = c.getCause();
            }
            return cut(e.getClass().getSimpleName() + ": " + (c.getMessage() == null ? e.getMessage() : c.getMessage()), 1000);
        }
    }

    private static String[] split(String v) {
        return Arrays.stream(v.split("\\s*,\\s*")).filter(StringUtils::hasText).toArray(String[]::new);
    }

    private static String cut(String v, int max) {
        return v == null ? "" : v.length() > max ? v.substring(0, max) : v;
    }
}
