data/patient-everything.csv: patient-everything.js
	k6 run patient-everything.js

plots/patient-everything.png: data/patient-everything.csv patient-everything.gnuplot
	gnuplot patient-everything.gnuplot

data/patient-graph.csv: patient-graph.js
	k6 run patient-graph.js

plots/patient-graph.png: data/patient-graph.csv patient-graph.gnuplot
	gnuplot patient-graph.gnuplot

all: plots/patient-everything.png plots/patient-graph.png

.PHONY: all
