package cn.kyne.bnr.client.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLException;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientHelper {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientHelper.class);
    static final int TIMEOUT = 20000;// Set time out
    static final int SO_TIMEOUT = 60000;// Set transaction time out
    private static HttpClientHelper instance = new HttpClientHelper();

    private static PoolingClientConnectionManager cm;
    private static HttpHost proxy = null;

    private HttpClientHelper() {
        ;
    }

    public static synchronized HttpClientHelper getInstance(String proxyHost, int proxyPort) {
        if (cm != null) {
            SchemeRegistry schemeRegistry = new SchemeRegistry();
            schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
            schemeRegistry.register(new Scheme("https", 443, SSLSocketFactory.getSocketFactory()));

            cm = new PoolingClientConnectionManager(schemeRegistry);
            // Increase max total connection to 800
            cm.setMaxTotal(800);
            // Increase default max connection per route to 400
            cm.setDefaultMaxPerRoute(400);

            logger.debug("Init connection manager.");
        }
        if (!StringUtils.isBlank(proxyHost)) {
            proxy = new HttpHost(proxyHost, proxyPort);
        }else{
            proxy = null;
        }
        return instance;
    }

    public DefaultHttpClient createHttpClient(boolean needProxy) {
        HttpParams params = new BasicHttpParams();
        params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, TIMEOUT);
        params.setParameter(CoreConnectionPNames.SO_TIMEOUT, SO_TIMEOUT);
        DefaultHttpClient httpClient = new DefaultHttpClient(cm, params);
        logger.debug("Init http client.");
        HttpRequestRetryHandler myRetryHandler = new HttpRequestRetryHandler() {
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                if (executionCount >= 5) {
                    // Do not retry if over max retry count
                    return false;
                }
                if (exception instanceof UnknownHostException) {
                    // Unknown host
                    return false;
                }
                if (exception instanceof SSLException) {
                    // SSL handshake exception
                    return false;
                }
                HttpRequest request = (HttpRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
                boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
                if (idempotent) {
                    // Retry if the request is considered idempotent
                    return true;
                }
                return false;
            }
        };
        logger.debug("Set retry handler");
        httpClient.setHttpRequestRetryHandler(myRetryHandler);
        if (proxy != null) {
            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        return httpClient;
    }

    public String doGet(String uri) throws IOException {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "application/json;charset=UTF-8"); // Add default
                                                                 // header
        return doGet(uri, null, headers);
    }

    public String doGetWithHeaders(String uri, Map<String, String> headers) throws IOException {
        return doGet(uri, null, headers);
    }

    public String doPost(String uri, String body) throws IOException {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "application/json;charset=UTF-8"); // Add default
                                                                 // header
        return doPost(uri, body, null, headers);
    }

    public String doPost(String uri, String body, Map<String, String> parameters, Map<String, String> headers) throws IOException {

        URIBuilder builder = new URIBuilder();
        builder.setPath(uri);
        if (parameters != null) {
            for (String key : parameters.keySet()) {
                builder.addParameter(key, parameters.get(key));
            }
        }
        String fullUri = builder.toString();
        HttpPost postRequest = new HttpPost(fullUri);
        logger.debug("Sending POST request to {}, with body : {}", fullUri, body);

        if (headers != null) {
            for (String key : headers.keySet()) {
                postRequest.addHeader(key, headers.get(key));
            }
        }
        if (body != null) {
            postRequest.setEntity(new StringEntity(body, ContentType.create("application/json", "UTF-8")));
        }

        String result = "";
        HttpEntity responseEntity = null;
        try {
            DefaultHttpClient httpClient = createHttpClient(true);
            HttpResponse response = httpClient.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {

                responseEntity = response.getEntity();
                result = EntityUtils.toString(responseEntity, "UTF-8");
                logger.debug("Response from POST {}, body : {}", fullUri, result);
            } else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                logger.error("Failed : HTTP error code : {}, uri : {}", response.getStatusLine().getStatusCode(), fullUri);
                throw new IOException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
            } else {
                logger.error("Failed : HTTP error code : {}, uri : {}", response.getStatusLine().getStatusCode(), fullUri);
                String errorMsg = "Return code for http post is not 200. : Status code is -" + statusCode + " for url - " + fullUri;
                if (parameters != null) {
                    errorMsg = errorMsg + " and parameters:" + parameters.entrySet();
                }
                if (headers != null) {
                    errorMsg = errorMsg + " and headers:" + headers.entrySet();
                }
                throw new IOException("Failed : HTTP error : " + errorMsg);
            }
        } catch (IOException ioe) {
            postRequest.abort();
            logger.error("POST request to " + fullUri + " error.", ioe);
            throw ioe;
        } finally {
            EntityUtils.consumeQuietly(responseEntity);
        }
        return result;
    }

    public String doPut(String url, InputStream stream, Map<String, String> headers) throws IOException {
        HttpPut putRequest = new HttpPut(url);
        logger.debug("Sending put request to {}", url);
        if (headers != null) {
            for (String key : headers.keySet()) {
                putRequest.addHeader(key, headers.get(key));
            }
        }
        InputStreamEntity entity = new InputStreamEntity(stream, stream.available());
        putRequest.setEntity(entity);
        String result = "";
        HttpEntity responseEntity = null;
        try {
            System.out.println(putRequest.getURI());
            DefaultHttpClient httpClient = createHttpClient(true);
            HttpResponse response = httpClient.execute(putRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {

                responseEntity = response.getEntity();
                result = EntityUtils.toString(responseEntity, "UTF-8");
                logger.debug("Response from POST {}, body : {}", url, result);
            } else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                logger.error("Failed : HTTP error code : {}, uri : {}", response.getStatusLine().getStatusCode(), url);
                throw new IOException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
            } else {
                logger.error("Failed : HTTP error code : {}, uri : {}", response.getStatusLine().getStatusCode(), url);
                String errorMsg = "Return code for http post is not 200. : Status code is -" + statusCode + " for url - " + url;
                responseEntity = response.getEntity();
                if (responseEntity != null) {
                    result = EntityUtils.toString(responseEntity, "UTF-8");
                    errorMsg = errorMsg + "\n" + result;
                }
                if (headers != null) {
                    errorMsg = errorMsg + " and headers:" + headers.entrySet();
                }
                throw new IOException("Failed : HTTP error : " + errorMsg);
            }
        } catch (IOException ioe) {
            putRequest.abort();
            logger.error("POST request to " + url + " error.", ioe);
            throw ioe;
        } finally {
            EntityUtils.consumeQuietly(responseEntity);
        }
        return result;
    }

    public String doGet(String url, Map<String, String> parameters, Map<String, String> headers) throws IOException {
        URIBuilder builder = new URIBuilder();
        builder.setPath(url);
        if (parameters != null) {
            for (String key : parameters.keySet()) {
                builder.addParameter(key, parameters.get(key));
            }
        }

        String fullUri = builder.toString();
        HttpGet getRequest = new HttpGet(fullUri);
        if (headers != null) {
            for (String key : headers.keySet()) {
                getRequest.addHeader(key, headers.get(key));
            }
        }

        String result = "";
        HttpEntity responseEntity = null;
        try {
            logger.debug("Sending GET request to {}", fullUri);
            DefaultHttpClient httpClient = createHttpClient(true);
            HttpResponse response = httpClient.execute(getRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                responseEntity = response.getEntity();
                result = EntityUtils.toString(responseEntity, "UTF-8");
                logger.debug("Response from get {}, body : {}", fullUri, result);
            } else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                logger.error("Failed : HTTP error code : {}, uri : {}", response.getStatusLine().getStatusCode(), fullUri);
                throw new IOException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
            } else {
                logger.error("Failed : HTTP error code : {}, uri : {}", response.getStatusLine().getStatusCode(), fullUri);
                throw new IOException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
            }
        } catch (IOException ioe) {
            getRequest.abort();
            logger.error("Get request to " + fullUri + " error.", ioe);
            throw ioe;
        } finally {
            EntityUtils.consumeQuietly(responseEntity);
        }
        return result;
    }

    public byte[] doGetToByte(String url, Map<String, String> parameters, Map<String, String> headers) throws IOException {
        URIBuilder builder = new URIBuilder();
        builder.setPath(url);
        if (parameters != null) {
            for (String key : parameters.keySet()) {
                builder.addParameter(key, parameters.get(key));
            }
        }

        String fullUri = builder.toString();
        HttpGet getRequest = new HttpGet(fullUri);
        if (headers != null) {
            for (String key : headers.keySet()) {
                getRequest.addHeader(key, headers.get(key));
            }
        }
        HttpEntity responseEntity = null;
        try {
            logger.debug("Sending GET request to {}", fullUri);
            DefaultHttpClient httpClient = createHttpClient(true);
            HttpResponse response = httpClient.execute(getRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                responseEntity = response.getEntity();
                return EntityUtils.toByteArray(responseEntity);
            } else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                logger.error("Failed : HTTP error code : {}, uri : {}", response.getStatusLine().getStatusCode(), fullUri);
                throw new IOException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
            } else {
                logger.error("Failed : HTTP error code : {}, uri : {}", response.getStatusLine().getStatusCode(), fullUri);
                throw new IOException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
            }
        } catch (IOException ioe) {
            getRequest.abort();
            logger.error("Get request to " + fullUri + " error.", ioe);
            throw ioe;
        } finally {
            EntityUtils.consumeQuietly(responseEntity);
        }

    }
}
