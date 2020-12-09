lint:
	clj-kondo --lint src test deps.edn

test:
	clojure -M:test --profile :ci

test-coverage:
	clojure -M:test-coverage

clear:
	rm -r .clj-kondo/.cache .cpcache target

.PHONY: lint test test-coverage clear
