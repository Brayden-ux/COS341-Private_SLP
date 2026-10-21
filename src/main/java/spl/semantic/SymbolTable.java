package spl.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The static symbol table built by Phase 2a. It maps the unique ID of every
 * USER-DEFINED-NAME leaf of the syntax tree to the Symbol it denotes, so
 * later phases can use the node IDs as foreign keys.
 */
public final class SymbolTable {
    private final List<Symbol> symbols = new ArrayList<>();
    private final Map<Integer, Symbol> byNode = new LinkedHashMap<>();
    private Scope globalScope;
    private int counter = 0;

    Symbol newSymbol(String originalName, Symbol.Kind kind, int level, int declNodeId,
                     boolean parameter, String returnKind){
        counter++;
        Symbol s = new Symbol("sys" + counter, originalName, kind, level, declNodeId, parameter, returnKind);
        symbols.add(s);
        return s;
    }

    void bind(int nodeId, Symbol s){
        byNode.put(nodeId, s);
    }

    void setGlobalScope(Scope s){ this.globalScope = s; }

    /**
     * Returns a brand-new name such as t57 or L58. It shares one counter with the
     * sysN names, so a generated temporary or label can never collide with a
     * renamed user entity or with any other generated name. Intended for the code generator.
     */
    public String freshName(String prefix) {
        counter++;
        return prefix + counter;
    }

    /** All variables and parameters of the whole program (each declared exactly once). */
    public List<Symbol> getVariableSymbols(){
        List<Symbol> out = new ArrayList<>();
        for (Symbol s : symbols) if (s.getKind() == Symbol.Kind.VARIABLE) out.add(s);
        return out;
    }

    public Scope getGlobalScope(){ return globalScope; }
    public List<Symbol> getSymbols(){ return symbols; }
    public Map<Integer, Symbol> getNodeBindings(){ return byNode; }

    /** The symbol a NAME leaf refers to or null if the node is not a bound name. */
    public Symbol symbolFor(int nodeId){ return byNode.get(nodeId); }

    /** The system-generated internal name for a NAME leaf, or null. */
    public String internalNameFor(int nodeId){
        Symbol s = byNode.get(nodeId);
        return s == null ? null : s.getInternalName();
    }

    public String dump(){
        StringBuilder sb = new StringBuilder();
        sb.append("SYMBOL TABLE (after unique renaming)\n");
        if (globalScope != null) dumpScope(globalScope, 0, sb);
        sb.append("\nNODE BINDINGS (syntax-tree node id -> original name -> internal name)\n");
        for (Map.Entry<Integer, Symbol> e : byNode.entrySet()){
            Symbol s = e.getValue();
            sb.append(String.format("  node %-4d %-12s -> %s%n", e.getKey(), s.getOriginalName(), s.getInternalName()));
        }
        return sb.toString();
    }

    private void dumpScope(Scope scope, int indent, StringBuilder sb){
        String pad = "  ".repeat(indent);
        sb.append(pad).append("scope level ").append(scope.getLevel())
          .append(" (").append(scope.describe()).append(")\n");
        for (Symbol s : scope.getVariables().values()){
            sb.append(pad).append("  ").append(s.isParameter() ? "param " : "var   ")
              .append(String.format("%-12s -> %s%n", s.getOriginalName(), s.getInternalName()));
        }
        for (Symbol s : scope.getFunctions().values()){
            StringBuilder params = new StringBuilder();
            for (Symbol prm : s.getParameters()) {
                if (params.length() > 0) params.append(", ");
                params.append(prm.getInternalName());
            }
            sb.append(pad).append("  func  ").append(s.getReturnKind()).append(' ')
              .append(String.format("%s -> %s (params: %s)%n", s.getOriginalName(), s.getInternalName(), params));
        }
        for (Scope child : scope.getChildren()) dumpScope(child, indent + 1, sb);
    }
}