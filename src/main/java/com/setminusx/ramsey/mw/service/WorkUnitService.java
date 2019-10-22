package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.dto.WorkUnitDto;
import com.setminusx.ramsey.mw.entity.WorkUnit;
import com.setminusx.ramsey.mw.repository.WorkUnitRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorkUnitService {

    @Autowired
    private WorkUnitRepo workUnitRepo;

    public void insertWorkUnit(WorkUnitDto workUnitDto) {
        workUnitRepo.save(mapDTOToWorkUnit(workUnitDto));
    }

    private WorkUnit mapDTOToWorkUnit(WorkUnitDto workUnitDto) {
        WorkUnit workUnit = new WorkUnit();
        workUnit.setAssignedDate(workUnitDto.getAssignedDate());
        workUnit.setBaseGraphId(workUnitDto.getBaseGraphId());
        workUnit.setCliqueCount(workUnitDto.getCliqueCount());
        workUnit.setCompletedDate(workUnitDto.getCompletedDate());
        workUnit.setCreatedDate(workUnitDto.getCreatedDate());
        workUnit.setEdgesToFlip(workUnitDto.getEdgesToFlip());
        workUnit.setId(workUnitDto.getId());
        workUnit.setStatus(workUnitDto.getStatus());
        workUnit.setSubgraphSize(workUnitDto.getSubgraphSize());
        workUnit.setVertexCount(workUnitDto.getVertexCount());
        workUnit.setAssignedClient(workUnitDto.getAssignedClient());
        return workUnit;
    }


}
