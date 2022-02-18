#!/bin/bash -e

URL=$1

while [[ "$(curl -s -o /dev/null -w ''%{http_code}'' $URL)" != "200" ]]; do sleep 2; done
