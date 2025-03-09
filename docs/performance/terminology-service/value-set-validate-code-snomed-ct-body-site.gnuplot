# Set the terminal to PNG and specify the output file
set terminal pngcairo enhanced font 'Helvetica Neue,12'
set output 'plots/value-set-validate-code-snomed-ct-body-site.png'

# Set the data separator and skip the header
set datafile separator ","

# Configure legend
set key left top reverse

# Define x-axis and y-axis labels
set title "SNOMED CT – ValueSet – http://hl7.org/fhir/ValueSet/body-site"
set xlabel 'Concurrent Clients'
set ylabel 'Requests/s'
set y2label 'Processing Time (ms)'

# Enable second y-axis
set y2tics

# Set grid
set grid

set xrange [0:85]
set yrange [0:50000]
set y2range [0:10]

# Plot the data
plot 'data/value-set-validate-code-snomed-ct-body-site.csv' using 1:2 with linespoints pt 7 title 'Requests/s' axes x1y1, \
     'data/value-set-validate-code-snomed-ct-body-site.csv' using 1:3 with linespoints pt 7 title 'Median RT' axes x1y2, \
     'data/value-set-validate-code-snomed-ct-body-site.csv' using 1:4 with linespoints pt 7 title 'P95 RT' axes x1y2, \
     'data/value-set-validate-code-snomed-ct-body-site.csv' using 1:5 with linespoints pt 7 title 'P99 RT' axes x1y2
