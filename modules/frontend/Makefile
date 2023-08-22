install:
	npm install

update:
	npm update

lint-js: install
	npm run lint

lint-clj:
	clj-kondo --lint clj deps.edn

lint: lint-js lint-clj

check: install
	npm run check

test-js: check
	npm run test:unit

build: test-js
	npm run build

test: build
	clojure -M:test:kaocha --profile :ci

test-coverage: build
	clojure -M:test:coverage

clean:
	rm -rf .clj-kondo/.cache .cpcache target build node_modules .svelte-kit

.PHONY: install update lint-js lint-clj lint check test-js build test test-coverage clean
