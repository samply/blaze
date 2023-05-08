lint:
	clj-kondo --lint src test deps.edn

prep:
	clojure -X:deps prep

test: prep
	clojure -M:test:kaocha --profile :ci

test-coverage:
	true

register-test: prep
	clojure -M:register test --concurrency 16 --time-limit 60 -n localhost:8080 --delta-time 0.1

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: lint prep test test-coverage clean
