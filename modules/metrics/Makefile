fmt:
	cljfmt check src test build.clj deps.edn tests.edn

lint:
	clj-kondo --lint src test build.clj deps.edn

prep:
	$(MAKE) -C ../module-base prep
	clojure -X:deps prep :current true

test: prep
	clojure -M:test:kaocha --profile :ci

test-coverage: prep
	clojure -M:test:coverage

cloc-prod:
	cloc src

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint prep test test-coverage cloc-prod cloc-test clean
