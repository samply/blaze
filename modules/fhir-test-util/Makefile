fmt:
	cljfmt check src deps.edn

lint:
	clj-kondo --lint src deps.edn

test:
	true

test-coverage:
	true

cloc-prod:

cloc-test:
	cloc src

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint test test-coverage cloc-prod cloc-test clean
