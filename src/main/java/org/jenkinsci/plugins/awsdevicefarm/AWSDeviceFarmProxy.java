package org.jenkinsci.plugins.awsdevicefarm;

public class AWSDeviceFarmProxy {
    private String httpProxyFQDN;
    private int httpProxyPort;
    private String httpProxyUser;
    private String httpProxyPass;

    AWSDeviceFarmProxy(String httpProxyFQDN, int httpProxyPort, String httpProxyUser, String httpProxyPass) {
        this.setHttpProxyFQDN(httpProxyFQDN);
        this.setHttpProxyPort(httpProxyPort);
        this.setHttpProxyUser(httpProxyUser);
        this.setHttpProxyPass(httpProxyPass);
    }

    public void setHttpProxyFQDN(String value) {
        this.httpProxyFQDN = value;
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

    public int getHttpProxyPort() {
        return this.httpProxyPort;
    }

    public String getHttpProxyUser() {
        return this.httpProxyUser;
    }

    public String getHttpProxyPass() {
        return this.httpProxyPass;
    }
}
