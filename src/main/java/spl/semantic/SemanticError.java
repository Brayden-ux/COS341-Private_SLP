package spl.semantic;

import spl.lexer.Token;

// One violation of the SPL naming/scoping rules (Phase 2a). 
public final class SemanticError{
    private final Token token;
    private final String message;

    public SemanticError(Token token, String message){
        this.token = token;
        this.message = message;
    }

    public Token getToken(){
        return token;
    }

    public String getMessage(){
        return message;
    }

    public String format(){
        return "Semantic error at line " + token.getLine() + ", column " + token.getColumn() + ": " + message;
    }

    @Override
    public String toString(){
        return format();
    }
}