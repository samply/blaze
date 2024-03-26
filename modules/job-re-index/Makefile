fmt:
	cljfmt check

lint:
	clj-kondo --lint src test deps.edn

test:
	clojure -M:test:kaocha --profile :ci

test-coverage:
	clojure -M:test:coverage

validator_cli.jar:
	wget https://github.com/hapifhir/org.hl7.fhir.validator-wrapper/releases/latest/download/validator_cli.jar

clean:
	rm -rf .clj-kondo/.cache .cpcache target

.PHONY: fmt lint test test-coverage clean
