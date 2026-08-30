export const environment = {
  production: false,
  // Requests go through the ng serve proxy (proxy.conf.json) to stay same-origin with the
  // portal-server at localhost:8080 — required for both the session cookie and Angular's default
  // XSRF interceptor (which only attaches X-XSRF-TOKEN on same-origin requests).
  apiBaseUrl: '',
};
