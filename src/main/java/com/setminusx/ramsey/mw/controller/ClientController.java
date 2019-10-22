package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.dto.ClientDto;
import com.setminusx.ramsey.mw.model.ClientStatus;
import com.setminusx.ramsey.mw.model.ClientType;
import com.setminusx.ramsey.mw.service.ClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.setminusx.ramsey.mw.utility.Constants.ERROR_HEADER;

@Slf4j
@RestController
public class ClientController {

    @Autowired
    private ClientService clientService;

    @PostMapping("/api/ramsey/clients")
    public ResponseEntity registerClient(
            @RequestHeader() String rqid,
            @RequestBody() ClientDto clientDTO) {

        log.info("Processing registerClient for rqid: {}", rqid);

        if (clientDTO.getClientId() == null) {
            log.error("Unable to save client as there is no client id");
            return ResponseEntity.badRequest().header(ERROR_HEADER, "Client is missing an id").build();
        }

        if (clientService.selectClientById(clientDTO.getClientId()).isPresent()) {
            log.error("Unable to register client as there is already a client with id {}", clientDTO.getClientId());
            return ResponseEntity.badRequest().header(ERROR_HEADER, "Unable to register client as there is already a client with id " + clientDTO.getClientId()).build();
        }

        Date date = new Date();
        clientDTO.setCreatedDate(date);
        clientDTO.setLastPhoneHomeDate(date);
        clientDTO.setStatus(ClientStatus.ACTIVE);
        log.info(clientDTO.toString());
        clientService.insertClient(clientDTO);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/api/ramsey/clients")
    public ResponseEntity updateClient(
            @RequestHeader() String rqid,
            @RequestBody() ClientDto clientDTO) {

        log.info("Processing updateClient for rqid: {}", rqid);

        if (clientDTO.getClientId() == null) {
            log.error("Unable to update client as there is no client id");
            return ResponseEntity.badRequest().header(ERROR_HEADER, "Client is missing an id").build();
        }

        Optional<ClientDto> existingClientDTOOptional = clientService.selectClientById(clientDTO.getClientId());
        if (existingClientDTOOptional.isEmpty()) {
            log.error("Unable to update client as there is no client with id {}", clientDTO.getClientId());
            return ResponseEntity.badRequest().header(ERROR_HEADER, "Unable to update client as there is no client with id " + clientDTO.getClientId()).build();
        }
        ClientDto existingClientDto = existingClientDTOOptional.get();
        existingClientDto.setLastPhoneHomeDate(new Date());
        existingClientDto.setStatus(clientDTO.getStatus());
        clientService.insertClient(existingClientDto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/ramsey/clients/{id}")
    public ResponseEntity<ClientDto> getClientById(
            @RequestHeader() String rqid,
            @PathVariable() String id) {

        log.info("Processing getClientById for rqid: {}", rqid);

        Optional<ClientDto> clientDTO = clientService.selectClientById(id);
        if (clientDTO.isEmpty()) {
            log.error("Unable to get client as there is no client with id {}", id);
            return ResponseEntity.badRequest().header(ERROR_HEADER, "Unable to get client as there is no client with id " + id).build();
        }

        return ResponseEntity.ok().body(clientDTO.get());

    }

    @GetMapping("/api/ramsey/clients")
    public ResponseEntity<List<ClientDto>> searchForClients(
            @RequestHeader() String rqid,
            @RequestParam() Integer subgraphSize,
            @RequestParam() Integer vertexCount,
            @RequestParam(required = false) ClientType clientType,
            @RequestParam(required = false) ClientStatus clientStatus) {

        log.info("Processing searchForClients for rqid: {}", rqid);

        List<ClientDto> clients = clientService.getAll(subgraphSize, vertexCount);
        if(clientType != null) {
            log.info("Filtering for type: {}", clientType);
            clients = clients.parallelStream()
                    .filter(c -> clientType.equals(c.getType()))
                    .collect(Collectors.toList());
        }
        if(clientStatus != null) {
            log.info("Filtering for status: {}", clientStatus);
            clients = clients.parallelStream()
                    .filter(c -> clientStatus.equals(c.getStatus()))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok().body(clients);

    }

}