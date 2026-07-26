package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.dto.ProgressionDTO;
import com.setminusx.ramsey.mw.entity.Stage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StageRepo extends JpaRepository<Stage, Integer> {

        @Query("SELECT s FROM Stage s WHERE " +
                        "(:campaignId IS NULL OR s.campaignId = :campaignId) AND " +
                        "(:status IS NULL OR s.status = :status)")
        List<Stage> findByCampaignIdAndStatus(
                        @Param("campaignId") Integer campaignId,
                        @Param("status") Stage.Status status);

        @Query("SELECT s FROM Stage s WHERE " +
                        "(:campaignId IS NULL OR s.campaignId = :campaignId) AND " +
                        "(:status IS NULL OR s.status = :status) " +
                        "ORDER BY s.stageId DESC")
        List<Stage> findRecentByCampaignIdAndStatus(
                        @Param("campaignId") Integer campaignId,
                        @Param("status") Stage.Status status,
                        Pageable pageable);

        @Query("SELECT new com.setminusx.ramsey.mw.dto.ProgressionDTO(s.stageId, s.baseGraphId, g.cliqueCount, s.createdDate, s.status, s.details) "
                        +
                        "FROM Stage s, Graph g " +
                        "WHERE s.baseGraphId = g.graphId " +
                        "AND s.campaignId = :campaignId " +
                        "ORDER BY s.createdDate ASC")
        List<ProgressionDTO> findProgressionByCampaignId(
                        @Param("campaignId") Integer campaignId);

}