data/patient-everything.csv: patient-everything.js
	k6 run patient-everything.js

data/patient-graph.csv: patient-graph.js
	k6 run patient-graph.js

# Systems the transaction test was run on. Each has its own committed
# data/transaction-<system>.csv.
TRANSACTION_SYSTEMS := LEA47 LEA79

data/transaction-%.csv: transaction.js
	SYSTEM=$* k6 run transaction.js

all: data/patient-everything.csv data/patient-graph.csv \
	$(TRANSACTION_SYSTEMS:%=data/transaction-%.csv)

.PHONY: all
