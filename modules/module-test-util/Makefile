fmt:
	cljfmt --file-pattern "\.(clj|edn)?$$" check resources src deps.edn

lint:
	clj-kondo --lint src deps.edn

test:
	true

test-coverage:
	true

deps-tree:
	clojure -X:deps tree

deps-list:
	clojure -X:deps list

cloc-test:
	cloc src

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint test test-coverage deps-tree deps-list cloc-prod cloc-test clean
