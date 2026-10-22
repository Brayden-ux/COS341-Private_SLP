# Handover: Phase 2b (types) to Phase 3a (code generation)

Everything 3a needs is already computed in memory by the time `SPLCompiler.run`
reaches the comment `// Phase 3a: the code generator plugs in here`. If the program
gets that far it has parsed, passed name/scope analysis (2a) and type analysis (2b),
which is exactly the precondition the 3a sheet assumes.

## What you get

| Object | Where | What to use it for |
|---|---|---|
| `SyntaxTree tree` | `spl.tree.SyntaxTree` | `tree.getRoot()`, `tree.children(node)`, `tree.child(node, "ALGO")`, `tree.getNode(id)` |
| `SymbolTable symbols` | `spl.semantic.SymbolTable` | `symbols.internalNameFor(leafId)` gives the unique name (`sys7`) of any NAME leaf: this is the spec's `lookUp(NAME SymbT)`. `symbols.freshName("t")` / `symbols.freshName("L")` give new variable and label names that can never clash with a `sysN` name or each other. |
| `TypeAnalyzer types` | `spl.semantic.TypeAnalyzer` | `types.programNumType()` returns `"int"` or `"float"`: this is the spec's `typ` for every `typ vN ;` you write (3a) and every variable declaration (3b). `types.typeOf(nodeId)` gives a node's 2b type (`ok`, `numeric`, `boolean`, `procedure`, `unknown`). `Symbol.getType()` is `numeric` for variables and num functions, `procedure` for void functions. |

Run with `--dump-symbols --dump-types` to see all of this for a given input.

## Tree shapes (children, left to right)

Inner nodes have `contents()` equal to the non-terminal name; leaves have `contents()`
equal to the lexeme and `getToken().getType()` gives the `TokenType`.

- `ALGO`: `[]` or `[INSTR, ;, ALGO]`
- `INSTR`: `[print, OUTP]`, `[nop]`, `[comment, STRING]`, `[ASSIGN]`, `[BRANCH]`, `[LOOP]`, `[CALL]`
- `OUTP`: `[STRING]` or `[(, TERM, )]`
- `ASSIGN`: `[NAME, =, TERM]`
- `TERM`: `[NAME]`, `[NUM]`, `[CALL]`, `[op, (, TERM, TERM, )]`, `[neg, (, TERM, )]`
- `BRANCH`: `[if, BOOL, then, {, ALGO, }, else, {, ALGO, }]` (true ALGO at index 4, false ALGO at index 8)
- `LOOP`: `[COND, BOOL, do, {, ALGO, }]` or `[do, {, ALGO, }, COND, BOOL]`; `COND` is `[while]` or `[until]`
- `BOOL`: `[not, (, BOOL, )]`, `[and|or, (, BOOL, BOOL, )]`, `[eq|larger|lesser, (, TERM, TERM, )]`
- `CALL`: `[NAME, (, INPUT, )]`, `INPUT`: `[]` or `[TERM, INPUT]` (calls are 3b)

## Things 2b guarantees, so 3a need not check

- Every NAME in a TERM or ASSIGN is a variable and has an internal name.
- Every TERM is numeric and every BOOL is boolean.
- A CALL inside a TERM is to a `num` function, a CALL used as an INSTR is to a `void`
  function.
- A program never uses both `mod` and `div`, and a program with `mod` has no decimal
  numbers. So `typ` is `int` whenever `%` appears and `float` whenever `/` appears.
- Not checked (the 2b spec has no rule for it): that a call passes as many arguments as
  the function has parameters. 3b inlining pairs arguments with parameters one by one,
  so the 3b code should guard against a count mismatch.

## Gotchas for the C output

- A STRING leaf's lexeme already includes its quotes, so `std::cout << ` + lexeme is right as is.
- NUM lexemes can be negative (`-9.01`); they are valid C literals unchanged.
- In C (before C23) a label must be followed by a statement. `Lc : /* continue */` at the
  very end of a block, or `le :` with nothing after it, will not compile. Write `Lc : ;`
  (an empty statement) to be safe.
- `typ` rule used by 2b: `mod` in the program means `int`; `div` or any NUM with a
  decimal dot means `float`; otherwise `int`.

## How 2b was specified

2b follows `spec/Prac-Spec-2b.pdf` rule by rule (one `check...` method per non-terminal in
`TypeAnalyzer`), including the crude float/integer rule and its exact error message
"A float-integer-conflict might perhaps be possible".
