fmt:
	cljfmt check resources src test test-perf build.clj deps.edn tests.edn

lint:
	clj-kondo --lint src test test-perf build.clj deps.edn

build:
	clojure -T:build copy-profiles

prep: build
	clojure -X:deps prep

test: prep
	clojure -M:test:kaocha --profile :ci

test-coverage: prep
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

.PHONY: fmt lint prep test test-coverage deps-tree deps-list cloc-prod cloc-test clean
