package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.dto.ClientDto;
import com.setminusx.ramsey.mw.entity.Client;
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

    public void insertClient(ClientDto clientDTO) {
        clientRepo.save(mapDTOToClient(clientDTO));
    }

    public Optional<ClientDto> selectClientById(String id) {
        return clientRepo.findById(id).map(ClientService::mapClientToDTO);
    }

    public List<ClientDto> getAll(Integer subgraphSize, Integer vertexCount) {
        return clientRepo.findAllBySubgraphSizeAndVertexCount(subgraphSize, vertexCount).stream().map(ClientService::mapClientToDTO).collect(Collectors.toList());
    }

    private Client mapDTOToClient(ClientDto clientDTO) {
        Client client = new Client();
        client.setClientId(clientDTO.getClientId());
        client.setSubgraphSize(clientDTO.getSubgraphSize());
        client.setVertexCount(clientDTO.getVertexCount());
        client.setStatus(clientDTO.getStatus());
        client.setType(clientDTO.getType());
        client.setCreatedDate(clientDTO.getCreatedDate());
        client.setLastPhoneHomeDate(clientDTO.getLastPhoneHomeDate());
        return client;
    }


    private static ClientDto mapClientToDTO(Client client) {
        ClientDto clientDTO = new ClientDto();
        clientDTO.setClientId(client.getClientId());
        clientDTO.setSubgraphSize(client.getSubgraphSize());
        clientDTO.setVertexCount(client.getVertexCount());
        clientDTO.setStatus(client.getStatus());
        clientDTO.setType(client.getType());
        clientDTO.setCreatedDate(client.getCreatedDate());
        clientDTO.setLastPhoneHomeDate(client.getLastPhoneHomeDate());
        return clientDTO;
    }

}
