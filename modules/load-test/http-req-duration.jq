[ inputs | select(
  .type == "Point" and
  .metric == "http_req_duration" and
  .data.tags.vus_active == "16" and
  .data.tags.name == "everything" and
  .data.tags.status == "200"
).data ] as $data | [ $data[].value ] | sort as $durations |
{
  "req_s": (length / (($data[-1].time[:19] + "Z") | fromdate - (($data[0].time[:19] + "Z") | fromdate))),
  "cnt": length,
  "med": $durations[(length * 0.5) | round],
  "q95": $durations[(length * 0.95) | round],
  "q99": $durations[(length * 0.99) | round]
}
