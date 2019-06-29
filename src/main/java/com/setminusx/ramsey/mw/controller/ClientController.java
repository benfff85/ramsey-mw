package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.dto.ClientDTO;
import com.setminusx.ramsey.mw.service.ClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@Slf4j
@RestController
public class ClientController {

    @Autowired
    private ClientService clientService;

    @PostMapping("/api/ramsey/clients")
    public ResponseEntity registerClient(@RequestHeader() String rqid, @RequestBody() ClientDTO clientDTO) {

        log.info("Processing registerClient for rqid: {}", rqid);

        if (clientDTO.getClientId() == null) {
            log.error("Unable to save client as there is no client id");
            return ResponseEntity.badRequest().header("Error-Description", "Client is missing an id").build();
        }

        if (clientService.selectClientById(clientDTO.getClientId()) != null){
            log.error("Unable to register client as there is already a client with id {}", clientDTO.getClientId());
            return ResponseEntity.badRequest().header("Error-Description", "Unable to register client as there is already a client with id " + clientDTO.getClientId()).build();
        }

        Date date = new Date();
        clientDTO.setCreatedDate(date);
        clientDTO.setLastPhoneHomeDate(date);
        clientDTO.setStatus("ACTIVE");
        log.info(clientDTO.toString());
        clientService.insertClient(clientDTO);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/api/ramsey/clients")
    public ResponseEntity updateClient(@RequestHeader() String rqid, @RequestBody() ClientDTO clientDTO) {

        log.info("Processing updateClient for rqid: {}", rqid);
        log.info(clientDTO.toString());


        if (clientDTO.getClientId() == null) {
            log.error("Unable to update client as there is no client id");
            return ResponseEntity.badRequest().header("Error-Description", "Client is missing an id").build();
        }

        ClientDTO existingClientDTO = clientService.selectClientById(clientDTO.getClientId());
        if (existingClientDTO == null){
            log.error("Unable to update client as there is no client with id {}", clientDTO.getClientId());
            return ResponseEntity.badRequest().header("Error-Description", "Unable to update client as there is no client with id " + clientDTO.getClientId()).build();
        }

        Date date = new Date();
        existingClientDTO.setLastPhoneHomeDate(date);
        existingClientDTO.setStatus("ACTIVE");
        clientService.insertClient(existingClientDTO);
        return ResponseEntity.ok().build();
    }

}