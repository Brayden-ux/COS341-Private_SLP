package spl.semantic;

/**
 * The type attribute of a syntax-tree node in Phase 2b, as named in the 2b specification.
 * Every node starts as UNKNOWN; the type analyser replaces that with a more specific type.
 */
public enum Type {
    UNKNOWN, OK, NUMERIC, BOOLEAN, PROCEDURE;

    public String label(){
        return name().toLowerCase();
    }
}
