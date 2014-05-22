package biz.neustar.pagerduty.util;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.RFC2617Scheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;

@Singleton
public class PagerDutyHttpClient extends DefaultHttpClient {
    private String subdomain;
    private AuthScope authScope;
    private Credentials creds;
    private enum AuthType {
        BASIC, TOKEN
    };
    private AuthType authType;

    @Inject
    public PagerDutyHttpClient(@Named("pagerduty.subdomain") String subdomain,
                               @Named("pagerduty.username") String username,
                               @Named("pagerduty.password") String password) {
        this.subdomain = subdomain;
        authScope = new AuthScope(subdomain + ".pagerduty.com", 443);
        authType = AuthType.BASIC;
        creds = new UsernamePasswordCredentials(username, password);
    }

    @Inject
    public PagerDutyHttpClient(@Named("pagerduty.subdomain") String subdomain,
                               @Named("pagerduty.token") String token) {
        this.subdomain = subdomain;
        authScope = new AuthScope(subdomain + ".pagerduty.com", 443);
        authType = AuthType.TOKEN;
        creds = new TokenAuthCredentials(token);
    }

    @Override
    protected CredentialsProvider createCredentialsProvider() {
        CredentialsProvider provider = super.createCredentialsProvider();
        if (null != creds) {
          provider.setCredentials(authScope, creds);
        }

        return provider;
    }

    @Override
    protected HttpRequestExecutor createRequestExecutor() {
        return super.createRequestExecutor();
    }

    @Override
    protected HttpContext createHttpContext() {
        HttpContext ctx = super.createHttpContext();
        RFC2617Scheme authScheme = null;
        AuthCache authCache = new BasicAuthCache();

        if (AuthType.BASIC.equals(authType)) {
            authScheme = new BasicScheme();
        } else if (AuthType.TOKEN.equals(authType)) {
            authScheme = new TokenAuthScheme();
        }

        authCache.put(new HttpHost(subdomain + ".pagerduty.com", 443, "https"), authScheme);
        authCache.put(new HttpHost(subdomain + ".pagerduty.com", -1, "https"), authScheme);
        // Add AuthCache to the execution context
        ctx.setAttribute(ClientContext.AUTH_CACHE, authCache);

        AuthState state = new AuthState();
        state.setAuthScheme(authScheme);
        state.setAuthScope(authScope);
        state.setCredentials(creds);
        ctx.setAttribute(ClientContext.TARGET_AUTH_STATE, state);

        return ctx;
    }

    @Override
    protected HttpParams createHttpParams() {
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setUserAgent(params, "PagerDuty Client/" + Version.get());

        return params;
    }

    @Override
    protected ClientConnectionManager createClientConnectionManager() {
        SchemeRegistry registry = new SchemeRegistry();
        try {
            registry.register(new Scheme("https", 443, SSLSocketFactory.getSocketFactory()));
        } catch (Exception e) {
            throw new RuntimeException("Could not register SSL socket factor for Loggly", e);
        }

        ThreadSafeClientConnManager connManager = new ThreadSafeClientConnManager(registry);
        int maxThreads = 4;
        connManager.setMaxTotal(maxThreads);
        connManager.setDefaultMaxPerRoute(maxThreads);

        return connManager;
    }
}
