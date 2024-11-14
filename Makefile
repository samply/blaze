MODULES := $(wildcard modules/*)

$(MODULES):
	$(MAKE) -C $@ $(MAKECMDGOALS)

fmt-root:
	cljfmt check dev profiling resources src test deps.edn tests.edn

fmt: $(MODULES) fmt-root

lint-root:
	clj-kondo --lint src test deps.edn

lint: $(MODULES) lint-root

prep:
	clojure -X:deps prep

test-root: prep
	clojure -M:test:kaocha --profile :ci

test: $(MODULES) test-root

test-coverage: $(MODULES)

clean-root:
	rm -rf .clj-kondo/.cache .cpcache target

clean: $(MODULES) clean-root
	$(MAKE) -C job-ig clean

build-frontend:
	$(MAKE) -C modules/frontend build

build-frontend-image: build-frontend
	docker build -t blaze-frontend:latest modules/frontend

build-ingress:
	$(MAKE) -C modules/ingress all

uberjar: prep
	clojure -T:build uber

build-image: uberjar
	docker build -t blaze:latest .

build-all: build-image build-frontend-image build-ingress

outdated:
	clojure -M:outdated

deps-tree:
	clojure -X:deps tree

deps-list:
	clojure -X:deps list

emacs-repl: prep
	clj -M:test:emacs-repl

cloc-prod-root:
	cloc src

cloc-prod: $(MODULES) cloc-prod-root

cloc-test-root:
	cloc dev profiling test .github

cloc-test: $(MODULES) cloc-test-root

.PHONY: $(MODULES) lint-root lint prep test-root test test-coverage clean-root \
	clean build-frontend build-frontend-image build-ingress uberjar build-all \
	outdated deps-tree deps-list emacs-repl cloc-prod cloc-test
