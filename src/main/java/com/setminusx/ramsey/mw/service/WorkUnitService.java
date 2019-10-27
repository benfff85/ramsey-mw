package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.dto.WorkUnitDto;
import com.setminusx.ramsey.mw.entity.WorkUnit;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import com.setminusx.ramsey.mw.repository.WorkUnitRepo;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkUnitService {

    @Autowired
    private WorkUnitRepo workUnitRepo;

    @Value("${ramsey.work-unit.page-size.default}")
    private Integer defaultPageSize;

    public List<WorkUnitDto> insertWorkUnits(List<WorkUnitDto> workUnitDtos) {

        List<WorkUnit> workUnits = workUnitDtos.stream().map(WorkUnitService::mapDTOToWorkUnit).collect(Collectors.toList());
        Iterable<WorkUnit> createdWorkUnits = workUnitRepo.saveAll(workUnits);

        List<WorkUnitDto> createdWorkUnitDtos = new ArrayList<>();
        for (WorkUnit workUnit : createdWorkUnits) {
            createdWorkUnitDtos.add(mapWorkUnitToDTO(workUnit));
        }

        return createdWorkUnitDtos;
    }

    public List<WorkUnitDto> getAll(WorkUnitStatus status, Integer vertexCount, Integer subgraphSize, Integer pageSize) {
                Pageable pageable = PageRequest.of(0, ObjectUtils.defaultIfNull(pageSize, defaultPageSize));
        return workUnitRepo.findAllBySubgraphSizeAndVertexCountAndStatus(subgraphSize, vertexCount, status, pageable).stream().map(WorkUnitService::mapWorkUnitToDTO).collect(Collectors.toList());
    }

    public List<WorkUnitDto> assignWorkUnit(Integer vertexCount, Integer subgraphSize, String clientId, Integer pageSize) {
        Pageable pageable = PageRequest.of(0, ObjectUtils.defaultIfNull(pageSize, defaultPageSize));
        Date date = new Date();
        List<WorkUnit> workUnits = workUnitRepo.findAllBySubgraphSizeAndVertexCountAndStatus(subgraphSize, vertexCount, WorkUnitStatus.NEW, pageable);

        for (WorkUnit workUnit : workUnits) {
            workUnit.setAssignedClient(clientId);
            workUnit.setStatus(WorkUnitStatus.ASSIGNED);
            workUnit.setAssignedDate(date);
        }
        workUnitRepo.saveAll(workUnits);
        return workUnits.stream().map(WorkUnitService::mapWorkUnitToDTO).collect(Collectors.toList());

    }

    private static WorkUnit mapDTOToWorkUnit(WorkUnitDto workUnitDto) {
        WorkUnit workUnit = new WorkUnit();
        workUnit.setAssignedDate(workUnitDto.getAssignedDate());
        workUnit.setBaseGraphId(workUnitDto.getBaseGraphId());
        workUnit.setCliqueCount(workUnitDto.getCliqueCount());
        workUnit.setCompletedDate(workUnitDto.getCompletedDate());
        workUnit.setProcessingStartedDate(workUnitDto.getProcessingStartedDate());
        workUnit.setCreatedDate(workUnitDto.getCreatedDate());
        workUnit.setEdgesToFlip(workUnitDto.getEdgesToFlip());
        workUnit.setId(workUnitDto.getId());
        workUnit.setStatus(workUnitDto.getStatus());
        workUnit.setSubgraphSize(workUnitDto.getSubgraphSize());
        workUnit.setVertexCount(workUnitDto.getVertexCount());
        workUnit.setAssignedClient(workUnitDto.getAssignedClient());
        workUnit.setPriority(workUnitDto.getPriority());
        return workUnit;
    }

    private static WorkUnitDto mapWorkUnitToDTO(WorkUnit workUnit) {
        WorkUnitDto workUnitDto = new WorkUnitDto();
        workUnitDto.setAssignedDate(workUnit.getAssignedDate());
        workUnitDto.setBaseGraphId(workUnit.getBaseGraphId());
        workUnitDto.setCliqueCount(workUnit.getCliqueCount());
        workUnitDto.setCompletedDate(workUnit.getCompletedDate());
        workUnitDto.setProcessingStartedDate(workUnit.getProcessingStartedDate());
        workUnitDto.setCreatedDate(workUnit.getCreatedDate());
        workUnitDto.setEdgesToFlip(workUnit.getEdgesToFlip());
        workUnitDto.setId(workUnit.getId());
        workUnitDto.setStatus(workUnit.getStatus());
        workUnitDto.setSubgraphSize(workUnit.getSubgraphSize());
        workUnitDto.setVertexCount(workUnit.getVertexCount());
        workUnitDto.setAssignedClient(workUnit.getAssignedClient());
        workUnitDto.setPriority(workUnit.getPriority());
        return workUnitDto;
    }

}
