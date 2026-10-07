package com.setminusx.ramsey.mw.utility;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The one place the middleware maps edges to positions in a graph's '0'/'1' edge string. */
public final class GraphBits {

    private static final Pattern EDGE = Pattern.compile("\\{(\\d+):(\\d+)\\}");

    private GraphBits() {
    }

    /** Position of edge (v1, v2) in the upper-triangle edge string; vertex order does not matter. */
    public static int edgeIndex(int v1, int v2, int vertexCount) {
        int a = Math.min(v1, v2);
        int b = Math.max(v1, v2);
        return a * (2 * vertexCount - a - 1) / 2 + (b - a - 1);
    }

    /**
     * Toggle every edge in {@code edges} ("{{v1:v2},{v3:v4}}") in place, in order. An edge outside
     * the string is skipped, exactly as {@code GraphService.deriveGraph} always behaved.
     */
    public static void flip(char[] bits, int vertexCount, String edges) {
        Matcher m = EDGE.matcher(edges);
        while (m.find()) {
            int i = edgeIndex(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), vertexCount);
            if (i < bits.length) {
                bits[i] = bits[i] == '0' ? '1' : '0';
            }
        }
    }
}
