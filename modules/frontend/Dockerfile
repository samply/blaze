FROM node:21-alpine@sha256:3c27c3c7a43ecd9f970a435a187ecbddaaa558a4ea741b0edb8e9107aff0e86d

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
