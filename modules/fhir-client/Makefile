lint:
	clj-kondo --lint src deps.edn

test:
	clojure -M:test --profile :ci

test-coverage:
	clojure -M:test-coverage

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: lint test test-coverage clean
