package com.setminusx.ramsey.mw.utility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphBitsTest {

    /** The index the QM's GraphHashUtil uses: a*(n-1) - a*(a+1)/2 + b - 1. Must agree for every edge. */
    private static int qmIndex(int a, int b, int n) {
        return a * (n - 1) - a * (a + 1) / 2 + b - 1;
    }

    @Test
    void indexMatchesTheQueueManagerForEveryEdgeOf282() {
        int n = 282, seen = 0;
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                assertEquals(qmIndex(a, b, n), GraphBits.edgeIndex(a, b, n));
                assertEquals(qmIndex(a, b, n), GraphBits.edgeIndex(b, a, n), "vertex order must not matter");
                seen++;
            }
        }
        assertEquals(39_621, seen);
    }

    @Test
    void flipsListedEdgesInPlace() {
        char[] bits = "0000000000".toCharArray(); // n = 5
        GraphBits.flip(bits, 5, "{{0:1},{3:4}}");
        assertEquals("1000000001", new String(bits));
    }

    @Test
    void doubleFlipIsNoOp() {
        char[] bits = "0110100101".toCharArray();
        GraphBits.flip(bits, 5, "{{1:3},{3:1}}");
        assertEquals("0110100101", new String(bits));
    }

    /** deriveGraph's guard is on the INDEX, not the vertices: an edge whose index lands past the string is skipped. */
    @Test
    void outOfRangeEdgeIsIgnoredAsDeriveGraphAlwaysDid() {
        char[] bits = "0000000000".toCharArray();
        GraphBits.flip(bits, 5, "{{4:6}}"); // index 11 of a 10-edge string
        assertEquals("0000000000", new String(bits));
    }
}
