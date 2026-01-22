package com.setminusx.ramsey.mw.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.setminusx.ramsey.mw.entity.Stage;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProgressionDTO {
    private Integer stageId;
    private Integer graphId;
    private Integer cliqueCount;
    private LocalDateTime createdDate;
    private Stage.Status status;
}
