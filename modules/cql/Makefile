fmt:
	cljfmt check src test test-perf deps.edn tests.edn

lint:
	clj-kondo --fail-level error --lint src test deps.edn

prep:
	clojure -X:deps prep

cql-test:
    # Please remove --no-check-certificate if the certificate is ok gain.
    # We can do that because the check the SHA256 of the ZIP file.
	wget --no-check-certificate https://cql.hl7.org/tests.zip
	echo "0d48a7441c43b6ee46e71d73decfa0cf4ea81e2ce70951f20e9163c3bebfc49a  tests.zip" | sha256sum --check --status
	unzip -jd cql-test tests.zip
	rm tests.zip
	# See: https://github.com/HL7/cql/pull/69
	sed -i.bak '277d' cql-test/CqlArithmeticFunctionsTest.xml

test: cql-test prep
	clojure -M:test:kaocha --profile :ci

test-coverage: cql-test prep
	clojure -M:test:coverage

deps-tree:
	clojure -X:deps tree

deps-list:
	clojure -X:deps list

cloc-prod:
	cloc src

cloc-test:
	cloc test

clean:
	rm -rf .clj-kondo/.cache .cpcache cql-test target

.PHONY: fmt lint prep test test-coverage deps-tree deps-list cloc-prod cloc-test clean
