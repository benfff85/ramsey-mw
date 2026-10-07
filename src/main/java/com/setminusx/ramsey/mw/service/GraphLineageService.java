package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.Graph;
import com.setminusx.ramsey.mw.repository.GraphRepo;
import com.setminusx.ramsey.mw.utility.GraphBits;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Rebuilds a graph's '0'/'1' edge string from its lineage: walk parent links to the nearest row
 * that stores its edge data, then replay each descendant's flips in order.
 *
 * Most graphs written in DELTA mode store only parent + flips (see the graph-delta-lineage plan),
 * so this is how every caller still gets a full edge string from GET /graphs/{id}.
 */
@Slf4j
@Service
public class GraphLineageService {

    /** 10x the default checkpoint interval: a longer chain means checkpoints stopped being written. */
    static final int MAX_HOPS = 10_000;

    public enum Mode {
        /** Stop at the first ancestor that stores edge data. */
        STORED,
        /** Use stored edge data only on snapshots (lineage_depth 0 or null): verifies SHADOW rows. */
        FORCE
    }

    private final GraphRepo graphRepo;
    private final int maxHops;

    @Autowired
    public GraphLineageService(GraphRepo graphRepo) {
        this(graphRepo, MAX_HOPS);
    }

    GraphLineageService(GraphRepo graphRepo, int maxHops) {
        this.graphRepo = graphRepo;
        this.maxHops = maxHops;
    }

    public String edgeData(Graph target, Mode mode) {
        Deque<String> flips = new ArrayDeque<>();
        Integer id = target.getGraphId();
        boolean hasBits = target.getEdgeData() != null;
        Integer depth = target.getLineageDepth();
        Integer parent = target.getParentGraphId();
        String edges = target.getFlippedEdges();
        for (int hops = 0; ; hops++) {
            if (hasBits && (mode == Mode.STORED || depth == null || depth == 0)) {
                String stored = hops == 0 ? target.getEdgeData() : graphRepo.findEdgeData(id);
                char[] bits = stored.toCharArray();
                for (String f : flips) {
                    GraphBits.flip(bits, target.getVertexCount(), f);
                }
                String rebuilt = new String(bits);
                // The row recorded the hash of its true bits when it was written. A rebuild that
                // disagrees (a corrupt flip list, drifted stored bits) must never be served: a QM
                // restart would re-base the search on it, and materialize would persist it.
                if (target.getGraphHash() != null && !target.getGraphHash().equals(sha256Hex(rebuilt))) {
                    throw broken(target, target.getGraphId(), "rebuilt bits do not match graph_hash");
                }
                return rebuilt;
            }
            if (parent == null || edges == null) {
                throw broken(target, id, "no usable edge data and no parent");
            }
            if (hops == maxHops) {
                throw broken(target, id, "chain longer than " + maxHops + " hops");
            }
            flips.addFirst(edges);
            Integer parentId = parent;
            GraphRepo.LineageStep step = graphRepo.findLineageStep(parentId)
                    .orElseThrow(() -> broken(target, parentId, "parent row missing"));
            id = step.getGraphId();
            hasBits = Boolean.TRUE.equals(step.getHasEdgeData());
            depth = step.getLineageDepth();
            parent = step.getParentGraphId();
            edges = step.getFlippedEdges();
        }
    }

    private static String sha256Hex(String bits) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(bits.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static LineageBrokenException broken(Graph target, Integer at, String why) {
        LineageBrokenException e = new LineageBrokenException(target.getGraphId(), at, why);
        log.warn(e.getMessage()); // WARN: the mw logs at WARN, and this must be seen
        return e;
    }
}
