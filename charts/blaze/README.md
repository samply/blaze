# blaze

[Samply Blaze FHIR® Store](https://alexanderkiel.gitbook.io/blaze/) - A FHIR® server with internal, fast CQL Evaluation Engine.

## TL;DR;

```console
$ helm repo add samply https://samply.github.io/charts
$ helm repo update
$ helm install blaze-store samply/blaze -n blaze
```

## Introduction

This chart deploys Blaze on a [Kubernetes](http://kubernetes.io) cluster using the [Helm](https://helm.sh) package manager.

## Prerequisites

- Kubernetes v1.16+
- Helm v3

## Installing the Chart

To install the chart with the release name `blaze-store`:

```console
$ helm install blaze-store samply/blaze -n blaze
```

The command deploys Blaze on the Kubernetes cluster in the default configuration. The [configuration](#configuration) section lists the parameters that can be configured during installation.

> **Tip**: List all releases using `helm list`

## Uninstalling the Chart

To uninstall/delete the `blaze-store`:

```console
$ helm delete blaze-store -n blaze
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

## Configuration

The following table lists the configurable parameters of the `blaze` chart and their default values.

| Parameter                                   | Description                                                                                                                                  | Default                        |
| ------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------ |
| nameOverride                                |                                                                                                                                              | `""`                           |
| fullnameOverride                            |                                                                                                                                              | `""`                           |
| image.pullPolicy                            |                                                                                                                                              | `Always`                       |
| image.registry                              |                                                                                                                                              | `docker.io`                    |
| image.repository                            |                                                                                                                                              | `samply/blaze`                 |
| image.tag                                   |                                                                                                                                              | `0.9.0-alpha.179.27`           |
| jvmOpts                                     |                                                                                                                                              | `"-Xms2g -Xmx2g -XX:+UseG1GC"` |
| baseUrlOverride                             | the server base URL is set to the ingress host and path or, if not set, the service name. this value allows for manually overriding the URL. | `""`                           |
| readinessProbe.enabled                      |                                                                                                                                              | `true`                         |
| readinessProbe.initialDelaySeconds          |                                                                                                                                              | `30`                           |
| readinessProbe.periodSeconds                |                                                                                                                                              | `10`                           |
| readinessProbe.timeoutSeconds               |                                                                                                                                              | `5`                            |
| readinessProbe.failureThreshold             |                                                                                                                                              | `5`                            |
| readinessProbe.successThreshold             |                                                                                                                                              | `1`                            |
| metrics.enabled                             |                                                                                                                                              | `false`                        |
| metrics.service.type                        |                                                                                                                                              | `ClusterIP`                    |
| metrics.service.port                        |                                                                                                                                              | `8081`                         |
| metrics.serviceMonitor.enabled              |                                                                                                                                              | `false`                        |
| metrics.serviceMonitor.additionalLabels     |                                                                                                                                              | `{}`                           |
| metrics.serviceMonitor.interval             |                                                                                                                                              | `10s`                          |
| metrics.serviceMonitor.scrapeTimeout        |                                                                                                                                              | `10s`                          |
| extraEnv                                    | Add extra environment variables as name-value-tuples                                                                                         | `[]`                           |
| updateStrategy.type                         | updateStrategy for Blaze store StatefulSet ref: <https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#update-strategies>   | `RollingUpdate`                |
| persistence.enabled                         |                                                                                                                                              | `false`                        |
| persistence.mountPath                       | The path the volume will be mounted at                                                                                                       | `/app/data`                    |
| persistence.size                            | the requested size of the persistent volume claim                                                                                            | `32Gi`                         |
| persistence.annotations                     |                                                                                                                                              | `{}`                           |
| volumePermissions.enabled                   |                                                                                                                                              | `false`                        |
| volumePermissions.image.registry            |                                                                                                                                              | `docker.io`                    |
| volumePermissions.image.repository          |                                                                                                                                              | `busybox`                      |
| volumePermissions.image.tag                 |                                                                                                                                              | `1.32.0`                       |
| volumePermissions.image.pullPolicy          |                                                                                                                                              | `Always`                       |
| volumePermissions.securityContext.runAsUser |                                                                                                                                              | `0`                            |
| securityContext.enabled                     |                                                                                                                                              | `true`                         |
| securityContext.privileged                  |                                                                                                                                              | `false`                        |
| securityContext.fsGroup                     |                                                                                                                                              | `65532`                        |
| securityContext.runAsUser                   |                                                                                                                                              | `65532`                        |
| securityContext.runAsGroup                  |                                                                                                                                              | `65532`                        |
| securityContext.runAsNonRoot                |                                                                                                                                              | `true`                         |
| service.type                                |                                                                                                                                              | `ClusterIP`                    |
| service.port                                |                                                                                                                                              | `8080`                         |
| service.annotations                         |                                                                                                                                              | `{}`                           |
| ingress.enabled                             |                                                                                                                                              | `false`                        |
| ingress.annotations                         |                                                                                                                                              | `{}`                           |
| ingress.tls                                 |                                                                                                                                              | `[]`                           |
| resources                                   |                                                                                                                                              | `{}`                           |
| nodeSelector                                |                                                                                                                                              | `{}`                           |
| tolerations                                 |                                                                                                                                              | `[]`                           |
| affinity                                    |                                                                                                                                              | `{}`                           |
| podAnnotations                              |                                                                                                                                              | `{}`                           |

Specify each parameter using the `--set key=value[,key=value]` argument to `helm install`. For example:

```console
$ helm install blaze-store samply/blaze -n blaze --set image.pullPolicy=Always
```

Alternatively, a YAML file that specifies the values for the parameters can be provided while
installing the chart. For example:

```console
$ helm install blaze-store samply/blaze -n blaze --values values.yaml
```

### Production

When running in production, make sure to set `persistence.enabled=true`.
