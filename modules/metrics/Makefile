fmt:
	cljfmt check

lint:
	clj-kondo --lint src test deps.edn

build:
	clojure -T:build compile

test: build
	clojure -M:test:kaocha --profile :ci

test-coverage: build
	clojure -M:test:coverage

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint build test test-coverage clean
