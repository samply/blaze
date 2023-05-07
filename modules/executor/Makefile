lint:
	clj-kondo --lint src test deps.edn

prep:
	clojure -X:deps prep

test: prep
	clojure -M:test:kaocha --profile :ci

test-coverage: prep
	clojure -M:test:coverage

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: lint prep test test-coverage clean
