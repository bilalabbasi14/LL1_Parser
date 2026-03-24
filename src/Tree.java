import java.util.*;

public class Tree {

    // NODE (inner class)
    public static class Node {
        String       symbol;
        List<Node>   children;
        boolean      isLeaf;

        public Node(String symbol) {
            this.symbol   = symbol;
            this.children = new ArrayList<>();
            this.isLeaf   = false;
        }

        public void addChild(Node child) {
            this.children.add(child);
            // If this node has children it's internal,
            // but the child itself might be a leaf
            child.isLeaf = child.children.isEmpty();
        }

        public void addChildren(List<Node> kids) {
            for (Node kid : kids) addChild(kid);
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }

        public String getSymbol() { return symbol; }
        public List<Node> getChildren() { return children; }
    }


    // TREE
    private Node root;

    public Tree(String startSymbol) {
        this.root = new Node(startSymbol);
    }

    public Node getRoot() { return root; }

    // PRINT — indented text format

    public void print() {
        System.out.println("\n========== Parse Tree ==========");
        if (root == null) {
            System.out.println("(empty tree)");
            return;
        }
        printNode(root, "", true);
        System.out.println("=================================");
    }

    private void printNode(Node node, String prefix, boolean isLast) {
        String connector = isLast ? "└── " : "├── ";
        String label     = node.symbol;

        // Mark terminals and epsilon visually
        if (node.isLeaf()) {
            if (node.symbol.equals(Grammar.EPSILON))
                label = "ε";
            else
                label = "[" + node.symbol + "]";  // terminals in brackets
        }

        System.out.println(prefix + connector + label);

        String childPrefix = prefix + (isLast ? "    " : "│   ");
        List<Node> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            printNode(children.get(i), childPrefix, i == children.size() - 1);
        }
    }


    // TRAVERSALS
    /** Preorder traversal — returns list of symbols visited */
    public List<String> preorder() {
        List<String> result = new ArrayList<>();
        preorderHelper(root, result);
        return result;
    }

    private void preorderHelper(Node node, List<String> result) {
        if (node == null) return;
        result.add(node.symbol);
        for (Node child : node.getChildren()) preorderHelper(child, result);
    }

    /** Postorder traversal — returns list of symbols visited */
    public List<String> postorder() {
        List<String> result = new ArrayList<>();
        postorderHelper(root, result);
        return result;
    }

    private void postorderHelper(Node node, List<String> result) {
        if (node == null) return;
        for (Node child : node.getChildren()) postorderHelper(child, result);
        result.add(node.symbol);
    }

    /** Returns only the leaf nodes left to right — should match original input */
    public List<String> getLeaves() {
        List<String> leaves = new ArrayList<>();
        getLeavesHelper(root, leaves);
        return leaves;
    }

    private void getLeavesHelper(Node node, List<String> leaves) {
        if (node == null) return;
        if (node.isLeaf()) {
            if (!node.symbol.equals(Grammar.EPSILON))
                leaves.add(node.symbol);
            return;
        }
        for (Node child : node.getChildren()) getLeavesHelper(child, leaves);
    }

    // DOT FORMAT (optional Graphviz output)
    public String toDotFormat() {
        StringBuilder sb  = new StringBuilder();
        sb.append("digraph ParseTree {\n");
        sb.append("  node [shape=ellipse, fontname=\"Helvetica\"];\n");
        Map<Node, Integer> ids = new IdentityHashMap<>();
        assignIds(root, ids, new int[]{0});
        dotHelper(root, ids, sb);
        sb.append("}\n");
        return sb.toString();
    }

    private void assignIds(Node node, Map<Node, Integer> ids, int[] counter) {
        ids.put(node, counter[0]++);
        for (Node child : node.getChildren()) assignIds(child, ids, counter);
    }

    private void dotHelper(Node node, Map<Node, Integer> ids, StringBuilder sb) {
        int id    = ids.get(node);
        String label = node.symbol.equals(Grammar.EPSILON) ? "ε" : node.symbol;

        if (node.isLeaf())
            sb.append("  n").append(id)
                    .append(" [label=\"").append(label)
                    .append("\", shape=box];\n");
        else
            sb.append("  n").append(id)
                    .append(" [label=\"").append(label).append("\"];\n");

        for (Node child : node.getChildren()) {
            sb.append("  n").append(id)
                    .append(" -> n").append(ids.get(child)).append(";\n");
            dotHelper(child, ids, sb);
        }
    }


    // VERIFY
     // Verifies that the leaves of the parse tree match the original input tokens

    public boolean verify(List<String> originalTokens) {
        List<String> leaves = getLeaves();
        return leaves.equals(originalTokens);
    }
}