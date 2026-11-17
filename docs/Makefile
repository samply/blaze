install:
	npm clean-install --prefer-offline --fetch-retries=5

update:
	npm update

test: install
	npm test

build: test
	npm run docs:build

# Adds the rendering of the Implementation Guide, which `make -C modules/ig
# publish` produces, to the built site under /fhir. It's copied after the
# VitePress build rather than placed in public/, so that VitePress doesn't
# process the hundreds of generated HTML files.
add-ig:
	rm -rf .vitepress/dist/fhir
	cp -r ../modules/ig/output .vitepress/dist/fhir
	../.github/scripts/check-ig-links.sh .vitepress/dist

.PHONY: install update test build add-ig
