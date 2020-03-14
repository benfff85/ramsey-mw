package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.Client;
import com.setminusx.ramsey.mw.model.ClientStatus;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface ClientRepo extends PagingAndSortingRepository<Client, Integer> {

    List<Client> findAllBySubgraphSizeAndVertexCountAndStatus(Integer subgraphSize, Integer vertexCount, ClientStatus status);

}