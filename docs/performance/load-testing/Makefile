data/patient-everything.csv: patient-everything.js
	k6 run patient-everything.js

data/patient-graph.csv: patient-graph.js
	k6 run patient-graph.js

# Systems the transaction test was run on. Each has its own committed
# data/transaction-<system>.csv.
TRANSACTION_SYSTEMS := LEA47 LEA79 A5N46

# The transaction test is run several times, because single runs vary. Each run
# is kept in data/raw and data/transaction-<system>.csv holds their median.
TRANSACTION_RUNS ?= 1 2 3

# Every run needs an empty server, so the Blaze of the system, which runs on the
# same host via its docker-compose-<system>.yml, is restarted with an empty
# database first.
data/transaction-%.csv: transaction.js median.sh reset.sh docker-compose-%.yml
	for run in $(TRANSACTION_RUNS); do \
		./reset.sh $* && SYSTEM=$* RUN=$$run k6 run transaction.js || exit 1; \
	done
	./median.sh $(TRANSACTION_RUNS:%=data/raw/transaction-$*-%.csv) > $@

all: data/patient-everything.csv data/patient-graph.csv \
	$(TRANSACTION_SYSTEMS:%=data/transaction-%.csv)

# Not part of `all`: this test reports through the k6 summary and its thresholds
# rather than a CSV, and it requires an empty server.
patient-ui: patient-ui.js
	k6 run patient-ui.js

.PHONY: all patient-ui
