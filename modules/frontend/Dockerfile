FROM node:21-alpine@sha256:c986eb0b8970240f8d648e524bab46016b78f290f912aac16a4aa6705dde05f4

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
