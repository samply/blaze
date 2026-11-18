IG_DIR := ../ig
IG_CANONICAL := $(shell sed -n 's|^canonical: ||p' $(IG_DIR)/sushi-config.yaml)

install:
	npm clean-install --prefer-offline --fetch-retries=5

update:
	npm update

test:
	npm test

# Writes the index of the resources of the guide and the Worker routes covering
# them into target/. Needs the resources SUSHI generates and the rendering of the
# guide, which `make -C ../ig publish` produces.
index:
	node src/build-index-cli.ts $(IG_CANONICAL) $(IG_DIR)/fsh-generated/resources $(IG_DIR)/output target

# Deploys the Worker on the routes `index` wrote. Needs `install` and
# CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID. It doesn't depend on
# `install`, so the install scripts of the dependencies can run without the
# token in their environment.
deploy: target/routes.txt
	node_modules/.bin/wrangler deploy $(foreach route,$(file < $<),--route '$(route)')

target/routes.txt:
	$(error $@ is missing, run `make index` first)

clean:
	rm -rf node_modules target

.PHONY: install update test index deploy fmt lint test-coverage cloc-prod cloc-test clean
