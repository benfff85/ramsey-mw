package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.WorkResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkResultRepo extends JpaRepository<WorkResult, Long> {

    List<WorkResult> findByStageId(Integer stageId);

    List<WorkResult> findByBaseGraphId(Integer baseGraphId);

    @Query("SELECT COUNT(r) FROM WorkResult r WHERE r.stageId = :stageId")
    long countByStageId(@Param("stageId") Integer stageId);

    @Query("SELECT r FROM WorkResult r WHERE r.stageId = :stageId AND r.cliqueCount < :maxCliqueCount ORDER BY r.cliqueCount ASC")
    List<WorkResult> findBestResultsByStageId(
            @Param("stageId") Integer stageId,
            @Param("maxCliqueCount") Integer maxCliqueCount);

}
