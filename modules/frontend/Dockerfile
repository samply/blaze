FROM node:21-alpine@sha256:db8772d9f5796ac4e8c47508038c413ea1478da010568a2e48672f19a8b80cd2

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
