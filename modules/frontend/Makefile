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

prep: build
	clojure -X:deps prep

test: prep
	clojure -M:test:kaocha --profile :ci

test-coverage: prep
	clojure -M:test:coverage

deps-tree:
	clojure -X:deps tree

deps-list:
	clojure -X:deps list

clean:
	rm -rf .clj-kondo/.cache .cpcache target build node_modules .svelte-kit

.PHONY: install update lint-js lint-clj lint check test-js build prep test test-coverage deps-tree deps-list clean
