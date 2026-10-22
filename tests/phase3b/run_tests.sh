#!/bin/sh
# Run from repository root after compiling sources into target/classes.
# Positive programs are compiled as complete C++17 translation units and executed.
cd "$(dirname "$0")" || exit 1
CP=../../target/classes
CXX=${CXX:-c++}
TMP=${TMPDIR:-/tmp}/spl3b.$$
mkdir -p "$TMP" || exit 1
pass=0; fail=0

for src in b*.txt; do
  name=${src%.txt}
  why=""
  java -cp "$CP" spl.SPLCompiler "$src" "$TMP/tree.xml" "$TMP/$name.cpp" 2>"$TMP/compiler.err"
  status=$?
  if [ "$status" != 0 ]; then why="compiler rejected input: $(cat "$TMP/compiler.err")"; fi
  if [ -z "$why" ] && ! $CXX -std=c++17 -pedantic-errors -Wall -Wextra -Werror -Wno-unused-label "$TMP/$name.cpp" -o "$TMP/$name" 2>"$TMP/cxx.err"; then
    why="C++ compilation failed: $(cat "$TMP/cxx.err")"
  fi
  if [ -z "$why" ]; then
    "$TMP/$name" > "$TMP/$name.out"
    actual=$(cat "$TMP/$name.out")
    expected=$(cat "$name.expected")
    if [ "$actual" != "$expected" ]; then
      why="output differs (got '$(cat "$TMP/$name.out")', expected '$(cat "$name.expected")')"
    fi
  fi
  if [ -z "$why" ]; then pass=$((pass+1)); echo "PASS $src"
  else fail=$((fail+1)); echo "FAIL $src: $why"; fi
done

java -cp "$CP" spl.SPLCompiler e01_argument_count.txt "$TMP/tree.xml" "$TMP/arity.cpp" 2>"$TMP/arity.err"
status=$?
if [ "$status" != 0 ] && grep -q 'Code generation error: call to #sum has 1 argument(s), but its declaration has 2' "$TMP/arity.err"; then
  pass=$((pass+1)); echo "PASS e01_argument_count.txt"
else
  fail=$((fail+1)); echo "FAIL e01_argument_count.txt (exit $status; expected clear arity error)"; cat "$TMP/arity.err"
fi

rm -rf "$TMP"
echo "passed $pass, failed $fail"
[ "$fail" = 0 ]
