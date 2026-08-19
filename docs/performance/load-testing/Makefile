data/patient-everything.csv: patient-everything.js
	k6 run patient-everything.js

data/patient-graph.csv: patient-graph.js
	k6 run patient-graph.js

# Systems the transaction test was run on. Each has its own committed
# data/transaction-<system>.csv.
TRANSACTION_SYSTEMS := LEA47 LEA79 A5N46

data/transaction-%.csv: transaction.js
	SYSTEM=$* k6 run transaction.js

all: data/patient-everything.csv data/patient-graph.csv \
	$(TRANSACTION_SYSTEMS:%=data/transaction-%.csv)

# Not part of `all`: this test reports through the k6 summary and its thresholds
# rather than a CSV, and it requires an empty server.
patient-ui: patient-ui.js
	k6 run patient-ui.js

.PHONY: all patient-ui
