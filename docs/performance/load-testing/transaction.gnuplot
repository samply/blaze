# The system is passed in via -e "system='LEA47'"

# Set the terminal to PNG and specify the output file
set terminal pngcairo enhanced font 'Helvetica Neue,12'
set output 'plots/transaction-'.system.'.png'

# Set the data separator and skip the header
set datafile separator ","

# Configure legend
set key left top reverse

# Define x-axis and y-axis labels
set title "Transaction (".system.")"
set xlabel 'Concurrent Clients'
set logscale x
set xtics (1, 2, 4, 8, 16, 32, 64)

set ylabel 'Requests/s'
set y2label 'Processing Time (ms)'

# Enable second y-axis
set y2tics

# Set grid
set grid

set xrange [1:64]
set autoscale y
set autoscale y2

datafile = 'data/transaction-'.system.'.csv'

# Plot the data
plot datafile using 1:2 with linespoints pt 7 title 'Requests/s' axes x1y1, \
     datafile using 1:3 with linespoints pt 7 title 'Median RT' axes x1y2, \
     datafile using 1:4 with linespoints pt 7 title 'P95 RT' axes x1y2, \
     datafile using 1:5 with linespoints pt 7 title 'P99 RT' axes x1y2
