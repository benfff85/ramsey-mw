package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.Graph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface GraphRepo extends PagingAndSortingRepository<Graph, Integer> {

    Graph findGraphByGraphId(Integer id);

    List<Graph> findAllByOrderByCliqueCountAsc(Pageable pageable);

}