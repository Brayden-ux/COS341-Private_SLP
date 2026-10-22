package spl.semantic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import spl.lexer.TokenType;
import spl.tree.LeafNode;
import spl.tree.SyntaxTree;
import spl.tree.SyntaxTreeNode;

/**
 * Phase 2a: analysis of names and scopes.
 *
 * Works in two passes over the syntax tree produced by the parser:
 *  1. declaration pass: builds the scope tree (one scope per P node), declares
 *     parameters, local variables and functions, and assigns every distinct
 *     entity a unique internal name (sys1, sys2, ...);
 *  2. resolution pass: resolves every use of a variable or function name
 *     against the declarations and binds the NAME leaf to its Symbol.
 *
 * Rules implemented:
 *  - a variable used in an ALGO (or in a function's return term) must be declared
 *    in the same scope or in an enclosing scope; the nearest declaration wins;
 *    declarations in sibling or descendant scopes are not visible;
 *  - no name may be declared twice in the same scope, and a function's local
 *    V_DECL may not reuse one of the function's parameter names;
 *  - a called function must be declared in the F_DECL of the same scope level
 *    as the ALGO that calls it, which makes recursion impossible;
 *  - function names are unique within one F_DECL (no overloading).
 */
public final class NameAnalyzer{
    private final SyntaxTree tree;
    private final SymbolTable table = new SymbolTable();
    private final List<SemanticError> errors = new ArrayList<>();
    private final List<Scope> allScopes = new ArrayList<>();

    public NameAnalyzer(SyntaxTree tree){
        this.tree = tree;
    }

    public SymbolTable analyze(){
        SyntaxTreeNode program = child(tree.getRoot(), "P");
        if (program == null) {
            throw new IllegalStateException("the syntax tree has no P node below its root");
        }
        declare(program, null, null, null, null);
        for (Scope scope : allScopes){
            for (SyntaxTreeNode body : scope.getBodies()){
                resolve(body, scope);
            }
        }
        errors.sort(Comparator
                .comparingInt((SemanticError e) -> e.getToken().getLine())
                .thenComparingInt(e -> e.getToken().getColumn()));
        return table;
    }

    public List<SemanticError> getErrors(){
        return errors;
    }

    public boolean hasErrors(){
        return !errors.isEmpty();
    }

    // pass 1

    private void declare(SyntaxTreeNode p, Scope parent, Symbol owner,
                         SyntaxTreeNode paramVDecl, SyntaxTreeNode returnTerm){
        int level = parent == null ? 0 : parent.getLevel() + 1;
        Scope scope = new Scope(level, parent, owner);
        allScopes.add(scope);
        if (parent == null) table.setGlobalScope(scope);

        if (paramVDecl != null) {
            for (LeafNode name : namesOf(paramVDecl)){
                Symbol param = declareVariable(scope, name, true);
                if (owner != null && !owner.getParameters().contains(param)) owner.getParameters().add(param);
            }
        }
        for (LeafNode name : namesOf(child(p, "V_DECL"))) declareVariable(scope, name, false);

        Map<SyntaxTreeNode, Symbol> functions = new LinkedHashMap<>();
        for (SyntaxTreeNode ftype : functionTypes(child(p, "F_DECL"))){
            LeafNode nameLeaf = firstNameLeaf(ftype);
            String returnKind = children(ftype).get(0).contents();
            functions.put(ftype, declareFunction(scope, nameLeaf, returnKind, ftype.getId()));
        }

        scope.getBodies().add(child(p, "ALGO"));
        if (returnTerm != null) scope.getBodies().add(returnTerm);

        for (Map.Entry<SyntaxTreeNode, Symbol> e : functions.entrySet()){
            SyntaxTreeNode ftype = e.getKey();
            declare(child(ftype, "P"), scope, e.getValue(), child(ftype, "V_DECL"), child(ftype, "TERM"));
        }
    }

    private Symbol declareVariable(Scope scope, LeafNode leaf, boolean parameter){
        String name = leaf.getToken().getLexeme();
        Symbol existing = scope.getVariables().get(name);
        if (existing != null) {
            if (existing.isParameter() && !parameter) {
                error(leaf, "local variable '" + name + "' of " + scope.describe()
                        + " masks the parameter of the same name (declared at "
                        + positionOf(existing) + "); local declarations may not reuse parameter names");
            } else {
                error(leaf, "variable '" + name + "' is declared more than once at scope level "
                        + scope.getLevel() + " (" + scope.describe() + "); first declaration at "
                        + positionOf(existing));
            }
            table.bind(leaf.getId(), existing);
            return existing;
        }
        Symbol s = table.newSymbol(name, Symbol.Kind.VARIABLE, scope.getLevel(), leaf.getId(), parameter, null);
        scope.getVariables().put(name, s);
        table.bind(leaf.getId(), s);
        return s;
    }

    private Symbol declareFunction(Scope scope, LeafNode leaf, String returnKind, int ftypeNodeId){
        String name = leaf.getToken().getLexeme();
        Symbol existing = scope.getFunctions().get(name);
        if (existing != null) {
            error(leaf, "function '" + name + "' is declared more than once at scope level "
                    + scope.getLevel() + " (" + scope.describe() + "); first declaration at "
                    + positionOf(existing) + ". Overloading is not allowed");
            table.bind(leaf.getId(), existing);
            return existing;
        }
        Symbol s = table.newSymbol(name, Symbol.Kind.FUNCTION, scope.getLevel(), leaf.getId(), false, returnKind);
        s.setFtypeNodeId(ftypeNodeId);
        scope.getFunctions().put(name, s);
        table.bind(leaf.getId(), s);
        return s;
    }

    // pass 2

    private void resolve(SyntaxTreeNode node, Scope scope){
        if (node == null || node instanceof LeafNode) return;
        List<SyntaxTreeNode> kids = children(node);
        String symbol = node.contents();
        int start = 0;
        if (symbol.equals("ASSIGN") && !kids.isEmpty() && isName(kids.get(0))){
            useVariable((LeafNode) kids.get(0), scope);
            start = 1;
        } else if (symbol.equals("CALL") && !kids.isEmpty() && isName(kids.get(0))){
            useFunction((LeafNode) kids.get(0), scope);
            start = 1;
        } else if (symbol.equals("TERM") && kids.size() == 1 && isName(kids.get(0))){
            useVariable((LeafNode) kids.get(0), scope);
            return;
        }
        for (int i = start; i < kids.size(); i++) resolve(kids.get(i), scope);
    }

    private void useVariable(LeafNode leaf, Scope scope){
        String name = leaf.getToken().getLexeme();
        Symbol s = scope.lookupVariable(name);
        if (s != null) {
            table.bind(leaf.getId(), s);
            return;
        }
        StringBuilder msg = new StringBuilder("variable '" + name + "' is used in " + scope.describe()
                + " (scope level " + scope.getLevel() + ") but has no declaration in any enclosing V_DECL");
        Symbol elsewhere = findAnywhere(name, Symbol.Kind.VARIABLE);
        if (elsewhere != null) {
            Scope home = scopeOf(elsewhere);
            msg.append(". A variable with this name is declared in ").append(home.describe())
               .append(" (scope level ").append(home.getLevel()).append(", ").append(positionOf(elsewhere))
               .append("), but that scope does not enclose this use, so the declaration is not visible here");
        } else if (findAnywhere(name, Symbol.Kind.FUNCTION) != null){
            msg.append(". '").append(name).append("' is declared as a function, not as a variable");
        }
        error(leaf, msg.toString());
    }

    private void useFunction(LeafNode leaf, Scope scope){
        String name = leaf.getToken().getLexeme();
        Symbol f = scope.getFunctions().get(name);
        if (f != null) {
            table.bind(leaf.getId(), f);
            return;
        }
        for (Scope s = scope; s != null; s = s.getParent()){
            if (s.getOwner() != null && s.getOwner().getOriginalName().equals(name)){
                error(leaf, "recursive call to function '" + name + "' from " + scope.describe()
                        + ": recursion (direct or indirect) is not allowed in SPL");
                return;
            }
        }
        StringBuilder msg = new StringBuilder("function '" + name + "' is called in " + scope.describe()
                + " (scope level " + scope.getLevel() + ") but is not declared in the F_DECL of this scope");
        Symbol elsewhere = findAnywhere(name, Symbol.Kind.FUNCTION);
        if (elsewhere != null) {
            Scope home = scopeOf(elsewhere);
            msg.append(". A function with this name is declared in ").append(home.describe())
               .append(" (scope level ").append(home.getLevel()).append(", ").append(positionOf(elsewhere))
               .append("), but a function may only call functions declared directly in its own scope; ")
               .append("if two functions need the same helper, declare a copy inside each of them");
        } else if (findAnywhere(name, Symbol.Kind.VARIABLE) != null) {
            msg.append(". '").append(name).append("' is declared as a variable, not as a function");
        }
        error(leaf, msg.toString());
    }

    // helpers

    private void error(LeafNode at, String message){
        errors.add(new SemanticError(at.getToken(), message));
    }

    private String positionOf(Symbol s){
        SyntaxTreeNode n = tree.getNode(s.getDeclNodeId());
        if (n instanceof LeafNode leaf) {
            return "line " + leaf.getToken().getLine() + ", column " + leaf.getToken().getColumn();
        }
        return "unknown position";
    }

    private Symbol findAnywhere(String name, Symbol.Kind kind){
        for (Symbol s : table.getSymbols()) {
            if (s.getKind() == kind && s.getOriginalName().equals(name)) return s;
        }
        return null;
    }

    private Scope scopeOf(Symbol sym){
        for (Scope s : allScopes) {
            Map<String, Symbol> m = sym.getKind() == Symbol.Kind.VARIABLE ? s.getVariables() : s.getFunctions();
            if (m.get(sym.getOriginalName()) == sym) return s;
        }
        return table.getGlobalScope();
    }

    private List<SyntaxTreeNode> children(SyntaxTreeNode n){
        return tree.children(n);
    }

    private SyntaxTreeNode child(SyntaxTreeNode n, String nonTerminal){
        return tree.child(n, nonTerminal);
    }

    private boolean isName(SyntaxTreeNode n){
        return n instanceof LeafNode l && l.getToken().getType() == TokenType.USER_DEFINED_NAME;
    }

    private LeafNode firstNameLeaf(SyntaxTreeNode n){
        for (SyntaxTreeNode c : children(n)){
            if (isName(c)) return (LeafNode) c;
        }
        throw new IllegalStateException("expected a name below node " + n.getId());
    }

    /** Flattens V_DECL -> NAME V_DECL - epsilon into the list of NAME leaves. */
    private List<LeafNode> namesOf(SyntaxTreeNode vdecl){
        List<LeafNode> out = new ArrayList<>();
        SyntaxTreeNode cur = vdecl;
        while (cur != null) {
            List<SyntaxTreeNode> k = children(cur);
            if (k.isEmpty()) break;
            if (isName(k.get(0))) out.add((LeafNode) k.get(0));
            cur = k.size() > 1 ? k.get(1) : null;
        }
        return out;
    }

    /** Flattens F_DECL -> F_TYPE F_DECL - epsilon into the list of F_TYPE nodes. */
    private List<SyntaxTreeNode> functionTypes(SyntaxTreeNode fdecl){
        List<SyntaxTreeNode> out = new ArrayList<>();
        SyntaxTreeNode cur = fdecl;
        while (cur != null) {
            List<SyntaxTreeNode> k = children(cur);
            if (k.isEmpty()) break;
            out.add(k.get(0));
            cur = k.size() > 1 ? k.get(1) : null;
        }
        return out;
    }
}