package com.setminusx.ramsey.mw.utility;

import com.setminusx.ramsey.mw.entity.Edge;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.StringUtils.*;

@Converter
public class EdgeListConverter implements AttributeConverter<List<Edge>, String> {


    @Override
    public String convertToDatabaseColumn(List<Edge> edges) {
        return "{" + edges.stream().map(Edge::toString).collect(Collectors.joining(",")) + "}";
    }

    @Override
    public List<Edge> convertToEntityAttribute(String string) {
        string = remove(string, "{");
        string = remove(string, "}");
        String[] edgeStrings = string.split(",");
        List<Edge> edges = new ArrayList<>();
        for (String edgeString : edgeStrings) {
            edges.add(Edge.builder()
                    .vertexOne(parseInt(substringBefore(edgeString, ":")))
                    .vertexTwo(parseInt(substringAfter(edgeString, ":")))
                    .build());
        }
        return edges;
    }
}
