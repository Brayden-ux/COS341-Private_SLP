package spl.tree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SyntaxTree {
    private final RootNode root;
    private final Map<Integer, SyntaxTreeNode> nodes = new LinkedHashMap<>();

    public SyntaxTree(RootNode root) {
        this.root = root;
        addNode(root);
    }

    public RootNode getRoot() {
        return root;
    }

    public void addNode(SyntaxTreeNode n) {
        nodes.put(n.getId(), n);
    }

    public SyntaxTreeNode getNode(int id) {
        return nodes.get(id);
    }

    /** The children of a node in left-to-right order (empty for a leaf). */
    public List<SyntaxTreeNode> children(SyntaxTreeNode n) {
        List<Integer> ids;
        if (n instanceof RootNode r) ids = r.getChildIds();
        else if (n instanceof InnerNode in) ids = in.getChildIds();
        else return List.of();
        List<SyntaxTreeNode> out = new ArrayList<>(ids.size());
        for (int id : ids) out.add(nodes.get(id));
        return out;
    }

    /** The first child of n that is an inner node for the given non-terminal, or null. */
    public SyntaxTreeNode child(SyntaxTreeNode n, String nonTerminal) {
        for (SyntaxTreeNode c : children(n)) {
            if (!(c instanceof LeafNode) && c.contents().equals(nonTerminal)) return c;
        }
        return null;
    }

    public void traverse(TreeVisitor v) {
        traverseFrom(root.getId(), v);
    }

    private void traverseFrom(int id, TreeVisitor v) {
        SyntaxTreeNode n = nodes.get(id);
        n.accept(v);
        if (n instanceof RootNode rn) {
            for (int childId : rn.getChildIds()) traverseFrom(childId, v);
        } else if (n instanceof InnerNode in) {
            for (int childId : in.getChildIds()) traverseFrom(childId, v);
        }
    }
}
