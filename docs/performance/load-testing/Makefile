data/patient-everything.csv: patient-everything.js
	k6 run patient-everything.js

plots/patient-everything.png: data/patient-everything.csv patient-everything.gnuplot
	gnuplot patient-everything.gnuplot

data/patient-graph.csv: patient-graph.js
	k6 run patient-graph.js

plots/patient-graph.png: data/patient-graph.csv patient-graph.gnuplot
	gnuplot patient-graph.gnuplot

# Systems the transaction test was run on. Each has its own committed
# data/transaction-<system>.csv and plots/transaction-<system>.png.
TRANSACTION_SYSTEMS := LEA47 LEA79

data/transaction-%.csv: transaction.js
	SYSTEM=$* k6 run transaction.js

plots/transaction-%.png: data/transaction-%.csv transaction.gnuplot
	gnuplot -e "system='$*'" transaction.gnuplot

all: plots/patient-everything.png plots/patient-graph.png \
	$(TRANSACTION_SYSTEMS:%=plots/transaction-%.png)

.PHONY: all
