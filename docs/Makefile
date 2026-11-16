install:
	npm clean-install --prefer-offline --fetch-retries=5

update:
	npm update

test: install
	npm test

build: test
	npm run docs:build

.PHONY: install update test build
