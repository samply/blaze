install:
	npm ci
	npx playwright install --with-deps

test:
	npx playwright test

test-dev:
	DEV="1" npx playwright test

test-ui-dev:
	DEV="1" npx playwright test --ui --project chromium

cloc-prod:

cloc-test:
	cloc src

.PHONY: fmt lint install test test-ui-dev test-coverage cloc-prod cloc-test clean
