fmt:
	cljfmt check build.clj

lint:
	clj-kondo --lint build.clj deps.edn

build:
	clojure -T:build copy-profiles

prep: build
	clojure -X:deps prep

test: prep
	true

test-coverage: prep
	true

cloc-prod:

cloc-test:

clean:
	rm -rf .clj-kondo/.cache .cpcache target resources

.PHONY: fmt lint build prep test test-coverage cloc-prod cloc-test clean
