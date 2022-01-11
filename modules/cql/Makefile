lint:
	clj-kondo --lint src test deps.edn

cql-test:
	wget https://cql.hl7.org/tests.zip
	unzip -jd cql-test tests.zip
	rm tests.zip
	# See: https://github.com/HL7/cql/pull/69
	sed -i.bak '277d' cql-test/CqlArithmeticFunctionsTest.xml

test: cql-test
	clojure -M:test --profile :ci

test-coverage: cql-test
	clojure -M:test-coverage

clean:
	rm -rf .clj-kondo/.cache .cpcache cql-test target

.PHONY: lint test test-coverage clean
