# Phase 3b: functions and call inlining

Implemented in [`spl.codegen.CodeGenerator`](../src/main/java/spl/codegen/CodeGenerator.java),
following [`spec/Prac-Spec-3b.pdf`](../spec/Prac-Spec-3b.pdf). It builds on the expression and
statement translations documented in [`PHASE-3a.md`](PHASE-3a.md).

The generated file is a C++ translation unit. It contains a non-executable code-mine for each
function, followed by `#include <iostream>` and an executable `int main()`. The main program's
global declarations and `ALGO` are emitted inside `main`, followed by `return 0;`.

## Function code-mine

The generator walks each `F_DECL` and constructs a translation for every `F_TYPE`. It processes
the functions declared in a function's own `P` before translating that function's body. This
ensures a helper call has already been expanded before its enclosing function's code is saved.
SPL disallows recursive calls, so this bottom-up construction terminates.

Each function translation declares its parameter names and local `V_DECL` names using their
Phase 2a `sysN` names and the program-wide numeric type from Phase 2b. It then emits the function
body with `transAlgo`. For `num` functions it also declares a return temporary and translates the
return `TERM` into it. Void functions have no return temporary.

The code-mine is included as line comments. This retains readable generated snippets while
ensuring comments in snippets (such as `/* if */`) cannot terminate the surrounding code-mine.
The snippets are not executable; executable copies are generated from the same in-memory strings.

## Call translation

For both `TERM -> CALL` and `INSTR -> CALL`, arguments are evaluated from left to right, each
into a fresh temporary as in `Trans_Exps` (Fig. 6.3). The generator checks that the number of
arguments matches `Symbol.getParameters()`, because Phase 2b does not enforce argument counts.
It then:

1. Opens a C++ block to give declarations in this copy their own scope.
2. Assigns each argument temporary to its corresponding parameter's internal name.
3. Copies the saved body code into the block.
4. For a numeric function call in a `TERM`, assigns its return temporary to the caller's `place`.
5. Closes the block.

The block permits the same parameter, local, and temporary declarations to appear at multiple
call sites. Variables keep their Phase 2a names because each copy is block-scoped. Every `L<number>`
label in a copied body is replaced consistently with a fresh `symbols.freshName("L")`; labels have
function scope in C++, so the block alone would not prevent duplicate labels. This also keeps a
copied function's jumps from targeting labels in another copy.

## Program assembly

`generate()` now emits:

```text
/* code-mine ... */
// ... non-executable function snippets ...
/* end code-mine ... */
#include <iostream>
int main()
{
  ... global variable declarations ...
  ... translated main ALGO ...
  return 0;
}
```

Declarations use `types.programNumType()` (`int` or `float`) and the internal name bound to each
`V_DECL` leaf. Empty declarations contribute no declaration line.

## Tests and validation

The compiler's existing semantic phases must accept the program before code generation. A mismatch
between call arguments and declared parameters is reported as a code-generation error; arity is
outside the Phase 2b checks. The target remains C++ (it uses `std::cout`), as in Phase 3a.

`tests/phase3b/run_tests.sh` compiles each positive output with strict C++17 warnings and runs it
against its expected result. It covers multi-argument and zero-argument numeric calls, void calls,
nested helper inlining, repeated calls to a function containing loop labels, float returns, and an
argument-count mismatch. The Phase 3a suite continues to check its statement and condition cases
against complete generated programs.
