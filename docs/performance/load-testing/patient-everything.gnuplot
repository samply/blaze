# Set the terminal to PNG and specify the output file
set terminal pngcairo enhanced font 'Helvetica Neue,12'
set output 'plots/patient-everything.png'

# Set the data separator and skip the header
set datafile separator ","

# Configure legend
set key left top reverse

# Define x-axis and y-axis labels
set title "Patient Everything"
set xlabel 'Concurrent Clients'
set logscale x
set xtics (1, 2, 4, 8, 16, 32)

set ylabel 'Requests/s'
set y2label 'Processing Time (ms)'

# Enable second y-axis
set y2tics

# Set grid
set grid

set xrange [1:32]
set yrange [0:160]
set y2range [0:800]

# Plot the data
plot 'data/patient-everything.csv' using 1:2 with linespoints pt 7 title 'Requests/s' axes x1y1, \
     'data/patient-everything.csv' using 1:3 with linespoints pt 7 title 'Median RT' axes x1y2, \
     'data/patient-everything.csv' using 1:4 with linespoints pt 7 title 'P95 RT' axes x1y2, \
     'data/patient-everything.csv' using 1:5 with linespoints pt 7 title 'P99 RT' axes x1y2
