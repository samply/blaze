install:
	npm clean-install --prefer-offline --fetch-retries=5

update:
	npm update

# The version and the date of the guide follow whatever is being rendered, which
# is only known in CI. The values committed to sushi-config.yaml are the fallback
# for local builds. The date isn't committed at all, so it's inserted after the
# version rather than replaced.
#
# IG_RELEASE says that this render is the one of a release. It is separate from
# IG_VERSION, because a render can carry a version without being a release — a
# development build is versioned too, and has to keep the committed `ci-build`
# label.
stamp-release:
ifdef IG_RELEASE
ifndef IG_VERSION
	$(error IG_RELEASE requires IG_VERSION, the version the release publishes)
endif
endif
ifdef IG_VERSION
	sed -i "s|^version: .*|version: $(IG_VERSION)|" sushi-config.yaml
endif
ifdef IG_DATE
	sed -i "/^date: /d; /^version: /a date: $(IG_DATE)" sushi-config.yaml
endif
ifdef IG_RELEASE
	sed -i "s|^releaseLabel: [^ ]*|releaseLabel: release|" sushi-config.yaml
endif

build: install stamp-release
	node_modules/.bin/sushi build -s .

# ImplementationGuide-blaze.json is the guide itself, not one of the conformance
# artifacts under test, so it's excluded here. It stays on disk because ig.ini
# points the IG Publisher at it.
validate:
	java -jar ../../validator_cli.jar -version 4.0.1 -level error -tx n/a -allow-example-urls -ig fsh-generated/resources $$(ls fsh-generated/resources/*.json | grep -v '/ImplementationGuide-blaze\.json$$')

IG_CANONICAL := $(shell sed -n 's|^canonical: ||p' sushi-config.yaml)

# The IG Publisher shells out to Jekyll, which is installed from Gemfile.lock
# into vendor/bundle and put on the PATH of the publisher as a binstub, so that
# no globally installed Jekyll is ever picked up.
bin/jekyll: Gemfile Gemfile.lock
	bundle config set --local path vendor/bundle
	bundle install
	bundle binstubs jekyll --path bin
	touch $@

# Render the guide into output/, including the FHIR package as output/package.tgz.
# Needs Ruby with Bundler, and the publisher itself, which
# .github/scripts/install-ig-publisher.sh provides. SUSHI is skipped because
# `build` already ran it with the pinned local install rather than the global one
# the publisher looks for.
#
# A release render is told where it will be published. Without that the package
# is marked notForPublication and records the local output directory as its
# location, which is what a local, pull request or development render is.
publish: build bin/jekyll
	PATH="$(CURDIR)/bin:$$PATH" java -jar ../../publisher.jar -ig . -tx n/a -no-sushi $(if $(IG_RELEASE),-publish $(IG_CANONICAL)/$(IG_VERSION))

# Build and validate the IG. Invoked by the root module fan-out (e.g. `make test`).
test: build validate

clean:
	rm -rf fsh-generated output temp template input-cache bin vendor .bundle

.PHONY: install update stamp-release build validate publish fmt lint test test-coverage cloc-prod cloc-test clean
