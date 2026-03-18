FROM node:22-alpine AS frontend-builder

WORKDIR /app

COPY frontend/frontend/package.json frontend/frontend/package-lock.json ./
RUN npm ci

COPY frontend/frontend/ ./
RUN npm run build

FROM nginx:1.27-alpine

COPY docker/nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=frontend-builder /app/dist /usr/share/nginx/html

EXPOSE 5173
