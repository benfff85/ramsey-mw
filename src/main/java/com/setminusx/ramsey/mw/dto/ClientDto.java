package com.setminusx.ramsey.mw.dto;

import com.setminusx.ramsey.mw.model.ClientStatus;
import com.setminusx.ramsey.mw.model.ClientType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClientDto {

    private Integer clientId;
    private Integer subgraphSize;
    private Integer vertexCount;
    private ClientType type;
    private ClientStatus status;
    private LocalDateTime createdDate;
    private LocalDateTime lastPhoneHomeDate;

}
