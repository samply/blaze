fmt:
	cljfmt --file-pattern "\.(cljs|edn)?$$" check resources src test build.clj deps.edn tests.edn

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

.PHONY: fmt lint prep build test test-coverage deps-tree deps-list cloc-prod cloc-test clean
