package org.jenkinsci.plugins.awsdevicefarm;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;

public class AWSDeviceFarmProxy {
    private String httpProxyFQDN;
    private int httpProxyPort;
    private String httpProxyUser;
    private String httpProxyPass;
    private boolean active = false;

    AWSDeviceFarmProxy(String httpProxyFQDN, int httpProxyPort, String httpProxyUser, String httpProxyPass) {
        this.setHttpProxyFQDN(httpProxyFQDN != null ? httpProxyFQDN : "");
        this.setHttpProxyPort(httpProxyPort);
        this.setHttpProxyUser(httpProxyUser != null ? httpProxyUser : "");
        this.setHttpProxyPass(httpProxyPass != null ? httpProxyPass : "");
    }

    public void setHttpProxyFQDN(String value) {
        this.httpProxyFQDN = value;
        if (!value.isEmpty()) {
            this.setActive(true);
        }
    }

    public void setActive(boolean value) {
        this.active = value;
    }

    public void setHttpProxyPort(int value) {
        this.httpProxyPort = value;
    }

    public void setHttpProxyUser(String value) {
        this.httpProxyUser = value;
    }

    public void setHttpProxyPass(String value) {
        this.httpProxyPass = value;
    }

    public String getHttpProxyFQDN() {
        return this.httpProxyFQDN;
    }

    public boolean getActive() {
        return this.active;
    }

    public int getHttpProxyPort() {
        return this.httpProxyPort;
    }

    public String getHttpProxyUser() {
        return this.httpProxyUser;
    }

    public String getHttpProxyPass() {
        return this.httpProxyPass;
    }

    public CloseableHttpClient httpClientWithProxy() {
        CredentialsProvider credentialProvider = new BasicCredentialsProvider();
        credentialProvider.setCredentials(
            new AuthScope(getHttpProxyFQDN(), getHttpProxyPort()),
            new UsernamePasswordCredentials(getHttpProxyUser(), getHttpProxyPass())
        );

        HttpHost customProxy = new HttpHost(getHttpProxyFQDN(), getHttpProxyPort());
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        clientBuilder
            .setProxy(customProxy)
            .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
            .setDefaultCredentialsProvider(credentialProvider);

        return clientBuilder.build();
    }
}
