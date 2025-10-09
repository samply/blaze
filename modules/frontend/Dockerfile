FROM node:22-alpine@sha256:dbcedd8aeab47fbc0f4dd4bffa55b7c3c729a707875968d467aaaea42d6225af

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
