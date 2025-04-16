package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.WorkUnit;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import com.setminusx.ramsey.mw.repository.WorkUnitRepo;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkUnitService {

    private final WorkUnitRepo workUnitRepo;
    @Value("${ramsey.work-unit.page-size.default}")
    private Integer defaultPageSize;


    public WorkUnitService(WorkUnitRepo workUnitRepo) {
        this.workUnitRepo = workUnitRepo;
    }


    public List<WorkUnit> getWorkUnits(WorkUnitStatus status, Integer stageId, String assignedClientId, Integer pageSize) {
        return workUnitRepo.findByStatusAndStageIdAndAssignedClient(status, stageId, assignedClientId, getPageable(pageSize));
    }

    public WorkUnit getWorkUnitById(Integer id) {
        return workUnitRepo.findById(id).orElse(null);
    }

    public List<WorkUnit> createOrUpdateWorkUnits(List<WorkUnit> workUnits) {
        return workUnitRepo.saveAll(workUnits);
    }

    public void deleteWorkUnit(Integer id) {
        workUnitRepo.deleteById(id);
    }

    public Long getWorkUnitCountByStageIdAndStatus(Integer stageId, List<WorkUnitStatus> workUnitStatusList) {
        return workUnitRepo.countWorkUnitsByStageIdAndStatus(stageId, workUnitStatusList);
    }

    private Pageable getPageable(Integer pageSize) {
        return PageRequest.of(0, ObjectUtils.defaultIfNull(pageSize, defaultPageSize));
    }

}
