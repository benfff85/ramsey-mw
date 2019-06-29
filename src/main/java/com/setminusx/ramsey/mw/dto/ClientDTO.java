package com.setminusx.ramsey.mw.dto;

import lombok.Data;

import java.util.Date;

@Data
public class ClientDTO {

    private String clientId;
    private Integer subgraphSize;
    private Integer vertexCount;
    private String type;
    private String status;
    private Date createdDate;
    private Date lastPhoneHomeDate;

}
