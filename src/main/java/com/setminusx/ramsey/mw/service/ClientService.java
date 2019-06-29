package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.dto.ClientDTO;
import com.setminusx.ramsey.mw.entity.Client;
import com.setminusx.ramsey.mw.repository.ClientRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ClientService {

    @Autowired
    private ClientRepo clientRepo;

    public void insertClient(ClientDTO clientDTO) {

        Client client = mapDTOToClient(clientDTO);
        clientRepo.save(client);

    }

    public ClientDTO selectClientById(String id) {

        Optional<Client> client = clientRepo.findById(id);
        if (client.isPresent()) {
            return mapClientToDTO(client.get());
        } else {
            return null;
        }
    }

    private Client mapDTOToClient(ClientDTO clientDTO) {
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


    private static ClientDTO mapClientToDTO(Client client) {
        ClientDTO clientDTO = new ClientDTO();
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
