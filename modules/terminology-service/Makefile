fmt:
	cljfmt check

lint:
	clj-kondo --lint src deps.edn

test:
	true

test-coverage:
	true

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint test test-coverage clean
