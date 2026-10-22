package spl.codegen;

import java.util.ArrayList;
import java.util.List;
import spl.lexer.TokenType;
import spl.semantic.SymbolTable;
import spl.semantic.TypeAnalyzer;
import spl.tree.LeafNode;
import spl.tree.SyntaxTree;
import spl.tree.SyntaxTreeNode;

/**
 * Phase 3a: code generation for ALGO, following Prac-Spec-3a and the syntax-directed
 * translation functions of Chapter 6 of the textbook (Mogensen, Introduction to Compiler Design).
 *
 * Runs after Phase 2b, so the program is known to be correct. One trans method per
 * non-terminal, each returning the generated code as a string (the synthesised attribute)
 * without its final " ;", which the caller writes (the spec's "code ; ASCII13"):
 *  - transAlgo / transInstr: the instruction sequence, one instruction per line (Fig. 6.5, Stat1 ; Stat2);
 *  - transTerm(TERM, place): the parent decides where the value goes, as in Fig. 6.3 (method 2);
 *  - transBool(BOOL, labelT, labelF): conditions as jumps, as in Fig. 6.6 and Fig. 6.8.
 *
 * Every new variable (newvar() in the textbook) gets a "typ v ;" declaration right where it
 * is created, typ being the int or float that Phase 2b found for the whole program.
 *
 * The target language has no else, so a condition cannot use the textbook's
 * IF c THEN labelT ELSE labelF. Instead transBool has this contract: the code jumps to labelT
 * when the condition is true, and when it is false it reaches labelF, which the caller places
 * directly after the code; so the false case may simply "fall to the bottom". The spec's
 * "dummy" is exactly this labelF. See docs/PHASE-3a.md for why not, and, or follow from it.
 *
 * Function calls (CALL) are inlined in Phase 3b. Here the arguments are already evaluated into
 * new variables (TransExps of Fig. 6.3) and a comment marks where the inlined code goes.
 */
public final class CodeGenerator {
    private static final String NL = "\n";
    private static final String END = " ;" + NL;

    private final SyntaxTree tree;
    private final SymbolTable symbols;
    private final String typ;

    public CodeGenerator(SyntaxTree tree, SymbolTable symbols, TypeAnalyzer types){
        this.tree = tree;
        this.symbols = symbols;
        this.typ = types.programNumType();
    }

    /** trans(ALGO) of the main program (the ALGO of the outermost P), followed by a new line. */
    public String generate(){
        SyntaxTreeNode program = tree.child(tree.getRoot(), "P");
        if (program == null) {
            throw new IllegalStateException("the syntax tree has no P node below its root");
        }
        return transAlgo(tree.child(program, "ALGO")) + NL;
    }

    // ALGO and INSTR

    /** ALGO -> epsilon gives the comment "end"; ALGO -> INSTR ; ALGO gives trans(INSTR) ; ASCII13 trans(ALGO). */
    public String transAlgo(SyntaxTreeNode algo){
        List<SyntaxTreeNode> kids = tree.children(algo);
        if (kids.isEmpty()) return "/* end */";
        return transInstr(kids.get(0)) + END + transAlgo(kids.get(2));
    }

    private String transInstr(SyntaxTreeNode instr){
        List<SyntaxTreeNode> kids = tree.children(instr);
        SyntaxTreeNode first = kids.get(0);
        if (first instanceof LeafNode leaf) {
            return switch (leaf.getToken().getType()) {
                case KW_PRINT -> transOutput(kids.get(1));
                case KW_NOP -> "/* nop */";
                case KW_COMMENT -> "/* " + lexeme(kids.get(1)) + " */";
                default -> throw new IllegalStateException("unexpected INSTR " + leaf.contents());
            };
        }
        return switch (first.contents()) {
            case "ASSIGN" -> transAssign(first);
            case "BRANCH" -> transBranch(first);
            case "LOOP" -> transLoop(first);
            case "CALL" -> transCall(first, null);
            default -> throw new IllegalStateException("unexpected INSTR child " + first.contents());
        };
    }

    /** print OUTP: a STRING is printed as it is; a TERM is caught in a new variable first. */
    private String transOutput(SyntaxTreeNode outp){
        List<SyntaxTreeNode> kids = tree.children(outp);
        if (kids.size() == 1) return "std::cout << " + lexeme(kids.get(0));
        String v = newVar();
        return lines(declare(v), transTerm(kids.get(1), v), "std::cout << " + v);
    }

    /** NAME = TERM: the TERM goes into a new variable, which is then copied to the NAME (Fig. 6.5). */
    private String transAssign(SyntaxTreeNode assign){
        List<SyntaxTreeNode> kids = tree.children(assign);
        String v = newVar();
        String intern = internalName(kids.get(0));
        return lines(declare(v), transTerm(kids.get(2), v), intern + " = " + v);
    }

    // TERM, as Trans_Exp in Fig. 6.3

    /** Code that stores the value of the TERM in the variable place. */
    private String transTerm(SyntaxTreeNode term, String place){
        List<SyntaxTreeNode> kids = tree.children(term);
        SyntaxTreeNode first = kids.get(0);
        if (kids.size() == 1 && first instanceof LeafNode leaf) {
            if (leaf.getToken().getType() == TokenType.NUM) return place + " = " + lexeme(leaf);
            return place + " = " + internalName(leaf);
        }
        if (kids.size() == 1) return transCall(first, place);

        String op = transOp(((LeafNode) first).getToken().getType());
        if (kids.size() == 4) {
            // neg ( TERM ): unop Exp1
            String place1 = newVar();
            return lines(declare(place1), transTerm(kids.get(2), place1), place + " = " + op + " " + place1);
        }
        // op ( TERM TERM ): Exp1 binop Exp2
        String place1 = newVar();
        String place2 = newVar();
        return lines(declare(place1), declare(place2),
                transTerm(kids.get(2), place1),
                transTerm(kids.get(3), place2),
                place + " = " + place1 + " " + op + " " + place2);
    }

    /** transop: SPL operator to C operator. */
    private static String transOp(TokenType op){
        return switch (op) {
            case KW_MOD -> "%";
            case KW_DIV -> "/";
            case KW_ADD -> "+";
            case KW_SUB, KW_NEG -> "-";
            case KW_MUL -> "*";
            case KW_EQ -> "==";
            case KW_LARGER -> ">";
            case KW_LESSER -> "<";
            default -> throw new IllegalStateException("no C operator for " + op);
        };
    }

    /**
     * CALL -> NAME ( INPUT ), inlined in Phase 3b. The arguments are translated as Trans_Exps in
     * Fig. 6.3: each input TERM into its own new variable p1 ... pN. A comment then marks the place
     * where the function body and, for a term, the assignment place = r of the result will go.
     */
    private String transCall(SyntaxTreeNode call, String place){
        List<SyntaxTreeNode> kids = tree.children(call);
        String f = internalName(kids.get(0));
        List<String> code = new ArrayList<>();
        List<String> args = new ArrayList<>();
        for (SyntaxTreeNode input = kids.get(2); !tree.children(input).isEmpty();
                input = tree.children(input).get(1)) {
            String p = newVar();
            args.add(p);
            code.add(declare(p));
            code.add(transTerm(tree.children(input).get(0), p));
        }
        String target = place == null ? "" : place + " = ";
        code.add("/* " + target + "call " + f + " ( " + String.join(" ", args)
                + (args.isEmpty() ? "" : " ") + ") : inlined in Phase 3b */");
        return lines(code.toArray(new String[0]));
    }

    // BRANCH and LOOP

    /**
     * if BOOL then { ALGO_true } else { ALGO_false }. As Fig. 6.5, but with the false code straight
     * behind the condition (where a false condition falls to), so no else is needed.
     */
    private String transBranch(SyntaxTreeNode branch){
        List<SyntaxTreeNode> kids = tree.children(branch);
        String la = newLabel();
        String lb = newLabel();
        String lc = newLabel();
        String decisionCode = transBool(kids.get(1), la, lb);
        String trueCode = transAlgo(kids.get(4));
        String falseCode = transAlgo(kids.get(8));
        return "/* if */" + NL
                + decisionCode + END
                + lb + " : /* else */" + NL
                + falseCode + END
                + "goto " + lc + END
                + la + " : /* then */" + NL
                + trueCode + END
                + lc + " : /* continue */";
    }

    private String transLoop(SyntaxTreeNode loop){
        List<SyntaxTreeNode> kids = tree.children(loop);
        boolean bodyFirst = kids.get(0) instanceof LeafNode; // do { ALGO } COND BOOL
        SyntaxTreeNode cond = tree.child(loop, "COND");
        boolean until = ((LeafNode) tree.children(cond).get(0)).getToken().getType() == TokenType.KW_UNTIL;
        SyntaxTreeNode bool = tree.child(loop, "BOOL");
        SyntaxTreeNode body = tree.child(loop, "ALGO");
        if (bodyFirst) {
            // do-until is do-while with the condition negated
            return transDoWhile(bool, until, body);
        }
        // while is until with the condition negated
        return transUntil(bool, !until, body, until ? "until" : "while");
    }

    /**
     * until BOOL do { ALGO }: as soon as the condition is true jump to the exit, otherwise
     * fall into the body. With negate, the condition is not ( BOOL ): the while loop.
     */
    private String transUntil(SyntaxTreeNode bool, boolean negate, SyntaxTreeNode body, String keyword){
        String ls = newLabel();
        String le = newLabel();
        String d = newLabel();
        String decisionCode = negate ? transNot(bool, le, d) : transBool(bool, le, d);
        String bodyCode = transAlgo(body);
        return ls + " : /* " + keyword + " */" + NL
                + decisionCode + END
                + d + " : /* do */" + NL
                + bodyCode + END
                + "goto " + ls + END
                + le + " :" + NL
                + "/* exit */";
    }

    /**
     * do { ALGO } while BOOL: as soon as the condition is true jump back to the start, otherwise
     * fall out of the loop. With negate, the condition is not ( BOOL ): the do-until loop.
     */
    private String transDoWhile(SyntaxTreeNode bool, boolean negate, SyntaxTreeNode body){
        String ld = newLabel();
        String le = newLabel();
        String decisionCode = negate ? transNot(bool, ld, le) : transBool(bool, ld, le);
        String bodyCode = transAlgo(body);
        return ld + " : /* do */" + NL
                + bodyCode + END
                + "/* " + (negate ? "until" : "while") + " */" + NL
                + decisionCode + END
                + le + " : /* exit */";
    }

    // BOOL, as Trans_Cond in Fig. 6.6 and Fig. 6.8

    /**
     * Code that jumps to labelT if the BOOL is true. If it is false, the code reaches labelF,
     * which the caller places directly after this code (so the code may fall to the bottom).
     */
    private String transBool(SyntaxTreeNode bool, String labelT, String labelF){
        List<SyntaxTreeNode> kids = tree.children(bool);
        TokenType op = ((LeafNode) kids.get(0)).getToken().getType();
        switch (op) {
            case KW_NOT -> {
                // Fig. 6.8: swap the true and false labels
                return transNot(kids.get(2), labelT, labelF);
            }
            case KW_OR -> {
                // B1 true: jump to labelT; B1 false: fall into B2, which decides
                String next = newLabel();
                String code1 = transBool(kids.get(2), labelT, next);
                return code1 + END
                        + placeLabel(code1, next, "or")
                        + transBool(kids.get(3), labelT, labelF);
            }
            case KW_AND -> {
                // not B1: jump to labelF; B1 true: fall into B2, which decides
                String next = newLabel();
                String code1 = transNot(kids.get(2), labelF, next);
                return code1 + END
                        + placeLabel(code1, next, "and")
                        + transBool(kids.get(3), labelT, labelF);
            }
            default -> {
                // eq, larger, lesser ( TERM TERM ): Fig. 6.6 without the ELSE
                String x = newVar();
                String y = newVar();
                return lines(declare(x), declare(y),
                        transTerm(kids.get(2), x),
                        transTerm(kids.get(3), y),
                        "if ( " + x + " " + transOp(op) + " " + y + " ) goto " + labelT);
            }
        }
    }

    /**
     * trans( not ( BOOL ) , labelT , labelF ) = trans( BOOL , labelF , labelT ), as in Fig. 6.8.
     * labelF follows directly, so the BOOL jumps to it when true; labelT does not follow, so the
     * BOOL's false case, which falls to the bottom, continues with goto labelT.
     */
    private String transNot(SyntaxTreeNode bool, String labelT, String labelF){
        String fallThrough = newLabel();
        String code = transBool(bool, labelF, fallThrough);
        return code + END
                + placeLabel(code, fallThrough, "not")
                + "goto " + labelT;
    }

    /**
     * A label that only receives jumps from the code just before it. It is written only if that
     * code really jumps to it; otherwise falling to the bottom gets there anyway.
     */
    private static String placeLabel(String code, String label, String comment){
        String jump = "goto " + label;
        boolean used = code.contains(jump + END) || code.endsWith(jump);
        return used ? label + " : /* " + comment + " */" + NL : "";
    }

    // helpers

    private String newVar(){ return symbols.freshName("v"); }

    private String newLabel(){ return symbols.freshName("L"); }

    private String declare(String v){ return typ + " " + v; }

    /** Code lines joined by " ;" and a new line (the last one without its " ;"). */
    private static String lines(String... code){
        return String.join(END, code);
    }

    private String internalName(SyntaxTreeNode name){
        String intern = symbols.internalNameFor(name.getId());
        if (intern == null) {
            throw new IllegalStateException("no internal name for " + name.contents() + " (node " + name.getId() + ")");
        }
        return intern;
    }

    private static String lexeme(SyntaxTreeNode leaf){
        return ((LeafNode) leaf).getToken().getLexeme();
    }
}
