package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.Graph;
import com.setminusx.ramsey.mw.repository.GraphRepo;
import com.setminusx.ramsey.mw.utility.GraphBits;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class GraphLineageServiceTest {

    private static final int N = 20; // 190 edges: small enough to be fast, big enough for collisions

    private record Step(Integer getGraphId, Integer getParentGraphId, String getFlippedEdges,
                        Integer getLineageDepth, Boolean getHasEdgeData) implements GraphRepo.LineageStep {}

    /** A repo over an in-memory table, counting edge-data loads so tests can see what was read. */
    private static GraphRepo repoOver(Map<Integer, Graph> rows, Map<Integer, Integer> edgeLoads) {
        GraphRepo repo = mock(GraphRepo.class);
        when(repo.findLineageStep(anyInt())).thenAnswer(inv -> Optional.ofNullable(rows.get((Integer) inv.getArgument(0)))
                .map(g -> new Step(g.getGraphId(), g.getParentGraphId(), g.getFlippedEdges(), g.getLineageDepth(), g.getEdgeData() != null)));
        when(repo.findEdgeData(anyInt())).thenAnswer(inv -> {
            Integer id = inv.getArgument(0);
            edgeLoads.merge(id, 1, Integer::sum);
            return rows.get(id).getEdgeData();
        });
        return repo;
    }

    /**
     * A 2,500-graph chain from one stored root, with a snapshot every 100 hops. Each delta
     * flips 1-2 random edges, sometimes the same edge twice. Returns the true bits of every graph.
     * storeAll=true is the SHADOW layout (every row stores its bits); false is DELTA.
     */
    private static Map<Integer, String> chain(Map<Integer, Graph> rows, boolean storeAll, long seed) {
        Random rng = new Random(seed);
        Map<Integer, String> truth = new HashMap<>();
        char[] bits = new char[N * (N - 1) / 2];
        for (int i = 0; i < bits.length; i++) bits[i] = rng.nextBoolean() ? '1' : '0';
        rows.put(1, graph(1, null, null, 0, new String(bits)));
        truth.put(1, new String(bits));
        int depth = 0;
        for (int id = 2; id <= 2_500; id++) {
            // Distinct endpoints only: a self-loop {{v:v}} is not an edge (it maps to a negative index).
            int a = rng.nextInt(N), b = (a + 1 + rng.nextInt(N - 1)) % N;
            int c = rng.nextInt(N), d = (c + 1 + rng.nextInt(N - 1)) % N;
            String edges = rng.nextInt(10) == 0 ? "{{" + a + ":" + b + "},{" + b + ":" + a + "}}"      // repeat: a no-op
                    : rng.nextBoolean() ? "{{" + a + ":" + b + "}}"
                    : "{{" + a + ":" + b + "},{" + c + ":" + d + "}}";
            GraphBits.flip(bits, N, edges);
            depth = (id % 100 == 0) ? 0 : depth + 1;
            String stored = (storeAll || depth == 0) ? new String(bits) : null;
            rows.put(id, graph(id, id - 1, edges, depth, stored));
            truth.put(id, new String(bits));
        }
        return truth;
    }

    private static Graph graph(int id, Integer parent, String edges, Integer depth, String bits) {
        Graph g = new Graph();
        g.setGraphId(id);
        g.setVertexCount(N);
        g.setParentGraphId(parent);
        g.setFlippedEdges(edges);
        g.setLineageDepth(depth);
        g.setEdgeData(bits);
        return g;
    }

    @Test
    void storedModeRebuildsEveryDeltaGraphExactly() {
        Map<Integer, Graph> rows = new HashMap<>();
        Map<Integer, String> truth = chain(rows, false, 1);
        GraphLineageService lineage = new GraphLineageService(repoOver(rows, new HashMap<>()));
        for (int id = 1; id <= 2_500; id++) {
            assertEquals(truth.get(id), lineage.edgeData(rows.get(id), GraphLineageService.Mode.STORED), "graph " + id);
        }
    }

    @Test
    void forceModeIgnoresStoredBitsOnDeltaRowsAndReadsOnlySnapshots() {
        Map<Integer, Graph> rows = new HashMap<>();
        Map<Integer, String> truth = chain(rows, true, 2);
        Map<Integer, Integer> loads = new HashMap<>();
        GraphLineageService lineage = new GraphLineageService(repoOver(rows, loads));
        for (int id = 1; id <= 2_500; id++) {
            assertEquals(truth.get(id), lineage.edgeData(rows.get(id), GraphLineageService.Mode.FORCE), "graph " + id);
        }
        assertTrue(loads.keySet().stream().allMatch(id -> rows.get(id).getLineageDepth() == 0),
                "FORCE must load edge data only from snapshots, got " + loads.keySet());
    }

    @Test
    void brokenChainThrows() {
        Map<Integer, Graph> rows = new HashMap<>();
        chain(rows, false, 3);
        rows.remove(1_250); // a parent row vanishes
        GraphLineageService lineage = new GraphLineageService(repoOver(rows, new HashMap<>()));
        assertThrows(LineageBrokenException.class, () -> lineage.edgeData(rows.get(1_260), GraphLineageService.Mode.STORED));
    }

    @Test
    void deltaRowWithoutFlipsThrows() {
        Graph orphan = graph(5, null, null, 3, null);
        GraphLineageService lineage = new GraphLineageService(repoOver(Map.of(5, orphan), new HashMap<>()));
        assertThrows(LineageBrokenException.class, () -> lineage.edgeData(orphan, GraphLineageService.Mode.STORED));
    }

    @Test
    void runawayChainThrows() {
        Map<Integer, Graph> rows = new HashMap<>();
        chain(rows, false, 4);
        GraphLineageService lineage = new GraphLineageService(repoOver(rows, new HashMap<>()), 10);
        assertThrows(LineageBrokenException.class, () -> lineage.edgeData(rows.get(2_499), GraphLineageService.Mode.STORED));
    }
}
