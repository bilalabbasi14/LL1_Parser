// ErrorHandler.java
import java.util.*;

public class ErrorHandler {

    // Total errors found during a single parse
    private int errorCount;

    // Log of all errors for reporting
    private List<ErrorRecord> errorLog;

    public ErrorHandler() {
        errorLog   = new ArrayList<>();
        errorCount = 0;
    }

    // ERROR TYPES

    public enum ErrorType {
        MISSING_SYMBOL,      // Expected terminal not found
        UNEXPECTED_SYMBOL,   // Terminal appears where not expected
        EMPTY_TABLE_ENTRY,   // No production in M[X, a]
        PREMATURE_END        // Input ends but stack not empty
    }

    // ERROR RECORD (inner class)
    public static class ErrorRecord {
        int       step;
        ErrorType type;
        String    expected;
        String    found;
        String    message;

        public ErrorRecord(int step, ErrorType type,
                           String expected, String found, String message) {
            this.step     = step;
            this.type     = type;
            this.expected = expected;
            this.found    = found;
            this.message  = message;
        }

        @Override
        public String toString() {
            return String.format("[Step %d] %s | Expected: %-10s | Found: %-10s | %s",
                    step, type.name(), expected, found, message);
        }
    }

    // ERROR HANDLERS — called by Parser.java

    /**
     * Called when M[NonTerminal][currentToken] is empty.
     * Strategy: pop the non-terminal, skip input until FOLLOW symbol found.
     * Returns a short action string for the trace table.
     */
    public String handleEmptyEntry(String nonTerminal, String currentToken, int step) {
        errorCount++;
        String msg = "ERROR: No rule for " + nonTerminal
                + " on input '" + currentToken + "'"
                + " | Recovery: pop " + nonTerminal + ", skip to FOLLOW";

        errorLog.add(new ErrorRecord(
                step,
                ErrorType.EMPTY_TABLE_ENTRY,
                "production for " + nonTerminal,
                currentToken,
                msg
        ));

        // Print detailed error to console immediately
        System.out.println();
        System.out.println("  *** ERROR at step " + step + " ***");
        System.out.println("  Type    : EMPTY TABLE ENTRY");
        System.out.println("  Context : No production M[" + nonTerminal + "][" + currentToken + "]");
        System.out.println("  Expected: a token from FIRST(" + nonTerminal + ")");
        System.out.println("  Found   : '" + currentToken + "'");
        System.out.println("  Recovery: Popping '" + nonTerminal
                + "', skipping input to FOLLOW set");
        System.out.println();

        return "ERROR: No rule M[" + nonTerminal + "][" + currentToken + "] -> skipping";
    }

    /**
     * Called when top of stack is a terminal but doesn't match current input.
     * Strategy: pop the mismatched terminal from the stack (insertion recovery).
     * Returns a short action string for the trace table.
     */
    public String handleMismatch(String expected, String found, int step) {
        errorCount++;

        String msg;
        ErrorType type;

        if (found.equals(Grammar.EOF)) {
            // Input ended too early
            type = ErrorType.PREMATURE_END;
            msg  = "ERROR: Premature end of input. Expected '" + expected + "'";
        } else {
            // Unexpected symbol
            type = ErrorType.UNEXPECTED_SYMBOL;
            msg  = "ERROR: Expected '" + expected + "' but found '" + found + "'";
        }

        errorLog.add(new ErrorRecord(step, type, expected, found, msg));

        // Print detailed error to console immediately
        System.out.println();
        System.out.println("  *** ERROR at step " + step + " ***");
        System.out.println("  Type    : " + type.name());
        System.out.println("  Expected: '" + expected + "'");
        System.out.println("  Found   : '" + found + "'");
        System.out.println("  Recovery: Popping '" + expected + "' from stack (insertion)");
        System.out.println();

        return "ERROR: Expected '" + expected + "' got '" + found + "' -> pop stack";
    }

    /**
     * Called when stack still has symbols but input is exhausted.
     */
    public String handlePrematureEnd(String stackTop, int step) {
        errorCount++;
        String msg = "ERROR: Input ended but stack still has '" + stackTop + "'";

        errorLog.add(new ErrorRecord(
                step,
                ErrorType.PREMATURE_END,
                stackTop,
                Grammar.EOF,
                msg
        ));

        System.out.println();
        System.out.println("  *** ERROR at step " + step + " ***");
        System.out.println("  Type    : PREMATURE END");
        System.out.println("  Expected: '" + stackTop + "'");
        System.out.println("  Found   : end of input ($)");
        System.out.println("  Recovery: Popping remaining stack symbols");
        System.out.println();

        return "ERROR: Premature end, popping '" + stackTop + "'";
    }

    // ERROR SUMMARY REPORT

    public void printErrorReport() {
        if (errorLog.isEmpty()) {
            System.out.println("\nNo errors found.");
            return;
        }

        System.out.println("\n========== Error Report ==========");
        System.out.println("Total errors: " + errorCount);
        System.out.println();
        for (ErrorRecord rec : errorLog) {
            System.out.println(rec);
        }
        System.out.println("==================================");
    }
    // RESET (called before each new parse)

    public void reset() {
        errorCount = 0;
        errorLog.clear();
    }

    // GETTERS

    public int getErrorCount()           { return errorCount; }
    public List<ErrorRecord> getErrors() { return errorLog; }
    public boolean hasErrors()           { return errorCount > 0; }
}
