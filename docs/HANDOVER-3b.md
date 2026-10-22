# Handover: Phase 3a (ALGO code) to Phase 3b (functions and inlining)

Phase 3a is described in [`PHASE-3a.md`](PHASE-3a.md). This note lists what 3b can reuse
and what it has to watch out for.

## What you get

- `new CodeGenerator(tree, symbols, types)` with `generate()` (trans of the main ALGO) and
  the public `transAlgo(ALGO)`. `transAlgo` works for any ALGO node, so it also translates
  function bodies for the code-mine. Its result has no trailing ` ;`, exactly like
  `trans(ALGO)` in the spec.
- `typ` is `types.programNumType()`, for `V_DECL` as well.
- `SymbolTable.symbolFor(nameLeafId).getParameters()` gives a function's parameter symbols
  in order. `Symbol.getReturnKind()` is `num` or `void`.

## Calls

`transCall` already evaluates the arguments as 3b prescribes (`pN ← trans(TERM, pN)`, the
count N found by walking `INPUT`). It then writes one marker comment in place of the inlined
code:

```
/* v6 = call sys3 ( v7 ) : inlined in Phase 3b */      (TERM -> CALL, result goes to v6)
/* call sys5 ( v8 v9 ) : inlined in Phase 3b */        (INSTR -> CALL)
```

In 3b, replace that last `code.add(...)` in `transCall` with:

1. `a_i = p_i ;` for each parameter `a_i` of the callee (from `getParameters()`). Guard
   against a different number of arguments: Phase 2b does not check it, see HANDOVER-3a.
2. The callee's body code, i.e. `trans(P)` of the function with its local `V_DECL`
   declarations, with **all labels renamed freshly for every copy**. Every label the
   generator writes has the form `L<number>` and is unique, so a copy can rename each
   distinct `L<number>` it contains (`goto Lk` and `Lk :` together) to a new
   `symbols.freshName("L")`.
3. For a num function, `trans(TERM, r)` of the return term, then `place = r ;`.

Variables do not need renaming per copy. Every internal name is unique, and inlining is
non-recursive. The `vN` temporaries in a copied body are declared again in each copy,
though, and a second `int v12 ;` in the same block will not compile. So either rename the
`vN` names per copy, like the labels, or wrap each inlined body in `{ ... }`. A goto into a
block past declarations without initialisers is still legal C++, but labels have function
scope, so renaming them is needed either way.

If a function f calls its own sub-function g, inline g into f's code-mine first, then
copy f.
