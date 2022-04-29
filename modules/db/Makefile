lint:
	clj-kondo --lint src test test-perf deps.edn

test:
	clojure -M:test:kaocha --profile :ci

test-coverage:
	clojure -M:test:coverage

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: lint test test-coverage clean
