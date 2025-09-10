fmt:
	cljfmt --file-pattern "\.(cljs|edn)?$$" check resources src deps.edn

lint:
	clj-kondo --lint src deps.edn

prep:
	clojure -X:deps prep

test:
	true

test-coverage:
	true

cloc-prod:

cloc-test:
	cloc src

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint prep test test-coverage cloc-prod cloc-test clean
