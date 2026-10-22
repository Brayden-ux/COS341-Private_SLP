# Phase 3a: Code generation for ALGO

Implemented in [`spl.codegen.CodeGenerator`](../src/main/java/spl/codegen/CodeGenerator.java),
following `spec/Prac-Spec-3a.pdf` and the syntax-directed translation functions of
Chapter 6 of the textbook (Mogensen, *Introduction to Compiler Design*): Fig. 6.3
(expressions), Fig. 6.5 (statements), Fig. 6.6 (simple conditions) and Fig. 6.8
(sequential logical operators).

The generator runs only when Phases 1, 2a and 2b accepted the program. It translates the
`ALGO` of the main program and writes the result to the third command-line argument
(default `code.txt`):

```
java -jar group-28.jar SPL.txt tree.xml code.txt
```

Function declarations (`F_DECL`), variable declarations (`V_DECL`) and calls are Phase 3b.

## Structure

As in the textbook, there is one translation function per syntactic category. Each one
returns the generated code as a string (the synthesised attribute) without its final ` ;`.
The caller writes that ` ;` and the new line, which is the spec's `code ; ASCII13`.

| Function | Textbook | Inherited attributes |
|---|---|---|
| `transAlgo(ALGO)`, `transInstr(INSTR)` | Fig. 6.5 *Trans_Stat* | none |
| `transTerm(TERM, place)` | Fig. 6.3 *Trans_Exp*, method 2: the parent chooses the `place` | `place` |
| `transBool(BOOL, labelT, labelF)` | Fig. 6.6 / 6.8 *Trans_Cond* | `labelT`, `labelF` |
| `transCall(CALL, place)` | Fig. 6.3 *Trans_Exps* for the arguments | `place` (or none) |

`vtable` is the Phase 2a symbol table (`SymbolTable.internalNameFor`, the spec's
`lookUp(NAME SymbT)`). `newvar()` and `newlabel()` are `SymbolTable.freshName("v")` and
`freshName("L")`. They share one counter with the `sysN` names, so no generated name can
clash with another one.

**Declarations.** Every new variable is declared as `typ vN ;` where it is created, as the
spec requires, so the target code is valid C/C++. That covers `vN` in print and assignment,
and also `place1`/`place2` of an operator and `x`/`y` of a comparison. `typ` is `int` or
`float` for the whole program, from `TypeAnalyzer.programNumType()` (Phase 2b).

## Translation rules

| Rule | Generated code |
|---|---|
| `ALGO → ε` | `/* end */` |
| `ALGO → INSTR ; ALGO` | `trans(INSTR) ;` new line `trans(ALGO)` |
| `INSTR → print STRING` | `std::cout << "..."` (the lexeme already contains its quotes) |
| `INSTR → print ( TERM )` | `typ vN ;` `trans(TERM, vN) ;` `std::cout << vN` |
| `INSTR → nop` | `/* nop */` |
| `INSTR → comment STRING` | `/* "..." */` (an SPL string can contain neither `*` nor `/`, so it cannot close the comment early) |
| `ASSIGN → NAME = TERM` | `typ vN ;` `trans(TERM, vN) ;` `sysK = vN` (Fig. 6.5: never assign straight into the NAME) |
| `TERM → NAME` | `place = sysK` |
| `TERM → NUM` | `place = NUM` (unchanged, also negative and decimal numbers) |
| `TERM → op ( TERM TERM )` | `typ v1 ;` `typ v2 ;` `trans(TERM1, v1) ;` `trans(TERM2, v2) ;` `place = v1 op v2` (v1, v2 new) with `mod`→`%`, `div`→`/`, `add`→`+`, `sub`→`-`, `mul`→`*` |
| `TERM → neg ( TERM )` | `typ v1 ;` `trans(TERM, v1) ;` `place = - v1` (v1 new) |
| `BOOL → eq/larger/lesser ( TERM TERM )` | `typ x ;` `typ y ;` `trans(TERM1, x) ;` `trans(TERM2, y) ;` `if ( x op y ) goto labelT` with `==`, `>`, `<` |
| `BRANCH` | exactly the spec's layout: `/* if */`, decision code, `Lb : /* else */`, false code, `goto Lc`, `La : /* then */`, true code, `Lc : /* continue */` |
| `until BOOL do { ALGO }` | `ls : /* until */`, `trans(BOOL, le, d)`, `d : /* do */`, body, `goto ls`, `le :`, `/* exit */` |
| `while BOOL do { ALGO }` | the until translation of `until not ( BOOL )` (the comment says `while`) |
| `do { ALGO } while BOOL` | `ld : /* do */`, body, `/* while */`, `trans(BOOL, ld, le)`, `le : /* exit */` |
| `do { ALGO } until BOOL` | the do-while translation of `do { ALGO } while not ( BOOL )` |

No `else`, `&&`, `||` or `!` appears in the target code. Conditions only ever use
`if ( x op y ) goto L` with `op` one of `==`, `>`, `<`.

Every instruction ends with ` ;`, so the labels `Lc : /* continue */`, `le : /* exit */` and
so on are always followed by a statement (at least the empty statement `;`). That makes
them legal even at the end of a block.

## Conditions without ELSE

The textbook's `IF c THEN labelT ELSE labelF` chooses between two labels. Without `else`,
`if ( c ) goto labelT` can only jump on true. On false it falls through to the next line. So
`transBool(BOOL, labelT, labelF)` has this contract:

> The code jumps to `labelT` when the condition is true. When it is false, control reaches
> `labelF`, which the caller places **directly after** the code.

This is exactly how the spec uses its third parameter, the "dummy". In every caller (`BRANCH`
with `La, Lb`, `until` with `le, d`, `do-while` with `ld, le`) the dummy is the label written
straight after the decision code. Under this contract:

**Comparison.** `if ( x op y ) goto labelT`. On false the code falls to `labelF`. This is
Fig. 6.6 without the ELSE, as the spec says.

**`or ( B1 B2 )`.** `trans(B1, labelT, n) ;` `trans(B2, labelT, labelF)`. If B1 is true, jump
to `labelT`. If it is false, fall into B2, which decides. This is the spec's rule, and
Fig. 6.8's `||` with `n` as `arg2`.

**`not ( B )`.** Fig. 6.8 swaps the labels: `trans(not B, labelT, labelF) = trans(B, labelF, labelT)`.
Under the fall-through contract, that only works if `labelT` follows the code, but the
caller placed `labelF` there. So B is translated with its true jump going to `labelF`, which
is fine because a jump can go anywhere. B's *false* case, which falls to the bottom, then
needs one explicit `goto labelT`:

```
trans(B, labelF, n) ;      // B true  -> not B false -> labelF
n : /* not */              // (written only if B jumps to n itself)
goto labelT                // B false -> not B true  -> labelT
```

**`and ( B1 B2 )`.** If B1 is false, jump to `labelF`. Otherwise fall into B2, which decides.
That is the spec's idea ("jump to the False-dummy if any one of the two is not true") for the
first operand, and Fig. 6.8's `&&` (`code1 = Trans_Cond(Cond1, arg2, labelF)`, `LABEL arg2`,
`code2 = Trans_Cond(Cond2, labelT, labelF)`):

```
trans(not B1, labelF, n) ; // B1 false -> labelF, B1 true -> falls to n
n : /* and */
trans(B2, labelT, labelF)
```

### Why the spec's `not` and `and` were not copied literally

The spec's text, read literally under its own fall-to-the-bottom design, gives wrong code:

- `trans(not ( B ), target, dummy) = trans(B, dummy, target)` with B a comparison gives just
  `if ( x op y ) goto dummy`. When B is false, nothing jumps and control falls to the
  dummy, which is also where it goes when B is true. Example: `if not ( eq ( #x 1 ) ) then { print "a" } else { print "b" }`
  would always print `b`. The missing piece is the `goto target` on the fall-through path,
  which the textbook gets for free from its ELSE.
- `trans(and ( B1 B2 ), target, dummy) = trans(not B1, dummy, target) ; trans(not B2, dummy, target)`.
  By the not rule above, this expands to `trans(B1, target, dummy) ; trans(B2, target, dummy)`,
  which is the translation of `or`.

The `not` and `and` rules above keep the spec's ideas: swap the labels for `not`, jump to the
false dummy as soon as one operand of `and` is false, never generate ELSE or Boolean
operators. They add only the `goto` that the missing ELSE requires. The helper labels (`n`
above) are fresh labels. They are written only when some jump in the code before them
actually targets them, so a simple condition gives exactly the code shown in the spec.

## C++ validity

The target code uses `std::cout`, so it is C++. In C++ a label may be followed by a
declaration, and a `goto` may jump past a declaration that has no initialiser. The
generator never writes an initialiser (`typ vN ;` and `vN = ...` are separate lines), so
every jump is legal. Every generated name is unique, so declaring `vN` again on the next
pass through a loop body is fine.

Checked with GCC (`g++`) for every standard from `-std=c++11` to `-std=c++23` with
`-pedantic-errors -Wall -Wextra -Werror` (only unused-label warnings switched off), and with
Apple clang. `gcc` also works when it is told the file is C++ and links the C++ library:
`gcc -x c++ code.txt -lstdc++`. As plain C (`gcc code.c`) it cannot work, because C has no
`<iostream>` or `std::cout`. That is why the tutors (announcement #34) allow calling the
output C++. The user manual recommends g++.

## Function calls (until Phase 3b)

The spec leaves `INSTR → CALL` and `TERM → CALL` for 3b (inlining). The generator already
does the first step that 3b prescribes, which is also *Trans_Exps* in Fig. 6.3: each input
`TERM` is translated into its own new variable. A comment then marks where 3b inlines the
body and assigns the result:

```
int v7 ;
v7 = sys1 ;
/* v6 = call sys3 ( v7 ) : inlined in Phase 3b */ ;
```

## Example

`if eq ( #x 3 ) then { print ( #y ) ; } else { nop ; } ;` with `#x`→`sys1`, `#y`→`sys2`:

```
/* if */
int v11 ;
int v12 ;
v11 = sys1 ;
v12 = 3 ;
if ( v11 == v12 ) goto L8 ;
L9 : /* else */
/* nop */ ;
/* end */ ;
goto L10 ;
L8 : /* then */
int v13 ;
v13 = sys2 ;
std::cout << v13 ;
/* end */ ;
L10 : /* continue */ ;
```

## Tests

`tests/phase3a/run_tests.sh` checks the original Phase 3a programs against reviewed full C++
outputs in `X.code`, compiles them as C++17, and runs the `r*` programs against `X.expected`.
The target snapshots now include Phase 3b's wrapper and declarations; valid calls are inlined.
`c*` programs are compile-only. The dedicated Phase 3b runtime and error cases live in
`tests/phase3b/run_tests.sh`.

The suite covers every INSTR and TERM form, int and float programs, all four loop forms
(including zero-iteration while/until and the always-once do loops), nested branches and
loops, and complete truth tables of `and`, `or`, `not` and nested combinations (XOR, XNOR,
De Morgan, double negation) over both variables. Run with `UPDATE=1` to rewrite the `.code`
files after a deliberate change, then review the diff.
