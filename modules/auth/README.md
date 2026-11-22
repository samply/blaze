# Module Auth

The Auth module provides the protocol `blaze.auth.protocols/AuthenticationBackend`, the middleware `blaze.middleware.authentication/wrap-authentication`, which authenticates requests with such a backend, and the function `blaze.auth/www-authenticate`, which builds the `WWW-Authenticate` header of 401 responses.
