package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.Graph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface GraphRepo extends PagingAndSortingRepository<Graph, Integer> {

    @Query("SELECT g FROM Graph g WHERE g.graphId = ?1")
    Graph findGraphById(Integer id);

    List<Graph> findAllByOrderByCliqueCountAsc(Pageable pageable);

}