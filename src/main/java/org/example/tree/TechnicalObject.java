package org.example.tree;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TechnicalObject {
    public final String id;
    public final String label;
    public final IconKind kind;
    public final Map<String, String> fields;
    public final List<TechnicalObject> children;

    private final String searchText;

    TechnicalObject(String id, String label, IconKind kind, Map<String, String> fields, List<TechnicalObject> children) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.fields = fields;
        this.searchText = String.join(" ", fields.values()).toLowerCase(Locale.ROOT);
        this.children = children;
    }

    public String searchText() {
        return searchText;
    }
}
