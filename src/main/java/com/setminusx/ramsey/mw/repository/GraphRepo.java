package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.Graph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GraphRepo extends JpaRepository<Graph, Integer> {

    Graph findGraphByGraphId(Integer id);

    List<Graph> findAllByOrderByCliqueCountAsc(Pageable pageable);

}