import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, timeout } from 'rxjs';
import { of } from 'rxjs';
import { environment } from '../../../environments/environment';

interface GitHubRelease {
  tag_name?: string;
}

interface CachedReleaseVersion {
  version: string;
  fetchedAt: number;
}

const RELEASE_API = 'https://api.github.com/repos/tanmaysinghx/portal-sso/releases/latest';
const CACHE_KEY = 'portal-sso-latest-release-version';
const CACHE_TTL_MS = 6 * 60 * 60 * 1000;

@Injectable({ providedIn: 'root' })
export class ReleaseVersionService {
  private readonly http = inject(HttpClient);
  readonly version = signal(environment.version);
  readonly latestReleaseUrl = 'https://github.com/tanmaysinghx/portal-sso/releases/latest';
  private loaded = false;

  loadLatest(): void {
    if (this.loaded) return;
    this.loaded = true;

    const cached = this.readCache();
    if (cached) {
      this.version.set(cached.version);
      if (Date.now() - cached.fetchedAt < CACHE_TTL_MS) return;
    }

    this.http.get<GitHubRelease>(RELEASE_API, {
      headers: { Accept: 'application/vnd.github+json' },
    }).pipe(
      timeout(5000),
      catchError(() => of(null)),
    ).subscribe(release => {
      const version = release?.tag_name?.trim().replace(/^v/i, '');
      if (!version || !/^[0-9A-Za-z][0-9A-Za-z._+-]*$/.test(version)) return;

      this.version.set(version);
      try {
        localStorage.setItem(CACHE_KEY, JSON.stringify({ version, fetchedAt: Date.now() }));
      } catch {
        // Storage may be unavailable; the in-memory value still updates.
      }
    });
  }

  private readCache(): CachedReleaseVersion | null {
    try {
      const raw = localStorage.getItem(CACHE_KEY);
      if (!raw) return null;
      const cached = JSON.parse(raw) as CachedReleaseVersion;
      if (typeof cached.version !== 'string' || !/^[0-9A-Za-z][0-9A-Za-z._+-]*$/.test(cached.version)
        || typeof cached.fetchedAt !== 'number') return null;
      return cached;
    } catch {
      return null;
    }
  }
}
