"use client";

import { useEffect, useState } from "react";

const DISMISS_KEY = "force_gym.install_banner.dismissed.v1";

type LanAccessInfo = {
  hostname?: string | null;
  lanIp?: string | null;
  httpUrl?: string | null;
  httpsUrl?: string | null;
};

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed"; platform: string }>;
};

declare global {
  interface WindowEventMap {
    beforeinstallprompt: BeforeInstallPromptEvent;
  }
}

export function InstallBanner() {
  const [promptEvent, setPromptEvent] = useState<BeforeInstallPromptEvent | null>(null);
  const [installed, setInstalled] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const [fallbackMessage, setFallbackMessage] = useState<string | null>(null);
  const [lanAccess, setLanAccess] = useState<LanAccessInfo | null>(null);

  async function requestInstall() {
    setFallbackMessage(null);

    if (!promptEvent) {
      const targetHttpsUrl = lanAccess?.httpsUrl ?? (window.location.protocol !== "https:" ? `https://${window.location.hostname}:3443` : null);

      if (targetHttpsUrl && window.location.href !== targetHttpsUrl && window.location.protocol !== "https:") {
        window.location.href = targetHttpsUrl;
        return;
      }

      const isIos = /iphone|ipad|ipod/i.test(window.navigator.userAgent);
      if (isIos) {
        setDismissed(false);
        setFallbackMessage("En iPhone o iPad abre esta misma web en Safari y usa Compartir > Anadir a pantalla de inicio para que el icono quede en el inicio.");
        return;
      }

      setDismissed(false);
      setFallbackMessage("Tu navegador todavia no mostro la instalacion automatica. Entra por HTTPS y, si hace falta, abre el menu del navegador para instalar la app manualmente.");
      return;
    }

    await promptEvent.prompt();
    const choice = await promptEvent.userChoice;

    if (choice.outcome === "accepted") {
      setPromptEvent(null);
      return;
    }

    setDismissed(true);
  }

  useEffect(() => {
    const standalone = window.matchMedia("(display-mode: standalone)").matches || (window.navigator as Navigator & { standalone?: boolean }).standalone === true;
    setInstalled(standalone);
    setDismissed(window.localStorage.getItem(DISMISS_KEY) === "1");
    void fetch("/lan-access.json", { cache: "no-store" })
      .then((response) => (response.ok ? response.json() : null))
      .then((payload) => {
        if (payload) {
          setLanAccess(payload as LanAccessInfo);
        }
      })
      .catch(() => {
        setLanAccess(null);
      });

    const handlePrompt = (event: BeforeInstallPromptEvent) => {
      event.preventDefault();
      setPromptEvent(event);
    };

    const handleInstalled = () => {
      setInstalled(true);
      setPromptEvent(null);
    };

    window.addEventListener("beforeinstallprompt", handlePrompt);
    window.addEventListener("appinstalled", handleInstalled);

    return () => {
      window.removeEventListener("beforeinstallprompt", handlePrompt);
      window.removeEventListener("appinstalled", handleInstalled);
    };
  }, []);

  if (installed) {
    return null;
  }

  if (dismissed) {
    return (
      <div className="install-launcher">
        <button
          className="button button-primary"
          onClick={() => {
            window.localStorage.removeItem(DISMISS_KEY);
            setDismissed(false);
            setFallbackMessage(null);
          }}
          type="button"
        >
          Descargar app
        </button>
      </div>
    );
  }

  return (
    <aside className="install-banner">
      <div className="install-banner__content">
        <p className="install-banner__eyebrow">Quieres descargar la app?</p>
        <strong>Instala Force Gym en tu inicio</strong>
        <p>Acepta y la web quedara disponible como app en escritorio o movil, con su icono en pantalla de inicio y acceso mas rapido.</p>
        {fallbackMessage ? <p className="install-banner__fallback">{fallbackMessage}</p> : null}
      </div>

      <div className="install-banner__actions">
        <button
          className="button button-primary"
          onClick={() => {
            void requestInstall();
          }}
          type="button"
        >
          Si, descargar
        </button>

        <button
          className="button button-secondary"
          onClick={() => {
            window.localStorage.setItem(DISMISS_KEY, "1");
            setDismissed(true);
          }}
          type="button"
        >
          No por ahora
        </button>
      </div>
    </aside>
  );
}