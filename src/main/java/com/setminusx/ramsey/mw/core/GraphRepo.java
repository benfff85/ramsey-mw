package com.setminusx.ramsey.mw.core;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface GraphRepo extends CrudRepository<Graph, Integer> {

    @Query("SELECT g FROM Graph g WHERE g.cliqueCount = (SELECT MIN(g.cliqueCount) FROM g)")
    Graph getGraphWithMinCliqueCount();

    @Query("SELECT g FROM Graph g WHERE g.graphId = ?1")
    Graph findGraphById(Integer id);

    Iterable<Graph> findTop20ByOrderByCliqueCountAsc();

}