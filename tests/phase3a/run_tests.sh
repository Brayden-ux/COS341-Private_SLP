#!/bin/sh
# Usage: tests/phase3a/run_tests.sh   (run from the repository root after compiling to target/classes)
# For every test program X.txt the compiler must accept it and write target code that
#  - is exactly X.code (the reviewed translation; run with UPDATE=1 to rewrite the .code files), and
#  - compiles as C++ once wrapped in a main() that declares the program's sysN variables,
#    with -pedantic-errors (set CXX to choose the compiler, e.g. CXX=g++ or CXX=g++-13).
# r* programs are also run: their output must be exactly X.expected.
# c* programs contain function calls (inlined only in Phase 3b), so they are only compiled.
cd "$(dirname "$0")" || exit 1
CP=../../target/classes
CXX=${CXX:-c++}
TMP=${TMPDIR:-/tmp}/spl3a.$$
mkdir -p "$TMP"
pass=0; fail=0
for src in r*.txt c*.txt; do
  name=${src%.txt}
  why=""
  out=$(java -cp $CP spl.SPLCompiler "$src" "$TMP/tree.xml" "$TMP/$name.code" --dump-types 2>"$TMP/err.txt")
  if [ $? != 0 ]; then why="rejected: $(cat "$TMP/err.txt")"; fi
  if [ -z "$why" ] && [ -n "$UPDATE" ]; then cp "$TMP/$name.code" "$name.code"; fi
  if [ -z "$why" ] && ! cmp -s "$TMP/$name.code" "$name.code"; then
    why="target code differs from $name.code:
$(diff "$name.code" "$TMP/$name.code")"
  fi
  if [ -z "$why" ]; then
    typ=$(printf '%s\n' "$out" | sed -n 's/.*typ for Phase 3a): //p')
    {
      echo '#include <iostream>'
      echo 'int main()'
      echo '{'
      for v in $(grep -o 'sys[0-9]*' "$name.code" | sort -u); do echo "$typ $v ;"; done
      cat "$name.code"
      echo 'return 0;'
      echo '}'
    } > "$TMP/$name.cpp"
    case $name in
      r*) flags="-pedantic-errors -Wall -Wextra -Werror -Wno-unused-label" ;;
      *) flags="-pedantic-errors -fsyntax-only -Wno-uninitialized -Wno-unused-label" ;;
    esac
    if ! $CXX -std=c++17 $flags -o "$TMP/$name" "$TMP/$name.cpp" 2>"$TMP/cxx.txt"; then
      why="C++ compilation failed:
$(cat "$TMP/cxx.txt")"
    fi
  fi
  if [ -z "$why" ] && [ -f "$name.expected" ]; then
    "$TMP/$name" > "$TMP/$name.out"
    if ! cmp -s "$TMP/$name.out" "$name.expected"; then
      why="output '$(cat "$TMP/$name.out")', expected '$(cat "$name.expected")'"
    fi
  fi
  if [ -z "$why" ]; then pass=$((pass+1)); echo "PASS $src"
  else fail=$((fail+1)); echo "FAIL $src: $why"; fi
done
rm -rf "$TMP"
echo "passed $pass, failed $fail"
[ "$fail" = 0 ]
