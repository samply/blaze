lint:
	clj-kondo --lint src test deps.edn

cql-test:
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlAggregateFunctionsTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlArithmeticFunctionsTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlComparisonOperatorsTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlConditionalOperatorsTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlDateTimeOperatorsTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlErrorsAndMessagingOperatorsTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlIntervalOperatorsTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlListOperatorsTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlLogicalOperatorsTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlNullologicalOperatorsTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlStringOperatorsTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlTypeOperatorsTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlTypesTest.xml
	wget -P cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/ValueLiteralsAndSelectors.xml

test: cql-test
	clojure -M:test --profile :ci

test-coverage:
	clojure -M:test-coverage

clean:
	rm -r .clj-kondo/.cache .cpcache cql-test target

.PHONY: lint test test-coverage clean
