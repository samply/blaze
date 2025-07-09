# Set the terminal to PNG and specify the output file
set terminal pngcairo enhanced font 'Helvetica Neue,12'
set output 'multiple-search-param-search-download-1M.png'

# Set the data separator and skip the header
set datafile separator "|"
set style data histograms
set style histogram clustered
set style fill solid border -1
set boxwidth 0.8
set key left top reverse

# Define x-axis and y-axis labels
set title "Multiple Search Param Search - Download - Dataset 1M"
set xlabel 'System'
set ylabel 'Resources/s'
set format y "%.0f k"
set yrange [0:70]

# Define grid
set grid ytics

# Define line styles and colors for each code
set style line 1 lc rgb '#4DA8DA'
set style line 2 lc rgb '#2E75B6'

# Plot the data
plot 'multiple-search-param-search-download-1M.txt' using 9:xtic(3) every 2 ls 1 title 'vital-signs', \
     'multiple-search-param-search-download-1M.txt' using 9:xtic(3) every 2::1 ls 2 title 'laboratory'
