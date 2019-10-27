package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.Client;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface ClientRepo extends PagingAndSortingRepository<Client, Integer> {

    List<Client> findAllBySubgraphSizeAndVertexCount(Integer subgraphSize, Integer vertexCount);

}