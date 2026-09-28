<script setup lang="ts">
  const release = import.meta.env.VITE_LATEST_RELEASE;
  const dashboardUrl = `https://github.com/samply/blaze/releases/download/${release}/blaze-dashboard.json`;
  const provenanceUrl = `${dashboardUrl}.intoto.jsonl`;
</script>

# Monitoring

It's recommended to use [Prometheus][1] and [Grafana][2] to monitor the runtime behaviour of Blaze and of the server Blaze runs on.

![](monitoring/prometheus.png)

## Prometheus Config

A basic Prometheus config looks like this:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
- job_name: 'node'
  static_configs:
  - targets: ['<server-ip-addr>:9100']
  - labels:
      instance: 'blaze'

- job_name: 'blaze'
  static_configs:
  - targets: ['<server-ip-addr>:8081']
  - labels:
      instance: 'blaze'
```

## Import the Blaze Dashboard

The Blaze dashboard is published as a release asset. Please download <a :href="dashboardUrl">blaze-dashboard.json</a> and upload it in the import dialog on the Import dashboard site:

![](monitoring/import-dashboard-1.png)

After that, please click "Import" on the next site:

![](monitoring/import-dashboard-2.png)

After the Import, the Blaze dashboard should look like this:

![](monitoring/dashboard.png)

The dashboard is generated from [modules/monitoring/dashboard.edn][3]. Maintainers change that file and not the exported JSON. The generated JSON is checked in CI with the [Grafana dashboard linter][4], configured in [modules/monitoring/.lint][5].

### Verification <Badge type="warning" text="Since 1.12"/>

Each release contains [SLSA build provenance][6] for the dashboard. It allows users to confirm that the dashboard was built by the expected CI pipeline and has not been modified after publication. Please download <a :href="provenanceUrl">blaze-dashboard.json.intoto.jsonl</a> next to the dashboard and verify it with the [GitHub CLI][7] before importing:

```sh-vue
gh attestation verify blaze-dashboard.json \
  --bundle blaze-dashboard.json.intoto.jsonl \
  --repo samply/blaze \
  --source-ref "refs/tags/{{ release }}"
```

## Node Exporter for the Server

The Prometheus [Node Exporter](https://github.com/prometheus/node_exporter) should be used to gather metrics about the server Blaze is hosted on.

### Dashboards

* [Node Exporter Full](https://grafana.com/grafana/dashboards/1860-node-exporter-full/)

[1]: <https://prometheus.io>
[2]: <https://grafana.com>
[3]: <https://github.com/samply/blaze/blob/main/modules/monitoring/dashboard.edn>
[4]: <https://github.com/grafana/dashboard-linter>
[5]: <https://github.com/samply/blaze/blob/main/modules/monitoring/.lint>
[6]: <https://slsa.dev/spec/v1.0/provenance>
[7]: <https://cli.github.com/manual/gh_attestation_verify>
