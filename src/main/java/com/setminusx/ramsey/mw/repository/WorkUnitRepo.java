package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.WorkUnit;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkUnitRepo extends JpaRepository<WorkUnit, String> {

    List<WorkUnit> findAllBySubgraphSizeAndVertexCountAndStatus(Integer subgraphSize, Integer vertexCount, WorkUnitStatus workUnitStatus, Pageable pageable);

    List<WorkUnit> findAllBySubgraphSizeAndVertexCountAndStatusAndAssignedClient(Integer subgraphSize, Integer vertexCount, WorkUnitStatus workUnitStatus, String assignedClient, Pageable pageable);

    List<WorkUnit> findAllByBaseGraphIdOrderByIdDesc(Integer graphId, Pageable pageable);

}