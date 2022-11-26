#!/bin/bash -e

test() {
  if [ "$2" = "$3" ]; then
    echo "OK: the $1 is $3"
  else
    echo "Fail: the $1 is $2, expected $3"
    exit 1
  fi
}
