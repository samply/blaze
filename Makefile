MODULES := $(wildcard modules/*)

$(MODULES):
	$(MAKE) -C $@ $(MAKECMDGOALS)

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

uberjar: prep
	clojure -T:build uber

outdated:
	clojure -M:outdated

deps-tree:
	clojure -X:deps tree

deps-list:
	clojure -X:deps list

.PHONY: $(MODULES) lint-root lint prep test-root test test-coverage clean-root clean uberjar outdated deps-tree deps-list
