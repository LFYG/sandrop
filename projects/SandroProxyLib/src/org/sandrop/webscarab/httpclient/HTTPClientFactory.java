/***********************************************************************
 *
 * This file is part of SandroProxy, 
 * For details, please see http://code.google.com/p/sandrop/
 *
 * Copyright (c) 20012 supp.sandrob@gmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * Getting Source
 * ==============
 *
 * Source for this application is maintained at
 * http://code.google.com/p/sandrop/
 *
 * Software is build from sources of WebScarab project
 * For details, please see http://www.sourceforge.net/projects/owasp
 *
 */

package org.sandrop.webscarab.httpclient;

import java.io.IOException;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.sandrop.webscarab.model.Preferences;
import org.sandrop.webscarab.model.Request;
import org.sandrop.webscarab.model.Response;
import org.sandrop.webscarab.plugin.Framework;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 *
 * @author  rdawes
 */
public class HTTPClientFactory {
    
    private static HTTPClientFactory _instance;
    
    private Logger _logger = Logger.getLogger(getClass().getName());
    
    private String _httpProxy = "";
    private int _httpProxyPort = 80;
    private String _httpsProxy = "";
    private int _httpsProxyPort = 80;
    private String[] _noProxy = new String[0];
    
    private int _connectTimeout = 30000;
    private int _readTimeout = 0;
    
    private SSLContextManager _sslContextManager = null;
    
    private Authenticator _authenticator = null;
    
    private List _clientList = new ArrayList();
    private List _availableClients = new ArrayList();
    
    /** Creates a new instance of HttpClientFactory */
    protected HTTPClientFactory(Context context) {
        _logger.setLevel(Level.FINEST);
        _sslContextManager = new SSLContextManager();
        try {
            
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            
			String filename = pref.getString("preference_client_cert_file_path", ""); // "/mnt/sdcard/cert.p12";
			_logger.info("certificate file name:" + filename);
			String keyPassword =  pref.getString("preference_client_cert_password", ""); //"sandropssl";
			if (keyPassword.length() > 0){
			    _logger.info("certificate password:" + keyPassword.substring(0,1) + "... length " + keyPassword.length());
			}else{
			    _logger.info("certificate password empty!!");
			}
        	_sslContextManager.loadPKCS12Certificate(filename, keyPassword);
			_sslContextManager.setDefaultKey("key");
			_sslContextManager.unlockKey(0, 0, keyPassword);
			_logger.info("client certificate used for ssl sessions");
		} catch (KeyManagementException e) {
		    _logger.info("error using client certificate:" + e.getMessage());
			e.printStackTrace();
		} catch (KeyStoreException e) {
		    _logger.info("error using client certificate:" + e.getMessage());
			e.printStackTrace();
		} catch (CertificateException e) {
		    _logger.info("error using client certificate:" + e.getMessage());
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
		    _logger.info("error using client certificate:" + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
		    _logger.info("error using client certificate:" + e.getMessage());
			e.printStackTrace();
		}
    }
    
    public static HTTPClientFactory getInstance(Context context) {
        if (_instance == null){
            _instance = new HTTPClientFactory(context);
        }
        return _instance;
    }
    
    public static HTTPClientFactory getValidInstance() {
        if (_instance == null){
            Logger.getLogger("HTTPClientFactory").info("error getting HTTPClientFactory:");
        }
        return _instance;
    }
    
    public static void invalidateInstance(){
        _instance = null;
    }
    
    
    public SSLContextManager getSSLContextManager() {
        return _sslContextManager;
    }
    
    public void setHttpProxy(String proxy, int port) {
        if (proxy == null) proxy = "";
        _httpProxy = proxy;
        if (port<1 || port > 65535) throw new IllegalArgumentException("Port is out of range: " + port);
        _httpProxyPort = port;
    }
    
    public String getHttpProxy() {
        return _httpProxy;
    }
    
    public int getHttpProxyPort() {
        return _httpProxyPort;
    }
    
    public void setHttpsProxy(String proxy, int port) {
        if (proxy == null) proxy = "";
        _httpsProxy = proxy;
        if (port<1 || port > 65535) throw new IllegalArgumentException("Port is out of range: " + port);
        _httpsProxyPort = port;
    }
    
    public String getHttpsProxy() {
        return _httpsProxy;
    }
    
    public int getHttpsProxyPort() {
        return _httpsProxyPort;
    }
    
    public void setNoProxy(String[] noProxy) {
        _noProxy = noProxy;
        if (_noProxy == null) _noProxy = new String[0];
    }
    
    public String[] getNoProxy() {
        return _noProxy;
    }
    
    public void setTimeouts(int connectTimeout, int readTimeout) {
        _connectTimeout = connectTimeout;
        _readTimeout = readTimeout;
    }
    
    public void setAuthenticator(Authenticator authenticator) {
        _authenticator = authenticator;
    }
    
    public Authenticator getAuthenticator() {
        return _authenticator;
    }
    
    public HTTPClient getHTTPClient() {
        URLFetcher uf = new URLFetcher();
        uf.setHttpProxy(_httpProxy, _httpProxyPort);
        uf.setHttpsProxy(_httpsProxy, _httpsProxyPort);
        uf.setNoProxy(_noProxy);
        uf.setSSLContextManager(_sslContextManager);
        uf.setTimeouts(_connectTimeout, _readTimeout);
        uf.setAuthenticator(_authenticator);
        return uf;
    }
    
    public Response fetchResponse(Request request) throws IOException {
        HTTPClient hc = null;
        synchronized (_availableClients) {
            if (_availableClients.size()>0) {
                hc = (HTTPClient) _availableClients.remove(0);
            } else {
                _logger.info("Creating a new Fetcher");
                hc = getHTTPClient();
                _clientList.add(hc);
            }
        }
        Response response = null;
        IOException ioe = null;
        try {
            response = hc.fetchResponse(request);
        } catch (IOException e) {
            ioe = e;
        }
        synchronized (_availableClients) {
            _availableClients.add(hc);
        }
        if (ioe != null) throw ioe;
        return response;
    }
    
}