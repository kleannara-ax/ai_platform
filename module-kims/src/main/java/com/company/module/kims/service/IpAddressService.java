package com.company.module.kims.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.IpCreateRequest;
import com.company.module.kims.dto.request.IpModifyRequest;
import com.company.module.kims.dto.request.IpReclaimRequest;
import com.company.module.kims.dto.response.IpAddressDetailResponse;
import com.company.module.kims.dto.response.IpAddressResponse;
import com.company.module.kims.dto.response.IpGroupUtilResponse;
import com.company.module.kims.dto.response.IpHistoryResponse;
import com.company.module.kims.entity.IpAddress;
import com.company.module.kims.entity.IpHistory;
import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.entity.enums.IpChangeType;
import com.company.module.kims.entity.enums.IpSite;
import com.company.module.kims.entity.enums.IpStatus;
import com.company.module.kims.repository.IpAddressRepository;
import com.company.module.kims.repository.IpHistoryRepository;
import com.company.module.kims.repository.ServiceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * IP 관리 비즈니스 로직.
 * <p>신규 등록 / 정보·사용자 변경 / 회수 / 목록·상세 / 미품의 변경 조회 / 이력 / 엑셀을 담당한다.
 * 모든 변경은 {@link IpHistory} 에 자동 기록된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IpAddressService {

    private final IpAddressRepository ipAddressRepository;
    private final IpHistoryRepository ipHistoryRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ExcelExportService excelExportService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // ================================================================
    // 신규 IP 등록 (이력: 신규생성)
    // ================================================================
    @Transactional
    public IpAddressResponse create(IpCreateRequest req) {
        if (ipAddressRepository.existsByIpAddress(req.getIpAddress())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "이미 등록된 IP 주소입니다. ip=" + req.getIpAddress());
        }

        boolean assigned = req.getUserName() != null && !req.getUserName().isBlank();
        IpAddress ip = IpAddress.builder()
                .ipAddress(req.getIpAddress())
                .status(assigned ? IpStatus.IN_USE : IpStatus.AVAILABLE)
                .userName(req.getUserName())
                .department(req.getDepartment())
                .location(req.getLocation())
                .device(req.getDevice())
                .approved(req.isApproved())
                .approvalNo(req.getApprovalNo())
                .remark(req.getRemark())
                .noteDate(req.getNoteDate())
                .site(parseSite(req.getSite()))
                .build();
        ip.updateSpec(req.getModel(), req.getSerialNo(), req.getVendor(),
                req.getOsVersion(), req.getOsSerial(), req.getOfficeVersion(), req.getOfficeSerial(),
                req.getHangulVersion(), req.getHangulSerial(), req.getRentalCompany(), req.getPcAssetNo(), req.getMonitorAssetNo());
        IpAddress saved = ipAddressRepository.save(ip);

        ipHistoryRepository.save(IpHistory.of(saved, findRequestOrNull(req.getRequestId()),
                IpChangeType.CREATED, "IP 신규 등록", req.isApproved(), req.getApprovalNo(), req.getChangedBy()));

        return IpAddressResponse.from(saved);
    }

    // ================================================================
    // 정보/사용자 변경 (이력: 정보변경 또는 사용자변경)
    // ================================================================
    @Transactional
    public IpAddressResponse modify(Long ipId, IpModifyRequest req) {
        IpAddress ip = findIp(ipId);

        // 사용자/부서가 바뀌면 USER_CHANGED, 그 외는 MODIFIED 로 분류
        boolean userChanged = !equalsNullable(ip.getUserName(), req.getUserName())
                || !equalsNullable(ip.getDepartment(), req.getDepartment());
        IpChangeType type = userChanged ? IpChangeType.USER_CHANGED : IpChangeType.MODIFIED;

        // 변경 전 사용자(스냅샷) 확보 후 갱신
        String beforeUser = ip.getUserName();
        ip.update(req.getUserName(), req.getDepartment(), req.getLocation(), req.getDevice(),
                req.isApproved(), req.getApprovalNo(), req.getRemark(), req.getNoteDate());
        ip.updateSpec(req.getModel(), req.getSerialNo(), req.getVendor(),
                req.getOsVersion(), req.getOsSerial(), req.getOfficeVersion(), req.getOfficeSerial(),
                req.getHangulVersion(), req.getHangulSerial(), req.getRentalCompany(), req.getPcAssetNo(), req.getMonitorAssetNo());
        String afterUser = ip.getUserName();

        String content = (req.getReason() != null && !req.getReason().isBlank())
                ? req.getReason() : type.getLabel();
        ipHistoryRepository.save(IpHistory.builder()
                .ipAddress(ip)
                .serviceRequest(findRequestOrNull(req.getRequestId()))
                .changeType(type)
                .content(content)
                .approved(req.isApproved())
                .approvalNo(req.getApprovalNo())
                .changedBy(req.getChangedBy())
                .beforeUser(beforeUser)
                .afterUser(afterUser)
                .build());

        return IpAddressResponse.from(ip);
    }

    // ================================================================
    // 회수 처리 (이력: 회수)
    // ================================================================
    @Transactional
    public IpAddressResponse reclaim(Long ipId, IpReclaimRequest req) {
        IpAddress ip = findIp(ipId);
        ip.reclaim(req.getReason());

        String content = (req.getReason() != null && !req.getReason().isBlank())
                ? req.getReason() : "IP 회수";
        // 회수 이력은 품의 여부와 무관하게 기록 (approved=true 로 두어 미품의 알림에 잡히지 않게 함)
        ipHistoryRepository.save(IpHistory.of(ip, findRequestOrNull(req.getRequestId()),
                IpChangeType.RECLAIMED, content, true, null, req.getChangedBy()));

        return IpAddressResponse.from(ip);
    }

    // ================================================================
    // 반납 처리 (PC+IP 반납, 이력: 반납) — PC 정보까지 비운다
    // ================================================================
    @Transactional
    public IpAddressResponse returnPc(Long ipId, IpReclaimRequest req) {
        IpAddress ip = findIp(ipId);
        String beforeUser = ip.getUserName();
        ip.returnPc(req.getReason());

        String content = (req.getReason() != null && !req.getReason().isBlank())
                ? req.getReason() : "PC+IP 반납";
        ipHistoryRepository.save(IpHistory.builder()
                .ipAddress(ip)
                .serviceRequest(findRequestOrNull(req.getRequestId()))
                .changeType(IpChangeType.RETURNED)
                .content(content)
                .approved(true)
                .changedBy(req.getChangedBy())
                .beforeUser(beforeUser)
                .afterUser(null)
                .build());
        return IpAddressResponse.from(ip);
    }

    // ================================================================
    // IP 변경(이동) — 같은 PC 를 다른 IP 로 옮긴다 (정보변경/IP 이동 이력)
    // ================================================================
    @Transactional
    public IpAddressResponse moveIp(Long ipId, com.company.module.kims.dto.request.IpMoveRequest req) {
        IpAddress src = findIp(ipId);
        String newIp = req.getNewIpAddress().trim();
        if (src.getIpAddress().equals(newIp)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "현재 IP와 동일합니다. ip=" + newIp);
        }
        IpAddress dst = ipAddressRepository.findByIpAddress(newIp)
                .orElseThrow(() -> new EntityNotFoundException("대상 IP가 관리대장에 없습니다. ip=" + newIp));
        if (dst.getStatus() == IpStatus.IN_USE) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "대상 IP가 이미 사용중입니다. ip=" + newIp);
        }

        String movedUser = src.getUserName();
        String oldIp = src.getIpAddress();

        // PC 데이터를 대상 IP 로 복사
        dst.update(src.getUserName(), src.getDepartment(), src.getLocation(), src.getDevice(),
                src.isApproved(), src.getApprovalNo(), src.getRemark(), src.getNoteDate());
        dst.updateSpec(src.getModel(), src.getSerialNo(), src.getVendor(),
                src.getOsVersion(), src.getOsSerial(), src.getOfficeVersion(), src.getOfficeSerial(),
                src.getHangulVersion(), src.getHangulSerial(), src.getRentalCompany(),
                src.getPcAssetNo(), src.getMonitorAssetNo());
        // 원래 슬롯 비우기
        src.vacate();

        String content = (req.getReason() != null && !req.getReason().isBlank())
                ? req.getReason() : "IP 변경 (" + oldIp + " → " + newIp + ")";
        // 이력은 이동 후 IP(dst)에 부착 — 변경 전/후 IP + 동일 사용자
        ipHistoryRepository.save(IpHistory.builder()
                .ipAddress(dst)
                .serviceRequest(findRequestOrNull(req.getRequestId()))
                .changeType(IpChangeType.MODIFIED)
                .content(content)
                .approved(true)
                .changedBy(req.getChangedBy())
                .beforeIp(oldIp)
                .afterIp(newIp)
                .beforeUser(movedUser)
                .afterUser(movedUser)
                .build());
        return IpAddressResponse.from(dst);
    }

    // ================================================================
    // 업무요청 완료 시 자동 반영 / 취소 시 원복
    // ================================================================

    /** 대역(그룹) 추출: 192.1.20.166 → 192.1.20 */
    private static String groupOf(String ip) {
        int d = ip.lastIndexOf('.');
        return (d > 0) ? ip.substring(0, d) : ip;
    }

    /** 신규 IP 행 생성(가용 상태) */
    private IpAddress createEmptyIp(String ip) {
        IpAddress row = IpAddress.builder()
                .ipAddress(ip).status(IpStatus.AVAILABLE).approved(false).build();
        row.assignGroup(groupOf(ip));
        return ipAddressRepository.save(row);
    }

    private String toJson(List<com.company.module.kims.entity.IpRowSnapshot> rows, List<Long> createdIds) {
        try {
            java.util.Map<String, Object> wrap = new java.util.HashMap<>();
            wrap.put("rows", rows);
            wrap.put("createdIpIds", createdIds);
            return objectMapper.writeValueAsString(wrap);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "스냅샷 직렬화 실패");
        }
    }

    /**
     * [IP변경] 변경자의 현재 PC(srcIpId)를 newIp 로 이동. 기존 IP 는 회수, 대상 IP 는 점유중이면 교체(프론트 확인 후 호출).
     * @return 원복용 스냅샷 JSON
     */
    @Transactional
    public String applyIpMove(Long srcIpId, String newIp, String changedBy, Long requestId) {
        newIp = newIp.trim();
        IpAddress src = findIp(srcIpId);
        String oldIp = src.getIpAddress();
        if (newIp.equals(oldIp)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "현재 IP와 동일합니다.");
        }
        String movedUser = src.getUserName();
        List<com.company.module.kims.entity.IpRowSnapshot> snaps = new ArrayList<>();
        List<Long> created = new ArrayList<>();
        snaps.add(src.toSnapshot());

        IpAddress dst = ipAddressRepository.findByIpAddress(newIp).orElse(null);
        if (dst == null) { dst = createEmptyIp(newIp); created.add(dst.getIpId()); }
        else { snaps.add(dst.toSnapshot()); }

        // 대상에 PC 데이터 복사 후 원 슬롯 회수
        dst.update(src.getUserName(), src.getDepartment(), src.getLocation(), src.getDevice(),
                src.isApproved(), src.getApprovalNo(), src.getRemark(), src.getNoteDate());
        dst.updateSpec(src.getModel(), src.getSerialNo(), src.getVendor(),
                src.getOsVersion(), src.getOsSerial(), src.getOfficeVersion(), src.getOfficeSerial(),
                src.getHangulVersion(), src.getHangulSerial(), src.getRentalCompany(),
                src.getPcAssetNo(), src.getMonitorAssetNo());
        src.vacate();

        ipHistoryRepository.save(IpHistory.builder()
                .ipAddress(dst).serviceRequest(findRequestOrNull(requestId))
                .changeType(IpChangeType.MODIFIED)
                .content("업무요청 IP변경: " + oldIp + " → " + newIp + " (기존 IP 회수)")
                .approved(true).changedBy(changedBy)
                .beforeIp(oldIp).afterIp(newIp).beforeUser(movedUser).afterUser(movedUser).build());
        return toJson(snaps, created);
    }

    /**
     * [IP신규생성] 생성자에게 newIp 부여. 대상이 점유중이면 사용자 교체(프론트 확인 후 호출).
     * @return 원복용 스냅샷 JSON
     */
    @Transactional
    public String applyIpNew(String creatorName, String newIp, String department, String changedBy, Long requestId) {
        newIp = newIp.trim();
        List<com.company.module.kims.entity.IpRowSnapshot> snaps = new ArrayList<>();
        List<Long> created = new ArrayList<>();
        IpAddress dst = ipAddressRepository.findByIpAddress(newIp).orElse(null);
        boolean isNew = (dst == null);
        String priorUser = isNew ? null : dst.getUserName();
        if (isNew) { dst = createEmptyIp(newIp); created.add(dst.getIpId()); }
        else { snaps.add(dst.toSnapshot()); }

        dst.assignTo(creatorName, department, "업무요청 IP 신규 부여");
        ipHistoryRepository.save(IpHistory.builder()
                .ipAddress(dst).serviceRequest(findRequestOrNull(requestId))
                .changeType(isNew ? IpChangeType.CREATED : IpChangeType.USER_CHANGED)
                .content("업무요청 IP신규생성: " + newIp + " → " + creatorName
                        + (priorUser != null ? " (기존 '" + priorUser + "' 교체)" : ""))
                .approved(true).changedBy(changedBy)
                .afterIp(newIp).beforeUser(priorUser).afterUser(creatorName).build());
        return toJson(snaps, created);
    }

    /**
     * [PC변경] 대상 PC(ipId)의 선택 항목만 부분 수정.
     * @return 원복용 스냅샷 JSON
     */
    @Transactional
    public String applyPcChange(Long ipId, java.util.Map<String, String> fields, String changedBy, Long requestId) {
        IpAddress ip = findIp(ipId);
        List<com.company.module.kims.entity.IpRowSnapshot> snaps = new ArrayList<>();
        snaps.add(ip.toSnapshot());
        String beforeUser = ip.getUserName();
        ip.applyPcFields(fields);
        String changed = String.join(", ", fields == null ? java.util.Set.of() : fields.keySet());
        ipHistoryRepository.save(IpHistory.builder()
                .ipAddress(ip).serviceRequest(findRequestOrNull(requestId))
                .changeType(IpChangeType.MODIFIED)
                .content("업무요청 PC변경: " + changed)
                .approved(true).changedBy(changedBy)
                .beforeUser(beforeUser).afterUser(ip.getUserName()).build());
        return toJson(snaps, java.util.List.of());
    }

    /** 완료된 요청의 자동 반영을 취소 시 원복 */
    @Transactional
    public void revertApplied(String snapshotJson, String changedBy) {
        if (snapshotJson == null || snapshotJson.isBlank()) return;
        try {
            var node = objectMapper.readTree(snapshotJson);
            // 신규 생성된 행 삭제 (이력 포함)
            for (var idNode : node.path("createdIpIds")) {
                Long id = idNode.asLong();
                ipHistoryRepository.deleteAll(ipHistoryRepository.findByIpAddress_IpIdOrderByCreatedAtDesc(id));
                ipAddressRepository.findById(id).ifPresent(ipAddressRepository::delete);
            }
            // 기존 행 원복
            for (var rowNode : node.path("rows")) {
                var snap = objectMapper.treeToValue(rowNode, com.company.module.kims.entity.IpRowSnapshot.class);
                ipAddressRepository.findById(snap.ipId()).ifPresent(ip -> {
                    ip.restore(snap);
                    ipHistoryRepository.save(IpHistory.of(ip, null, IpChangeType.MODIFIED,
                            "업무요청 취소로 원복", true, null, changedBy));
                });
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "변경 원복 실패: " + e.getMessage());
        }
    }

    // ================================================================
    // 목록 / 상세 / 이력 / 미품의 변경
    // ================================================================
    /** 등록된 IP 그룹 목록 (사업장별. 미지정 시 청주공장 — 기존 기능 유지) */
    public java.util.List<String> getGroups(String site) {
        return ipAddressRepository.findDistinctGroupsBySite(parseSite(site));
    }

    public PageResponse<IpAddressResponse> getList(String keyword, String searchField, IpStatus status,
                                                  String department, String ipGroup, String excludeGroup,
                                                  String site, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<IpAddressResponse> result = ipAddressRepository
                .search(emptyToNull(keyword), emptyToNull(searchField), status, emptyToNull(department),
                        emptyToNull(ipGroup), emptyToNull(excludeGroup), parseSite(site), pageable)
                .map(IpAddressResponse::from);
        return PageResponse.of(result);
    }

    public IpAddressDetailResponse getDetail(Long ipId) {
        IpAddress ip = findIp(ipId);
        List<IpHistoryResponse> histories = ipHistoryRepository
                .findByIpAddress_IpIdOrderByCreatedAtDesc(ipId)
                .stream().map(IpHistoryResponse::from).toList();
        return IpAddressDetailResponse.of(ip, histories);
    }

    public List<IpHistoryResponse> getHistory(Long ipId) {
        findIp(ipId);
        return ipHistoryRepository.findByIpAddress_IpIdOrderByCreatedAtDesc(ipId)
                .stream().map(IpHistoryResponse::from).toList();
    }

    /** 미품의 IP 변경 내역 (사업장별. PC 관리 화면의 '미품의 변경 내역' 버튼용) */
    public List<IpHistoryResponse> getUnapprovedChanges(String site) {
        return ipHistoryRepository.findByApprovedFalseAndIpAddress_SiteOrderByCreatedAtDesc(parseSite(site))
                .stream().map(IpHistoryResponse::from).toList();
    }

    /**
     * 이력 검색 (제조번호별/IP별) — 해당 장비/주소의 <b>사용자 인수인계 흐름</b>만 남긴다.
     * <p>사용자(afterUser)가 바뀐 시점과 신규생성만 남기고, 같은 사용자의 필드 변경(부서/IP 등)은 접는다.
     */
    public List<IpHistoryResponse> searchHistory(String field, String keyword, String site) {
        String f = emptyToNull(field);
        String kw = emptyToNull(keyword);
        if (f == null || kw == null) {
            return java.util.List.of();
        }
        List<IpHistory> raw = ipHistoryRepository
                .searchHistory(f, kw, parseSite(site), org.springframework.data.domain.PageRequest.of(0, 2000));
        // 장비(IP_ID)별로 묶어 시간순으로 훑고, 사용자가 바뀌는 시점만 남긴다
        // IP별: 당시 IP(SNAPSHOT_IP)가 검색 IP인 이력만 남긴다 ('그 IP'의 사용 이력)
        if ("ip".equals(f)) {
            final String q = kw;
            raw = raw.stream().filter(h -> histIp(h) != null && histIp(h).contains(q)).toList();
        }
        // 그룹키: IP별=당시 IP, 제조번호별=장비(IP_ID). 그룹 안에서 사용자 바뀐 시점만 남긴다
        java.util.Map<String, List<IpHistory>> byKey = new java.util.LinkedHashMap<>();
        for (IpHistory h : raw) {
            String key = "ip".equals(f) ? histIp(h) : ("D" + h.getIpAddress().getIpId());
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(h);
        }
        // 그룹 안에서 '뭐가 바뀐 시점'만 남긴다: 신규생성 · 사용자 변경 · 당시 IP 변경
        // (제조번호별=장비 이동/인수인계 흐름, IP별=IP 그룹이라 IP는 고정→사용자 변경만 남음)
        List<IpHistory> kept = new ArrayList<>();
        for (List<IpHistory> hist : byKey.values()) {
            hist.sort(java.util.Comparator.comparing(IpHistory::getCreatedAt));
            String prevUser = " ";
            String prevIp = " ";
            for (IpHistory h : hist) {
                boolean created = h.getChangeType() == IpChangeType.CREATED;
                String hip = histIp(h);
                if (created
                        || !java.util.Objects.equals(h.getAfterUser(), prevUser)
                        || !java.util.Objects.equals(hip, prevIp)) {
                    kept.add(h);
                    prevUser = h.getAfterUser();
                    prevIp = hip;
                }
            }
        }
        kept.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));   // 표는 최신순
        return kept.stream().map(IpHistoryResponse::from).toList();
    }

    /** 이력 시점의 IP (SNAPSHOT_IP 우선, 없으면 현재 장비 IP) */
    private static String histIp(IpHistory h) {
        String s = h.getSnapshotIp();
        return (s != null && !s.isBlank()) ? s : h.getIpAddress().getIpAddress();
    }

    /**
     * 사용자별 사용 이력 — 그 사용자가 관여한 장비에서 IP나 제조번호가 바뀐 시점과
     * 신규생성만 남기고, 각 시점의 (변경 전 → 후) 사용자를 함께 보여준다.
     * 당시 IP(SNAPSHOT_IP)로 표시해 관리대장 원본과 매칭된다.
     */
    public List<IpHistoryResponse> searchUserUsage(String keyword, String site) {
        String kw = emptyToNull(keyword);
        if (kw == null) {
            return java.util.List.of();
        }
        List<Long> deviceIds = ipHistoryRepository.findDistinctDeviceIdsByUser(kw, parseSite(site));
        List<IpHistory> collected = new ArrayList<>();
        for (Long ipId : deviceIds) {
            List<IpHistory> hist = ipHistoryRepository.findByIpAddress_IpIdOrderByCreatedAtAsc(ipId);
            String prevIp = null, prevSerial = null, prevUser = null;
            boolean first = true;
            for (IpHistory h : hist) {
                String ip = histIp(h);
                String serial = h.getIpAddress().getSerialNo();
                String after = h.getAfterUser();
                boolean created = h.getChangeType() == IpChangeType.CREATED;
                boolean changed = first || created
                        || !java.util.Objects.equals(ip, prevIp)
                        || !java.util.Objects.equals(serial, prevSerial)
                        || !java.util.Objects.equals(after, prevUser);
                boolean involved = (h.getBeforeUser() != null && h.getBeforeUser().contains(kw))
                        || (after != null && after.contains(kw));
                if (changed && involved) {
                    collected.add(h);
                }
                prevIp = ip; prevSerial = serial; prevUser = after; first = false;
            }
        }
        collected.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return collected.stream().map(IpHistoryResponse::from).toList();
    }

    // ================================================================
    // 대시보드: 대역 사용현황 / 월별 변경내역
    // ================================================================

    /** 사용자 대역 (기존 4개, 각 .1~.254) */
    private static final List<String> USER_GROUPS = List.of("192.1.0", "192.1.1", "192.1.20", "192.1.21");
    /** 설비 대역 (CCTV / PDA·무선AP 등 장비 / 서버) */
    private static final List<String> FACILITY_GROUPS = List.of("100.1.1", "192.1.100", "210.107.102");
    private static final int IPS_PER_GROUP = 254;

    /**
     * 대역별 사용중/미사용 현황 (사용자 대역 + 설비 대역, 각 type 표기).
     * <p>현재 등록된 대역(USER_GROUPS/FACILITY_GROUPS)은 청주공장 전용으로 확인되어,
     * 청주공장(CHEONGJU) 조회 시에만 기존과 동일하게 집계한다. 서울은 아직 대역 정보가
     * 정의되지 않았으므로 빈 목록을 반환한다(향후 서울 대역이 확정되면 이 메서드를 확장한다).
     */
    public List<IpGroupUtilResponse> getUtilization(String site) {
        if (parseSite(site) != IpSite.CHEONGJU) {
            return List.of();
        }
        List<IpGroupUtilResponse> result = new ArrayList<>();
        addUtil(result, USER_GROUPS, "USER");
        addUtil(result, FACILITY_GROUPS, "FACILITY");
        return result;
    }

    private void addUtil(List<IpGroupUtilResponse> result, List<String> groups, String type) {
        for (String g : groups) {
            String prefix = g + ".";
            int used = (int) ipAddressRepository.countByStatusAndIpAddressStartingWith(IpStatus.IN_USE, prefix);
            int registered = (int) ipAddressRepository.countByIpAddressStartingWith(prefix);
            int available = Math.max(0, IPS_PER_GROUP - used);
            result.add(IpGroupUtilResponse.builder()
                    .group(g).type(type).total(IPS_PER_GROUP).used(used).available(available).registered(registered)
                    .build());
        }
    }

    /** 특정 연·월의 IP 변경 내역 (당월/월별 조회용, 사업장별) */
    public List<IpHistoryResponse> getMonthlyHistory(int year, int month, String site) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDateTime from = first.atStartOfDay();
        LocalDateTime to = first.withDayOfMonth(first.lengthOfMonth()).atTime(23, 59, 59);
        return ipHistoryRepository.findByCreatedAtBetweenAndIpAddress_SiteOrderByCreatedAtDesc(from, to, parseSite(site))
                .stream().map(IpHistoryResponse::from).toList();
    }

    /** 변경 이력이 있는 월 목록 ("yyyy-MM", 최신순, 사업장별). 당월이 없으면 맨 앞에 추가. */
    public List<String> getHistoryMonths(String site) {
        List<String> months = new ArrayList<>();
        for (Object[] row : ipHistoryRepository.findDistinctYearMonthsBySite(parseSite(site))) {
            months.add(String.format("%04d-%02d", ((Number) row[0]).intValue(), ((Number) row[1]).intValue()));
        }
        String cur = String.format("%04d-%02d", LocalDate.now().getYear(), LocalDate.now().getMonthValue());
        if (!months.contains(cur)) {
            months.add(0, cur);
        }
        return months;
    }

    // ================================================================
    // Excel 다운로드 (IP 목록)
    // ================================================================
    public byte[] exportExcel(String keyword, String searchField, IpStatus status, String department,
                               String ipGroup, String site) {
        List<IpAddress> list = ipAddressRepository
                .search(emptyToNull(keyword), emptyToNull(searchField), status, emptyToNull(department),
                        emptyToNull(ipGroup), null, parseSite(site), Pageable.unpaged())
                .getContent();
        return excelExportService.buildIpListExcel(list);
    }

    // ----------------------------------------------------------------
    // 내부 공통
    // ----------------------------------------------------------------

    private IpAddress findIp(Long ipId) {
        return ipAddressRepository.findById(ipId)
                .orElseThrow(() -> new EntityNotFoundException("IP 를 찾을 수 없습니다. id=" + ipId));
    }

    private ServiceRequest findRequestOrNull(Long requestId) {
        if (requestId == null) {
            return null;
        }
        return serviceRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("연결할 업무 요청을 찾을 수 없습니다. id=" + requestId));
    }

    private boolean equalsNullable(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    private String emptyToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    /**
     * 사업장 문자열 파싱. 값이 없거나 알 수 없는 값이면 청주공장(CHEONGJU)으로 취급한다.
     * <p>기존(리팩터링 이전) 데이터/호출부는 사업장 개념이 없었으므로, 이 기본값 처리로
     * 청주 PC 관리 기능이 신규 서울 탭 도입 이전과 완전히 동일하게 동작하도록 보장한다.
     */
    private IpSite parseSite(String site) {
        if (site == null || site.isBlank()) {
            return IpSite.CHEONGJU;
        }
        try {
            return IpSite.valueOf(site.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return IpSite.CHEONGJU;
        }
    }
}
