import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-download',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './download.html',
})
export class Download {
  readonly version = environment.version;
  readonly jarDownloadUrl = '/api/public/download/portal-sso.jar';
  readonly jarChecksum = '4f8b9e2a7c6109e3bb88251e6b8c4c782729a6741b01c3e3a47da4f64722880b';

  readonly activeOs = signal<'linux' | 'mac' | 'windows'>('linux');
  readonly copiedSnippet = signal<string | null>(null);

  readonly snippetLinux = `curl -LO http://localhost:8080/api/public/download/portal-sso.jar\njava -jar portal-sso.jar --httpPort=8080`;
  readonly snippetMac = `curl -LO http://localhost:8080/api/public/download/portal-sso.jar\njava -jar portal-sso.jar --httpPort=8080`;
  readonly snippetWindows = `Invoke-WebRequest -Uri "http://localhost:8080/api/public/download/portal-sso.jar" -OutFile "portal-sso.jar"\njava -jar portal-sso.jar --httpPort=8080`;

  copySnippetText(text: string, id: string): void {
    navigator.clipboard.writeText(text).then(() => {
      this.copiedSnippet.set(id);
      setTimeout(() => this.copiedSnippet.set(null), 2000);
    });
  }
}
