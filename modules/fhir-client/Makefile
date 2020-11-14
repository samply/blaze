lint:
	clj-kondo --lint src deps.edn

test:
	clojure -M:test --profile :ci

test-coverage:
	clojure -M:test-coverage

.PHONY: lint test test-coverage
