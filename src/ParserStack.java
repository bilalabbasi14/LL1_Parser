// ParserStack.java
import java.util.*;

public class ParserStack {

    private LinkedList<String> stack;

    public ParserStack() {
        stack = new LinkedList<>();
    }

    public void push(String symbol)  { stack.addLast(symbol); }
    public String pop()              { return stack.removeLast(); }
    public String peek()             { return stack.getLast(); }
    public boolean isEmpty()         { return stack.isEmpty(); }
    public int size()                { return stack.size(); }

    /** Returns stack contents from bottom to top as a string. */
    @Override
    public String toString() {
        return String.join(" ", stack);
    }
}
