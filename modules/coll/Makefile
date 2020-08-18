lint:
	clj-kondo --lint src test

test:
	clojure -A:test --profile :ci

test-coverage:
	clojure -A:test-coverage

.PHONY: lint test test-coverage
