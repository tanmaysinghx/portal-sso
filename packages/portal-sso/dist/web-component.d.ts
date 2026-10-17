/**
 * Standard Web Component (<portal-login-button>) providing a ready-to-use
 * sign-in button that works natively in Angular, React, Vue, Svelte, and plain HTML.
 */
export declare class PortalLoginButtonElement extends HTMLElement {
    private button;
    private ssoInstance;
    private isLoading;
    static get observedAttributes(): string[];
    connectedCallback(): void;
    attributeChangedCallback(): void;
    private initSSO;
    private render;
    private updateButtonContent;
    private handleClick;
}
/**
 * Registers the <portal-login-button> custom element in the DOM if in browser environment.
 */
export declare function registerPortalButton(): void;
//# sourceMappingURL=web-component.d.ts.map