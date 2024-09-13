fmt:
	cljfmt check src test deps.edn tests.edn

lint:
	clj-kondo --lint src test deps.edn

prep:
	clojure -X:deps prep

test: prep
	clojure -M:test:kaocha --profile :ci

test-coverage:
	true

deps-tree:
	clojure -X:deps tree

deps-list:
	clojure -X:deps list

register-test-fast: prep
	clojure -M:register test --concurrency 16 --time-limit 60 -n localhost:8080 --delta-time 0.01

register-test-slow: prep
	clojure -M:register test --concurrency 16 --time-limit 60 -n localhost:8080 --delta-time 0.1

resource-history-test-fast: prep
	clojure -J-Xmx4g -M:resource-history test --concurrency 16 --time-limit 60 -n localhost:8080 --delta-time 0.02

resource-history-test-slow: prep
	clojure -J-Xmx4g -M:resource-history test --concurrency 16 --time-limit 60 -n localhost:8080 --delta-time 0.1

cloc-prod:
	cloc src

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache target store

.PHONY: fmt lint prep test test-coverage deps-tree deps-list cloc-prod cloc-test clean
