install:
	npm install
	npx playwright install --with-deps

test:
	npx playwright test

test-dev:
	DEV="1" npx playwright test

test-dev-chromium:
	DEV="1" npx playwright test --project chromium

test-dev-ui:
	DEV="1" npx playwright test --ui --project chromium

cloc-prod:

cloc-test:
	cloc src

.PHONY: fmt lint install test test-dev-chromium test-dev-ui test-coverage cloc-prod cloc-test clean
