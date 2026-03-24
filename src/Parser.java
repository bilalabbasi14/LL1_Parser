// Parser.java
import java.util.*;

public class Parser {

    private Grammar grammar;
    private FirstFollow firstFollow;
    private ErrorHandler errorHandler;
    private Tree parseTree;

    // Parsing table: M[NonTerminal][Terminal] -> production (List<String>)
    private Map<String, Map<String, List<String>>> parsingTable;

    // Flag to track if grammar is LL(1)
    private boolean isLL1;

    public Parser(Grammar grammar, FirstFollow firstFollow) {
        this.grammar      = grammar;
        this.firstFollow  = firstFollow;
        this.errorHandler = new ErrorHandler();
        this.parsingTable = new LinkedHashMap<>();
        this.isLL1        = true;

        // Initialize table rows for each non-terminal
        for (String nt : grammar.getNonTerminals()) {
            parsingTable.put(nt, new LinkedHashMap<>());
        }
    }

    // TABLE CONSTRUCTION

    public void buildParsingTable() {
        for (String nt : grammar.getNonTerminals()) {
            for (List<String> alt : grammar.getProductions().get(nt)) {

                Set<String> firstOfAlt = firstFollow.computeFirstOfSequence(alt);

                // Rule 1: for each terminal a in FIRST(alt), add production to M[nt][a]
                for (String symbol : firstOfAlt) {
                    if (!symbol.equals(Grammar.EPSILON)) {
                        addToTable(nt, symbol, alt);
                    }
                }

                // Rule 2: if epsilon in FIRST(alt), use FOLLOW(nt)
                if (firstOfAlt.contains(Grammar.EPSILON)) {
                    for (String follow : firstFollow.getFollow(nt)) {
                        addToTable(nt, follow, alt);
                    }
                }
            }
        }
    }

    /** Adds a production to table cell M[nt][terminal], flags conflict if cell already filled. */
    private void addToTable(String nt, String terminal, List<String> production) {
        Map<String, List<String>> row = parsingTable.get(nt);
        if (row.containsKey(terminal)) {
            // Conflict detected — not LL(1)
            isLL1 = false;
            System.out.println("[WARNING] LL(1) conflict at M[" + nt + "][" + terminal + "]");
            System.out.println("  Existing : " + row.get(terminal));
            System.out.println("  Incoming : " + production);
        } else {
            row.put(terminal, new ArrayList<>(production));
        }
    }

    // PARSING ALGORITHM

    public void parse(List<String> tokens, int inputNumber) {
        System.out.println("\n========== Parsing Input #" + inputNumber + " ==========");
        System.out.println("Input: " + String.join(" ", tokens) + " $");

        // Reset per-parse state
        errorHandler.reset();
        parseTree = new Tree(grammar.getStartSymbol());

        // Setup stack  [$, StartSymbol]  — top is end of list
        ParserStack stack = new ParserStack();
        stack.push(Grammar.EOF);
        stack.push(grammar.getStartSymbol());

        // Setup input with EOF marker
        List<String> input = new ArrayList<>(tokens);
        input.add(Grammar.EOF);
        int inputPtr = 0;

        // Map stack symbols to tree nodes for parse tree building
        Deque<Tree.Node> nodeStack = new ArrayDeque<>();
        nodeStack.push(parseTree.getRoot());

        // Print table header
        printTraceHeader();

        int step = 1;
        boolean accepted = false;

        while (!stack.isEmpty()) {
            String top   = stack.peek();
            String current = input.get(inputPtr);

            // Build remaining input string for display
            String remainingInput = String.join(" ", input.subList(inputPtr, input.size()));
            String stackStr       = stack.toString();

            // ---- CASE 1: Both are EOF -> Accept ----
            if (top.equals(Grammar.EOF) && current.equals(Grammar.EOF)) {
                printTraceRow(step++, stackStr, remainingInput, "Accept");
                accepted = true;
                break;
            }

            // ---- CASE 2: Top matches current input -> Match ----
            if (top.equals(current)) {
                printTraceRow(step++, stackStr, remainingInput, "Match " + current);
                stack.pop();
                inputPtr++;
                if (!nodeStack.isEmpty()) nodeStack.pop();
                continue;
            }

            // ---- CASE 3: Top is non-terminal -> Expand ----
            if (grammar.isNonTerminal(top)) {
                List<String> production = parsingTable
                        .getOrDefault(top, Collections.emptyMap())
                        .get(current);

                if (production != null) {
                    String action = top + " -> " + String.join(" ", production);
                    printTraceRow(step++, stackStr, remainingInput, action);

                    stack.pop();
                    Tree.Node parentNode = nodeStack.isEmpty() ? null : nodeStack.pop();

                    // Push production symbols in reverse order
                    if (!production.get(0).equals(Grammar.EPSILON)) {
                        List<Tree.Node> children = new ArrayList<>();
                        for (String sym : production) {
                            children.add(new Tree.Node(sym));
                        }
                        if (parentNode != null) parentNode.addChildren(children);

                        for (int i = production.size() - 1; i >= 0; i--) {
                            stack.push(production.get(i));
                            nodeStack.push(children.get(i));
                        }
                    } else {
                        // Epsilon production — add epsilon leaf to tree
                        if (parentNode != null)
                            parentNode.addChild(new Tree.Node(Grammar.EPSILON));
                    }

                } else {
                    // Empty table entry — error
                    String msg = errorHandler.handleEmptyEntry(top, current, step);
                    printTraceRow(step++, stackStr, remainingInput, msg);

                    // Panic mode: pop the non-terminal, try to recover
                    stack.pop();
                    if (!nodeStack.isEmpty()) nodeStack.pop();

                    // Skip input until we find something in FOLLOW(top)
                    Set<String> follow = firstFollow.getFollow(top);
                    while (!current.equals(Grammar.EOF) && !follow.contains(current)) {
                        inputPtr++;
                        current = input.get(inputPtr);
                    }
                }
                continue;
            }

            // ---- CASE 4: Top is terminal but doesn't match -> Error ----
            String msg = errorHandler.handleMismatch(top, current, step);
            printTraceRow(step++, stackStr, remainingInput, msg);

            // Recovery: pop the mismatched terminal from stack
            stack.pop();
            if (!nodeStack.isEmpty()) nodeStack.pop();
        }

        if (accepted) {
            System.out.println("\nResult: String accepted successfully!");
            parseTree.print();
        } else {
            System.out.println("\nResult: Parsing completed with "
                    + errorHandler.getErrorCount() + " error(s).");
        }
    }

    // DISPLAY

    public void printParsingTable() {
        System.out.println("\n========== LL(1) Parsing Table ==========");

        // Collect all terminals + $ as columns
        List<String> columns = new ArrayList<>(grammar.getTerminals());
        if (!columns.contains(Grammar.EOF)) columns.add(Grammar.EOF);

        // Column width
        int ntWidth  = 18;
        int colWidth = 22;

        // Header
        System.out.printf("%-" + ntWidth + "s", "Non-Terminal");
        for (String col : columns)
            System.out.printf("| %-" + (colWidth - 1) + "s", col);
        System.out.println();
        System.out.println("-".repeat(ntWidth + columns.size() * colWidth));

        // Rows
        for (String nt : grammar.getNonTerminals()) {
            System.out.printf("%-" + ntWidth + "s", nt);
            Map<String, List<String>> row = parsingTable.get(nt);
            for (String col : columns) {
                if (row.containsKey(col)) {
                    String cell = nt + "->" + String.join(" ", row.get(col));
                    // Truncate if too long for display
                    if (cell.length() > colWidth - 2)
                        cell = cell.substring(0, colWidth - 5) + "...";
                    System.out.printf("| %-" + (colWidth - 1) + "s", cell);
                } else {
                    System.out.printf("| %-" + (colWidth - 1) + "s", "");
                }
            }
            System.out.println();
        }

        System.out.println("\nGrammar is " + (isLL1 ? "LL(1) ✓" : "NOT LL(1) ✗"));
    }

    private void printTraceHeader() {
        System.out.println();
        System.out.printf("%-6s | %-35s | %-25s | %s%n",
                "Step", "Stack (bottom->top)", "Remaining Input", "Action");
        System.out.println("-".repeat(100));
    }

    private void printTraceRow(int step, String stack, String input, String action) {
        System.out.printf("%-6d | %-35s | %-25s | %s%n", step, stack, input, action);
    }

    // GETTERS
    public boolean isLL1()                                              { return isLL1; }
    public Map<String, Map<String, List<String>>> getParsingTable()     { return parsingTable; }
    public ErrorHandler getErrorHandler() { return errorHandler; }
}