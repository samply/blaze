fmt:
	cljfmt check src test build.clj deps.edn tests.edn dashboard.edn

lint:
	clj-kondo --lint src test build.clj deps.edn

prep:
	clojure -X:deps prep

build: prep
	clojure -T:build gen

lint-dashboard: build
	dashboard-linter lint -c .lint --strict target/blaze-dashboard.json

test: prep
	clojure -M:test:kaocha --profile :ci

test-focus: prep
	clojure -M:test:kaocha --profile :ci --focus "$(FOCUS)"

test-coverage: prep
	clojure -M:test:kaocha:coverage

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

.PHONY: fmt lint prep build lint-dashboard test test-focus test-coverage deps-tree deps-list cloc-prod cloc-test clean
