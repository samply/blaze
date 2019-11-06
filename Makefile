VERSION = "0.7.0-alpha.9"

check:
	clojure -A:check

lint:
	clojure -A:clj-kondo --lint modules
	clojure -A:clj-kondo --lint src

modules/cql/cql-test:
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlAggregateFunctionsTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlArithmeticFunctionsTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlComparisonOperatorsTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlConditionalOperatorsTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlDateTimeOperatorsTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlErrorsAndMessagingOperatorsTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlIntervalOperatorsTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlListOperatorsTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlLogicalOperatorsTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlNullologicalOperatorsTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlStringOperatorsTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlTypeOperatorsTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/CqlTypesTest.xml
	wget -P modules/cql/cql-test -q https://raw.githubusercontent.com/HL7/cql/v1.4-ballot/tests/cql/ValueLiteralsAndSelectors.xml

test-cql: modules/cql/cql-test
	cd modules/cql;	clojure -A:test --profile :ci

test-datomic:
	cd modules/datomic;	clojure -A:test --profile :ci

test-interaction:
	cd modules/interaction;	clojure -A:test --profile :ci

test-openid-auth:
	cd modules/openid-auth;	clojure -A:test --profile :ci

test-operations-measure-evaluate-measure:
	cd modules/operations/measure-evaluate-measure;	clojure -A:test --profile :ci

test-rest-api:
	cd modules/rest-api;	clojure -A:test --profile :ci

test-rest-util:
	cd modules/rest-util;	clojure -A:test --profile :ci

test: test-cql test-datomic test-interaction test-openid-auth test-operations-measure-evaluate-measure test-rest-api test-rest-util
	clojure -A:test --profile :ci

uberjar:
	clojure -A:depstar -m hf.depstar.uberjar target/blaze-${VERSION}-standalone.jar


.PHONY: check test-cql test-datomic test-interaction test-openid-auth test-operations-measure-evaluate-measure test-rest-api test-rest-util test uberjar
