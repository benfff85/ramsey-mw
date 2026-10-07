package com.setminusx.ramsey.mw.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A graph's edge data cannot be rebuilt: its chain hits a missing row, a row with no flips, or the hop cap. */
@ResponseStatus(HttpStatus.CONFLICT)
public class LineageBrokenException extends RuntimeException {
    public LineageBrokenException(Integer target, Integer at, String why) {
        super("cannot rebuild edge data of graph " + target + ": " + why + " at graph " + at);
    }
}
