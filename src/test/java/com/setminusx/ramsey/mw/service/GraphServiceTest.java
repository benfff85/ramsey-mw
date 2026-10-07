package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.Graph;
import com.setminusx.ramsey.mw.repository.GraphRepo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GraphServiceTest {

    private final GraphRepo repo = mock(GraphRepo.class);
    private final GraphLineageService lineage = mock(GraphLineageService.class);
    private final GraphService service = new GraphService(repo, lineage);

    private Graph delta() {
        Graph g = new Graph();
        g.setGraphId(9);
        g.setVertexCount(5);
        g.setParentGraphId(8);
        g.setFlippedEdges("{{0:1}}");
        g.setLineageDepth(3);
        return g;
    }

    @Test
    void storedRebuildsDeltaRowsWithoutTouchingTheEntity() {
        Graph row = delta();
        when(repo.findById(9)).thenReturn(Optional.of(row));
        when(lineage.edgeData(row, GraphLineageService.Mode.STORED)).thenReturn("1000000000");

        Graph out = service.getGraphByGraphId(9, "stored");

        assertEquals("1000000000", out.getEdgeData());
        assertNull(row.getEdgeData(), "the managed entity must not be modified (it could be flushed)");
        assertEquals(3, out.getLineageDepth());
    }

    @Test
    void noneReturnsTheRowAsStored() {
        when(repo.findById(9)).thenReturn(Optional.of(delta()));
        assertNull(service.getGraphByGraphId(9, "none").getEdgeData());
        verifyNoInteractions(lineage);
    }

    @Test
    void preLineageRowsAreReturnedUnchanged() {
        Graph old = new Graph();
        old.setGraphId(7);
        old.setEdgeData("0101010101");
        when(repo.findById(7)).thenReturn(Optional.of(old));
        assertSame(old, service.getGraphByGraphId(7, "stored"));
        verifyNoInteractions(lineage);
    }

    @Test
    void forceRebuildsEvenWhenTheRowStoresBits() {
        Graph shadow = delta();
        shadow.setEdgeData("1111111111");
        when(repo.findById(9)).thenReturn(Optional.of(shadow));
        when(lineage.edgeData(shadow, GraphLineageService.Mode.FORCE)).thenReturn("1000000000");
        assertEquals("1000000000", service.getGraphByGraphId(9, "force").getEdgeData());
    }

    @Test
    void materializePersistsRebuiltBits() {
        Graph row = delta();
        when(repo.findById(9)).thenReturn(Optional.of(row));
        when(lineage.edgeData(row, GraphLineageService.Mode.STORED)).thenReturn("1000000000");
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Graph out = service.materialize(9);

        assertEquals("1000000000", out.getEdgeData());
        verify(repo).save(eq(row));
    }
}
