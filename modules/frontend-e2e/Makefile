install:
	npm clean-install --prefer-offline --fetch-retries=5

update:
	npm update

install-playwright: install
	npx playwright install --with-deps
	$(MAKE) -C test-data install

lint: install
	npm run lint

test:
	npx playwright test

test-dev:
	DEV="1" npx playwright test

test-dev-chromium:
	DEV="1" npx playwright test --project chromium

test-dev-ui:
	DEV="1" npx playwright test --ui --project chromium

test-no-auth:
	npx playwright test --config playwright-no-auth.config.ts

test-dev-no-auth:
	DEV="1" npx playwright test --config playwright-no-auth.config.ts --project chromium

cloc-prod:

cloc-test:
	cloc src

clean:
	rm -rf node_modules
	$(MAKE) -C test-data clean

.PHONY: fmt install update install-playwright lint test test-dev test-dev-chromium test-dev-ui test-no-auth test-dev-no-auth cloc-prod cloc-test clean
