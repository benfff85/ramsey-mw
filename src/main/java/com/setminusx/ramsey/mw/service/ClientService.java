package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.Client;
import com.setminusx.ramsey.mw.repository.ClientRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@lombok.RequiredArgsConstructor
public class ClientService {

    private final ClientRepo clientRepo;

    public List<Client> getClients(Integer campaignId, Client.Status status, Client.Type type) {
        if (campaignId == null && status == null && type == null) {
            return clientRepo.findAll();
        } else {
            return clientRepo.findByCampaignIdAndStatusAndType(campaignId, status, type);
        }
    }

    public Client getClientById(Integer id) {
        return clientRepo.findById(id).orElse(null);
    }

    public Client createOrUpdateClient(Client client) {
        return clientRepo.save(client);
    }

    public void deleteClient(Integer id) {
        clientRepo.deleteById(id);
    }

}
