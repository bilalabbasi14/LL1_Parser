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

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Split on "->"
            String[] parts = line.split("->", 2);
            if (parts.length != 2)
                throw new IllegalArgumentException("Invalid production: " + line);

            String lhs = parts[0].trim();           // e.g. "Expr"
            String rhs = parts[1].trim();           // e.g. "Expr + Term | Term"

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
                productions.get(lhs).add(symbols);
            }
        }

        br.close();

        if (nonTerminals.isEmpty())
            throw new IllegalArgumentException("Grammar file is empty or invalid.");

        startSymbol = nonTerminals.get(0);

        // Derive terminals: anything that appears in RHS but is NOT a non-terminal
        deriveTerminals();
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

        // Find the longest common prefix shared by at least 2 alternatives
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

        // Create new non-terminal: NtPrime (keep adding ' until unique)
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

    /** Returns the first symbol of the longest common prefix group, or null if none. */
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
        List<String> ntList = new ArrayList<>(nonTerminals);

        for (int i = 0; i < ntList.size(); i++) {
            String ai = ntList.get(i);

            // Step 1: eliminate indirect left recursion using Aj (j < i)
            for (int j = 0; j < i; j++) {
                String aj = ntList.get(j);
                substituteIndirect(ai, aj);
            }

            // Step 2: eliminate direct left recursion for Ai
            eliminateDirectLeftRecursion(ai);
        }

        deriveTerminals();
    }

    /**
     * Replace every production Ai -> Aj γ
     * with    Ai -> δ1 γ | δ2 γ | ... where Aj -> δ1 | δ2 | ...
     */
    private void substituteIndirect(String ai, String aj) {
        List<List<String>> aiAlts = productions.get(ai);
        List<List<String>> ajAlts = productions.get(aj);
        List<List<String>> result = new ArrayList<>();

        for (List<String> alt : aiAlts) {
            if (!alt.isEmpty() && alt.get(0).equals(aj)) {
                // Replace with all Aj alternatives prepended to the rest
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
     * For A -> Aα1 | Aα2 | β1 | β2
     * Produce:
     *   A       -> β1 APrime | β2 APrime
     *   APrime  -> α1 APrime | α2 APrime | epsilon
     */
    private void eliminateDirectLeftRecursion(String nt) {
        List<List<String>> alts = productions.get(nt);
        List<List<String>> recursive    = new ArrayList<>(); // Aα
        List<List<String>> nonRecursive = new ArrayList<>(); // β

        for (List<String> alt : alts) {
            if (!alt.isEmpty() && alt.get(0).equals(nt))
                recursive.add(alt.subList(1, alt.size())); // strip leading A
            else
                nonRecursive.add(alt);
        }

        if (recursive.isEmpty()) return; // no direct left recursion

        String primeName = nt + "Prime";
        while (productions.containsKey(primeName)) primeName += "Prime";

        // A -> β1 APrime | β2 APrime
        List<List<String>> newAlts = new ArrayList<>();
        for (List<String> beta : nonRecursive) {
            List<String> newAlt = new ArrayList<>(beta);
            if (newAlt.equals(List.of(EPSILON))) newAlt.clear(); // don't keep epsilon before prime
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

    public String getStartSymbol()                              { return startSymbol; }
    public List<String> getNonTerminals()                      { return nonTerminals; }
    public Set<String> getTerminals()                          { return terminals; }
    public Map<String, List<List<String>>> getProductions()    { return productions; }
    public boolean isNonTerminal(String s)                     { return productions.containsKey(s); }
    public boolean isTerminal(String s)                        { return terminals.contains(s); }
}