FROM node:22-alpine@sha256:9db789c423efafd5e68fe6db7c10b45e6b3c4a5a8456e640d2e7da6581c701d3

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
