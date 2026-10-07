package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.Graph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GraphRepo extends JpaRepository<Graph, Integer> {

    List<Graph> findAllBySubgraphSizeAndVertexCount(Integer subgraphSize, Integer vertexCount, Pageable pageable);

    /** One hop of a lineage walk, without loading the (possibly 40 KB) edge data. */
    interface LineageStep {
        Integer getGraphId();
        Integer getParentGraphId();
        String getFlippedEdges();
        Integer getLineageDepth();
        Boolean getHasEdgeData();
    }

    @Query("select g.graphId as graphId, g.parentGraphId as parentGraphId, g.flippedEdges as flippedEdges, "
            + "g.lineageDepth as lineageDepth, case when g.edgeData is null then false else true end as hasEdgeData "
            + "from Graph g where g.graphId = :id")
    Optional<LineageStep> findLineageStep(@Param("id") Integer id);

    @Query("select g.edgeData from Graph g where g.graphId = :id")
    String findEdgeData(@Param("id") Integer id);

}