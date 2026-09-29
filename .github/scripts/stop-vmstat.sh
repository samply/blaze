#!/bin/bash
set -euo pipefail

# Stops the sampling started by start-vmstat.sh under the name $1 and prints
# the samples together with their means. The first sample is excluded from the
# means, because it covers the time since boot.

name="$1"

if [ ! -f "$name.vmstat.pid" ]; then
  echo "No vmstat sampling named $name was started."
  exit 0
fi

kill "$(cat "$name.vmstat.pid")" || true

echo "=== vmstat $name (2s interval)"
cat "$name.vmstat.log"

echo "=== vmstat $name means"
awk 'NR > 3 {n++; r+=$1; bo+=$10; us+=$13; sy+=$14; id+=$15; wa+=$16; st+=$17}
     END {if (n) printf "samples=%d r=%.0f us=%.0f sy=%.0f id=%.0f wa=%.0f st=%.0f bo=%.0f MB/s\n",
                        n, r/n, us/n, sy/n, id/n, wa/n, st/n, bo/n/1024}' "$name.vmstat.log"
