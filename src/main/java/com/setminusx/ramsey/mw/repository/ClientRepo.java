package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.Client;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface ClientRepo extends PagingAndSortingRepository<Client, String> {

    Client findClientByClientId(Integer id);

    List<Client> findAllByTypeAndAndSubgraphSizeAndVertexCountAndStatus(String type, Integer subgraphSize, Integer vertexCount, String status);

}