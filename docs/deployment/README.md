# Deployment

## Production

For production ready deployments, there are three options:

* [Full Standalone](full-standalone.md)
* [Standalone Backend Only](standalone-backend.md)
* [Distributed Backend Only](distributed-backend.md)

## Configuration

* [Environment Variables](environment-variables.md)

## Development

Blaze can be started with a single command using docker:

```sh
docker run -d --name blaze -p 8080:8080 samply/blaze:latest
```
