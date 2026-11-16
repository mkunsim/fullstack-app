
FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

FROM node:22-alpine

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=5173

COPY package*.json ./
RUN npm install --omit=dev

COPY server.js sitemap.mjs ./
COPY --from=build /app/dist ./dist

EXPOSE 5173

CMD ["npm", "start"]
