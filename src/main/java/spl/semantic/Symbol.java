package spl.semantic;

import java.util.ArrayList;
import java.util.List;

/**
 * One computational entity (a variable or a function) after unique renaming.
 * Every USER-DEFINED-NAME leaf of the syntax tree that refers to this entity
 * carries the same internal name. different entities never share one.
 */
public final class Symbol{
    public enum Kind { VARIABLE, FUNCTION }

    private final String internalName;
    private final String originalName;
    private final Kind kind;
    private final int scopeLevel;
    private final int declNodeId;
    private final boolean parameter;
    private final String returnKind; // "num" or "void" for functions, null for variables
    private final List<Symbol> parameters = new ArrayList<>(); // functions only, in declaration order
    private int ftypeNodeId = -1; // functions only: the F_TYPE node that declares this function
    private String type = "unknown"; // Phase 2b: "numeric" or "procedure"

    Symbol(String internalName, String originalName, Kind kind, int scopeLevel,
           int declNodeId, boolean parameter, String returnKind){
        this.internalName = internalName;
        this.originalName = originalName;
        this.kind = kind;
        this.scopeLevel = scopeLevel;
        this.declNodeId = declNodeId;
        this.parameter = parameter;
        this.returnKind = returnKind;
    }

    public String getInternalName(){ return internalName; }
    public String getOriginalName(){ return originalName; }
    public Kind getKind(){ return kind; }
    public int getScopeLevel(){ return scopeLevel; }
    public int getDeclNodeId(){ return declNodeId; }
    public boolean isParameter(){ return parameter; }
    public String getReturnKind(){ return returnKind; }
    public List<Symbol> getParameters(){ return parameters; }
    public int getFtypeNodeId(){ return ftypeNodeId; }
    void setFtypeNodeId(int id){ this.ftypeNodeId = id; }
    public String getType(){ return type; }
    public void setType(String type){ this.type = type; }

    @Override
    public String toString(){
        return kind + " " + originalName + " -> " + internalName;
    }
}