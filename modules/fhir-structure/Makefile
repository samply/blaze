fmt:
	cljfmt --file-pattern "\.(clj|edn)?$$" check resources src test test-perf build.clj deps.edn tests.edn

lint:
	clj-kondo --lint src test build.clj deps.edn

prep:
	$(MAKE) -C ../module-base prep
	clojure -X:deps prep :current true

test: prep
	clojure -M:test:kaocha --profile :ci --skip-meta :mem-size

test-mem-size-normal-heap: prep
	clojure -J-XX:+UseCompactObjectHeaders -J--sun-misc-unsafe-memory-access=allow -J-Xmx4g -M:test:kaocha --profile :ci --focus-meta :mem-size

test-mem-size-large-heap: prep
	clojure -J-XX:+UseCompactObjectHeaders -J--sun-misc-unsafe-memory-access=allow -J-Xmx32g -M:test:kaocha --profile :ci --focus-meta :mem-size

test-coverage: prep
	clojure -M:test:coverage --skip-meta :mem-size

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

.PHONY: fmt lint prep test test-coverage deps-tree deps-list cloc-prod cloc-test clean
