fmt:
	cljfmt check resources src test test-perf build.clj deps.edn tests.edn

lint:
	clj-kondo --lint src test build.clj deps.edn

prep:
	clojure -X:deps prep

build: prep
	clojure -T:build all

test: build
	clojure -M:test:kaocha --profile :ci

test-coverage: build
	clojure -M:test:coverage

deps-tree:
	clojure -X:deps tree

deps-list:
	clojure -X:deps list

cloc-prod:
	cloc src java

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache target resources/blaze/fhir/*.json

.PHONY: fmt lint prep build test test-coverage deps-tree deps-list cloc-prod cloc-test clean
