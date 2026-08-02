fmt:
	cljfmt check src test deps.edn tests.edn

lint:
	clj-kondo --lint src test deps.edn

prep:
	n=0; \
	until clojure -X:deps prep; do \
		n=$$((n + 1)); \
		if [ $$n -ge 3 ]; then \
			echo "clojure -X:deps prep failed after 3 attempts" >&2; \
			exit 1; \
		fi; \
		echo "clojure -X:deps prep failed (attempt $$n/3), retrying in $$((n * 5))s..." >&2; \
		sleep $$((n * 5)); \
	done

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

.PHONY: fmt lint prep test test-focus test-coverage deps-tree deps-list cloc-prod cloc-test clean
