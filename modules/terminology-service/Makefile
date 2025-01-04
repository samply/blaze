fmt:
	cljfmt check src test deps.edn tests.edn

lint:
	clj-kondo --lint src test deps.edn

build:
	clojure -T:build download-loinc

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
	rm -rf .clj-kondo/.cache .cpcache target resources/blaze/terminology_service/local/code_system/loinc

.PHONY: fmt lint prep test test-coverage cloc-prod cloc-test clean
