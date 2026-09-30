package spl.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import spl.grammar.Production;
import spl.grammar.SPLGrammar;
import spl.grammar.Terminal;
import spl.lexer.Lexer;
import spl.lexer.Token;
import spl.table.Accept;
import spl.table.Action;
import spl.table.ParseTable;
import spl.table.Reduce;
import spl.table.Shift;
import spl.tree.InnerNode;
import spl.tree.LeafNode;
import spl.tree.NodeIdGenerator;
import spl.tree.RootNode;
import spl.tree.SyntaxTree;
import spl.tree.SyntaxTreeNode;

public final class SLRParser {
    private final SPLGrammar grammar;
    private final Lexer lexer;
    private final ParseTable table;
    private final ParseStack stack = new ParseStack();
    private final NodeIdGenerator ids = new NodeIdGenerator();
    private final ErrorReporter reporter;
    private final Map<Integer, SyntaxTreeNode> allNodes = new LinkedHashMap<>();
    private RootNode root;

    public SLRParser(SPLGrammar grammar, Lexer lexer, ParseTable table, ErrorReporter reporter) {
        this.grammar = grammar;
        this.lexer = lexer;
        this.table = table;
        this.reporter = reporter;
    }

    private static final SyntaxTreeNode BOTTOM = new SyntaxTreeNode(-1) {
        @Override
        public String contents() {
            return "$bottom$";
        }

        @Override
        public void accept(spl.tree.TreeVisitor v) {
            // sentinel: never visited
        }
    };

	private static final SyntaxTreeNode ENDMARKER = new SyntaxTreeNode(-2) {
        @Override
        public String contents() {
            return "$";
        }

        @Override
        public void accept(spl.tree.TreeVisitor v) {
            // sentinel: never part of the output tree
        }
    };

    public SyntaxTree parse() {
        stack.push(0, BOTTOM);
        while (true) {
            Token lookahead = lexer.peekToken();
            Terminal term = grammar.terminal(lookahead.getType());
            if (term == null) {
                throw abort(lookahead, stack.topState());
            }
            Action action = table.action(stack.topState(), term);
            if (action == null) {
                throw abort(lookahead, stack.topState());
            }
            if (action instanceof Shift shift) {
                shift(lookahead, shift.getTargetState());
            } else if (action instanceof Reduce red) {
                reduce(red.getProduction());
            } else if (action instanceof Accept) {
                lexer.nextToken();
                break;
            }
        }
        if (root == null) {
            throw new ParseException("Parser accepted without producing a syntax tree root");
        }
        SyntaxTree tree = new SyntaxTree(root);
        for (SyntaxTreeNode n : allNodes.values()) {
            tree.addNode(n);
        }
        return tree;
    }

    private void shift(Token t, int target) {
		if (t.getType() == spl.lexer.TokenType.EOF) {
            stack.push(target, ENDMARKER);
            lexer.nextToken();
            return;
        }

        int id = ids.newId();
        LeafNode leaf = new LeafNode(id, t);
        allNodes.put(id, leaf);
        stack.push(target, leaf);
        lexer.nextToken();
    }

    private void reduce(Production p) {
        List<SyntaxTreeNode> children = stack.popMany(p.length());
        int newTopState = stack.topState();
        int id = ids.newId();

        SyntaxTreeNode node;
        if (p.getLhs().getName().equals("SPL_PROG")) {
            RootNode r = new RootNode(id, p.getLhs());
            for (SyntaxTreeNode c : children) {
				if ( c == ENDMARKER ){
					continue;
				}
                r.addChild(c.getId());
                c.setParent(id);
            }
            root = r;
            node = r;
        } else {
            InnerNode inner = new InnerNode(id, p.getLhs());
            for (SyntaxTreeNode c : children) {
                inner.addChild(c.getId());
                c.setParent(id);
            }
            node = inner;
        }
        allNodes.put(id, node);

        int gotoState = table.gotoState(newTopState, p.getLhs());
        if (gotoState == -1) {
            throw new ParseException("No GOTO entry for state " + newTopState + " on " + p.getLhs());
        }
        stack.push(gotoState, node);
    }

    private ParseException abort(Token t, int state) {
        SyntaxError err = buildError(t, state);
        reporter.syntaxError(err);
        return new ParseException(err.format());
    }

    private SyntaxError buildError(Token t, int state) {
        var expected = table.expectedAt(state);
        String hint = expected.isEmpty()
                ? "no valid continuation exists from this parser state; check for an unbalanced or truncated construct"
                : "the parser expected one of the listed tokens next";
        return new SyntaxError(t, expected, hint, t.getLine());
    }
}
