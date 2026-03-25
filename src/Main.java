// Main.java
import java.io.*;
import java.util.*;

public class Main {

    // -------------------------------------------------------------------------
    // ParseResult — holds everything produced by one parse() call so we can
    // write it to a file later WITHOUT re-running the parser a second time.
    // This is what fixes both bugs:
    //   Bug 1 — we now write one file per input string set (trace1, trace2 …)
    //   Bug 2 — the error-handler state captured here is from the REAL run,
    //            not a silent second run that overwrites it.
    // -------------------------------------------------------------------------
    private static class ParseResult {
        int            inputNumber;
        List<String>   tokens;
        String         traceOutput;   // full console output of this parse
        boolean        accepted;
        int            errorCount;

        ParseResult(int inputNumber, List<String> tokens,
                    String traceOutput, boolean accepted, int errorCount) {
            this.inputNumber = inputNumber;
            this.tokens      = tokens;
            this.traceOutput = traceOutput;
            this.accepted    = accepted;
            this.errorCount  = errorCount;
        }
    }

    // =========================================================================
    // MAIN
    // =========================================================================
    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║            LL(1) Parser BY                     ║");
        System.out.println("║         Ahmad Bilal and Muhammad Ali           ║");
        System.out.println("╚════════════════════════════════════════════════╝");

        // ── STEP 1: Grammar file ──────────────────────────────────────────────
        System.out.print("\nEnter grammar file path (e.g. input/grammar1.txt): ");
        String grammarFile = scanner.nextLine().trim();

        Grammar grammar = new Grammar();
        try {
            grammar.loadFromFile(grammarFile);
        } catch (IOException e) {
            System.out.println("ERROR: Could not read grammar file: " + e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            System.out.println("ERROR: Invalid grammar format: " + e.getMessage());
            return;
        }

        grammar.printGrammar("Original Grammar");

        // ── STEP 2: Left Factoring ────────────────────────────────────────────
        System.out.println("\n[1/5] Applying Left Factoring...");
        grammar.applyLeftFactoring();
        grammar.printGrammar("After Left Factoring");

        // ── STEP 3: Left Recursion Removal ───────────────────────────────────
        System.out.println("\n[2/5] Removing Left Recursion...");
        grammar.removeLeftRecursion();
        grammar.printGrammar("After Left Recursion Removal");

        // ── STEP 4: FIRST and FOLLOW sets ────────────────────────────────────
        System.out.println("\n[3/5] Computing FIRST and FOLLOW Sets...");
        FirstFollow ff = new FirstFollow(grammar);
        ff.computeFirstSets();
        ff.computeFollowSets();
        ff.printFirstSets();
        ff.printFollowSets();

        // ── STEP 5: Build LL(1) Parsing Table ───────────────────────────────
        System.out.println("\n[4/5] Building LL(1) Parsing Table...");
        Parser parser = new Parser(grammar, ff);
        parser.buildParsingTable();
        parser.printParsingTable();

        if (!parser.isLL1()) {
            System.out.println("\n[WARNING] Grammar is NOT LL(1).");
            System.out.println("Parsing may produce incorrect results due to conflicts.");
            System.out.print("Continue anyway? (yes/no): ");
            String choice = scanner.nextLine().trim().toLowerCase();
            if (!choice.equals("yes") && !choice.equals("y")) {
                System.out.println("Exiting.");
                return;
            }
        }

        // ── STEP 6: Read input strings ───────────────────────────────────────
        System.out.println("\n[5/5] Parsing Input Strings...");
        System.out.print("Enter input file path (e.g. input/grammar1_valid.txt): ");
        String inputFile = scanner.nextLine().trim();

        List<List<String>> inputStrings = readInputFile(inputFile);
        if (inputStrings == null) return;

        if (inputStrings.isEmpty()) {
            System.out.println("ERROR: Input file is empty.");
            return;
        }

        // ── STEP 7: Parse each input string ──────────────────────────────────
        // We capture the full console output of every parse() call into a
        // ParseResult. This is the ONLY time the parser runs — there is no
        // second run anywhere in the program.

        int              accepted  = 0;
        int              rejected  = 0;
        List<String>     summary   = new ArrayList<>();
        List<ParseResult> results  = new ArrayList<>();

        for (int i = 0; i < inputStrings.size(); i++) {
            List<String> tokens = inputStrings.get(i);

            // Redirect System.out to a ByteArrayOutputStream so we capture
            // the trace text AND still let it print to the real console.
            ByteArrayOutputStream baos      = new ByteArrayOutputStream();
            PrintStream           capture   = new PrintStream(baos);
            PrintStream           realOut   = System.out;
            TeeOutputStream       tee       = new TeeOutputStream(realOut, capture);
            System.setOut(new PrintStream(tee));

            parser.parse(tokens, i + 1);

            // Restore console immediately after the parse
            System.setOut(realOut);

            String  traceText  = baos.toString();
            boolean wasAccepted = !parser.getErrorHandler().hasErrors();
            int     errCount    = parser.getErrorHandler().getErrorCount();

            results.add(new ParseResult(i + 1, tokens, traceText, wasAccepted, errCount));

            if (wasAccepted) {
                accepted++;
                summary.add("Input #" + (i + 1) + " [ " + String.join(" ", tokens)
                        + " ] -> ACCEPTED");
            } else {
                rejected++;
                summary.add("Input #" + (i + 1) + " [ " + String.join(" ", tokens)
                        + " ] -> REJECTED (" + errCount + " error(s))");
                parser.getErrorHandler().printErrorReport();
            }
        }

        // ── STEP 8: Final summary ────────────────────────────────────────────
        printFinalSummary(summary, accepted, rejected);

        // ── STEP 9: Optionally save all outputs to files ─────────────────────
        System.out.print("\nSave outputs to files? (yes/no): ");
        String save = scanner.nextLine().trim().toLowerCase();
        if (save.equals("yes") || save.equals("y")) {
            // Derive a base name from the input file for naming trace files.
            // e.g. "input/grammar2_valid.txt" -> base = "grammar2_valid"
            String base = deriveBaseName(inputFile);
            saveOutputs(grammar, ff, parser, results, base);
        }

        System.out.println("\nDone. Goodbye!");
        scanner.close();
    }

    // =========================================================================
    // TeeOutputStream — writes to two streams simultaneously so the trace
    // appears on the console AND gets captured into the ByteArrayOutputStream.
    // =========================================================================
    private static class TeeOutputStream extends OutputStream {
        private final OutputStream primary;
        private final OutputStream secondary;

        TeeOutputStream(OutputStream primary, OutputStream secondary) {
            this.primary   = primary;
            this.secondary = secondary;
        }

        @Override public void write(int b) throws IOException {
            primary.write(b);
            secondary.write(b);
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            primary.write(b, off, len);
            secondary.write(b, off, len);
        }

        @Override public void flush() throws IOException {
            primary.flush();
            secondary.flush();
        }
    }

    // =========================================================================
    // READ INPUT FILE
    // =========================================================================
    private static List<List<String>> readInputFile(String filename) {
        List<List<String>> result = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                List<String> tokens = new ArrayList<>(Arrays.asList(line.split("\\s+")));
                result.add(tokens);
            }
            br.close();
        } catch (IOException e) {
            System.out.println("ERROR: Could not read input file: " + e.getMessage());
            return null;
        }
        return result;
    }

    // =========================================================================
    // SAVE OUTPUTS — Bug 1 fix: one trace file per input file (not one global
    // parsing_trace.txt), and a separate parse_trees.txt for accepted strings.
    // Bug 2 fix: uses pre-captured ParseResult objects — parser never re-runs.
    // =========================================================================
    private static void saveOutputs(Grammar grammar, FirstFollow ff,
                                    Parser parser,
                                    List<ParseResult> results,
                                    String base) {
        new File("output").mkdirs();

        // ── grammar_transformed.txt ───────────────────────────────────────────
        saveToFile("output/grammar_transformed.txt", ps -> {
            grammar.printGrammar("Transformed Grammar");
        });

        // ── first_follow_sets.txt ─────────────────────────────────────────────
        saveToFile("output/first_follow_sets.txt", ps -> {
            ff.printFirstSets();
            ff.printFollowSets();
        });

        // ── parsing_table.txt ─────────────────────────────────────────────────
        saveToFile("output/parsing_table.txt", ps -> {
            parser.printParsingTable();
        });

        // ── parsing_trace_<base>.txt — one file per input file, named after it.
        // e.g. grammar2_valid  -> output/parsing_trace_grammar2_valid.txt
        //      grammar2_errors -> output/parsing_trace_grammar2_errors.txt
        // This satisfies the assignment requirement for separate trace files
        // (parsing_trace1.txt / parsing_trace2.txt etc.) while also being
        // self-describing so you know which grammar and input set it belongs to.
        String traceFileName = "output/parsing_trace_" + base + ".txt";
        try (PrintWriter pw = new PrintWriter(new FileWriter(traceFileName))) {
            for (ParseResult r : results) {
                // Write the captured trace text exactly as it appeared on screen
                pw.print(r.traceOutput);
            }
            System.out.println("Saved -> " + traceFileName);
        } catch (IOException e) {
            System.out.println("ERROR saving " + traceFileName + ": " + e.getMessage());
        }

        // ── parse_trees_<base>.txt — only accepted strings, their tree printed.
        String treeFileName = "output/parse_trees_" + base + ".txt";
        long acceptedCount = results.stream().filter(r -> r.accepted).count();
        if (acceptedCount > 0) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(treeFileName))) {
                pw.println("========== Parse Trees for: " + base + " ==========");
                pw.println();
                for (ParseResult r : results) {
                    if (!r.accepted) continue;
                    pw.println("Input #" + r.inputNumber
                            + ": " + String.join(" ", r.tokens));
                    // The tree was already printed inside r.traceOutput.
                    // Extract just the tree section from the captured text.
                    String tree = extractTreeSection(r.traceOutput);
                    pw.println(tree.isEmpty() ? "(tree not found in output)" : tree);
                    pw.println();
                }
                System.out.println("Saved -> " + treeFileName);
            } catch (IOException e) {
                System.out.println("ERROR saving " + treeFileName + ": " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Functional interface so saveToFile() can accept a lambda that uses
     * System.out (which we temporarily redirect to the file).
     */
    @FunctionalInterface
    private interface OutputAction {
        void run(PrintStream ps) throws IOException;
    }

    /**
     * Redirects System.out to a file, runs the action, then restores System.out.
     * This is the same pattern as the original code but extracted to avoid
     * repetition across the four output files.
     */
    private static void saveToFile(String path, OutputAction action) {
        PrintStream realOut = System.out;
        try (PrintStream ps = new PrintStream(new FileOutputStream(path))) {
            System.setOut(ps);
            action.run(ps);
            System.setOut(realOut);
            realOut.println("Saved -> " + path);
        } catch (IOException e) {
            System.setOut(realOut);
            realOut.println("ERROR saving " + path + ": " + e.getMessage());
        }
    }

    /**
     * Extracts the parse tree section from a captured trace string.
     * The Tree.print() method wraps the tree between:
     *   "========== Parse Tree =========="
     *   "================================="
     */
    private static String extractTreeSection(String traceOutput) {
        String start = "========== Parse Tree ==========";
        String end   = "=================================";
        int s = traceOutput.indexOf(start);
        if (s == -1) return "";
        int e = traceOutput.indexOf(end, s);
        if (e == -1) return traceOutput.substring(s);
        return traceOutput.substring(s, e + end.length());
    }

    /**
     * Derives a clean base name from an input file path for use in output
     * file names. Strips the directory and extension.
     * e.g. "input/grammar2_valid.txt" -> "grammar2_valid"
     *      "grammar3_errors.txt"       -> "grammar3_errors"
     */
    private static String deriveBaseName(String filePath) {
        // Strip directory
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        String name = (slash >= 0) ? filePath.substring(slash + 1) : filePath;
        // Strip extension
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(0, dot) : name;
    }

    // =========================================================================
    // FINAL SUMMARY
    // =========================================================================
    private static void printFinalSummary(List<String> summary,
                                          int accepted, int rejected) {
        System.out.println("\n╔═══════════════════════════════════════════════════╗");
        System.out.println("║                 Parsing Summary                   ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        for (String line : summary) {
            System.out.println("║  " + line);
        }
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.printf ("║  Total: %-5d  Accepted: %-5d  Rejected: %-5d  ║%n",
                accepted + rejected, accepted, rejected);
        System.out.println("╚═══════════════════════════════════════════════════╝");
    }
}