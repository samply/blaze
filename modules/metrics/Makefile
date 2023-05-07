lint:
	clj-kondo --lint src test deps.edn

prep:
	clojure -X:deps prep

build: prep
	clojure -T:build compile

test: build
	clojure -M:test:kaocha --profile :ci

test-coverage: build
	clojure -M:test:coverage

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: lint prep build test test-coverage clean
