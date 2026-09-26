IG_PUBLISHER_VERSION := 2.3.2
IG_PUBLISHER_CHECKSUM := 07c576024df917cc1f879b6b5a64147cd0222d5b4129688e8f0ad9ccce58b1d5

install:
	npm clean-install --prefer-offline --fetch-retries=5

update:
	npm update

# The IG version and date follow the Blaze release the guide is published with,
# which is only known in CI. The values committed to sushi-config.yaml are the
# fallback for local builds.
stamp-release:
	@if [ -n "$(IG_VERSION)" ]; then \
	  sed -i "s|^version: .*|version: $(IG_VERSION)|" sushi-config.yaml; \
	  echo "Stamped IG version $(IG_VERSION)"; \
	fi
	@if [ -n "$(IG_DATE)" ]; then \
	  sed -i "s|^date: .*|date: $(IG_DATE)|" sushi-config.yaml; \
	  echo "Stamped IG date $(IG_DATE)"; \
	fi

build: install stamp-release
	node_modules/.bin/sushi build -s .

# ImplementationGuide-blaze.json is the IG resource itself, not one of the
# conformance artifacts under test. It is excluded here but kept on disk because
# ig.ini points the IG Publisher at it.
validate:
	java -jar ../../validator_cli.jar -version 4.0.1 -level error -tx n/a -allow-example-urls -ig fsh-generated/resources $$(ls fsh-generated/resources/*.json | grep -v '/ImplementationGuide-blaze\.json$$')

publisher.jar:
	curl -sLo publisher.jar "https://github.com/HL7/fhir-ig-publisher/releases/download/$(IG_PUBLISHER_VERSION)/publisher.jar"
	echo "$(IG_PUBLISHER_CHECKSUM) publisher.jar" | sha256sum -c

# Print the digest identifying the current set of conformance artifacts. The
# release workflow publishes a new version of the guide only when it changes.
artifact-hash: build
	@./scripts/artifact-hash.sh fsh-generated/resources

# Render the IG into output/. Needs Jekyll, which the IG Publisher shells out to.
# SUSHI already ran as part of `build`, using the pinned local install rather
# than the global one the publisher would look for.
publish: publisher.jar build
	java -jar publisher.jar -ig . -tx n/a -no-sushi
	./scripts/generate-canonical-redirects.sh output
	./scripts/artifact-hash.sh fsh-generated/resources > output/artifact-hash.txt

# Build and validate the IG. Invoked by the root module fan-out (e.g. `make test`).
test: build validate

clean:
	rm -rf fsh-generated output temp template input-cache

.PHONY: install update stamp-release build validate artifact-hash publish fmt lint test test-coverage cloc-prod cloc-test clean
