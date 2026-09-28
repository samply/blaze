fmt:
	cljfmt check src test deps.edn tests.edn

lint:
	clj-kondo --lint src test deps.edn

prep:
	$(MAKE) -C ../module-base prep
	clojure -Sthreads 1 -X:deps prep

test: prep
	clojure -Sthreads 1 -M:test:kaocha --profile :ci

test-focus: prep
	clojure -Sthreads 1 -M:test:kaocha --profile :ci --focus "$(FOCUS)"

test-coverage: prep
	clojure -Sthreads 1 -M:test:kaocha:coverage

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

.PHONY: fmt lint prep test test-focus test-coverage deps-tree deps-list cloc-prod cloc-test clean
