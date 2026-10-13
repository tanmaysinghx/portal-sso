export interface ParsedSessionDevice {
  browser: string;
  browserVersion: string;
  os: string;
  osVersion: string;
  deviceType: 'desktop' | 'mobile' | 'tablet' | 'unknown';
  displayName: string;
}

/**
 * Parses raw User-Agent string into human-readable browser, OS, and device type information.
 */
export function parseUserAgent(uaString: string | null | undefined): ParsedSessionDevice {
  if (!uaString || uaString === 'Unknown Device' || uaString.trim() === '') {
    return {
      browser: 'Unknown Browser',
      browserVersion: '',
      os: 'Unknown Device',
      osVersion: '',
      deviceType: 'unknown',
      displayName: 'Unknown Device',
    };
  }

  const ua = uaString;

  // OS detection
  let os = 'Unknown OS';
  let osVersion = '';
  let deviceType: 'desktop' | 'mobile' | 'tablet' | 'unknown' = 'desktop';

  if (/Windows NT 10\.0/i.test(ua)) {
    os = 'Windows';
    osVersion = '10 / 11';
  } else if (/Windows NT 6\.3/i.test(ua)) {
    os = 'Windows';
    osVersion = '8.1';
  } else if (/Windows NT 6\.2/i.test(ua)) {
    os = 'Windows';
    osVersion = '8';
  } else if (/Windows NT 6\.1/i.test(ua)) {
    os = 'Windows';
    osVersion = '7';
  } else if (/Windows/i.test(ua)) {
    os = 'Windows';
  } else if (/iPhone/i.test(ua)) {
    os = 'iOS';
    deviceType = 'mobile';
    const match = ua.match(/OS (\d+[_\.]\d+)/i);
    if (match) osVersion = match[1].replace(/_/g, '.');
  } else if (/iPad/i.test(ua)) {
    os = 'iPadOS';
    deviceType = 'tablet';
    const match = ua.match(/OS (\d+[_\.]\d+)/i);
    if (match) osVersion = match[1].replace(/_/g, '.');
  } else if (/Android/i.test(ua)) {
    os = 'Android';
    deviceType = /Mobile/i.test(ua) ? 'mobile' : 'tablet';
    const match = ua.match(/Android (\d+(\.\d+)?)/i);
    if (match) osVersion = match[1];
  } else if (/Macintosh|Mac OS X/i.test(ua)) {
    os = 'macOS';
    const match = ua.match(/Mac OS X (\d+[_\.]\d+)/i);
    if (match) osVersion = match[1].replace(/_/g, '.');
  } else if (/CrOS/i.test(ua)) {
    os = 'ChromeOS';
  } else if (/Ubuntu/i.test(ua)) {
    os = 'Ubuntu Linux';
  } else if (/Fedora/i.test(ua)) {
    os = 'Fedora Linux';
  } else if (/Linux/i.test(ua)) {
    os = 'Linux';
  }

  // Device type refined checks
  if (deviceType === 'desktop') {
    if (/Mobile|Phone|iPod|webOS|BlackBerry|IEMobile|Opera Mini/i.test(ua)) {
      deviceType = 'mobile';
    } else if (/Tablet|PlayBook|Silk/i.test(ua)) {
      deviceType = 'tablet';
    }
  }

  // Browser detection
  let browser = 'Web Browser';
  let browserVersion = '';

  if (/Edg(?:e)?\/(\d+(\.\d+)?)/i.test(ua)) {
    browser = 'Microsoft Edge';
    const m = ua.match(/Edg(?:e)?\/(\d+(\.\d+)?)/i);
    if (m) browserVersion = m[1];
  } else if (/OPR\/(\d+(\.\d+)?)/i.test(ua) || /Opera/i.test(ua)) {
    browser = 'Opera';
    const m = ua.match(/OPR\/(\d+(\.\d+)?)/i);
    if (m) browserVersion = m[1];
  } else if (/Vivaldi\/(\d+(\.\d+)?)/i.test(ua)) {
    browser = 'Vivaldi';
    const m = ua.match(/Vivaldi\/(\d+(\.\d+)?)/i);
    if (m) browserVersion = m[1];
  } else if (/Brave/i.test(ua)) {
    browser = 'Brave';
  } else if (/SamsungBrowser\/(\d+(\.\d+)?)/i.test(ua)) {
    browser = 'Samsung Internet';
    const m = ua.match(/SamsungBrowser\/(\d+(\.\d+)?)/i);
    if (m) browserVersion = m[1];
  } else if (/Firefox\/(\d+(\.\d+)?)/i.test(ua)) {
    browser = 'Firefox';
    const m = ua.match(/Firefox\/(\d+(\.\d+)?)/i);
    if (m) browserVersion = m[1];
  } else if (/Chrome\/(\d+(\.\d+)?)/i.test(ua)) {
    browser = 'Chrome';
    const m = ua.match(/Chrome\/(\d+(\.\d+)?)/i);
    if (m) browserVersion = m[1];
  } else if (/Version\/(\d+(\.\d+)?).*Safari/i.test(ua)) {
    browser = 'Safari';
    const m = ua.match(/Version\/(\d+(\.\d+)?)/i);
    if (m) browserVersion = m[1];
  } else if (/Safari/i.test(ua)) {
    browser = 'Safari';
  } else if (/PostmanRuntime/i.test(ua)) {
    browser = 'Postman';
    deviceType = 'unknown';
  } else if (/curl/i.test(ua)) {
    browser = 'cURL';
    deviceType = 'unknown';
  }

  // Compose title
  let displayName = `${browser} on ${os}`;
  if (osVersion && (os === 'Windows' || os === 'Android')) {
    displayName = `${browser} on ${os} ${osVersion}`;
  } else if (os === 'iOS') {
    displayName = `${browser} on iPhone`;
  } else if (os === 'iPadOS') {
    displayName = `${browser} on iPad`;
  } else if (os === 'Unknown OS' && browser === 'Unknown Browser') {
    displayName = 'Unknown Device';
  }

  return {
    browser,
    browserVersion,
    os,
    osVersion,
    deviceType,
    displayName,
  };
}

/**
 * Returns a human-friendly relative time string (e.g. "Active now", "5m ago", "2h ago", "Yesterday").
 */
export function getRelativeTime(timestamp: string | number | Date, isCurrent: boolean = false): string {
  if (isCurrent) {
    return 'Active now';
  }

  const date = new Date(timestamp);
  if (isNaN(date.getTime())) {
    return 'Recently';
  }

  const now = Date.now();
  const diffSec = Math.floor((now - date.getTime()) / 1000);

  if (diffSec < 60) {
    return 'Just now';
  }
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) {
    return `${diffMin}m ago`;
  }
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) {
    return `${diffHours}h ago`;
  }
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays === 1) {
    return 'Yesterday';
  }
  if (diffDays < 7) {
    return `${diffDays}d ago`;
  }
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

/**
 * Categorizes an IP address into a user-friendly network badge.
 */
export function getIpType(ip: string | null | undefined): string | null {
  if (!ip) return null;
  const trimmed = ip.trim();
  if (trimmed === '127.0.0.1' || trimmed === '::1' || trimmed === '0:0:0:0:0:0:0:1') {
    return 'Localhost';
  }
  if (
    trimmed.startsWith('10.') ||
    trimmed.startsWith('192.168.') ||
    trimmed.startsWith('172.16.') ||
    trimmed.startsWith('172.17.') ||
    trimmed.startsWith('172.18.') ||
    trimmed.startsWith('172.19.') ||
    trimmed.startsWith('172.2') ||
    trimmed.startsWith('172.30.') ||
    trimmed.startsWith('172.31.')
  ) {
    return 'Private LAN';
  }
  return null;
}
