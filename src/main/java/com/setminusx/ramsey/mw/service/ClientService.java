package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.dto.ClientDTO;
import com.setminusx.ramsey.mw.entity.Client;
import com.setminusx.ramsey.mw.model.ClientStatus;
import com.setminusx.ramsey.mw.model.ClientType;
import com.setminusx.ramsey.mw.repository.ClientRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ClientService {

    @Autowired
    private ClientRepo clientRepo;

    public void insertClient(ClientDTO clientDTO) {

        Client client = mapDTOToClient(clientDTO);
        clientRepo.save(client);

    }

    public Optional<ClientDTO> selectClientById(String id) {
        return clientRepo.findById(id).map(ClientService::mapClientToDTO);
    }

    public List<ClientDTO> getAll(Integer subgraphSize, Integer vertexCount) {
        return clientRepo.findAllBySubgraphSizeAndVertexCount(subgraphSize, vertexCount).stream().map(ClientService::mapClientToDTO).collect(Collectors.toList());
    }


    private Client mapDTOToClient(ClientDTO clientDTO) {
        Client client = new Client();
        client.setClientId(clientDTO.getClientId());
        client.setSubgraphSize(clientDTO.getSubgraphSize());
        client.setVertexCount(clientDTO.getVertexCount());
        client.setStatus(clientDTO.getStatus().toString());
        client.setType(clientDTO.getType().toString());
        client.setCreatedDate(clientDTO.getCreatedDate());
        client.setLastPhoneHomeDate(clientDTO.getLastPhoneHomeDate());
        return client;
    }


    private static ClientDTO mapClientToDTO(Client client) {
        ClientDTO clientDTO = new ClientDTO();
        clientDTO.setClientId(client.getClientId());
        clientDTO.setSubgraphSize(client.getSubgraphSize());
        clientDTO.setVertexCount(client.getVertexCount());
        clientDTO.setStatus(ClientStatus.valueOf(client.getStatus()));
        clientDTO.setType(ClientType.valueOf(client.getType()));
        clientDTO.setCreatedDate(client.getCreatedDate());
        clientDTO.setLastPhoneHomeDate(client.getLastPhoneHomeDate());
        return clientDTO;
    }

}
