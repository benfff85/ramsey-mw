package com.setminusx.ramsey.mw.dto;

import java.time.LocalDateTime;

/** One row of {@code StageRepo.findProgressionPage}; the native query's column aliases bind here. */
public interface ProgressionRow {
    Integer getStageId();
    Integer getGraphId();
    Integer getCliqueCount();
    LocalDateTime getCreatedDate();
    String getStatus();
    String getDetails();
}
