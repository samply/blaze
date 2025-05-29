fmt:
	cljfmt check src test deps.edn tests.edn

lint:
	clj-kondo --lint src test deps.edn

test:
	clojure -M:test:kaocha --profile :ci

test-coverage:
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

.PHONY: fmt lint test test-coverage cloc-prod cloc-test clean
