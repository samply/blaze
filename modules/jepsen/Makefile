lint:
	clj-kondo --lint src test deps.edn

test:
	clojure -M:test --profile :ci

test-coverage:
	true

register-test:
	clojure -M:register test --concurrency 16 --time-limit 60 -n localhost:8080 --delta-time 0.1

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: lint test test-coverage clean
