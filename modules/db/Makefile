fmt:
	cljfmt --file-pattern "\.(cljs|edn)?$$" check resources src test test-perf build.clj deps.edn tests.edn

lint:
	clj-kondo --lint src test test-perf build.clj deps.edn

prep:
	clojure -X:deps prep

build: prep
	clojure -T:build all

test: build
	clojure -M:test:kaocha --profile :ci --skip-meta :slow

test-slow: build
	clojure -M:test:kaocha --profile :ci --focus-meta :slow

test-coverage: build
	clojure -M:test:coverage --skip-meta :slow

test-coverage-slow: build
	clojure -M:test:coverage --focus-meta :slow

test-coverage-all: build
	clojure -M:test:coverage

deps-tree:
	clojure -X:deps tree

deps-list:
	clojure -X:deps list

cloc-prod:
	cloc src

cloc-test:
	cloc test

IG_PROFILES := $(wildcard resources/blaze/db/[A-Z]*.json)

clean:
	rm -rf .clj-kondo/.cache .cpcache target $(IG_PROFILES)

.PHONY: fmt lint prep build test test-coverage deps-tree deps-list cloc-prod cloc-test clean
