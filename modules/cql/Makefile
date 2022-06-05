lint:
	clj-kondo --lint src test deps.edn

cql-test:
	wget http://cql.hl7.org/tests.zip
	echo "0d48a7441c43b6ee46e71d73decfa0cf4ea81e2ce70951f20e9163c3bebfc49a  tests.zip" | sha256sum --check --status
	unzip -jd cql-test tests.zip
	rm tests.zip
	# See: https://github.com/HL7/cql/pull/69
	sed -i.bak '277d' cql-test/CqlArithmeticFunctionsTest.xml

test: cql-test
	clojure -M:test:kaocha --profile :ci

test-coverage: cql-test
	clojure -M:test:coverage

clean:
	rm -rf .clj-kondo/.cache .cpcache cql-test target

.PHONY: lint test test-coverage clean
