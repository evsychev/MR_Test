package org.example.tree;

public record TreeRow(TechnicalObject node, int depth,
               boolean hasChildren, boolean expanded) {
}
