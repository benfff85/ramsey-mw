package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.WorkUnit;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkUnitRepo extends JpaRepository<WorkUnit, Integer> {

    @Query("SELECT w FROM WorkUnit w WHERE " +
            "(:status IS NULL OR w.status = :status) AND " +
            "(:stageId IS NULL OR w.stageId = :stageId) AND " +
            "(:assignedClient IS NULL OR w.assignedClient = :assignedClient)")
    List<WorkUnit> findByStatusAndStageIdAndAssignedClient(
            @Param("status") WorkUnitStatus status,
            @Param("stageId") Integer stageId,
            @Param("assignedClient") String assignedClient, Pageable pageable);

    @Query("SELECT COUNT(w) FROM WorkUnit w WHERE " +
            "w.stageId = :stageId AND " +
            "w.status IN :workUnitStatusList")
    long countWorkUnitsByStageIdAndStatus(
            @Param("stageId") Integer stageId,
            @Param("workUnitStatusList") List<WorkUnitStatus> workUnitStatusList);

    @Query("SELECT COUNT(w) FROM WorkUnit w WHERE " +
            "w.assignedClient = :clientId AND " +
            "w.status IN :workUnitStatusList")
    long countWorkUnitsByClientIdAndStatus(
            @Param("clientId") String clientId,
            @Param("workUnitStatusList") List<WorkUnitStatus> workUnitStatusList);

}