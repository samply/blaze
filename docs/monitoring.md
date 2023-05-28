# Monitoring

It's recommended to use [Prometheus][1] and [Grafana][2] to monitor the runtime behaviour of Blaze and of the server Blaze runs on.

![](monitoring/prometheus.png)

## Import the Blaze Dashboard

In order to import the Blaze dashboard into your Grafana instance, please copy the contents of [blaze.json](monitoring/blaze.json) and pate it into the import dialog on the Import dashboard site:

![](monitoring/import-dashboard-1.png)

After that, please click "Import" on the next site:

![](monitoring/import-dashboard-2.png)

After the Import, the Blaze dashboard should look like this:

![](monitoring/dashboard.png)

## Node Exporter for the Server

The Prometheus [Node Exporter](https://github.com/prometheus/node_exporter) should be used to gather metrics about the server Blaze is hosted on.

### Dashboards

* [Node Exporter Full](https://grafana.com/grafana/dashboards/1860-node-exporter-full/)

[1]: <https://prometheus.io>
[2]: <https://grafana.com>
