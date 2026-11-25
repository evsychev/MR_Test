package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TechnicalObjectTreeDataSource {
    private TechnicalObjectTreeDataSource() {
    }

    static TreeData loadTree() {
        var resource = TechnicalObjectTreeUI.class.getResourceAsStream("/content/technical-objects.csv");
        if (resource == null) {
            throw new IllegalStateException("Missing tree data resource: /content/technical-objects.csv");
        }

        Map<String, NodeBuilder> nodes = new LinkedHashMap<>();
        List<String> rootIds = new ArrayList<>();
        Set<String> expandedIds = new HashSet<>();
        String selectedId = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !header.replace("\uFEFF", "").equals("id,parent_id,type,label,expanded,selected")) {
                throw new IllegalStateException("Unexpected CSV header in technical object data");
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = parseCsvLine(line, lineNumber);
                if (fields.size() != 6) {
                    throw new IllegalStateException("Expected 6 CSV fields on line " + lineNumber);
                }

                String id = fields.get(0).strip();
                String parentId = fields.get(1).strip();
                String label = fields.get(3).strip();
                if (id.isEmpty() || label.isEmpty()) {
                    throw new IllegalStateException("Object id and label are required on line " + lineNumber);
                }
                IconKind kind;
                try {
                    kind = IconKind.valueOf(fields.get(2).strip().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException("Unknown object type on line " + lineNumber, exception);
                }
                NodeBuilder node = new NodeBuilder(id, label, kind);
                if (nodes.putIfAbsent(id, node) != null) {
                    throw new IllegalStateException("Duplicate object id on line " + lineNumber + ": " + id);
                }
                if (Boolean.parseBoolean(fields.get(4).strip())) {
                    expandedIds.add(id);
                }
                if (Boolean.parseBoolean(fields.get(5).strip())) {
                    if (selectedId != null) {
                        throw new IllegalStateException("Only one CSV row can be selected");
                    }
                    selectedId = id;
                }
                if (parentId.isEmpty()) {
                    rootIds.add(id);
                } else {
                    NodeBuilder parent = nodes.get(parentId);
                    if (parent == null) {
                        throw new IllegalStateException("Parent must precede child on line " + lineNumber
                                + ": " + parentId);
                    }
                    parent.children.add(id);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read technical object CSV data", exception);
        }

        if (rootIds.isEmpty()) {
            throw new IllegalStateException("Technical object CSV contains no root objects");
        }
        if (selectedId == null) {
            selectedId = rootIds.getFirst();
        }
        List<TreeNode> roots = rootIds.stream().map(id -> buildNode(id, nodes)).toList();
        return new TreeData(roots, expandedIds, selectedId);
    }

    private static TreeNode buildNode(String id, Map<String, NodeBuilder> nodes) {
        NodeBuilder node = nodes.get(id);
        List<TreeNode> children = node.children.stream().map(childId -> buildNode(childId, nodes)).toList();
        return new TreeNode(node.id, node.label, node.kind, children);
    }

    private static List<String> parseCsvLine(String line, int lineNumber) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new IllegalStateException("Unclosed quoted field on CSV line " + lineNumber);
        }
        fields.add(field.toString());
        return fields;
    }

    enum IconKind {
        FACTORY, BUILDING,EQUIPMENT,MACHINE,THERMAL,POWER,WAREHOUSE,SERVER,GRID
    }

    static final class TreeNode {
        final String id;
        final String label;
        final IconKind kind;
        final List<TreeNode> children;

        TreeNode(String id, String label, IconKind kind, List<TreeNode> children) {
            this.id = id;
            this.label = label;
            this.kind = kind;
            this.children = children;
        }
    }

    private static final class NodeBuilder {
        private final String id;
        private final String label;
        private final IconKind kind;
        private final List<String> children = new ArrayList<>();

        private NodeBuilder(String id, String label, IconKind kind) {
            this.id = id;
            this.label = label;
            this.kind = kind;
        }
    }

    record TreeData(List<TreeNode> roots, Set<String> expandedIds, String selectedId) {
    }
}
