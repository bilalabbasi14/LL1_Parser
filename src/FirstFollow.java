import java.util.*;

public class FirstFollow {

    private Grammar grammar;

    // FIRST sets for all non-terminals
    private Map<String, Set<String>> firstSets;

    // FOLLOW sets for all non-terminals
    private Map<String, Set<String>> followSets;

    public FirstFollow(Grammar grammar) {
        this.grammar   = grammar;
        this.firstSets  = new LinkedHashMap<>();
        this.followSets = new LinkedHashMap<>();

        // Initialize empty sets for all non-terminals
        for (String nt : grammar.getNonTerminals()) {
            firstSets.put(nt, new LinkedHashSet<>());
            followSets.put(nt, new LinkedHashSet<>());
        }
    }

    // FIRST SET COMPUTATION

    public void computeFirstSets() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String nt : grammar.getNonTerminals()) {
                for (List<String> alt : grammar.getProductions().get(nt)) {
                    changed |= addAllToSet(firstSets.get(nt), computeFirstOfSequence(alt));
                }
            }
        }
    }

    /**
     * Compute FIRST of a sequence of symbols [Y1, Y2, ..., Yn]
     * This is used both for non-terminal FIRST computation
     * and during parsing table construction.
     */
    public Set<String> computeFirstOfSequence(List<String> symbols) {
        Set<String> result = new LinkedHashSet<>();

        if (symbols.isEmpty() || symbols.get(0).equals(Grammar.EPSILON)) {
            result.add(Grammar.EPSILON);
            return result;
        }

        for (String sym : symbols) {
            if (!grammar.isNonTerminal(sym)) {
                // sym is a terminal
                result.add(sym);
                return result; // terminals don't derive epsilon, stop here
            }

            // sym is a non-terminal
            Set<String> firstOfSym = firstSets.get(sym);

            // Add FIRST(sym) - {epsilon}
            for (String s : firstOfSym) {
                if (!s.equals(Grammar.EPSILON)) result.add(s);
            }

            // If sym cannot derive epsilon, stop
            if (!firstOfSym.contains(Grammar.EPSILON)) return result;

            // Otherwise continue to next symbol
        }

        // All symbols can derive epsilon
        result.add(Grammar.EPSILON);
        return result;
    }

    // FOLLOW SET COMPUTATION

    public void computeFollowSets() {
        // Rule 1: add $ to FOLLOW of start symbol
        followSets.get(grammar.getStartSymbol()).add(Grammar.EOF);

        boolean changed = true;
        while (changed) {
            changed = false;

            for (String nt : grammar.getNonTerminals()) {
                for (List<String> alt : grammar.getProductions().get(nt)) {
                    for (int i = 0; i < alt.size(); i++) {
                        String sym = alt.get(i);

                        // Only compute FOLLOW for non-terminals
                        if (!grammar.isNonTerminal(sym)) continue;

                        // Beta = everything after sym in this production
                        List<String> beta = alt.subList(i + 1, alt.size());

                        // Rule 2: add FIRST(beta) - {epsilon} to FOLLOW(sym)
                        Set<String> firstOfBeta = computeFirstOfSequence(beta);
                        for (String s : firstOfBeta) {
                            if (!s.equals(Grammar.EPSILON)) {
                                changed |= followSets.get(sym).add(s);
                            }
                        }

                        // Rule 3: if epsilon in FIRST(beta), add FOLLOW(nt) to FOLLOW(sym)
                        if (firstOfBeta.contains(Grammar.EPSILON)) {
                            changed |= addAllToSet(followSets.get(sym), followSets.get(nt));
                        }
                    }
                }
            }
        }
    }


    // DISPLAY
    public void printFirstSets() {
        System.out.println("\n========== FIRST Sets ==========");
        int colWidth = 20;
        System.out.printf("%-" + colWidth + "s | %s%n", "Non-Terminal", "FIRST Set");
        System.out.println("-".repeat(colWidth) + "-+-" + "-".repeat(40));
        for (String nt : grammar.getNonTerminals()) {
            System.out.printf("%-" + colWidth + "s | { %s }%n",
                    nt, String.join(", ", firstSets.get(nt)));
        }
    }

    public void printFollowSets() {
        System.out.println("\n========== FOLLOW Sets ==========");
        int colWidth = 20;
        System.out.printf("%-" + colWidth + "s | %s%n", "Non-Terminal", "FOLLOW Set");
        System.out.println("-".repeat(colWidth) + "-+-" + "-".repeat(40));
        for (String nt : grammar.getNonTerminals()) {
            System.out.printf("%-" + colWidth + "s | { %s }%n",
                    nt, String.join(", ", followSets.get(nt)));
        }
    }


    // HELPER
    /** Returns true if the target set changed. */
    private boolean addAllToSet(Set<String> target, Set<String> source) {
        return target.addAll(source);
    }
    // GETTERS

    public Map<String, Set<String>> getFirstSets()  { return firstSets; }
    public Map<String, Set<String>> getFollowSets() { return followSets; }

    public Set<String> getFirst(String nt)  { return firstSets.get(nt); }
    public Set<String> getFollow(String nt) { return followSets.get(nt); }
}