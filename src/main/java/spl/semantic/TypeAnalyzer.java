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
import spl.tree.TreeVisitor;

/**
 * Phase 2b: type analysis, following Prac-Spec-2b.
 *
 * Runs after Phase 2a, so every NAME leaf is already bound to its Symbol. Every node's
 * type starts as "unknown" and is updated bottom-up by one rule per grammar rule:
 *  - SPL_PROG, P, V_DECL, F_DECL, ALGO, INSTR, ASSIGN, BRANCH, LOOP, COND, OUTP, INPUT, F_TYPE: "ok"
 *    when all their parts have the type the rule asks for;
 *  - V_DECL -> NAME V_DECL makes the NAME "numeric" (variables and parameters);
 *  - F_TYPE makes its NAME "procedure" (void) or "numeric" (num, return TERM must be numeric);
 *  - CALL -> NAME ( INPUT ) takes the type of NAME, if INPUT is ok and NAME is not unknown;
 *  - INSTR -> CALL needs a "procedure", TERM -> CALL needs a "numeric" CALL;
 *  - ASSIGN -> NAME = TERM needs a numeric NAME and a numeric TERM;
 *  - TERM is "numeric", BOOL is "boolean" when their operands are.
 *
 * Before that, the spec's crude float/integer rule is applied to the whole tree: a tree
 * with both a mod and a div node is rejected outright ("A float-integer-conflict might
 * perhaps be possible"), and a tree with a mod node may not contain a NUM with a decimal dot.
 * From the same information the analyser decides whether the program deals with integers
 * or floats, which is the typ that the code generator (Phase 3a/3b) declares its variables with.
 */
public final class TypeAnalyzer {
    public static final String CONFLICT = "A float-integer-conflict might perhaps be possible";

    private final SyntaxTree tree;
    private final SymbolTable symbols;
    private final Map<Integer, Type> types = new LinkedHashMap<>();
    private final List<SemanticError> errors = new ArrayList<>();
    private String numType = "int";

    public TypeAnalyzer(SyntaxTree tree, SymbolTable symbols){
        this.tree = tree;
        this.symbols = symbols;
    }

    public void analyze(){
        SyntaxTreeNode program = tree.child(tree.getRoot(), "P");
        if (program == null) {
            throw new IllegalStateException("the syntax tree has no P node below its root");
        }
        if (floatIntegerRule()) {
            Type t = checkP(program) == Type.OK ? Type.OK : Type.UNKNOWN;
            types.put(tree.getRoot().getId(), t);
        }
        errors.sort(Comparator
                .comparingInt((SemanticError e) -> e.getToken().getLine())
                .thenComparingInt(e -> e.getToken().getColumn()));
    }

    public List<SemanticError> getErrors(){ return errors; }
    public boolean hasErrors(){ return !errors.isEmpty(); }

    /** The type of a node: inner nodes and NAME leaves; UNKNOWN for anything not (yet) typed. */
    public Type typeOf(int nodeId){ return types.getOrDefault(nodeId, Type.UNKNOWN); }

    /**
     * "int" or "float" for the whole program: the typ that Phase 3a/3b writes in front of
     * every variable it declares. A program with mod is int; a program with div or with a
     * NUM containing a decimal dot is float; any other program is int.
     */
    public String programNumType(){ return numType; }

    // the crude float/integer rule

    /** Returns false if the tree was rejected outright. */
    private boolean floatIntegerRule(){
        List<LeafNode> mods = new ArrayList<>();
        List<LeafNode> divs = new ArrayList<>();
        List<LeafNode> reals = new ArrayList<>();
        tree.traverse(new TreeVisitor() {
            @Override public void visitRoot(spl.tree.RootNode n) { }
            @Override public void visitInner(spl.tree.InnerNode n) { }
            @Override public void visitLeaf(LeafNode n) {
                TokenType t = n.getToken().getType();
                if (t == TokenType.KW_MOD) mods.add(n);
                else if (t == TokenType.KW_DIV) divs.add(n);
                else if (t == TokenType.NUM && lexeme(n).contains(".")) reals.add(n);
            }
        });
        if (!mods.isEmpty() && !divs.isEmpty()) {
            LeafNode mod = mods.get(0), div = divs.get(0);
            LeafNode at = before(mod, div) ? div : mod;
            error(at, CONFLICT + ". The program uses mod (integers only, line " + mod.getToken().getLine()
                    + ") and div (floating point only, line " + div.getToken().getLine()
                    + "), so the whole program is rejected");
            return false;
        }
        if (!mods.isEmpty() && !reals.isEmpty()) {
            for (LeafNode r : reals) {
                error(r, "the number " + lexeme(r) + " contains a decimal dot, but the program uses mod (line "
                        + mods.get(0).getToken().getLine() + "), which forces all numbers to be integers");
            }
            return false;
        }
        numType = !divs.isEmpty() || !reals.isEmpty() ? "float" : "int";
        return true;
    }

    private static boolean before(LeafNode a, LeafNode b){
        int la = a.getToken().getLine(), lb = b.getToken().getLine();
        return la < lb || (la == lb && a.getToken().getColumn() < b.getToken().getColumn());
    }

    // the type rules, one method per non-terminal

    private Type checkP(SyntaxTreeNode p){
        boolean ok = checkVDecl(tree.child(p, "V_DECL")) == Type.OK;
        ok &= checkFDecl(tree.child(p, "F_DECL")) == Type.OK;
        ok &= checkAlgo(tree.child(p, "ALGO")) == Type.OK;
        return put(p, ok);
    }

    /** V_DECL -> NAME V_DECL makes every declared NAME numeric. */
    private Type checkVDecl(SyntaxTreeNode vdecl){
        List<SyntaxTreeNode> kids = tree.children(vdecl);
        if (kids.isEmpty()) return put(vdecl, true);
        boolean ok = checkVDecl(kids.get(1)) == Type.OK;
        if (ok) setNameType((LeafNode) kids.get(0), Type.NUMERIC);
        return put(vdecl, ok);
    }

    private Type checkFDecl(SyntaxTreeNode fdecl){
        List<SyntaxTreeNode> kids = tree.children(fdecl);
        if (kids.isEmpty()) return put(fdecl, true);
        boolean ok = checkFType(kids.get(0)) == Type.OK;
        ok &= checkFDecl(kids.get(1)) == Type.OK;
        return put(fdecl, ok);
    }

    private Type checkFType(SyntaxTreeNode ftype){
        List<SyntaxTreeNode> kids = tree.children(ftype);
        boolean isVoid = ((LeafNode) kids.get(0)).getToken().getType() == TokenType.KW_VOID;
        LeafNode name = (LeafNode) kids.get(1);
        boolean ok = checkVDecl(tree.child(ftype, "V_DECL")) == Type.OK;
        ok &= checkP(tree.child(ftype, "P")) == Type.OK;
        if (!isVoid) {
            SyntaxTreeNode ret = tree.child(ftype, "TERM");
            ok &= checkTerm(ret) == Type.NUMERIC;
        }
        if (ok) setNameType(name, isVoid ? Type.PROCEDURE : Type.NUMERIC);
        return put(ftype, ok);
    }

    private Type checkAlgo(SyntaxTreeNode algo){
        List<SyntaxTreeNode> kids = tree.children(algo);
        if (kids.isEmpty()) return put(algo, true);
        boolean ok = checkInstr(kids.get(0)) == Type.OK;
        ok &= checkAlgo(kids.get(2)) == Type.OK;
        return put(algo, ok);
    }

    private Type checkInstr(SyntaxTreeNode instr){
        List<SyntaxTreeNode> kids = tree.children(instr);
        SyntaxTreeNode first = kids.get(0);
        boolean ok;
        if (first instanceof LeafNode leaf) {
            if (leaf.getToken().getType() == TokenType.KW_PRINT) ok = checkOutp(kids.get(1)) == Type.OK;
            else ok = true; // nop, comment STRING
        } else {
            switch (first.contents()) {
                case "ASSIGN" -> ok = checkAssign(first) == Type.OK;
                case "BRANCH" -> ok = checkBranch(first) == Type.OK;
                case "LOOP" -> ok = checkLoop(first) == Type.OK;
                case "CALL" -> {
                    Type t = checkCall(first);
                    ok = t == Type.PROCEDURE;
                    if (t == Type.NUMERIC) {
                        LeafNode name = (LeafNode) tree.children(first).get(0);
                        error(name, "function '" + lexeme(name) + "' is a num function (type numeric), but it is "
                                + "called as an instruction, which needs a void function (type procedure); "
                                + "assign its result to a variable, e.g. #x = " + lexeme(name) + " ( ... ) ;");
                    }
                }
                default -> throw new IllegalStateException("unexpected INSTR child " + first.contents());
            }
        }
        return put(instr, ok);
    }

    private Type checkOutp(SyntaxTreeNode outp){
        List<SyntaxTreeNode> kids = tree.children(outp);
        if (kids.size() == 1) return put(outp, true); // STRING
        return put(outp, checkTerm(kids.get(1)) == Type.NUMERIC);
    }

    private Type checkAssign(SyntaxTreeNode assign){
        List<SyntaxTreeNode> kids = tree.children(assign);
        LeafNode name = (LeafNode) kids.get(0);
        boolean ok = nameType(name) == Type.NUMERIC && isVariable(name);
        if (!ok) {
            error(name, "'" + lexeme(name) + "' is not a numeric variable, so no value can be assigned to it");
        }
        ok &= checkTerm(kids.get(2)) == Type.NUMERIC;
        return put(assign, ok);
    }

    private Type checkBranch(SyntaxTreeNode branch){
        // if BOOL then { ALGO } else { ALGO }
        List<SyntaxTreeNode> kids = tree.children(branch);
        boolean ok = checkBool(kids.get(1)) == Type.BOOLEAN;
        ok &= checkAlgo(kids.get(4)) == Type.OK;
        ok &= checkAlgo(kids.get(8)) == Type.OK;
        return put(branch, ok);
    }

    private Type checkLoop(SyntaxTreeNode loop){
        // COND BOOL do { ALGO }   or   do { ALGO } COND BOOL
        boolean ok = put(tree.child(loop, "COND"), true) == Type.OK;
        ok &= checkBool(tree.child(loop, "BOOL")) == Type.BOOLEAN;
        ok &= checkAlgo(tree.child(loop, "ALGO")) == Type.OK;
        return put(loop, ok);
    }

    /** CALL -> NAME ( INPUT ): the type of NAME, if INPUT is ok and NAME is not unknown. */
    private Type checkCall(SyntaxTreeNode call){
        List<SyntaxTreeNode> kids = tree.children(call);
        LeafNode name = (LeafNode) kids.get(0);
        boolean inputOk = checkInput(kids.get(2)) == Type.OK;
        Type nameType = nameType(name);
        Symbol f = symbols.symbolFor(name.getId());
        if (f != null && f.getKind() != Symbol.Kind.FUNCTION) {
            error(name, "'" + lexeme(name) + "' is a variable, not a function, so it cannot be called");
            nameType = Type.UNKNOWN;
        } else if (f != null && nameType == Type.UNKNOWN) {
            error(name, "the type of function '" + lexeme(name) + "' is unknown because its declaration "
                    + "contains a type error, so this call cannot be typed");
        }
        Type t = inputOk ? nameType : Type.UNKNOWN;
        types.put(call.getId(), t);
        return t;
    }

    private Type checkInput(SyntaxTreeNode input){
        List<SyntaxTreeNode> kids = tree.children(input);
        if (kids.isEmpty()) return put(input, true);
        boolean ok = checkTerm(kids.get(0)) == Type.NUMERIC;
        ok &= checkInput(kids.get(1)) == Type.OK;
        return put(input, ok);
    }

    private Type checkTerm(SyntaxTreeNode term){
        List<SyntaxTreeNode> kids = tree.children(term);
        SyntaxTreeNode first = kids.get(0);
        boolean ok;
        if (kids.size() == 1 && first instanceof LeafNode leaf) {
            if (leaf.getToken().getType() == TokenType.NUM) {
                ok = true;
            } else {
                ok = nameType(leaf) == Type.NUMERIC && isVariable(leaf);
                if (!ok) error(leaf, "'" + lexeme(leaf) + "' is not a numeric variable, so it cannot be used as a term");
            }
        } else if (kids.size() == 1) {
            Type t = checkCall(first);
            ok = t == Type.NUMERIC;
            if (t == Type.PROCEDURE) {
                LeafNode name = (LeafNode) tree.children(first).get(0);
                error(name, "function '" + lexeme(name) + "' is a void function (type procedure), so it returns "
                        + "no value, but it is used as a term, which needs a num function (type numeric)");
            }
        } else {
            // op ( TERM ) or op ( TERM TERM )
            ok = checkTerm(kids.get(2)) == Type.NUMERIC;
            if (kids.size() == 5) ok &= checkTerm(kids.get(3)) == Type.NUMERIC;
        }
        Type t = ok ? Type.NUMERIC : Type.UNKNOWN;
        types.put(term.getId(), t);
        return t;
    }

    private Type checkBool(SyntaxTreeNode bool){
        List<SyntaxTreeNode> kids = tree.children(bool);
        TokenType op = ((LeafNode) kids.get(0)).getToken().getType();
        boolean ok;
        switch (op) {
            case KW_NOT -> ok = checkBool(kids.get(2)) == Type.BOOLEAN;
            case KW_AND, KW_OR -> {
                ok = checkBool(kids.get(2)) == Type.BOOLEAN;
                ok &= checkBool(kids.get(3)) == Type.BOOLEAN;
            }
            default -> { // eq, larger, lesser
                ok = checkTerm(kids.get(2)) == Type.NUMERIC;
                ok &= checkTerm(kids.get(3)) == Type.NUMERIC;
            }
        }
        Type t = ok ? Type.BOOLEAN : Type.UNKNOWN;
        types.put(bool.getId(), t);
        return t;
    }

    // helpers

    /** Records "ok" or leaves the node "unknown". */
    private Type put(SyntaxTreeNode n, boolean ok){
        Type t = ok ? Type.OK : Type.UNKNOWN;
        if (n != null) types.put(n.getId(), t);
        return t;
    }

    /** Stores a declared name's type in the symbol table (and on the declaring leaf). */
    private void setNameType(LeafNode name, Type t){
        types.put(name.getId(), t);
        Symbol s = symbols.symbolFor(name.getId());
        if (s != null) s.setType(t.label());
    }

    /** Looks a used NAME up in the symbol table and records its type on the leaf. */
    private Type nameType(LeafNode name){
        Symbol s = symbols.symbolFor(name.getId());
        Type t = Type.UNKNOWN;
        if (s != null && s.getType().equals("numeric")) t = Type.NUMERIC;
        else if (s != null && s.getType().equals("procedure")) t = Type.PROCEDURE;
        types.put(name.getId(), t);
        return t;
    }

    private boolean isVariable(LeafNode leaf){
        Symbol s = symbols.symbolFor(leaf.getId());
        return s != null && s.getKind() == Symbol.Kind.VARIABLE;
    }

    private void error(LeafNode at, String message){
        errors.add(SemanticError.typeError(at.getToken(), message));
    }

    private static String lexeme(LeafNode leaf){
        return leaf.getToken().getLexeme();
    }

    public String dump(){
        StringBuilder sb = new StringBuilder();
        sb.append("TYPES (Phase 2b)\n");
        sb.append("  program number type (typ for Phase 3a): ").append(numType).append('\n');
        sb.append("\nSYMBOL TYPES\n");
        for (Symbol s : symbols.getSymbols()) {
            sb.append(String.format("  %-6s %-12s -> %-6s %s%n",
                    s.getKind() == Symbol.Kind.FUNCTION ? "func" : (s.isParameter() ? "param" : "var"),
                    s.getOriginalName(), s.getInternalName(), s.getType()));
        }
        sb.append("\nNODE TYPES (syntax-tree node id -> contents -> type)\n");
        for (Map.Entry<Integer, Type> e : new java.util.TreeMap<>(types).entrySet()) {
            SyntaxTreeNode n = tree.getNode(e.getKey());
            sb.append(String.format("  node %-4d %-10s %s%n", e.getKey(), n.contents(), e.getValue().label()));
        }
        return sb.toString();
    }
}
