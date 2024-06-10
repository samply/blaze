fmt:
	cljfmt check src test build.clj

lint:
	clj-kondo --lint src test build.clj deps.edn

build:
	clojure -T:build copy-profiles

prep: build
	clojure -X:deps prep

test: prep
	clojure -M:test:kaocha --profile :ci

test-coverage: prep
	clojure -M:test:coverage

cloc-prod:
	cloc src

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache target resources

.PHONY: fmt lint build prep test test-coverage cloc-prod cloc-test clean
