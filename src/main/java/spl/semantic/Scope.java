package spl.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import spl.tree.SyntaxTreeNode;

/**
 * One scope level: the P node of the main program (level 0) or the P node
 * of a function body (level of the function + 1). Parameters and local
 * variables of a function live together in the function's scope.
 */
public final class Scope {
    private final int level;
    private final Scope parent;
    private final Symbol owner; // the function whose body this is - null for level 0
    private final Map<String, Symbol> variables = new LinkedHashMap<>();
    private final Map<String, Symbol> functions = new LinkedHashMap<>();
    private final List<Scope> children = new ArrayList<>();
    private final List<SyntaxTreeNode> bodies = new ArrayList<>(); // ALGO (and return TERM) to resolve

    Scope(int level, Scope parent, Symbol owner){
        this.level = level;
        this.parent = parent;
        this.owner = owner;
        if (parent != null) parent.children.add(this);
    }

    public int getLevel(){ return level; }
    public Scope getParent(){ return parent; }
    public Symbol getOwner(){ return owner; }
    public Map<String, Symbol> getVariables(){ return variables; }
    public Map<String, Symbol> getFunctions(){ return functions; }
    public List<Scope> getChildren(){ return children; }
    List<SyntaxTreeNode> getBodies(){ return bodies; }

    public Symbol lookupVariable(String name){
        for (Scope s = this; s != null; s = s.parent){
            Symbol sym = s.variables.get(name);
            if (sym != null) return sym;
        }
        return null;
    }

    public String describe(){
        return owner == null ? "main program" : "function " + owner.getOriginalName();
    }
}