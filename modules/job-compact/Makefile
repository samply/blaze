fmt:
	cljfmt check src test build.clj deps.edn tests.edn

lint:
	clj-kondo --lint src test build.clj deps.edn

prep:
	$(MAKE) -C ../module-base prep
	clojure -Sthreads 1 -X:deps prep :aliases '[:test]' :current true

test: prep
	clojure -Sthreads 1 -M:test:kaocha --profile :ci

test-focus: prep
	clojure -Sthreads 1 -M:test:kaocha --profile :ci --focus "$(FOCUS)"

test-coverage: prep
	clojure -Sthreads 1 -M:test:kaocha:coverage

deps-list:
	clojure -X:deps list

cloc-prod:
	cloc src

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint prep test test-focus test-coverage deps-list cloc-prod cloc-test clean
