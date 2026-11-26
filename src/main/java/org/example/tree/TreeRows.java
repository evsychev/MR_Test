package org.example.tree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TreeRows {
    private final List<TechnicalObject> roots;
    private final Set<String> expanded;
    private List<TreeRow> rows = List.of();
    private String lastQuery;

    public TreeRows(List<TechnicalObject> roots, Set<String> initialExpansion) {
        this.roots = roots;
        expanded = new HashSet<>(initialExpansion);
    }

    public boolean toggle(String id) {
        lastQuery = null;
        if (expanded.add(id)) return true;
        expanded.remove(id);
        return false;
    }

    public Set<String> expandedIds() {
        return Set.copyOf(expanded);
    }

    public List<TreeRow> visible(String text) {
        String query = text.strip().toLowerCase(Locale.ROOT);
        if (query.equals(lastQuery)) return rows;
        Set<String> matches = new HashSet<>();
        if (!query.isEmpty()) {
            for (TechnicalObject root : roots) collectMatches(root, query, matches);
        }
        List<TreeRow> result = new ArrayList<>();
        for (TechnicalObject root : roots) append(root, 0, matches, !query.isEmpty(), result);
        rows = List.copyOf(result);
        lastQuery = query;
        return rows;
    }

    private boolean collectMatches(TechnicalObject node, String query, Set<String> matches) {
        boolean found = node.searchText().contains(query);
        for (TechnicalObject child : node.children) found |= collectMatches(child, query, matches);
        if (found) matches.add(node.id);
        return found;
    }

    private void append(TechnicalObject node, int depth, Set<String> matches,
                        boolean filtering, List<TreeRow> result) {
        if (filtering && !matches.contains(node.id)) return;
        boolean open = filtering || expanded.contains(node.id);
        result.add(new TreeRow(node, depth, !node.children.isEmpty(), open));
        if (open) {
            for (TechnicalObject child : node.children) append(child, depth + 1, matches, filtering, result);
        }
    }
}
