VERSION = "0.8.0-beta.1"

check:
	clojure -A:check

lint-anomaly:
	cd modules/anomaly; clojure -A:clj-kondo --lint src

lint-cql:
	cd modules/cql; clojure -A:clj-kondo --lint src test

lint-db:
	cd modules/db; clojure -A:clj-kondo --lint src test

lint-db-stub:
	cd modules/db-stub; clojure -A:clj-kondo --lint src

lint-executor:
	cd modules/executor; clojure -A:clj-kondo --lint src

lint-extern-terminology-service:
	cd modules/extern-terminology-service; clojure -A:clj-kondo --lint src

lint-fhir-client:
	cd modules/fhir-client; clojure -A:clj-kondo --lint src

lint-fhir-path:
	cd modules/fhir-path; clojure -A:clj-kondo --lint src test

lint-fhir-structure:
	cd modules/fhir-structure; clojure -A:clj-kondo --lint src

lint-interaction:
	cd modules/interaction; clojure -A:clj-kondo --lint src test

lint-module-base:
	cd modules/module-base; clojure -A:clj-kondo --lint src

lint-openid-auth:
	cd modules/openid-auth; clojure -A:clj-kondo --lint src test

lint-operations-measure-evaluate-measure:
	cd modules/operations/measure-evaluate-measure; clojure -A:clj-kondo --lint src test

lint-rest-api:
	cd modules/rest-api; clojure -A:clj-kondo --lint src test

lint-rest-util:
	cd modules/rest-util; clojure -A:clj-kondo --lint src test

lint-rocksdb:
	cd modules/rocksdb; clojure -A:clj-kondo --lint src

lint-spec:
	cd modules/spec; clojure -A:clj-kondo --lint src

lint-structure-definition:
	cd modules/structure-definition; clojure -A:clj-kondo --lint src

lint-terminology-service:
	cd modules/terminology-service; clojure -A:clj-kondo --lint src

lint-thread-pool-executor-collector:
	cd modules/thread-pool-executor-collector; clojure -A:clj-kondo --lint src

lint: lint-anomaly lint-cql lint-db lint-db-stub lint-executor lint-interaction lint-extern-terminology-service lint-fhir-client lint-fhir-path lint-fhir-structure lint-interaction lint-module-base lint-openid-auth lint-operations-measure-evaluate-measure lint-rest-api lint-rest-util lint-rocksdb lint-spec lint-structure-definition lint-terminology-service lint-thread-pool-executor-collector
	clojure -A:clj-kondo --lint src test

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

test-db:
	cd modules/db; clojure -A:test --profile :ci

test-fhir-path:
	cd modules/fhir-path; clojure -A:test --profile :ci

test-interaction:
	cd modules/interaction;	clojure -A:test --profile :ci

test-openid-auth:
	cd modules/openid-auth;	clojure -A:test --profile :ci

test-operations-measure-evaluate-measure:
	cd modules/operations/measure-evaluate-measure;	clojure -A:test --profile :ci

test-rest-api:
	cd modules/rest-api; clojure -A:test --profile :ci

test-rest-util:
	cd modules/rest-util; clojure -A:test --profile :ci

test: test-cql test-db test-fhir-path test-interaction test-openid-auth test-operations-measure-evaluate-measure test-rest-api test-rest-util
	clojure -A:test --profile :ci

uberjar:
	clojure -A:depstar -m hf.depstar.uberjar target/blaze-${VERSION}-standalone.jar


.PHONY: check test-cql test-interaction test-openid-auth test-operations-measure-evaluate-measure test-rest-api test-rest-util test uberjar
