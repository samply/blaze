fmt:
	cljfmt check src test deps.edn tests.edn

lint:
	clj-kondo --lint src test deps.edn

prep:
	$(MAKE) -C ../module-base prep
	clojure -X:deps prep :aliases '[:test]'

test: prep
	clojure -J--enable-native-access=ALL-UNNAMED -J--sun-misc-unsafe-memory-access=allow -M:test:kaocha --profile :ci

test-focus: prep
	clojure -J--enable-native-access=ALL-UNNAMED -J--sun-misc-unsafe-memory-access=allow -M:test:kaocha --profile :ci --focus "$(FOCUS)"

test-coverage: prep
	clojure -J--enable-native-access=ALL-UNNAMED -J--sun-misc-unsafe-memory-access=allow -M:test:kaocha:coverage

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
