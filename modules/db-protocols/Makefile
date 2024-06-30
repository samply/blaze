fmt:
	cljfmt check src deps.edn

lint:
	clj-kondo --lint src deps.edn

test:
	true

test-coverage:
	true

cloc-prod:
	cloc src

cloc-test:

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint test test-coverage cloc-prod cloc-test clean
