#!/bin/bash -e

gnuplot simple-code-search-100k.gnuplot
gnuplot simple-code-search-100k-fh.gnuplot
gnuplot simple-code-search-1M.gnuplot
gnuplot -e 'system="LEA58"' simple-code-search-all-datasets.gnuplot
gnuplot -e 'system="LEA47"' simple-code-search-all-datasets.gnuplot
gnuplot -e 'system="LEA36"' simple-code-search-all-datasets.gnuplot
gnuplot -e 'system="LEA25"' simple-code-search-all-datasets.gnuplot
gnuplot code-value-search-100k.gnuplot
gnuplot code-value-search-1M.gnuplot
gnuplot -e 'system="LEA58"' code-value-search-all-datasets.gnuplot
gnuplot -e 'system="LEA47"' code-value-search-all-datasets.gnuplot
gnuplot -e 'system="LEA36"' code-value-search-all-datasets.gnuplot
gnuplot -e 'system="LEA25"' code-value-search-all-datasets.gnuplot
gnuplot code-date-age-search-100k.gnuplot
gnuplot double-code-search-100k.gnuplot
gnuplot ten-code-search-100k.gnuplot
gnuplot ten-code-search-100k-fh.gnuplot
gnuplot ten-code-search-1M.gnuplot
gnuplot -e 'system="LEA58"' ten-code-search-all-datasets.gnuplot
gnuplot -e 'system="LEA47"' ten-code-search-all-datasets.gnuplot
gnuplot -e 'system="LEA36"' ten-code-search-all-datasets.gnuplot
gnuplot -e 'system="LEA25"' ten-code-search-all-datasets.gnuplot
gnuplot all-code-search-100k.gnuplot
gnuplot all-code-search-100k-fh.gnuplot
gnuplot all-code-search-1M.gnuplot
gnuplot inpatient-stress-search-100k.gnuplot
gnuplot inpatient-stress-search-100k-fh.gnuplot
gnuplot inpatient-stress-search-1M.gnuplot
gnuplot medication-search-100k.gnuplot
gnuplot medication-search-1M.gnuplot
gnuplot medication-ten-search-100k.gnuplot
gnuplot medication-ten-search-1M.gnuplot
