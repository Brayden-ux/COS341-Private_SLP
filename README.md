# SLP — SPL SLR(1) Parser

COS341 2026 Semester Practical: a lexer + SLR(1) parser for the Students'
Programming Language (SPL), implemented in Java per the architecture in
[`spl-slr-parser-class-diagram.mermaid`](spl-slr-parser-class-diagram.mermaid)
and the grammar in [`spec/Prac-Spec-Syntax.pdf`](spec/Prac-Spec-Syntax.pdf).

## Build

Requires JDK 17+. With Maven:

```bash
mvn package
```

Or compile directly:

```bash
javac -d target/classes $(find src/main/java -name "*.java")
```

## Run

```bash
java -cp target/classes spl.SPLCompiler path/to/SPL.txt tree.xml code.txt
```

An example program is provided at [`examples/SPL.txt`](examples/SPL.txt):

```bash
java -cp target/classes spl.SPLCompiler examples/SPL.txt tree.xml
```

- If the input is syntactically valid SPL, a structured `tree.xml` syntax
  tree is written (root/inner/leaf nodes with unique IDs, contents,
  children, and parent links).
- Otherwise, a meaningful syntax or lexical error message (with a hint) is
  printed to stderr and the process exits non-zero.
- After a successful parse, the program is checked for name/scope errors
  (Phase 2a) and type errors (Phase 2b). Each violation is printed as
  `Semantic error at line L, column C: ...` or `Type error at line L, column C: ...`
  and the process exits non-zero. A correct program produces no messages.
- A correct program is then translated (Phase 3a) and the target code for its main
  algorithm is written to `code.txt` (third argument, optional).

## Architecture

- **Lexical layer** (`spl.lexer`): `SourceReader`, `Lexer`, `Token`,
  `TokenType`, `LexicalError`. Every SPL token must be terminated by a
  blank space (ASCII 32/13) per the spec; the lexer enforces this and
  classifies NUM / USER-DEFINED-NAME / STRING via the spec's regular
  expressions.
- **Grammar layer** (`spl.grammar`): `Symbol` / `Terminal` / `NonTerminal`,
  `Production`, `SPLGrammar` (hard-codes the given CFG), `FirstFollowSets`
  (nullable/FIRST/FOLLOW computation).
- **Table construction** (`spl.table`): `LR0Item`, `ItemSet`,
  `CanonicalCollection` (LR(0) automaton), `Action`/`Shift`/`Reduce`/
  `Accept`/`ErrorEntry`, `ParseTable`, `SLRTableGenerator` (builds the
  SLR(1) action/goto tables, reducing on FOLLOW sets).
- **Parser driver** (`spl.parser`): `SLRParser` (shift-reduce driver over
  the generated table), `ParseStack`, `ErrorReporter`, `SyntaxError`.
- **Tree + XML output** (`spl.tree`): `SyntaxTree`, `RootNode`,
  `InnerNode`, `LeafNode`, `NodeIdGenerator`, `TreeVisitor`,
  `XmlTreeWriter` — writes `tree.xml` with unique node IDs, contents,
  children lists, and parent links as required by the spec.
- **Name and scope analysis, Phase 2a** (`spl.semantic`): `NameAnalyzer`,
  `Scope`, `SymbolTable`, `Symbol`. Checks the scoping rules and renames every
  entity uniquely (`sys1`, `sys2`, ...).
- **Type analysis, Phase 2b** (`spl.semantic`): `TypeAnalyzer`, `Type`. Implements
  the rules of `spec/Prac-Spec-2b.pdf`: every node starts as `unknown` and becomes `ok`,
  `numeric`, `boolean` or `procedure`; void functions may only be called as
  instructions and num functions only inside terms; a program with both `mod` and
  `div` is rejected ("A float-integer-conflict might perhaps be possible"), and a
  program with `mod` may not contain decimal numbers. It also decides whether the
  program uses integers (`int`) or floats (`float`) for the code generator.
- **Code generation, Phase 3a** (`spl.codegen`): `CodeGenerator`. Translates the main
  `ALGO` into C-style target code with `goto` jumps and no `else` or Boolean operators,
  following `spec/Prac-Spec-3a.pdf` and the translation functions of Chapter 6 of the
  textbook. See [`docs/PHASE-3a.md`](docs/PHASE-3a.md).
- **Entry point**: `spl.SPLCompiler`.

## Tests

```bash
javac -d target/classes $(find src/main/java -name "*.java")
sh tests/phase2a/run_tests.sh
sh tests/phase2b/run_tests.sh
sh tests/phase3a/run_tests.sh   # also needs a C++ compiler (c++)
```

Add `--dump-symbols` and/or `--dump-types` to a run to print the symbol table and
the type attributes. See [`docs/HANDOVER-3a.md`](docs/HANDOVER-3a.md) for how the
code generator (Phase 3a) uses them, and [`docs/HANDOVER-3b.md`](docs/HANDOVER-3b.md)
for what Phase 3b builds on.
