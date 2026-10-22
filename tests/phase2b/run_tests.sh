#!/bin/sh
# Usage: tests/phase2b/run_tests.sh   (run from the repository root after compiling to target/classes)
# v* files must be accepted silently; e* files must be rejected with the stated number of type errors.
cd "$(dirname "$0")" || exit 1
CP=../../target/classes
pass=0; fail=0
check() { # file expected_exit expected_error_count [expected program number type]
  out=$(java -cp $CP spl.SPLCompiler "$1" /tmp/_tree.xml --dump-types 2>/tmp/_err.txt); code=$?
  n=$(grep -c '^Type error' /tmp/_err.txt)
  typ=$(printf '%s\n' "$out" | sed -n 's/.*typ for Phase 3a): //p')
  if [ "$code" = "$2" ] && [ "$n" = "$3" ] && { [ -z "$4" ] || [ "$typ" = "$4" ]; }; then pass=$((pass+1)); echo "PASS $1"
  else fail=$((fail+1)); echo "FAIL $1 (exit $code, $n type errors, typ '$typ'; expected exit $2, $3 errors ${4:+and typ $4})"; grep -v JAVA_TOOL /tmp/_err.txt; fi
}
check v1_calls_ok.txt 0 0 int
check v2_whole_numbers.txt 0 0 float
check v3_real_number_is_float.txt 0 0 float
check v4_void_call_in_loop.txt 0 0 int
check v5_div_is_float.txt 0 0 float
check v6_mod_is_int.txt 0 0 int
check v7_argument_count_not_type_checked.txt 0 0 int
check e1_void_used_as_value.txt 1 1
check e2_num_called_as_instruction.txt 1 1
check e3_mod_and_div_conflict.txt 1 1
check e4_mod_with_decimal_number.txt 1 1
check e5_void_in_condition.txt 1 1
check e6_multiple_errors.txt 1 2
check e7_void_in_return_term.txt 1 1
check e8_call_to_function_with_type_error.txt 1 2
echo "passed $pass, failed $fail"
[ "$fail" = 0 ]
