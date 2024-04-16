#!/bin/bash -e

cloc() {
  make "cloc-$2" | grep "$1" | awk '{print $NF}' | awk '{sum+=$1} END {print sum}' | xargs
}

line() {
  printf "| %-12s  | %6s | %6s |\n" "$1" "$(cloc "$1" "prod")" "$(cloc "$1" "test")"
}

echo "| Language      | Prod   | Test   |"
echo "|---------------|-------:|-------:|"
line "Clojure"
line "Java"
line "Svelte"
line "TypeScript"
line "HTML"
line "CSS"
line "Bourne Shell"
line "YAML"
