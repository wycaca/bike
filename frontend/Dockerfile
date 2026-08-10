FROM m.daocloud.io/docker.io/library/node:22.19.0-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json .npmrc ./
RUN npm ci
COPY . .
RUN npm run build

FROM m.daocloud.io/docker.io/library/nginx:1.28.0-alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80

