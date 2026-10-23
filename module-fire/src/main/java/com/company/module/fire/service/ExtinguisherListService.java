package com.company.module.fire.service;

import com.company.module.fire.dto.ExtinguisherResponse;
import com.company.module.fire.entity.Extinguisher;
import com.company.module.fire.entity.ExtinguisherInspection;
import com.company.module.fire.repository.ExtinguisherRepository;
import com.company.module.fire.repository.ExtinguisherInspectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExtinguisherListService {
    private final ExtinguisherRepository repository;
    private final ExtinguisherInspectionRepository inspections;

    public ExtinguisherListQuery.Result query(List<Long> buildingIds, Long floorId, String q,
            String type, String status, String sort, String direction, int page, int size, boolean exportAll) {
        List<Long> ids = buildingIds == null ? null : buildingIds.stream()
                .filter(id -> id != null && id > 0).distinct().toList();
        if (ids != null && ids.isEmpty()) ids = null;
        List<Extinguisher> entities = repository.searchForListQuery(ids == null,
                ids == null ? List.of(-1L) : ids,
                floorId != null && floorId > 0 ? floorId : null,
                q == null || q.isBlank() ? null : q.trim());
        Map<Long, ExtinguisherInspection> latest = entities.isEmpty() ? Map.of() :
                inspections.findLatestForIds(entities.stream().map(Extinguisher::getExtinguisherId).toList())
                .stream().collect(Collectors.toMap(i -> i.getExtinguisher().getExtinguisherId(), i -> i));
        List<ExtinguisherResponse> rows = entities.stream().map(e -> {
            ExtinguisherResponse row = ExtinguisherResponse.from(e);
            row.setLastInspection(latest.get(e.getExtinguisherId()));
            return row;
        }).toList();
        return ExtinguisherListQuery.evaluate(rows, repository.findDistinctTypes(), type, status,
                sort, direction, page, size, exportAll, LocalDate.now(ZoneId.of("Asia/Seoul")));
    }
}
