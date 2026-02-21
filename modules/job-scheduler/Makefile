fmt:
	cljfmt check src test build.clj deps.edn tests.edn

lint:
	clj-kondo --fail-level error --lint src test build.clj deps.edn

prep:
	clojure -X:deps prep

build: prep
	clojure -T:build copy-profiles

test: build
	clojure -M:test:kaocha --profile :ci

test-coverage: build
	clojure -M:test:coverage

cloc-prod:
	cloc src

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint prep build test test-coverage cloc-prod cloc-test clean
