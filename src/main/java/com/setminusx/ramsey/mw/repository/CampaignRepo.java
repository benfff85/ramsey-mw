package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignRepo extends JpaRepository<Campaign, Integer> {

    @Query("SELECT c FROM Campaign c WHERE " +
            "(:subgraphSize IS NULL OR c.subgraphSize = :subgraphSize) AND " +
            "(:vertexCount IS NULL OR c.vertexCount = :vertexCount) AND " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:strategy IS NULL OR c.strategy = :strategy)")
    List<Campaign> findBySubgraphSizeAndVertexCountAndStatusAndStrategy(
            @Param("subgraphSize") Integer subgraphSize,
            @Param("vertexCount") Integer vertexCount,
            @Param("status") Campaign.Status status,
            @Param("strategy") Campaign.Strategy strategy);

}