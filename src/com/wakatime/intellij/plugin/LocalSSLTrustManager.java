package com.wakatime.intellij.plugin;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

class LocalSSLTrustManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(final X509Certificate[] arg0,
                                   final String arg1) throws CertificateException {
        // TODO Auto-generated method stub

    }

    @Override
    public void checkServerTrusted(final X509Certificate[] arg0,
                                   final String arg1) throws CertificateException {
        // TODO Auto-generated method stub

    }

    @Override
    public final X509Certificate[] getAcceptedIssuers() {
        // TODO Auto-generated method stub
        return null;
    }
}