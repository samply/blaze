install:
	npm install

update:
	npm update

fmt: install
	npm run check

lint: install
	npm run lint

test: install
	npm run test:unit

build: test
	npm run build
	cat shutdown.js >> build/index.js

cloc-prod:
	cloc --exclude-ext=json src

clean:
	rm -rf build node_modules .svelte-kit

.PHONY: install update fmt lint test test-coverage build cloc-prod cloc-test clean
