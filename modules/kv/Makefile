fmt:
	cljfmt check src test build.clj deps.edn tests.edn

lint:
	clj-kondo --fail-level error --lint src test build.clj deps.edn

build:
	clojure -T:build compile

prep: build
	clojure -X:deps prep

test: prep
	clojure -M:test:kaocha --profile :ci

test-coverage: build
	clojure -M:test:coverage

cloc-prod:
	cloc src java

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint build test test-coverage cloc-prod cloc-test clean
