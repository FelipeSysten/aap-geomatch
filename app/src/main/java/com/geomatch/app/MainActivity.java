package com.geomatch.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity; // NOVO
import android.content.Intent; // NOVO
import android.content.pm.PackageManager;
import android.net.Uri; // NOVO
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback; // NOVO
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // NOVO
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.app.AlertDialog;
import android.os.Build;
import android.content.DialogInterface;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final int REQUEST_CODE_PERMISSIONS = 101;
    private static final int REQUEST_CODE_LOCATION = 102; // Novo código de requisição para localização
    private static final int REQUEST_CODE_FILE_CHOOSER = 103; // NOVO: Código para o seletor de arquivos

    private static final int REQUEST_CODE_BACKGROUND_LOCATION = 104;
    private PermissionRequest pendingPermissionRequest;

    // Variáveis para guardar o callback de geolocalização
    private String geolocationOrigin;
    private GeolocationPermissions.Callback geolocationCallback;

    // NOVO: Variável para o callback do seletor de arquivos
    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebView.setWebContentsDebuggingEnabled(true);

        // Configurações da WebView
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setGeolocationEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        // Habilitar Cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // ATUALIZAÇÃO AQUI: Substitua o seu webView.setWebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Verifica se a URL é um link da web padrão
                if (url.startsWith("http:" ) || url.startsWith("https:" )) {
                    return false; // Deixa o WebView carregar a URL
                }

                // Se não for um link da web, é um esquema especial (tel:, mailto:, etc.)
                // Tenta criar uma Intent para que o sistema Android lide com a URL
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    // Opcional: Logar o erro se nenhuma aplicação puder lidar com a Intent
                    // Log.e("WebView", "Não foi possível lidar com a URL: " + url, e);
                }

                return true; // Impede o WebView de tentar carregar a URL
            }
        });


        webView.setWebChromeClient(new WebChromeClient() {
            // Lidar com permissões de Localização
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // Guarda o origin e o callback para usar depois
                    geolocationOrigin = origin;
                    geolocationCallback = callback;
                    // Solicita a permissão usando o novo código de requisição
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_LOCATION);
                } else {
                    // Se já tem a permissão, concede ao WebView.
                    callback.invoke(origin, true, false);
                }
            }

            // Lidar com permissões de Câmera/Microfone (WebRTC)
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                MainActivity.this.runOnUiThread(() -> {
                    pendingPermissionRequest = request;
                    String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
                    if (hasPermissions(permissions)) {
                        request.grant(request.getResources());
                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_CODE_PERMISSIONS);
                    }
                });
            }

            // NOVO: Lidar com a solicitação de abrir o seletor de arquivos
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams fileChooserParams) {
                // Se já houver um callback, cancele-o para evitar problemas
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                // Cria uma Intent para abrir a galeria de conteúdo
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                // Define o tipo de arquivo a ser selecionado. "image/*" para fotos.
                intent.setType("image/*");

                // Inicia a activity esperando um resultado
                startActivityForResult(intent, REQUEST_CODE_FILE_CHOOSER);

                return true; // Retorna true para indicar que vamos lidar com a solicitação
            }
        });


// Isso cria um objeto chamado "Android" que estará disponível no JavaScript da sua página web
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");


        // Carregar a URL do GeoMatch
        webView.loadUrl("https://geomatch-cvtv.onrender.com/" );
    }

    // NOVO: Metodo para receber o resultado do seletor de arquivos
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // Verifica se o resultado é do nosso seletor de arquivos
        if (requestCode == REQUEST_CODE_FILE_CHOOSER) {
            if (filePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }

            Uri[] results = null;
            // Verifica se a operação foi bem-sucedida e se há dados (a imagem escolhida)
            if (resultCode == Activity.RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }

            // Passa o resultado (a URI da imagem ou null se foi cancelado) de volta para o WebView
            filePathCallback.onReceiveValue(results);
            filePathCallback = null; // Limpa o callback
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void solicitarPermissaoSegundoPlano() {
        // A permissão de segundo plano só é tratada separadamente a partir do Android 10 (Q)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                // É recomendável e, muitas vezes, exigido pela Google Play explicar o motivo
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Localização Contínua Necessária");
                builder.setMessage("Para que o GeoMatch consiga manter a sua visibilidade em tempo real para os outros utilizadores mesmo com a aplicação minimizada ou o ecrã bloqueado, precisa de selecionar a opção 'Permitir o tempo todo' no ecrã seguinte.");

                builder.setPositiveButton("Configurar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                REQUEST_CODE_BACKGROUND_LOCATION);
                    }
                });

                builder.setNegativeButton("Agora Não", null);
                builder.show();
            }
        }
    }

    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Verifica se a resposta é para a permissão de Câmera/Áudio
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
                    pendingPermissionRequest = null; // Limpa a requisição pendente
                }
            }
        }
        // Verifica se a resposta é para a permissão de Localização
        else if (requestCode == REQUEST_CODE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Se a permissão foi concedida e temos um callback guardado (Primeiro Plano OK)
                if (geolocationCallback != null) {
                    // Concede a permissão à WebView
                    geolocationCallback.invoke(geolocationOrigin, true, false);
                }

                // NOVO: Agora que temos a permissão normal, pedimos a de segundo plano!
                solicitarPermissaoSegundoPlano();

            } else {
                // O utilizador negou a permissão.
                if (geolocationCallback != null) {
                    geolocationCallback.invoke(geolocationOrigin, false, false);
                }
            }
            // Limpa as variáveis de geolocalização
            geolocationOrigin = null;
            geolocationCallback = null;
        }
    }

    /**
     *
     */
    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}