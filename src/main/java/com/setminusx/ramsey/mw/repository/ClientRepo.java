package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.Client;
import com.setminusx.ramsey.mw.model.ClientStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientRepo extends JpaRepository<Client, Integer> {

    List<Client> findAllBySubgraphSizeAndVertexCountAndStatus(Integer subgraphSize, Integer vertexCount, ClientStatus status);

}