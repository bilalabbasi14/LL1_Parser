// Grammar.java
import java.io.*;
import java.util.*;

public class Grammar {

    // The start symbol (first non-terminal encountered)
    private String startSymbol;

    // Ordered list of non-terminals (order matters for indirect left recursion removal)
    private List<String> nonTerminals;

    // All terminals found in the grammar
    private Set<String> terminals;

    // Productions: NonTerminal -> List of alternatives, each alternative is a List<String> of symbols
    // e.g. "Expr" -> [["Term", "ExprPrime"], ["id"]]
    private Map<String, List<List<String>>> productions;

    public static final String EPSILON = "epsilon";
    public static final String EOF     = "$";

    public Grammar() {
        nonTerminals = new ArrayList<>();
        terminals    = new LinkedHashSet<>();
        productions  = new LinkedHashMap<>();
    }

    // FILE READING

    public void loadFromFile(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;
        int    lineNumber = 0;

        while ((line = br.readLine()) != null) {
            lineNumber++;
            line = line.trim();
            if (line.isEmpty()) continue;

            // Split on "->"
            String[] parts = line.split("->", 2);
            if (parts.length != 2)
                throw new IllegalArgumentException(
                        "Line " + lineNumber + ": invalid production (missing '->'): " + line);

            String lhs = parts[0].trim();
            String rhs = parts[1].trim();

            // --- VALIDATION: non-terminals must be multi-character and start with uppercase ---
            // Single-character names like E, T, F are explicitly disallowed by the spec.
            // A valid non-terminal: length >= 2, first char uppercase letter.
            if (!isValidNonTerminalName(lhs)) {
                br.close();
                throw new IllegalArgumentException(
                        "Line " + lineNumber + ": non-terminal '" + lhs + "' is invalid. "
                                + "Non-terminals must start with an uppercase letter and be at least "
                                + "2 characters long (e.g. Expr, Term, Factor). "
                                + "Single-character names like E, T, F are not allowed.");
            }

            // Register non-terminal
            if (!productions.containsKey(lhs)) {
                nonTerminals.add(lhs);
                productions.put(lhs, new ArrayList<>());
            }

            // Split alternatives on "|"
            String[] alternatives = rhs.split("\\|");
            for (String alt : alternatives) {
                List<String> symbols = new ArrayList<>();
                for (String sym : alt.trim().split("\\s+")) {
                    if (!sym.isEmpty()) symbols.add(sym);
                }
                if (symbols.isEmpty()) {
                    br.close();
                    throw new IllegalArgumentException(
                            "Line " + lineNumber + ": empty alternative in production for '"
                                    + lhs + "'. Use 'epsilon' to denote an empty production.");
                }
                productions.get(lhs).add(symbols);
            }
        }

        br.close();

        if (nonTerminals.isEmpty())
            throw new IllegalArgumentException("Grammar file is empty or contains no valid productions.");

        startSymbol = nonTerminals.get(0);

        // After all lines are read, validate that every non-terminal symbol referenced
        // on the RHS that looks like a non-terminal (starts with uppercase) is also
        // declared as an LHS. Catches typos early.
        validateRhsNonTerminals();

        // Derive terminals: anything on the RHS that is NOT a declared non-terminal
        deriveTerminals();
    }

    /**
     * A valid non-terminal name must:
     *   1. Be at least 2 characters long (single-char names like E, T, F are banned).
     *   2. Start with an uppercase letter.
     * This method is intentionally strict about rule 1 — the assignment spec
     * explicitly forbids single-character non-terminals.
     */
    private boolean isValidNonTerminalName(String name) {
        if (name == null || name.length() < 2) return false;
        return Character.isUpperCase(name.charAt(0));
    }

    /**
     * Warn about any RHS symbol that looks like a non-terminal (starts uppercase,
     * length >= 2) but was never declared on a LHS. This catches typos such as
     * writing "Epxr" instead of "Expr".
     * We warn rather than throw so that grammars using capitalized terminals
     * (e.g. keywords) are not broken.
     */
    private void validateRhsNonTerminals() {
        for (Map.Entry<String, List<List<String>>> entry : productions.entrySet()) {
            for (List<String> alt : entry.getValue()) {
                for (String sym : alt) {
                    if (sym.equals(EPSILON)) continue;
                    if (sym.length() >= 2
                            && Character.isUpperCase(sym.charAt(0))
                            && !productions.containsKey(sym)) {
                        System.out.println("[WARNING] Symbol '" + sym
                                + "' in production for '" + entry.getKey()
                                + "' looks like a non-terminal but was never declared "
                                + "on a left-hand side. If it is a terminal, this is fine.");
                    }
                }
            }
        }
    }

    private void deriveTerminals() {
        terminals.clear();
        for (List<List<String>> alts : productions.values()) {
            for (List<String> alt : alts) {
                for (String sym : alt) {
                    if (!sym.equals(EPSILON) && !productions.containsKey(sym)) {
                        terminals.add(sym);
                    }
                }
            }
        }
    }

    // LEFT FACTORING

    public void applyLeftFactoring() {
        boolean changed = true;
        while (changed) {
            changed = false;
            // Iterate over a snapshot of current non-terminals
            List<String> nts = new ArrayList<>(nonTerminals);
            for (String nt : nts) {
                if (leftFactorOne(nt)) changed = true;
            }
        }
        deriveTerminals();
    }

    /**
     * Applies one round of left factoring to a single non-terminal.
     * Returns true if any factoring was done.
     */
    private boolean leftFactorOne(String nt) {
        List<List<String>> alts = productions.get(nt);

        // Find the first symbol shared by at least 2 alternatives
        String prefix = findLongestCommonPrefix(alts);
        if (prefix == null) return false;

        // Partition alts into those starting with prefix and those that don't
        List<List<String>> withPrefix    = new ArrayList<>();
        List<List<String>> withoutPrefix = new ArrayList<>();

        for (List<String> alt : alts) {
            if (!alt.isEmpty() && alt.get(0).equals(prefix))
                withPrefix.add(alt);
            else
                withoutPrefix.add(alt);
        }

        // Create new non-terminal: NtPrime (append more "Prime" until unique)
        String newNt = nt + "Prime";
        while (productions.containsKey(newNt)) newNt += "Prime";

        // Build suffix alternatives for newNt
        List<List<String>> newAlts = new ArrayList<>();
        for (List<String> alt : withPrefix) {
            List<String> suffix = alt.subList(1, alt.size());
            if (suffix.isEmpty())
                newAlts.add(new ArrayList<>(List.of(EPSILON)));
            else
                newAlts.add(new ArrayList<>(suffix));
        }

        // Update original NT: keep without-prefix alts + add [prefix, newNt]
        List<String> factoredAlt = new ArrayList<>();
        factoredAlt.add(prefix);
        factoredAlt.add(newNt);

        List<List<String>> updatedAlts = new ArrayList<>(withoutPrefix);
        updatedAlts.add(factoredAlt);
        productions.put(nt, updatedAlts);

        // Register newNt
        nonTerminals.add(newNt);
        productions.put(newNt, newAlts);

        return true;
    }

    /** Returns the first symbol of the longest common prefix group, or null if none exists. */
    private String findLongestCommonPrefix(List<List<String>> alts) {
        Map<String, Integer> firstSymbolCount = new LinkedHashMap<>();
        for (List<String> alt : alts) {
            if (!alt.isEmpty()) {
                firstSymbolCount.merge(alt.get(0), 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : firstSymbolCount.entrySet()) {
            if (e.getValue() >= 2) return e.getKey();
        }
        return null;
    }


    // LEFT RECURSION REMOVAL

    public void removeLeftRecursion() {
        // Snapshot taken before the loop so newly added Prime non-terminals
        // are not processed by the algorithm — they are already recursion-free.
        List<String> ntList = new ArrayList<>(nonTerminals);

        for (int i = 0; i < ntList.size(); i++) {
            String ai = ntList.get(i);

            // Step 1: eliminate indirect left recursion via Aj (j < i)
            for (int j = 0; j < i; j++) {
                String aj = ntList.get(j);
                substituteIndirect(ai, aj);
            }

            // Step 2: eliminate any direct left recursion that remains in Ai
            eliminateDirectLeftRecursion(ai);
        }

        deriveTerminals();
    }

    /**
     * Replace every production  Ai -> Aj γ
     * with                       Ai -> δ1 γ | δ2 γ | ...
     * where Aj -> δ1 | δ2 | ...
     */
    private void substituteIndirect(String ai, String aj) {
        List<List<String>> aiAlts = productions.get(ai);
        List<List<String>> ajAlts = productions.get(aj);
        List<List<String>> result = new ArrayList<>();

        for (List<String> alt : aiAlts) {
            if (!alt.isEmpty() && alt.get(0).equals(aj)) {
                // Replace Aj with each of its alternatives, appending the rest of alt
                List<String> gamma = alt.subList(1, alt.size());
                for (List<String> delta : ajAlts) {
                    List<String> newAlt = new ArrayList<>(delta);
                    newAlt.addAll(gamma);
                    result.add(newAlt);
                }
            } else {
                result.add(new ArrayList<>(alt));
            }
        }

        productions.put(ai, result);
    }

    /**
     * For  A -> A α1 | A α2 | β1 | β2
     * Produce:
     *   A      -> β1 APrime | β2 APrime
     *   APrime -> α1 APrime | α2 APrime | epsilon
     */
    private void eliminateDirectLeftRecursion(String nt) {
        List<List<String>> alts        = productions.get(nt);
        List<List<String>> recursive    = new ArrayList<>(); // A α  (left-recursive)
        List<List<String>> nonRecursive = new ArrayList<>(); // β    (non-recursive)

        for (List<String> alt : alts) {
            if (!alt.isEmpty() && alt.get(0).equals(nt))
                recursive.add(alt.subList(1, alt.size())); // strip the leading A
            else
                nonRecursive.add(alt);
        }

        if (recursive.isEmpty()) return; // nothing to do

        String primeName = nt + "Prime";
        while (productions.containsKey(primeName)) primeName += "Prime";

        // A -> β1 APrime | β2 APrime
        List<List<String>> newAlts = new ArrayList<>();
        for (List<String> beta : nonRecursive) {
            List<String> newAlt = new ArrayList<>(beta);
            // If beta was just epsilon, don't keep it before the prime non-terminal
            if (newAlt.equals(List.of(EPSILON))) newAlt.clear();
            newAlt.add(primeName);
            newAlts.add(newAlt);
        }
        productions.put(nt, newAlts);

        // APrime -> α1 APrime | α2 APrime | epsilon
        List<List<String>> primeAlts = new ArrayList<>();
        for (List<String> alpha : recursive) {
            List<String> newAlt = new ArrayList<>(alpha);
            newAlt.add(primeName);
            primeAlts.add(newAlt);
        }
        primeAlts.add(new ArrayList<>(List.of(EPSILON))); // epsilon alternative

        nonTerminals.add(primeName);
        productions.put(primeName, primeAlts);
    }

    // DISPLAY

    public void printGrammar(String title) {
        System.out.println("\n========== " + title + " ==========");
        for (String nt : nonTerminals) {
            List<List<String>> alts = productions.get(nt);
            StringBuilder sb = new StringBuilder(nt + " -> ");
            for (int i = 0; i < alts.size(); i++) {
                sb.append(String.join(" ", alts.get(i)));
                if (i < alts.size() - 1) sb.append(" | ");
            }
            System.out.println(sb);
        }
    }

    // GETTERS

    public String                           getStartSymbol()   { return startSymbol; }
    public List<String>                     getNonTerminals()  { return nonTerminals; }
    public Set<String>                      getTerminals()     { return terminals; }
    public Map<String, List<List<String>>>  getProductions()   { return productions; }
    public boolean isNonTerminal(String s)                     { return productions.containsKey(s); }
    public boolean isTerminal(String s)                        { return terminals.contains(s); }
}