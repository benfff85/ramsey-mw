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
            "(:subgraphSize IS NULL OR w.subgraphSize = :subgraphSize) AND " +
            "(:vertexCount IS NULL OR w.vertexCount = :vertexCount) AND " +
            "(:status IS NULL OR w.status = :status) AND " +
            "(:stageId IS NULL OR w.stageId = :stageId) AND " +
            "(:assignedClient IS NULL OR w.assignedClient = :assignedClient)")
    List<WorkUnit> findBySubgraphSizeAndVertexCountAndStatusAndStageIdAndAssignedClient(
            @Param("subgraphSize") Integer subgraphSize,
            @Param("vertexCount") Integer vertexCount,
            @Param("status") WorkUnitStatus status,
            @Param("stageId") Integer stageId,
            @Param("assignedClient") String assignedClient, Pageable pageable);

    // TODO remove once campaign is implemented
    List<WorkUnit> findAllByBaseGraphIdOrderByIdDesc(Integer graphId, Pageable pageable);

}