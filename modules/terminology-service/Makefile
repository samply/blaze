fmt:
	cljfmt check src test deps.edn tests.edn

lint:
	clj-kondo --lint src test deps.edn

prep:
	clojure -X:deps prep

build: prep
	clojure -T:build download-loinc

test: build
	clojure -M:test:kaocha --profile :ci

test-coverage: build
	clojure -M:test:coverage

deps-tree:
	clojure -X:deps tree

deps-list:
	clojure -X:deps list

cloc-prod:
	cloc src

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache target resources/blaze/terminology_service/local/code_system/loinc

.PHONY: fmt lint prep test test-coverage deps-tree deps-list cloc-prod cloc-test clean
