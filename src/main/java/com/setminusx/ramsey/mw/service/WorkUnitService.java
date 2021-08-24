package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.dto.WorkUnitDto;
import com.setminusx.ramsey.mw.entity.WorkUnit;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import com.setminusx.ramsey.mw.repository.WorkUnitRepo;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WorkUnitService {

    @Value("${ramsey.work-unit.page-size.default}")
    private Integer defaultPageSize;

    private final WorkUnitRepo workUnitRepo;


    public WorkUnitService(WorkUnitRepo workUnitRepo) {
        this.workUnitRepo = workUnitRepo;
    }

    public List<WorkUnitDto> saveAll(List<WorkUnitDto> workUnitDtos) {

        List<WorkUnit> workUnits = workUnitDtos.stream().map(WorkUnitService::mapDTOToWorkUnit).toList();
        Iterable<WorkUnit> createdWorkUnits = workUnitRepo.saveAll(workUnits);

        List<WorkUnitDto> createdWorkUnitDtos = new ArrayList<>();
        for (WorkUnit workUnit : createdWorkUnits) {
            createdWorkUnitDtos.add(mapWorkUnitToDTO(workUnit));
        }

        return createdWorkUnitDtos;
    }

    public List<WorkUnitDto> getAll(WorkUnitStatus status, Integer vertexCount, Integer subgraphSize, Integer pageSize) {
        return workUnitRepo.findAllBySubgraphSizeAndVertexCountAndStatus(subgraphSize, vertexCount, status, getPageable(pageSize)).stream().map(WorkUnitService::mapWorkUnitToDTO).toList();
    }

    public List<WorkUnitDto> getAllAssignedToClient(WorkUnitStatus status, Integer vertexCount, Integer subgraphSize, String assignedClientId, Integer pageSize) {
        return workUnitRepo.findAllBySubgraphSizeAndVertexCountAndStatusAndAssignedClient(subgraphSize, vertexCount, status, assignedClientId, getPageable(pageSize)).stream().map(WorkUnitService::mapWorkUnitToDTO).toList();
    }

    public List<WorkUnitDto> getMostRecentForGraphId(Integer graphId) {
        return workUnitRepo.findAllByBaseGraphIdOrderByIdDesc(graphId, getPageable(1)).stream().map(WorkUnitService::mapWorkUnitToDTO).toList();
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

    private Pageable getPageable(Integer pageSize) {
        return PageRequest.of(0, ObjectUtils.defaultIfNull(pageSize, defaultPageSize));
    }

}
