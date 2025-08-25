install:
	npm install

build: install
	node_modules/.bin/sushi build -s .

validator_cli.jar:
	wget -q https://github.com/hapifhir/org.hl7.fhir.core/releases/latest/download/validator_cli.jar

validate: validator_cli.jar
	java -jar validator_cli.jar -version 5.0.0 -level error -tx n/a -ig fsh-generated/resources fsh-generated/resources/Task-*.json fsh-generated/resources/Bundle-*.json

clean:
	rm -rf fsh-generated
	rm -f validator_cli.jar

.PHONY: install build validate clean
