/**
 * Portal SSO Client SDK
 * Lightweight, zero-dependency OAuth 2.1 & OIDC client with seamless popup login support.
 * Compatible with React, Angular, Vue, Svelte, Next.js, JSP, JSF, and vanilla JavaScript.
 */
(function (global, factory) {
  if (typeof exports === 'object' && typeof module !== 'undefined') {
    module.exports = factory();
  } else if (typeof define === 'function' && define.amd) {
    define(factory);
  } else {
    global.PortalSSO = factory();
  }
})(typeof globalThis !== 'undefined' ? globalThis : typeof window !== 'undefined' ? window : this, function () {
  'use strict';

  function base64UrlEncode(buffer) {
    var bytes = new Uint8Array(buffer);
    var binary = '';
    for (var i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary)
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
  }

  function generateRandomString(length) {
    var charset = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
    var values = new Uint8Array(length);
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
      crypto.getRandomValues(values);
    } else {
      for (var i = 0; i < length; i++) {
        values[i] = Math.floor(Math.random() * 256);
      }
    }
    var result = '';
    for (var j = 0; j < length; j++) {
      result += charset[values[j] % charset.length];
    }
    return result;
  }

  async function sha256(plain) {
    var encoder = new TextEncoder();
    var data = encoder.encode(plain);
    var hash = await crypto.subtle.digest('SHA-256', data);
    return base64UrlEncode(hash);
  }

  function parseJwt(token) {
    try {
      var base64Url = token.split('.')[1];
      var base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      var jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map(function (c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
          })
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch (e) {
      return null;
    }
  }

  function PortalSSO(options) {
    if (!options || !options.clientId) {
      throw new Error('[PortalSSO] clientId is required');
    }
    this.portalUrl = (options.portalUrl || window.location.origin).replace(/\/$/, '');
    this.clientId = options.clientId;
    this.redirectUri = options.redirectUri || (this.portalUrl + '/oauth2/popup-callback');
    this.scope = options.scope || 'openid profile email';
    this.storageKey = options.storageKey || 'portal_sso_session';
  }

  /**
   * Logs the user in via a popup window.
   * Resolves with { user, accessToken, idToken, expiresIn }.
   */
  PortalSSO.prototype.loginWithPopup = async function (popupOptions) {
    var self = this;
    var width = (popupOptions && popupOptions.width) || 500;
    var height = (popupOptions && popupOptions.height) || 680;
    var left = window.screenX + (window.outerWidth - width) / 2;
    var top = window.screenY + (window.outerHeight - height) / 2;

    var codeVerifier = generateRandomString(64);
    var codeChallenge = await sha256(codeVerifier);
    var state = generateRandomString(32);

    var authUrl =
      self.portalUrl +
      '/oauth2/authorize?' +
      'response_type=code' +
      '&client_id=' + encodeURIComponent(self.clientId) +
      '&redirect_uri=' + encodeURIComponent(self.redirectUri) +
      '&scope=' + encodeURIComponent(self.scope) +
      '&code_challenge=' + encodeURIComponent(codeChallenge) +
      '&code_challenge_method=S256' +
      '&state=' + encodeURIComponent(state);

    var popup = window.open(
      authUrl,
      'portal_sso_popup_' + Date.now(),
      'width=' + width + ',height=' + height + ',left=' + left + ',top=' + top + ',menubar=no,toolbar=no,status=no'
    );

    if (!popup) {
      throw new Error('[PortalSSO] Popup blocked. Please allow popups for this site.');
    }

    return new Promise(function (resolve, reject) {
      var checkInterval = null;

      function cleanup() {
        if (checkInterval) clearInterval(checkInterval);
        window.removeEventListener('message', messageHandler);
      }

      function messageHandler(event) {
        // Only the Portal SSO popup may answer; anything else could otherwise abort the sign-in.
        if (event.origin !== new URL(self.redirectUri, window.location.href).origin ||!event.data || event.data.type !== 'PORTAL_SSO_POPUP_CALLBACK') {
          return;
        }

        var data = event.data;
        if (data.state !== state) {
          cleanup();
          reject(new Error('[PortalSSO] State mismatch error. Possible CSRF attack.'));
          return;
        }

        if (data.error) {
          cleanup();
          reject(new Error('[PortalSSO] ' + (data.errorDescription || data.error)));
          return;
        }

        if (data.code) {
          cleanup();
          self.exchangeCodeForTokens(data.code, codeVerifier, self.redirectUri)
            .then(function (session) {
              resolve(session);
            })
            .catch(function (err) {
              reject(err);
            });
        }
      }

      window.addEventListener('message', messageHandler);

      checkInterval = setInterval(function () {
        if (popup.closed) {
          cleanup();
          reject(new Error('[PortalSSO] Sign-in window closed by user'));
        }
      }, 800);
    });
  };

  /**
   * Exchanges an authorization code for access and ID tokens via PKCE.
   */
  PortalSSO.prototype.exchangeCodeForTokens = async function (code, codeVerifier, redirectUri) {
    var self = this;
    var tokenEndpoint = self.portalUrl + '/oauth2/token';

    var body = new URLSearchParams();
    body.append('grant_type', 'authorization_code');
    body.append('client_id', self.clientId);
    body.append('code', code);
    body.append('code_verifier', codeVerifier);
    body.append('redirect_uri', redirectUri || self.redirectUri);

    var res = await fetch(tokenEndpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: body.toString(),
    });

    if (!res.ok) {
      var errData = await res.json().catch(function () { return {}; });
      throw new Error(errData.error_description || errData.error || 'Failed to exchange authorization code for tokens');
    }

    var tokenData = await res.json();
    var idClaims = tokenData.id_token ? parseJwt(tokenData.id_token) : {};
    var accessClaims = tokenData.access_token ? parseJwt(tokenData.access_token) : {};

    var user = {
      id: idClaims.sub || accessClaims.sub || '',
      email: idClaims.email || accessClaims.email || '',
      name: idClaims.name || idClaims.preferred_username || '',
      roles: idClaims.roles || accessClaims.roles || [],
      rawIdTokenClaims: idClaims,
    };

    var session = {
      user: user,
      accessToken: tokenData.access_token,
      idToken: tokenData.id_token,
      refreshToken: tokenData.refresh_token,
      expiresIn: tokenData.expires_in,
      expiresAt: Date.now() + (tokenData.expires_in || 900) * 1000,
    };

    try {
      localStorage.setItem(self.storageKey, JSON.stringify(session));
    } catch (e) {
      // Storage unavailable or disabled
    }

    return session;
  };

  /**
   * Returns current authenticated user profile, or null if expired / not logged in.
   */
  PortalSSO.prototype.getCurrentUser = function () {
    try {
      var raw = localStorage.getItem(this.storageKey);
      if (!raw) return null;
      var session = JSON.parse(raw);
      if (session.expiresAt && Date.now() > session.expiresAt) {
        this.logout();
        return null;
      }
      return session.user;
    } catch (e) {
      return null;
    }
  };

  /**
   * Returns current session object if valid.
   */
  PortalSSO.prototype.getSession = function () {
    try {
      var raw = localStorage.getItem(this.storageKey);
      if (!raw) return null;
      var session = JSON.parse(raw);
      if (session.expiresAt && Date.now() > session.expiresAt) {
        this.logout();
        return null;
      }
      return session;
    } catch (e) {
      return null;
    }
  };

  /**
   * Clears stored tokens and logs the user out locally.
   */
  PortalSSO.prototype.logout = function () {
    try {
      localStorage.removeItem(this.storageKey);
    } catch (e) {}
  };

  return PortalSSO;
});
