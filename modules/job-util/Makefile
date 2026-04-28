fmt:
	cljfmt check src test deps.edn tests.edn

lint:
	clj-kondo --lint src test deps.edn

prep:
	$(MAKE) -C ../module-base prep
	clojure -X:deps prep :aliases '[:test]'

test: prep
	clojure -M:test:kaocha --profile :ci

test-coverage: prep
	clojure -M:test:kaocha:coverage

deps-list:
	clojure -X:deps list

cloc-prod:
	cloc src

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint test test-coverage deps-list cloc-prod cloc-test clean
