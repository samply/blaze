lint:
	clj-kondo --lint src test deps.edn

test:
	clojure -M:test:kaocha --profile :ci

test-coverage:
	true

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: lint test test-coverage clean
