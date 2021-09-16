lint:
	clj-kondo --lint src test deps.edn

compile:
	clojure -T:build compile

test: compile
	clojure -M:test --profile :ci

test-coverage: compile
	clojure -M:test-coverage

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: lint compile test test-coverage clean
