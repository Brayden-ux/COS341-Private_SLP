#!/bin/sh
# Usage: tests/phase2a/run_tests.sh   (run from the repository root after compiling to target/classes)
# v* files must be accepted silently; e* files must be rejected with the stated number of errors.
cd "$(dirname "$0")" || exit 1
CP=../../target/classes
pass=0; fail=0
check() { # file expected_exit expected_error_count
  out=$(java -cp $CP spl.SPLCompiler "$1" /tmp/_tree.xml /tmp/_code.txt 2>/tmp/_err.txt); code=$?
  n=$(grep -c '^Semantic error' /tmp/_err.txt)
  if [ "$code" = "$2" ] && [ "$n" = "$3" ]; then pass=$((pass+1)); echo "PASS $1"
  else fail=$((fail+1)); echo "FAIL $1 (exit $code, $n semantic errors, expected exit $2 and $3)"; cat /tmp/_err.txt; fi
}
check v1_example.txt 0 0
check v2_nested_shadowing.txt 0 0
check v3_param_visible_in_nested.txt 0 0
check v4_sideways_ok_inside_ancestor.txt 0 0
check e1_undeclared_var.txt 1 1
check e2_undeclared_func.txt 1 1
check e3_double_decl.txt 1 1
check e4_param_masked.txt 1 1
check e5_dup_function.txt 1 1
check e6_direct_recursion.txt 1 1
check e7_indirect_recursion.txt 1 1
check e8_sibling_call.txt 1 1
check e9_parent_level_function.txt 1 1
check e10_sideways_variable.txt 1 1
check e11_variable_called_as_function.txt 1 1
check e12_return_term_undeclared.txt 1 1
check e13_duplicate_parameter.txt 1 1
check e14_multiple_errors.txt 1 3
echo "passed $pass, failed $fail"
[ "$fail" = 0 ]
