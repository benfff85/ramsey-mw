package com.setminusx.ramsey.mw.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * The strategy name crosses three services: the queue manager matches on the string to size the
 * work space, the worker deserializes it, and this enum decides whether a stage POST is accepted.
 * A name missing here rejects every stage creation with a 400 and stalls the search outright, so
 * pin the set rather than relying on all three being updated together.
 */
class StageStrategyTest {

    @Test
    void everyStrategyTheFleetCanBeConfiguredWithIsAccepted() {
        for (String name : new String[] {
                "BASIC",
                "SINGLE_EDGE_CARDINALITY",
                "DUAL_EDGE_CARDINALITY",
                "DUAL_EDGE_CARDINALITY_WITH_SINGLES",
                "SEQUENTIAL_WITH_SINGLES",
        }) {
            assertDoesNotThrow(() -> Stage.WorkEnumerationStrategy.valueOf(name),
                    "middleware rejects strategy " + name + " — stage creation would 400");
        }
    }
}
