# Set the terminal to PNG and specify the output file
set terminal pngcairo enhanced font 'Helvetica Neue,12'
set output 'token-forward-chaining-search-download-1M.png'

# Set the data separator and skip the header
set datafile separator "|"
set style data histograms
set style histogram clustered
set style fill solid border -1
set boxwidth 0.8
set key left top reverse

# Define x-axis and y-axis labels
set title "Token and Forward Chaining Search - Download - Dataset 1M"
set xlabel 'System'
set ylabel 'Resources/s'
set format y "%.0f k"
set yrange [0:30]

# Define grid
set grid ytics

# Define line styles and colors
set style line 1 lc rgb '#4DA8DA'

# Plot the data
plot 'token-forward-chaining-search-download-1M.txt' using 4:xtic(2) ls 1 title '32 k hits'
