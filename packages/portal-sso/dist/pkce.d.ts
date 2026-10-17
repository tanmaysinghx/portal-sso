/**
 * Base64URL encodes an ArrayBuffer.
 */
export declare function base64UrlEncode(buffer: ArrayBuffer): string;
/**
 * Generates a cryptographically secure random string using WebCrypto.
 */
export declare function generateRandomString(length?: number): string;
/**
 * Computes the S256 code challenge from a plain code verifier using WebCrypto.
 */
export declare function generateCodeChallenge(verifier: string): Promise<string>;
/**
 * Safely parses the payload of a JWT without external libraries.
 */
export declare function parseJwtPayload(token: string): Record<string, any>;
//# sourceMappingURL=pkce.d.ts.map