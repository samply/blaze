FROM node:23-alpine@sha256:15b885c5e195296ba49bab32681b478e92eb0f255078a84ad29020b87b0bdcc6

COPY build /app
COPY package*.json /app/

WORKDIR /app
USER 1001

CMD ["node", "."]
