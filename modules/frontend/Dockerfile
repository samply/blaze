FROM node:23-alpine@sha256:3e58d4a77778684d17ab30ba8192515bd8078083df29a2d90fe7481f78f30093

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
