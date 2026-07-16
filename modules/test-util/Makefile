fmt:
	cljfmt check src test deps.edn tests.edn

lint:
	clj-kondo --lint src test deps.edn

test:
	clojure -M:test:kaocha --profile :ci

test-focus:
	clojure -M:test:kaocha --profile :ci --focus "$(FOCUS)"

test-coverage:
	clojure -M:test:kaocha:coverage

deps-list:
	clojure -X:deps list

cloc-prod:
	cloc src

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint test test-focus test-coverage deps-list cloc-prod cloc-test clean
