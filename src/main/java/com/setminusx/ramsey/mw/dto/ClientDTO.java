package com.setminusx.ramsey.mw.dto;

import com.setminusx.ramsey.mw.model.ClientStatus;
import com.setminusx.ramsey.mw.model.ClientType;
import lombok.Data;

import java.util.Date;

@Data
public class ClientDTO {

    private String clientId;
    private Integer subgraphSize;
    private Integer vertexCount;
    private ClientType type;
    private ClientStatus status;
    private Date createdDate;
    private Date lastPhoneHomeDate;

}
