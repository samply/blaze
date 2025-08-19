fmt:
	cljfmt check src test build.clj deps.edn tests.edn

lint:
	clj-kondo --lint src test build.clj deps.edn

prep:
	clojure -X:deps prep

build: prep
	clojure -T:build download-loinc

test: build
	clojure -M:test:kaocha --profile :ci

test-coverage: build
	clojure -M:test:coverage

cloc-prod:
	cloc src

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache target resources/blaze/terminology_service/local/code_system/loinc

.PHONY: fmt lint prep test test-coverage cloc-prod cloc-test clean
