# ============================================
# STAGE 1: Build the React application
# ============================================

FROM node:20-alpine AS build

WORKDIR /app

# Copy package files first (caches node_modules layer)
COPY package.json package-lock.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Build the production bundle (creates /app/dist folder)
RUN npm run build

# ============================================
# STAGE 2: Serve with Nginx
# ============================================
# Nginx is a lightweight web server — perfect for serving static files.
# In production, you don't run "npm run dev" — you build once and serve the static files.

FROM nginx:alpine

# Copy our custom nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the built React app from Stage 1
COPY --from=build /app/dist /usr/share/nginx/html

# Expose port 80
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
