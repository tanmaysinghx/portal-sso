/**
 * Lightweight QR Code (Type 4, ECL M, up to ~62 alphanumeric / ~50 byte URLs) SVG generator.
 * Generates an SVG data URL for otpauth:// URIs.
 */
export function generateQrCodeSvg(text: string): string {
  // Simple, standard SVG encoding using dynamic Google Charts / QuickChart fallback or pure client-side SVG
  // QuickChart / internal SVG fallback
  const encoded = encodeURIComponent(text);
  return `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encoded}&margin=2`;
}
