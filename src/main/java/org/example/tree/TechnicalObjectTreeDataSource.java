package org.example.tree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TechnicalObjectTreeDataSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(TechnicalObjectTreeDataSource.class);
    private static final String FIELD_SEPARATOR = "|~|";
    private static final String HIDDEN_ROOT_ID = "00000000-0001-0001-0001-000000000001";
    private static final List<String> EXPECTED_COLUMNS = List.of(
            "rtoId", "rtoParent", "rtoCurrentStatus", "del", "toId", "toCode", "toName",
            "toFullName", "toIsRepairObject", "toCurrentStatus", "toUpd", "classId", "classCode",
            "className", "classImageName", "classKindImageName", "classAdditional", "rtoSource",
            "propertyAndValue", "repairObject", "note", "NFCcode", "ActivityName", "OwnershipName");

    private TechnicalObjectTreeDataSource() {
    }

    public static TreeData loadTree() {
        var resource = TechnicalObjectTreeDataSource.class.getResourceAsStream("/content/technical-objects.csv");
        if (resource == null) {
            throw new IllegalStateException("Missing tree data resource: /content/technical-objects.csv");
        }

        Map<String, NodeBuilder> nodes = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalStateException("Technical object data is empty");
            }
            List<String> columns = splitRecord(header.replace("\uFEFF", ""), 1);
            if (!columns.equals(EXPECTED_COLUMNS)) {
                throw new IllegalStateException("Unexpected technical object data header: " + columns);
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = splitRecord(line, lineNumber);
                Map<String, String> fields = new LinkedHashMap<>();
                for (int index = 0; index < EXPECTED_COLUMNS.size(); index++) {
                    fields.put(EXPECTED_COLUMNS.get(index), values.get(index));
                }
                String id = value(fields, "rtoId");
                if (id.isBlank()) {
                    throw new IllegalStateException("rtoId is required on technical object line " + lineNumber);
                }
                if (HIDDEN_ROOT_ID.equals(id)) {
                    continue;
                }

                String label = firstNonBlank(value(fields, "toName"), value(fields, "toFullName"),
                        value(fields, "toCode"), value(fields, "toId"));
                if (label.isBlank()) {
                    throw new IllegalStateException("Object display name is missing on line " + lineNumber);
                }
                NodeBuilder node = new NodeBuilder(id, value(fields, "rtoParent"), label,
                        determineIconKind(fields), Collections.unmodifiableMap(fields));
                if (nodes.putIfAbsent(id, node) != null) {
                    throw new IllegalStateException("Duplicate rtoId on line " + lineNumber + ": " + id);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read technical object data", exception);
        }

        if (nodes.isEmpty()) {
            throw new IllegalStateException("Technical object data contains no visible objects");
        }

        List<NodeBuilder> rootBuilders = new ArrayList<>();
        int missingParentCount = 0;
        for (NodeBuilder node : nodes.values()) {
            if (node.parentId.isBlank() || HIDDEN_ROOT_ID.equals(node.parentId)) {
                rootBuilders.add(node);
                continue;
            }
            NodeBuilder parent = nodes.get(node.parentId);
            if (parent == null) {
                rootBuilders.add(node);
                missingParentCount++;
            } else {
                parent.children.add(node.id);
            }
        }
        if (missingParentCount > 0) {
            LOGGER.warn("{} technical object(s) reference parent IDs absent from the supplied data; showing them as roots",
                    missingParentCount);
        }
        if (rootBuilders.isEmpty()) {
            throw new IllegalStateException("Technical object hierarchy has no visible root; check parent links");
        }

        List<TechnicalObject> roots = rootBuilders.stream().map(node -> buildNode(node.id, nodes, new HashSet<>())).toList();
        String selectedId = rootBuilders.getFirst().id;
        return new TreeData(roots, selectedId);
    }

    private static List<String> splitRecord(String line, int lineNumber) {
        String[] split = line.split("\\Q" + FIELD_SEPARATOR + "\\E", -1);
        int fieldCount = split.length;
        if (fieldCount > 0 && split[fieldCount - 1].isEmpty()) {
            fieldCount--;
        }
        if (fieldCount != EXPECTED_COLUMNS.size()) {
            throw new IllegalStateException("Expected " + EXPECTED_COLUMNS.size()
                    + " fields on technical object line " + lineNumber + " but got " + fieldCount);
        }
        return java.util.Arrays.asList(split).subList(0, fieldCount);
    }

    private static TechnicalObject buildNode(String id, Map<String, NodeBuilder> nodes, Set<String> path) {
        NodeBuilder node = nodes.get(id);
        if (!path.add(id)) {
            throw new IllegalStateException("Cycle detected in technical object hierarchy at " + id);
        }
        List<TechnicalObject> children = node.children.stream()
                .map(childId -> buildNode(childId, nodes, path)).toList();
        path.remove(id);
        return new TechnicalObject(node.id, node.label, node.kind, node.fields, children);
    }

    private static IconKind determineIconKind(Map<String, String> fields) {
        String kind = value(fields, "classKindImageName").toUpperCase(Locale.ROOT);
        String className = value(fields, "className").toLowerCase(Locale.ROOT);
        String label = firstNonBlank(value(fields, "toName"), value(fields, "toFullName"))
                .toLowerCase(Locale.ROOT);
        if (kind.equals("TM") || label.contains("лпдс") || label.contains("завод") || label.contains("комплекс")) {
            return IconKind.FACTORY;
        }
        if (className.contains("температур") || className.contains("теплов")) {
            return IconKind.THERMAL;
        }
        if (className.contains("электр") || className.contains("коммуникац")) {
            return IconKind.POWER;
        }
        if (className.contains("помещен") || label.contains("цех")) {
            return IconKind.BUILDING;
        }
        if (className.contains("систем") || kind.startsWith("EO")) {
            return IconKind.EQUIPMENT;
        }
        return IconKind.MACHINE;
    }

    private static String value(Map<String, String> fields, String name) {
        return fields.getOrDefault(name, "").strip();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private static final class NodeBuilder {
        private final String id;
        private final String parentId;
        private final String label;
        private final IconKind kind;
        private final Map<String, String> fields;
        private final List<String> children = new ArrayList<>();

        private NodeBuilder(String id, String parentId, String label, IconKind kind, Map<String, String> fields) {
            this.id = id;
            this.parentId = parentId;
            this.label = label;
            this.kind = kind;
            this.fields = fields;
        }
    }

    public record TreeData(List<TechnicalObject> roots, String selectedId) {
    }
}
