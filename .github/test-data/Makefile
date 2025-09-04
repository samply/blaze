kds-testdata-2024.0.1.zip:
	wget -q https://github.com/medizininformatik-initiative/mii-testdata/releases/download/v1.0.1/kds-testdata-2024.0.1.zip

kds-testdata-2024.0.1: kds-testdata-2024.0.1.zip
	unzip kds-testdata-2024.0.1.zip

kds-testdata-2024.0.1/tx: kds-testdata-2024.0.1
	mkdir kds-testdata-2024.0.1/tx
	cp kds-testdata-2024.0.1/resources/Bundle-mii-exa-test-data-bundle.json kds-testdata-2024.0.1/tx

kds-testdata-2025.0.0.zip:
	wget -q https://github.com/medizininformatik-initiative/mii-testdata/releases/download/v2025.0.0-dev.7/kds-testdata-2025.0.0-20251021-124316.zip

kds-testdata-2025.0.0: kds-testdata-2025.0.0.zip
	unzip kds-testdata-2025.0.0-20251021-124316.zip

kds-testdata-2025.0.0/tx: kds-testdata-2025.0.0
	mkdir kds-testdata-2025.0.0/tx
	cp kds-testdata-2025.0.0-20251021-124316/resources/Bundle-mii-exa-test-data-bundle-pat-1.json kds-testdata-2025.0.0/tx

clean:
	rm -rf kds-testdata-2024.0.1
	rm kds-testdata-2024.0.1.zip
	rm -rf kds-testdata-2025.0.0
	rm kds-testdata-2025.0.0-20251021-124316.zip

.PHONY: clean
