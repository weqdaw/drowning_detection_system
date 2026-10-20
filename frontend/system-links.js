// Navigation only. Authentication state is never shared between the two systems.
(() => {
    const fallback = new URL('/#/', window.location.origin);
    fallback.port = '5173';
    const storageKey = 'hcSafetyReturnUrl';
    const requested = new URL(window.location.href).searchParams.get('returnTo');
    let stored = null;
    try { stored = sessionStorage.getItem(storageKey); } catch (_) { /* Storage may be disabled. */ }

    const validateReturnUrl = (value) => {
        if (!value) return null;
        try {
            const url = new URL(value);
            if (!['http:', 'https:'].includes(url.protocol) ||
                url.hostname !== window.location.hostname || url.username || url.password) {
                return null;
            }
            // Prevent a return link from pointing back into this console.
            if (url.origin === window.location.origin && url.pathname.startsWith('/platform')) {
                return null;
            }
            return url.href;
        } catch (_) {
            return null;
        }
    };

    const returnUrl = validateReturnUrl(requested) || validateReturnUrl(stored) || fallback.href;
    try { sessionStorage.setItem(storageKey, returnUrl); } catch (_) { /* Navigation still works. */ }
    const link = document.getElementById('safety-system-link');
    if (link) link.href = returnUrl;
})();
