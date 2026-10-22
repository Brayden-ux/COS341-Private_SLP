package spl.lexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import spl.parser.ErrorReporter;
import spl.parser.ParseException;

/**
 * Every SPL token must be terminated by a blank_space (ASCII 32 or ASCII 13),
 * per the language's lexical specification. This lexer therefore reads
 * whitespace-delimited "words" and classifies each one.
 */
public final class Lexer {
    private static final Pattern NUM_PATTERN = Pattern.compile(
            "^0$|^-?0\\.[0-9]*[1-9]$|^-?[1-9][0-9]*\\.[0-9]*[1-9]$|^-?[1-9][0-9]*$");
    private static final Pattern USER_NAME_PATTERN = Pattern.compile("^#[0-9a-z]*$");
    private static final Pattern STRING_PATTERN = Pattern.compile("^\"[,.:\\-?!0-9a-z]*\"$");

    private final SourceReader reader;
    private final Map<String, TokenType> keywords;
    private final ErrorReporter reporter;
    private Token lookahead;

    public Lexer(String source, ErrorReporter reporter) {
        this.reader = new SourceReader(source);
        this.reporter = reporter;
        this.keywords = buildKeywords();
    }

    private static Map<String, TokenType> buildKeywords() {
        Map<String, TokenType> k = new HashMap<>();
        k.put("void", TokenType.KW_VOID);
        k.put("num", TokenType.KW_NUM);
        k.put("return", TokenType.KW_RETURN);
        k.put("print", TokenType.KW_PRINT);
        k.put("nop", TokenType.KW_NOP);
        k.put("comment", TokenType.KW_COMMENT);
        k.put("if", TokenType.KW_IF);
        k.put("then", TokenType.KW_THEN);
        k.put("else", TokenType.KW_ELSE);
        k.put("while", TokenType.KW_WHILE);
        k.put("until", TokenType.KW_UNTIL);
        k.put("do", TokenType.KW_DO);
        k.put("not", TokenType.KW_NOT);
        k.put("and", TokenType.KW_AND);
        k.put("or", TokenType.KW_OR);
        k.put("eq", TokenType.KW_EQ);
        k.put("larger", TokenType.KW_LARGER);
        k.put("lesser", TokenType.KW_LESSER);
        k.put("mod", TokenType.KW_MOD);
        k.put("add", TokenType.KW_ADD);
        k.put("sub", TokenType.KW_SUB);
        k.put("mul", TokenType.KW_MUL);
        k.put("div", TokenType.KW_DIV);
        k.put("neg", TokenType.KW_NEG);
        return k;
    }

    public Token nextToken() {
        if (lookahead != null) {
            Token t = lookahead;
            lookahead = null;
            return t;
        }
        return scanLexeme();
    }

    public Token peekToken() {
        if (lookahead == null) {
            lookahead = scanLexeme();
        }
        return lookahead;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token t;
        do {
            t = nextToken();
            tokens.add(t);
        } while (t.getType() != TokenType.EOF);
        return tokens;
    }

    private void skipBlanks() {
        while (!reader.isAtEnd()) {
            char c = reader.peek();
            if (c == ' ' || c == '\r' || c == '\n' || c == '\t') {
                reader.advance();
            } else {
                break;
            }
        }
    }

    private Token scanLexeme() {
        skipBlanks();
        int line = reader.getLine();
        int column = reader.getColumn();
        if (reader.isAtEnd()) {
            return new Token(TokenType.EOF, "$", line, column);
        }

        reader.beginLexeme();
        while (!reader.isAtEnd()) {
            char c = reader.peek();
            if (c == ' ' || c == '\r' || c == '\n' || c == '\t') break;
            reader.advance();
        }
        String lexeme = reader.currentLexeme();
        requireBlankTerminator();

        TokenType type = classify(lexeme);
        if (type == null) {
            LexicalError err = new LexicalError(
                    "Unrecognised lexeme '" + lexeme + "'",
                    "check spelling of keywords, or that names start with '#' and numbers/strings follow the SPL lexical rules",
                    line, column);
            reporter.lexicalError(err);
            // stop here: feeding the parser a placeholder token only produced a misleading syntax error
            throw new ParseException(err.format());
        }
        return new Token(type, lexeme, line, column);
    }

    private void requireBlankTerminator() {
        if (!reader.isAtEnd()) {
            char c = reader.peek();
            if (c != ' ' && c != '\r' && c != '\n' && c != '\t') {
                LexicalError err = new LexicalError(
                        "Token must be terminated by a blank space",
                        "insert a space or newline after '" + reader.currentLexeme() + "'",
                        reader.getLine(), reader.getColumn());
                reporter.lexicalError(err);
            }
        }
    }

    private TokenType classify(String lexeme) {
        if (keywords.containsKey(lexeme)) return keywords.get(lexeme);
        switch (lexeme) {
            case "(": return TokenType.LPAREN;
            case ")": return TokenType.RPAREN;
            case "{": return TokenType.LBRACE;
            case "}": return TokenType.RBRACE;
            case ":": return TokenType.COLON;
            case ";": return TokenType.SEMICOLON;
            case "=": return TokenType.ASSIGN_OP;
            default: break;
        }
        if (isNum(lexeme)) return TokenType.NUM;
        if (isUserName(lexeme)) return TokenType.USER_DEFINED_NAME;
        if (isString(lexeme)) return TokenType.STRING;
        return null;
    }

    private boolean isNum(String s) {
        return NUM_PATTERN.matcher(s).matches();
    }

    private boolean isUserName(String s) {
        return USER_NAME_PATTERN.matcher(s).matches();
    }

    private boolean isString(String s) {
        return STRING_PATTERN.matcher(s).matches();
    }
}
