// Main.java
import java.io.*;
import java.util.*;

public class Main {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║            LL(1) Parser BY                     ║");
        System.out.println("║         Ahmad Bilal and Muhammad Ali           ║");
        System.out.println("╚════════════════════════════════════════════════╝");

        // STEP 1: Get grammar file path
        System.out.print("\nEnter grammar file path (e.g. input/grammar1.txt): ");
        String grammarFile = scanner.nextLine().trim();

        // STEP 2: Load & display original grammar
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

        // STEP 3: Left Factoring
        System.out.println("\n[1/5] Applying Left Factoring...");
        grammar.applyLeftFactoring();
        grammar.printGrammar("After Left Factoring");

        // STEP 4: Left Recursion Removal
        System.out.println("\n[2/5] Removing Left Recursion...");
        grammar.removeLeftRecursion();
        grammar.printGrammar("After Left Recursion Removal");
        // STEP 5: FIRST and FOLLOW sets

        System.out.println("\n[3/5] Computing FIRST and FOLLOW Sets...");
        FirstFollow ff = new FirstFollow(grammar);
        ff.computeFirstSets();
        ff.computeFollowSets();
        ff.printFirstSets();
        ff.printFollowSets();


        // STEP 6: Build LL(1) Parsing Table

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

        // STEP 7: Parse input strings

        System.out.println("\n[5/5] Parsing Input Strings...");
        System.out.print("Enter input file path (e.g. input/input_valid.txt): ");
        String inputFile = scanner.nextLine().trim();

        List<List<String>> inputStrings = readInputFile(inputFile);
        if (inputStrings == null) return;

        if (inputStrings.isEmpty()) {
            System.out.println("ERROR: Input file is empty.");
            return;
        }

        // STEP 8: Parse each input string and collect results

        int accepted = 0;
        int rejected = 0;
        List<String> summary = new ArrayList<>();

        for (int i = 0; i < inputStrings.size(); i++) {
            List<String> tokens = inputStrings.get(i);
            parser.parse(tokens, i + 1);

            // Track results for summary
            ErrorHandler eh = parser.getErrorHandler();
            if (!eh.hasErrors()) {
                accepted++;
                summary.add("Input #" + (i + 1) + " [ " + String.join(" ", tokens)
                        + " ] -> ACCEPTED");
            } else {
                rejected++;
                summary.add("Input #" + (i + 1) + " [ " + String.join(" ", tokens)
                        + " ] -> REJECTED (" + eh.getErrorCount() + " error(s))");
                eh.printErrorReport();
            }
        }

        // STEP 9: Final summary
        printFinalSummary(summary, accepted, rejected);

        // STEP 10: Optional — save outputs to files

        System.out.print("\nSave outputs to files? (yes/no): ");
        String save = scanner.nextLine().trim().toLowerCase();
        if (save.equals("yes") || save.equals("y")) {
            saveOutputs(grammar, ff, parser, inputStrings);
        }

        System.out.println("\nDone. Goodbye!");
        scanner.close();
    }

    // READ INPUT FILE

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
    // SAVE OUTPUTS TO FILES

    private static void saveOutputs(Grammar grammar, FirstFollow ff,
                                    Parser parser, List<List<String>> inputs) {
        new File("output").mkdirs();

        // --- grammar_transformed.txt ---
        try (PrintStream ps = new PrintStream("output/grammar_transformed.txt")) {
            PrintStream old = System.out;
            System.setOut(ps);
            grammar.printGrammar("Transformed Grammar");
            System.setOut(old);
            System.out.println("Saved -> output/grammar_transformed.txt");
        } catch (IOException e) {
            System.out.println("ERROR saving grammar_transformed.txt: " + e.getMessage());
        }

        // --- first_follow_sets.txt ---
        try (PrintStream ps = new PrintStream("output/first_follow_sets.txt")) {
            PrintStream old = System.out;
            System.setOut(ps);
            ff.printFirstSets();
            ff.printFollowSets();
            System.setOut(old);
            System.out.println("Saved -> output/first_follow_sets.txt");
        } catch (IOException e) {
            System.out.println("ERROR saving first_follow_sets.txt: " + e.getMessage());
        }

        // --- parsing_table.txt ---
        try (PrintStream ps = new PrintStream("output/parsing_table.txt")) {
            PrintStream old = System.out;
            System.setOut(ps);
            parser.printParsingTable();
            System.setOut(old);
            System.out.println("Saved -> output/parsing_table.txt");
        } catch (IOException e) {
            System.out.println("ERROR saving parsing_table.txt: " + e.getMessage());
        }

        // --- parsing_trace + parse_trees ---
        try (PrintStream ps = new PrintStream("output/parsing_trace.txt")) {
            PrintStream old = System.out;
            System.setOut(ps);
            for (int i = 0; i < inputs.size(); i++) {
                parser.parse(inputs.get(i), i + 1);
            }
            System.setOut(old);
            System.out.println("Saved -> output/parsing_trace.txt");
        } catch (IOException e) {
            System.out.println("ERROR saving parsing_trace.txt: " + e.getMessage());
        }
    }

    // FINAL SUMMARY


    private static void printFinalSummary(List<String> summary, int accepted, int rejected) {
        System.out.println("║              Parsing Summary              ║");
        for (String line : summary) {
            System.out.println("║  " + line);
        }
        System.out.printf ("║  Total: %-5d  Accepted: %-5d  Rejected: %-4d ║%n",
                accepted + rejected, accepted, rejected);
    }
}