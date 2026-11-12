package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). „Zamknij grę na PC” (dawniej „Zakończ sesję”) z menu w grze (12.11.2026): zamknięcie
// aplikacji na hoście tym samym
// żądaniem co upstreamowe „Zakończenie sesji” z listy PC i aplikacji (ServerHelper.doQuit → NvHTTP.quitApp,
// czyli /cancel). Sunshine po /cancel kończy sesje strumienia, zamyka aplikację i przywraca konfigurację ekranu
// (rozdzielczość sprzed startu, src/nvhttp.cpp `cancel`). Dane hosta z intencji Game (te same, z których Game
// buduje NvConnection). Wołane po zatrzymaniu strumienia (Game.stopConnection, wątek po conn.stop()).
// Komunikaty (G16, 12.11.2026): jedno krótkie zdanie ze wskazówką; szczegóły błędu (wyjątek, kod HTTP) tylko w logu.

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.HostHttpResponseException;
import com.limelight.nvstream.http.NvHTTP;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

final class ThumbcastHostQuit implements Runnable {

    private final Context appContext;
    private final String host;
    private final int port;
    private final int httpsPort;
    private final String uniqueId;
    private final byte[] serverCert;
    private final String appName;

    /** Dane z intencji Game kopiowane od razu (aktywność może się już kończyć, gdy żądanie pójdzie). */
    ThumbcastHostQuit(Context context, Intent intent) {
        this.appContext = context.getApplicationContext();
        this.host = intent.getStringExtra(Game.EXTRA_HOST);
        this.port = intent.getIntExtra(Game.EXTRA_PORT, NvHTTP.DEFAULT_HTTP_PORT);
        this.httpsPort = intent.getIntExtra(Game.EXTRA_HTTPS_PORT, 0);
        this.uniqueId = intent.getStringExtra(Game.EXTRA_UNIQUEID);
        this.serverCert = intent.getByteArrayExtra(Game.EXTRA_SERVER_CERT);
        String name = intent.getStringExtra(Game.EXTRA_APP_NAME);
        this.appName = name != null ? name : "";
    }

    /** Sieć: wołać poza wątkiem UI. Wynik jako Toast (kontekst aplikacji, bo Game już się zamyka). */
    @Override
    public void run() {
        String message;
        String detail;
        try {
            X509Certificate cert = null;
            if (serverCert != null) {
                cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(serverCert));
            }
            NvHTTP http = new NvHTTP(new ComputerDetails.AddressTuple(host, port), httpsPort, uniqueId, cert,
                    PlatformBinding.getCryptoProvider(appContext));
            boolean ok = http.quitApp();
            message = appContext.getString(ok ? R.string.thumbcast_game_quit_done : R.string.thumbcast_game_quit_failed);
            detail = ok ? "ok" : "quitApp returned false";
        } catch (HostHttpResponseException e) {
            // 599: aplikację uruchomił inny klient (NvHTTP.quitApp), tak jak w ServerHelper.doQuit.
            message = appContext.getString(e.getErrorCode() == 599
                    ? R.string.thumbcast_game_quit_not_owner : R.string.thumbcast_game_quit_failed);
            detail = "HTTP " + e.getErrorCode() + ": " + e.getMessage();
        } catch (CertificateException | java.io.IOException | org.xmlpull.v1.XmlPullParserException
                 | RuntimeException e) {
            message = appContext.getString(R.string.thumbcast_game_quit_failed);
            detail = String.valueOf(e);
        }
        LimeLog.info("Thumbcast: end session (" + appName + "): " + detail);
        final String toast = message;
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(appContext, toast, Toast.LENGTH_LONG).show());
    }
}
