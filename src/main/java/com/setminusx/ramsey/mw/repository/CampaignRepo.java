package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignRepo extends JpaRepository<Campaign, Integer> {

    // status is no longer a stored column (derived in CampaignService); filtering by
    // status happens after derivation there.
    @Query("SELECT c FROM Campaign c WHERE " +
            "(:subgraphSize IS NULL OR c.subgraphSize = :subgraphSize) AND " +
            "(:vertexCount IS NULL OR c.vertexCount = :vertexCount) AND " +
            "(:strategy IS NULL OR c.strategy = :strategy)")
    List<Campaign> findBySubgraphSizeAndVertexCountAndStrategy(
            @Param("subgraphSize") Integer subgraphSize,
            @Param("vertexCount") Integer vertexCount,
            @Param("strategy") Campaign.Strategy strategy);

}