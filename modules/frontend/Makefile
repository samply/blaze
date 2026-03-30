install:
	npm clean-install --prefer-offline --fetch-retries=5

update:
	npm update

fmt: install
	npm run check

lint: install
	npm run lint

build-fhir-structure:
	$(MAKE) -C ../fhir-structure prep

test: install build-fhir-structure
	npm run test:unit

build: test
	npm run build

cloc-prod:
	cloc --exclude-ext=json src

clean:
	rm -rf build node_modules .svelte-kit

.PHONY: install update fmt lint test build cloc-prod clean
