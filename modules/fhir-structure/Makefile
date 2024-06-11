fmt:
	cljfmt check src test test-perf build.clj

lint:
	clj-kondo --lint src test build.clj deps.edn

build:
	clojure -T:build compile

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
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint build test test-coverage deps-tree deps-list cloc-prod cloc-test clean
