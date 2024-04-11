fmt:
	cljfmt check

lint:
	clj-kondo --lint src test deps.edn

test:
	clojure -M:test:kaocha --profile :ci

test-coverage:
	true

cloc-prod:
	cloc src

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint test test-coverage cloc-prod cloc-test clean
