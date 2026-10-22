# COS341 2026 - Semester Practical, Phases 1, 2a, 2b and 3a: User Manual - Group 28

**Group Members:**

- Brayden Butler - 24824713
- Nathan Chisadza - 24825532
- Finnley Wyllie - 24754120
- Daniel Cohen - 24772756

---

## 1. What This Program Does

This program is the front-end, semantic analyser and (first part of the) code
generator of a compiler for the
Students' Programming Language (SPL), as defined in the COS341 2026
specifications. Given a plain ASCII text file containing an SPL program, it
runs the following phases in order and stops at the first phase that finds
an error:

1. **Lexing and parsing (Phase 1):** a lexer and an SLR(1) parser check the
   syntax and write the syntax tree to `tree.xml`.
2. **Name and scope analysis (Phase 2a):** checks that every variable and
   function is declared where it is used, that nothing is declared twice at
   the same scope level, that local variables do not mask a function's
   parameters, that calls are strictly hierarchic (no recursion), and gives
   every entity a unique internal name (`sys1`, `sys2`, ...) in the symbol
   table.
3. **Type analysis (Phase 2b):** gives every node of the syntax tree a type
   (`unknown`, `ok`, `numeric`, `boolean` or `procedure`) and checks the type
   rules, for example that a void function is only called as an instruction
   and a num function only inside a term. It also applies the float/integer
   rule: a program may not use both `mod` and `div`, and a program that uses
   `mod` may not contain decimal numbers.
4. **Code generation (Phases 3a and 3b):** translates the program into C++ and
   writes it to `code.txt`. Conditions, branches and loops become `if ( ... ) goto`
   jumps and labels, without `else` and without Boolean operators. Function calls
   are expanded inline from non-executable code-mine snippets.

## 2. How to Run the Program

The submitted executable is named:

```
group-28.jar
```

To run it, open a command prompt / terminal in the folder containing the
executable, and run:

```
java -jar group-28.jar SPL.txt tree.xml code.txt
```

Where:

- `SPL.txt` is the path to the plain-text SPL source file to be analysed.
- `tree.xml` is the path where the syntax tree is written if parsing
  succeeds (optional, the default is `tree.xml`).
- `code.txt` is the path where the generated target code is written if the
  program is correct (optional, the default is `code.txt`). To give it, the
  tree file must be given as well.

Optional flags, added after the file names, print the results of the
semantic phases for inspection:

- `--dump-symbols` prints the symbol table with the unique internal names.
- `--dump-types` prints the type of every symbol and tree node, and whether
  the program works with integers (`int`) or floats (`float`).

Example:

```
java -jar group-28.jar examples/SPL.txt tree.xml code.txt --dump-symbols --dump-types
```

## 3. Expected Behaviour

**On SUCCESS** (a syntactically and semantically correct SPL program):

- The program prints a short confirmation message to the console.
- A file is written at the given output path (e.g. `tree.xml`) containing
  the syntax tree, with:
  - a root node for the `SPL_PROG` start symbol,
  - inner nodes for every non-terminal in the derivation,
  - leaf nodes for every terminal token consumed from the input,
  - each with a unique ID, its contents, and (for non-root nodes) a
    reference to its parent's ID; and (for root/inner nodes) a list of its
    children's IDs.
- No error messages are printed by the name/scope and type analysis.
- The target code is written to the given path (e.g. `code.txt`): one
  instruction per line, each ending with a semicolon. Every user variable
  appears under its unique internal name (`sys1`, ...), every helper
  variable (`v5`, ...) is declared as `int` or `float` (as found by the type
  analysis), and labels (`L8 :`) mark the targets of `goto` jumps.
- The program exits with status code 0.

**On FAILURE:**

- A lexical or syntax error stops the program before `tree.xml` is written.
- A name/scope or type error is reported after `tree.xml` has been written
  (the syntax itself was correct), and no target code is written.
- Every error is printed to the console (stderr) with the line and column
  where it was detected, and an explanation or hint, in one of these forms:
  - `Lexical error at line L, column C: ...`
  - `Syntax error at line L, column C: ...`
  - `Semantic error at line L, column C: ...` (Phase 2a, names and scopes)
  - `Type error at line L, column C: ...` (Phase 2b, types)
- All name/scope errors (or all type errors) of a program are listed, not
  only the first one.
- The program exits with a non-zero status code.

Examples of type errors:

```
Type error at line 3, column 6: A float-integer-conflict might perhaps be possible. ...
Type error at line 3, column 6: function '#hello' is a void function (type procedure), so it returns no value, but it is used as a term, ...
```

## 4. Compiling and Running the Generated Code

The target code uses `std::cout` for printing, so it is **C++** (with the
restricted, low-level syntax required by the specification: assignments,
`if ( x op y ) goto L`, `goto L`, labels, `std::cout`, declarations, and
comments; no `else`, no `&&`, `||` or `!`, no loops). It is meant to be
read: one instruction per line, every jump target marked with a label and a
comment (`/* if */`, `/* else */`, `/* then */`, `/* while */`, `/* exit */`, ...).

**We prefer g++ for the run-tests.** The generated code compiles without
errors or warnings with g++, for every C++ standard from C++11 to C++23,
even with `-pedantic-errors -Wall -Wextra`.

- With **g++** (preferred):

  ```
  g++ program.cpp -o program
  ./program
  ```

- With **gcc**, tell it that the file is C++ and link the C++ library,
  otherwise it either treats the file as C (and cannot find `<iostream>`)
  or fails at link time:

  ```
  gcc -x c++ program.cpp -lstdc++ -o program
  ./program
  ```

Please keep the file extension `.cpp` (or use `-x c++`); a `.c` extension
makes gcc compile it as C, which does not offer `std::cout`.

The compiler emits a complete C++ program, including the wrapper, declarations,
and `return 0;`. Function bodies appear in non-executable comments and are copied
into the executable code at their call sites.

```
#include <iostream>
int main()
{
int sys1 ;            /* one line per user variable, int or float */
... contents of code.txt ...
return 0;
}
```

Notes for the run-tests:

- All numbers are `int` or, if the program uses `div` or a number with a
  decimal dot, `float`; this applies to the whole program.
- `print` writes exactly what is asked for, without spaces or new lines in
  between (SPL has no new-line command), so the output of several prints
  appears on one line.
- The compiler output may contain labels that no `goto` jumps to (e.g.
  `L9 : /* else */`); these come from the specification's templates and
  can at most cause an "unused label" warning with `-Wall`.

## 5. Notes on the SPL Language

- Every token in an SPL program must be terminated by a blank space
  (space, or newline/return).
- User-defined names (variables and function names) must begin with `#`,
  e.g. `#x`, `#counter`, `#add1`.
- The end-of-file marker `$` referenced in the grammar is a theoretical
  symbol only. It must never be typed in an input file, and it will not
  appear in the generated `tree.xml` output.
- All variables are numeric. Functions declared with `void` are procedures
  and can only be called as an instruction; functions declared with `num`
  return a number and can only be called inside a term.

## 6. Tests

The folders `tests/phase2a` and `tests/phase2b` contain small SPL programs:
files starting with `v` must be accepted, files starting with `e` must be
rejected with the stated number of errors. The folder `tests/phase3a`
contains programs together with their expected target code (`.code`); the
test script also compiles the target code as C++ and, for files starting
with `r`, runs it and compares the output with the `.expected` file. After compiling the sources to
`target/classes`, run them from the repository root with:

```
sh tests/phase2a/run_tests.sh
sh tests/phase2b/run_tests.sh
sh tests/phase3a/run_tests.sh
```

## 7. System Requirements

- Java 17 or newer.
- No other installation required; the executable is self-contained and can
  be run directly.
- To run the generated code and the Phase 3a tests: a C++ compiler,
  preferably g++. The test script uses `c++` by default; choose another one
  with e.g. `CXX=g++ sh tests/phase3a/run_tests.sh`.
