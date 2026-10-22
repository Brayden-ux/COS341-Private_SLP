#!/bin/sh
# Usage: tests/phase3a/run_tests.sh   (run from the repository root after compiling to target/classes)
# For every test program X.txt the compiler must accept it and write target code that
#  - is exactly X.code (the reviewed full C++ translation; run with UPDATE=1 to rewrite), and
#  - compiles as C++ with -pedantic-errors (set CXX to choose the compiler).
# r* programs are also run and compared with X.expected. c* programs are compile-only.
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
    cp "$name.code" "$TMP/$name.cpp"
    case $name in
      r*) flags="-pedantic-errors -Wall -Wextra -Werror -Wno-unused-label" ;;
      *) flags="-pedantic-errors -fsyntax-only -Wno-uninitialized -Wno-unused-label" ;;
    esac
    if [ "${name#c}" != "$name" ]; then
      compile_cmd="$CXX -std=c++17 $flags -fsyntax-only $TMP/$name.cpp"
    else
      compile_cmd="$CXX -std=c++17 $flags -o $TMP/$name $TMP/$name.cpp"
    fi
    if ! CCACHE_DISABLE=1 sh -c "$compile_cmd" 2>"$TMP/cxx.txt"; then
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
