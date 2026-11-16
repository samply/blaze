data/patient-everything.csv: patient-everything.js
	k6 run patient-everything.js

data/patient-graph.csv: patient-graph.js
	k6 run patient-graph.js

# Systems the transaction test was run on. Each has its own committed results
# in data/transaction/<system>/<version>.csv, one file per Blaze version.
TRANSACTION_SYSTEMS := LEA47 LEA79 A5N46

# The transaction test is run several times, because single runs vary. Each run
# is kept in data/raw/transaction/<system> and data/transaction/<system> holds
# their median.
TRANSACTION_RUNS ?= 1 2 3

# Runs the transaction test of the system SYSTEM against Blaze VERSION, a
# release like 1.10.0. Without a VERSION, the development build blaze:latest is
# tested. IMAGE overrides the image, like IMAGE=samply/blaze:main. Every run needs an empty server, so the Blaze of the system, which
# runs on the same host via its docker-compose-<system>.yml, is restarted with
# an empty database first. See transaction.sh.
transaction:
ifndef SYSTEM
	$(error SYSTEM is required, one of: $(TRANSACTION_SYSTEMS))
endif
	RUNS="$(TRANSACTION_RUNS)" ./transaction.sh $(SYSTEM) $(VERSION)

all: data/patient-everything.csv data/patient-graph.csv

# Not part of `all`: this test reports through the k6 summary and its thresholds
# rather than a CSV, and it requires an empty server.
patient-ui: patient-ui.js
	k6 run patient-ui.js

.PHONY: all transaction patient-ui
