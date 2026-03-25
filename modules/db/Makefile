fmt:
	cljfmt --file-pattern "\.(clj|edn)?$$" check resources src test test-perf build.clj deps.edn tests.edn

lint:
	clj-kondo --lint src test test-perf build.clj deps.edn

prep:
	$(MAKE) -C ../module-base prep
	clojure -X:deps prep :current true

test: prep
	clojure -M:test:kaocha --profile :ci --skip-meta :slow

test-slow: prep
	clojure -M:test:kaocha --profile :ci --focus-meta :slow

test-coverage: prep
	clojure -M:test:coverage --skip-meta :slow

test-coverage-slow: prep
	clojure -M:test:coverage --focus-meta :slow

test-coverage-all: prep
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
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint prep test test-coverage deps-tree deps-list cloc-prod cloc-test clean
