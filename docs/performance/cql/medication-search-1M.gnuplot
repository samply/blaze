# Set the terminal to PNG and specify the output file
set terminal pngcairo enhanced font 'Helvetica Neue,12'
set output 'medication-search-1M.png'

# Set the data separator and skip the header
set datafile separator "|"
set style data histograms
set style histogram clustered
set style fill solid border -1
set boxwidth 0.8
set key left top reverse

# Define x-axis and y-axis labels
set title "Medication Search - Dataset 1M"
set xlabel 'System'
set ylabel 'Patients/s'
set format y "%.0f k"
set yrange [0:2300]

# Define grid
set grid ytics

# Define line styles and colors for each code
set style line 1 lc rgb '#4DA8DA'
set style line 2 lc rgb '#2E75B6'

# Plot the data
plot 'medication-search-1M.txt' using 7:xtic(3) every 2 ls 1 title '1 % hits', \
     'medication-search-1M.txt' using 7:xtic(3) every 2::1 ls 2 title '7 % hits'
