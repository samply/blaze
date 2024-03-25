blaze-cert.pem:
	./gen-tls-cert.sh "blaze" "blaze.localhost"

keycloak-cert.pem:
	./gen-tls-cert.sh "keycloak" "keycloak.localhost"

keycloak-trust-store.p12: keycloak-cert.pem
	./gen-trust-store.sh "keycloak"

all: blaze-cert.pem keycloak-trust-store.p12

clean:
	rm -f *.pem
	rm -f *.p12

.PHONY: fmt lint test-coverage all cloc-prod cloc-test clean
