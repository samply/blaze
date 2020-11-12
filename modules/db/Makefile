lint:
	clj-kondo --lint src test

test:
	clojure -M:test --profile :ci

test-coverage:
	clojure -M:test-coverage

unused-vars:
	clojure -M:unused-vars src test

.PHONY: lint test test-coverage unused-vars
