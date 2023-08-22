lint:
	clj-kondo --lint src deps.edn

prep:
	clojure -X:deps prep

test:
	true

test-coverage:
	true

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: lint prep test test-coverage clean
