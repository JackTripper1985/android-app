package fr.gaulupeau.apps.Poche.network;

import android.util.Log;

import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;

import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getHttpURL;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequestBuilder;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequest;

/**
 * @author Victor Häggqvist
 * @since 10/20/15
 */
public class WallabagService {

    public static class FeedsCredentials {
        public String userID;
        public String token;
    }

    private static final String TAG = WallabagService.class.getSimpleName();

    private String endpoint;
    private final String username;
    private final String password;
    private OkHttpClient client;

    public WallabagService(String endpoint, String username, String password) {
        this(endpoint, username, password, WallabagConnection.getClient());
    }

    public WallabagService(String endpoint, String username, String password, OkHttpClient client) {
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.client = client;
    }

    public FeedsCredentials getCredentials() throws IOException {
        Request configRequest = getConfigRequest();

        String response = executeRequestForResult(configRequest);
        if(response == null) return null;

        Pattern pattern = Pattern.compile(
                "\"\\?feed&amp;type=home&amp;user_id=(\\d+)&amp;token=([a-zA-Z0-9]+)\"",
                Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(response);
        if(!matcher.find()) {
            Request generateTokenRequest = getGenerateTokenRequest();
            executeRequest(generateTokenRequest);

            response = executeRequestForResult(configRequest);
            if(response == null) return null;

            matcher = pattern.matcher(response);
            if(!matcher.find()) return null;
        }

        FeedsCredentials credentials = new FeedsCredentials();
        credentials.userID = matcher.group(1);
        credentials.token = matcher.group(2);

        return credentials;
    }

    public boolean addLink(String link) throws IOException {
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("plainurl", link)
                .build();

        return executeRequest(getRequest(url));
    }

    public boolean toggleArchive(int articleId) throws IOException {
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("action", "toggle_archive")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        return executeRequest(getRequest(url));
    }

    public boolean toggleFavorite(int articleId) throws IOException {
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("action", "toggle_fav")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        return executeRequest(getRequest(url));
    }

    public boolean deleteArticle(int articleId) throws IOException {
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("action", "delete")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        return executeRequest(getRequest(url));
    }

    public int testConnection() throws IOException {
        // TODO: detect redirects
        // TODO: check response codes prior to getting body

        HttpUrl httpUrl = HttpUrl.parse(endpoint + "/?view=about");
        if(httpUrl == null) {
            return 6;
        }
        Request testRequest = getRequest(httpUrl);

        Response response = exec(testRequest);
        if(response.code() == 401) {
            return 5; // fail because of HTTP Auth
        }

        String body = response.body().string();
        if(isRegularPage(body)) {
            return 0; // if HTTP-auth-only access control used, we should be already logged in
        }

        if(!isLoginPage(body)) {
            return 1; // it's not even wallabag login page: probably something wrong with the URL
        }

        Request loginRequest = getLoginRequest();

        response = exec(loginRequest);
        body = response.body().string();

        if(isLoginPage(body)) {
//            if(body.contains("div class='messages error'"))
            return 2; // still login page: probably wrong username or password
        }

        response = exec(testRequest);
        body = response.body().string();

        if(isLoginPage(body)) {
            return 3; // login page AGAIN: weird, probably authorization problems (maybe cookies expire)
        }

        if(!isRegularPage(body)) {
            return 4; // unexpected content: expected to find "log out" button
        }

        return 0;
    }

    private Request getLoginRequest() throws IOException {
        HttpUrl url = getHttpURL(endpoint + "/?login");

        // TODO: maybe move null checks somewhere else
        RequestBody formBody = new FormEncodingBuilder()
                .add("login", username != null ? username : "")
                .add("password", password != null ? password : "")
//                .add("longlastingsession", "on")
                .build();

        return getRequestBuilder()
                .url(url)
                .post(formBody)
                .build();
    }

    private Request getConfigRequest() throws IOException {
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("view", "config")
                .build();

        return getRequest(url);
    }

    private Request getGenerateTokenRequest() throws IOException {
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("feed", null)
                .setQueryParameter("action", "generate")
                .build();

        Log.d(TAG, "getGenerateTokenRequest() url: " + url.toString());

        return getRequest(url);
    }

    private boolean executeRequest(Request request) throws IOException {
        return executeRequest(request, true, true);
    }

    private boolean executeRequest(Request request, boolean checkResponse, boolean autoRelogin) throws IOException {
        return executeRequestForResult(request, checkResponse, autoRelogin) != null;
    }

    private String executeRequestForResult(Request request) throws IOException {
        return executeRequestForResult(request, true, true);
    }

    private String executeRequestForResult(Request request, boolean checkResponse, boolean autoRelogin)
            throws IOException {
        Log.d(TAG, "executeRequest() start; autoRelogin: " + autoRelogin);

        Response response = exec(request);
        Log.d(TAG, "executeRequest() got response");

        if(checkResponse) checkResponse(response);
        String body = response.body().string();
        if(!isLoginPage(body)) return body;
        Log.d(TAG, "executeRequest() response is login page");
        if(!autoRelogin) return null;

        Log.d(TAG, "executeRequest() trying to re-login");
        Response loginResponse = exec(getLoginRequest());
        if(checkResponse) checkResponse(response);
        if(isLoginPage(loginResponse.body().string())) {
            throw new IOException(App.getInstance()
                    .getString(R.string.wrongUsernameOrPassword_errorMessage));
        }

        Log.d(TAG, "executeRequest() re-login response is OK; re-executing request");
        response = exec(request);

        if(checkResponse) checkResponse(response);
        body = response.body().string();
        return !isLoginPage(body) ? body : null;
    }

    private Response exec(Request request) throws IOException {
        return client.newCall(request).execute();
    }

    private boolean checkResponse(Response response) throws IOException {
        return checkResponse(response, true);
    }

    private boolean checkResponse(Response response, boolean throwException) throws IOException {
        if(!response.isSuccessful()) {
            Log.w(TAG, "checkResponse() response is not OK; response code: " + response.code()
                    + ", response message: " + response.message());
            if(throwException)
                throw new IOException(String.format(
                        App.getInstance().getString(R.string.unsuccessfulRequest_errorMessage),
                        response.code(), response.message()
                ));

            return false;
        }

        return true;
    }

    private boolean isLoginPage(String body) throws IOException {
        if(body == null || body.length() == 0) return false;

//        "<body class=\"login\">"
        return body.contains("<form method=\"post\" action=\"?login\" name=\"loginform\">"); // any way to improve?
    }

    private boolean isRegularPage(String body) throws IOException {
        if(body == null || body.length() == 0) return false;

        return body.contains("href=\"./?logout\"");
    }

}
