package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.dto.ProgressionDTO;
import com.setminusx.ramsey.mw.dto.ProgressionRow;
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

        /**
         * Up to {@code limit} progression points after {@code sinceStageId}, in stage order.
         *
         * Native so it can pin idx_stage_campaign_stage. Left to itself MySQL picks
         * idx_stage_campaign_status and sorts every stage of the campaign before applying the limit:
         * 3.5 s per page on campaign 10's 3.66M stages, against 0.18 s on the ordered index.
         * Stage order is creation order — every campaign checked, zero inversions (2026-10-06).
         */
        @Query(value = "SELECT s.stage_id AS stageId, s.base_graph_id AS graphId, g.clique_count AS cliqueCount, "
                        + "s.created_date AS createdDate, s.status AS status, s.details AS details "
                        + "FROM stage s FORCE INDEX (idx_stage_campaign_stage) "
                        + "JOIN graph g ON g.graph_id = s.base_graph_id "
                        + "WHERE s.campaign_id = :campaignId AND s.stage_id > :sinceStageId "
                        + "ORDER BY s.stage_id LIMIT :limit", nativeQuery = true)
        List<ProgressionRow> findProgressionPage(
                        @Param("campaignId") Integer campaignId,
                        @Param("sinceStageId") Integer sinceStageId,
                        @Param("limit") int limit);

}