FROM m.daocloud.io/docker.io/library/node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json .npmrc ./
RUN npm ci
COPY . .
ARG VITE_AMAP_KEY
ARG VITE_AMAP_SERVICE_HOST=/_AMapService
ENV VITE_AMAP_KEY=${VITE_AMAP_KEY}
ENV VITE_AMAP_SERVICE_HOST=${VITE_AMAP_SERVICE_HOST}
RUN node -e "if (!process.env.VITE_AMAP_KEY?.trim()) throw new Error('VITE_AMAP_KEY 未配置，拒绝生成空地图镜像')"
RUN npm test && npm run build

FROM m.daocloud.io/docker.io/library/nginx:1.27-alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=6 CMD wget -q --spider http://127.0.0.1/ || exit 1
