package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.WorkUnit;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface WorkUnitRepo extends PagingAndSortingRepository<WorkUnit, String> {

    List<WorkUnit> findAllBySubgraphSizeAndVertexCountAndStatus(Integer subgraphSize, Integer vertexCount, WorkUnitStatus workUnitStatus, Pageable pageable);

}