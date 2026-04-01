# LL(1) Parser
 
**CS4031 — Compiler Construction | Assignment 02**
Spring 2026 · FAST National University of Computer and Emerging Sciences
 
| Team Member   | Student ID |
|---------------|------------|
| Ahmad Bilal   | 23I-0787   |
| Muhammad Ali  | 23I-2565   |
 
---
 
## Overview
 
A fully functional LL(1) parser implemented in Java that supports:
 
- **Left factoring** and **left recursion removal**
- **FIRST / FOLLOW set** computation
- **LL(1) parsing table** construction
- **Stack-based parsing** with detailed trace output
- **Parse tree** construction and display
- **Panic-mode error recovery**
 
---
 
## Prerequisites
 
- Java JDK 8 or higher
 
---
 
## Project Structure
 
```
project-root/
├── src/
│   ├── Main.java           # Entry point — orchestrates the full pipeline
│   ├── Grammar.java        # Loads CFG, handles left factoring and left recursion removal
│   ├── FirstFollow.java    # Computes FIRST and FOLLOW sets
│   ├── Parser.java         # Builds the LL(1) parsing table and performs stack-based parsing
│   ├── ParserStack.java    # Custom stack implementation
│   ├── Tree.java           # Constructs and displays parse trees
│   └── ErrorHandler.java   # Handles errors and panic-mode recovery
├── input/
│   ├── grammar1.txt                # Simple nullable grammar
│   ├── grammar1_valid.txt
│   ├── grammar1_errors.txt
│   ├── grammar1_edge_cases.txt
│   ├── grammar2.txt                # Arithmetic expression grammar (left recursive)
│   ├── grammar2_valid.txt
│   ├── grammar2_errors.txt
│   ├── grammar2_edge_cases.txt
│   ├── grammar3.txt                # If-then-else statement grammar
│   ├── grammar3_valid.txt
│   ├── grammar3_errors.txt
│   └── grammar3_edge_cases.txt
├── output/                         # Auto-created when saving outputs
│   ├── grammar_transformed.txt
│   ├── first_follow_sets.txt
│   ├── parsing_table.txt
│   ├── parsing_trace_<name>.txt
│   └── parse_trees_<name>.txt
├── docs/
│   └── report.pdf
├── build.bat                       # Windows build and run script
├── build.sh                        # Linux/macOS build and run script
└── README.md
```
 
---
 
## Compilation & Running
 
### Windows
 
Run the provided script:
 
```bat
build.bat
```
 
Or compile manually:
 
```bat
mkdir out
javac -d out src\*.java
```
 
Then run:
 
```bat
java -cp out Main
```
 
### Linux / macOS
 
```bash
chmod +x build.sh
./build.sh
```
 
---
 
## Step-by-Step Walkthrough
 
### 1. Enter the grammar file path
 
```
Enter grammar file path (e.g. input/grammar1.txt): input/grammar2.txt
```
 
The program will automatically:
 
- Load and display the original grammar
- Apply left factoring and display the result
- Remove left recursion and display the result
- Compute and display FIRST and FOLLOW sets
- Build and display the LL(1) parsing table
 
### 2. Enter the input strings file path
 
```
Enter input file path (e.g. input/grammar1_valid.txt): input/grammar2_valid.txt
```
 
The parser will:
 
- Parse each string and print a detailed trace
- Summarize accepted and rejected strings
 
### 3. Save outputs (optional)
 
```
Save outputs to files? (yes/no): yes
```
 
This generates the following files in `output/`:
 
```
grammar_transformed.txt
first_follow_sets.txt
parsing_table.txt
parsing_trace_grammar2_valid.txt
parse_trees_grammar2_valid.txt
```
 
> **Tip:** Repeat step 2 with `input/grammar2_errors.txt` to test error handling.
 
---
 
## Grammar File Format
 
- One production per line: `NonTerminal -> symbol1 symbol2 | symbol3 symbol4`
- `|` separates alternative productions
- **Non-terminals:** Names starting with an uppercase letter (e.g., `Expr`, `Term`, `Factor`)
- **Terminals:** Lowercase letters, operators, or keywords (e.g., `id`, `+`, `if`, `then`)
- **Epsilon:** Represented by the keyword `epsilon`
- The **first non-terminal** in the file is the start symbol
- Blank lines are ignored
 
**Example:**
 
```
Expr -> Expr + Term | Term
Term -> Term * Factor | Factor
Factor -> ( Expr ) | id
```
 
**Example with epsilon:**
 
```
Start -> First Second
First -> a | epsilon
Second -> b
```
 
---
 
## Input String File Format
 
- One string per line
- Tokens separated by spaces
- Only use terminals defined in the grammar
- Blank lines are ignored
 
**Example:**
 
```
id + id * id
( id + id ) * id
id
```
 
---
 
## Grammar Examples
 
### Grammar 1 — Simple Nullable Grammar
 
```
Start -> First Second
First -> a | epsilon
Second -> b
```
 
Tests epsilon handling: `First` can derive epsilon, so `b` alone is valid.
 
| Category      | Examples           |
|---------------|--------------------|
| Valid inputs  | `a b`, `b`         |
| Invalid inputs| `a`, `c b`, `a b c`|
 
---
 
### Grammar 2 — Arithmetic Expressions
 
**Original (left recursive):**
 
```
Expr   -> Expr + Term | Term
Term   -> Term * Factor | Factor
Factor -> ( Expr ) | id
```
 
**After left recursion removal:**
 
```
Expr      -> Term ExprPrime
ExprPrime -> + Term ExprPrime | epsilon
Term      -> Factor TermPrime
TermPrime -> * Factor TermPrime | epsilon
Factor    -> ( Expr ) | id
```
 
| Category      | Examples                          |
|---------------|-----------------------------------|
| Valid inputs  | `id + id * id`, `( id + id ) * id`, `id` |
| Invalid inputs| `id + * id`, `( id + id`          |
 
---
 
### Grammar 3 — If-Then-Else Statements
 
**Original:**
 
```
Stmt -> if Cond then Stmt else Stmt | if Cond then Stmt | assign
Cond -> id rel id | id
```
 
**After left factoring:**
 
```
Stmt      -> if Cond then Stmt StmtPrime | assign
StmtPrime -> else Stmt | epsilon
Cond      -> id CondPrime
CondPrime -> rel id | epsilon
```
 
| Category      | Examples                                          |
|---------------|---------------------------------------------------|
| Valid inputs  | `assign`, `if id then assign`, `if id then assign else assign` |
| Invalid inputs| `if then assign`, `else assign`                   |
 
---
 
## Known Limitations
 
- LL(1) conflicts are resolved by keeping the **first production** added to a table cell. Grammars that are not strictly LL(1) will produce a conflict warning and may parse incorrectly.
- The DOT-format parse tree export (`Tree.toDotFormat()`) is available in the code but not saved automatically.
- Input tokens must be **space-separated**; multi-character tokens with internal spaces are not supported.
- `|` cannot be used as a terminal symbol in grammar files.
