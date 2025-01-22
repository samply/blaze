kds-testdata-2024.0.1.zip:
	wget -q https://github.com/medizininformatik-initiative/mii-testdata/releases/download/v1.0.1/kds-testdata-2024.0.1.zip

kds-test-data: kds-testdata-2024.0.1.zip
	unzip kds-testdata-2024.0.1.zip
	rm kds-testdata-2024.0.1.zip

clean:
	rm -rf kds-testdata-2024.0.1

.PHONY: kds-test-data clean
