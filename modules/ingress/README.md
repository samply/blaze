# Ingress

Example nginx ingress configuration and certificate creation.

Information about deploying Blaze can be found [here](../../docs/deployment/README.md). 

Blaze doesn't come with it's own TLS termination and build in frontend. So an ingress component is essential for deploying Blaze in production. The following nginx configuration is an example but contains some important aspects:

## Basic TLS Config

```text
# TLS certificates and config
ssl_certificate /etc/nginx/conf.d/blaze-cert.pem;
ssl_certificate_key /etc/nginx/conf.d/blaze-key.pem;
ssl_protocols TLSv1.2 TLSv1.3;
ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:DHE-RSA-AES128-GCM-SHA256:DHE-RSA-AES256-GCM-SHA384:DHE-RSA-CHACHA20-POLY1305;
ssl_prefer_server_ciphers off;
```

> [!HINT]
> Please consider to constrain `ssl_ciphers` depending on the browsers and clients you use. To do so https://ssl-config.mozilla.org/ can be used.

## Compression Config

For optimal performance, responses should be compressed. The compression is done by nginx rather than Blaze itself.

```text
gzip  on;
gzip_types text/javascript text/css application/json application/fhir+json;
```

## Reverse Proxy Config

Special reverse proxy config is needed because the frontend uses quite large cookies to store the session information. Also request buffering is turned off i order to not buffer large FHIR uploads.

```text
proxy_http_version      1.1;
proxy_buffer_size       32k;
proxy_buffers           8 32k;
proxy_request_buffering off;
```

## Security Related Headers

The following headers are added to enhance security: 

```text
add_header X-Content-Type-Options nosniff;
add_header X-Frame-Options SAMEORIGIN;
```

> [!HINT]
> Please consider adding the `Strict-Transport-Security` header in production.

## Reverse Proxy Forward

Requests to the `/fhir` context path are forwarded to either the frontend or the backend of Blaze. The `Authorization` header is used to differentiate between the two services. The reason for this differentiation is, that API requests that use an `Authorization` header should be forwarded to the backend directly, where frontend requests use a session cookie for authorization and so need to go to the frontend. A nginx `map` definition is used to implement that. 

```text
map $http_authorization $upstream {
    default http://frontend:3000;

    "~Bearer" http://backend:8080;
}

server {
    location /fhir {
        proxy_pass $upstream;
        proxy_set_header x-forwarded-proto https;
        proxy_set_header x-forwarded-host ${BLAZE_HOST};
    }
}
```
