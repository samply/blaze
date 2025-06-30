kds-testdata-2024.0.1.zip:
	wget -q https://github.com/medizininformatik-initiative/mii-testdata/releases/download/v1.0.1/kds-testdata-2024.0.1.zip

kds-testdata-2024.0.1: kds-testdata-2024.0.1.zip
	unzip kds-testdata-2024.0.1.zip

kds-testdata-2024.0.1/tx: kds-testdata-2024.0.1
	mkdir kds-testdata-2024.0.1/tx
	cp kds-testdata-2024.0.1/resources/Bundle-mii-exa-test-data-bundle.json kds-testdata-2024.0.1/tx

clean:
	rm -rf kds-testdata-2024.0.1
	rm kds-testdata-2024.0.1.zip

.PHONY: clean
