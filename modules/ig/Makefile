install:
	npm clean-install --prefer-offline --fetch-retries=5

update:
	npm update

build: install
	node_modules/.bin/sushi build -s .
	rm fsh-generated/resources/ImplementationGuide-blaze.json

validate:
	java -jar ../../validator_cli.jar -version 4.0.1 -level error -tx n/a -allow-example-urls -ig fsh-generated/resources fsh-generated/resources/*.json

# Build and validate the IG. Invoked by the root module fan-out (e.g. `make test`).
test: build validate

clean:
	rm -rf fsh-generated

.PHONY: install update build validate fmt lint test test-coverage cloc-prod cloc-test clean
