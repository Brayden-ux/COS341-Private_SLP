package spl.semantic;

import spl.lexer.Token;

// One violation of the SPL naming/scoping rules (Phase 2a) or typing rules (Phase 2b).
public final class SemanticError{
    private final Token token;
    private final String message;
    private final String kind; // "Semantic" for 2a, "Type" for 2b

    public SemanticError(Token token, String message){
        this(token, message, "Semantic");
    }

    private SemanticError(Token token, String message, String kind){
        this.token = token;
        this.message = message;
        this.kind = kind;
    }

    public static SemanticError typeError(Token token, String message){
        return new SemanticError(token, message, "Type");
    }

    public Token getToken(){
        return token;
    }

    public String getMessage(){
        return message;
    }

    public boolean isTypeError(){
        return kind.equals("Type");
    }

    public String format(){
        return kind + " error at line " + token.getLine() + ", column " + token.getColumn() + ": " + message;
    }

    @Override
    public String toString(){
        return format();
    }
}
