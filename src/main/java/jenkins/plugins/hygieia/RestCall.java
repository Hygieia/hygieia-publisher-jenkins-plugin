package jenkins.plugins.hygieia;

import com.capitalone.dashboard.util.CommonConstants;
import hudson.ProxyConfiguration;
import hygieia.utils.WildCardURL;
import jenkins.model.Jenkins;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.jasypt.contrib.org.apache.commons.codec_1_3.binary.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;


public class RestCall {
    private static final Logger logger = Logger.getLogger(RestCall.class.getName());
    private static final String API_USER = "hygieia_publisher_plugin";
    private boolean useProxy;

    public RestCall(boolean useProxy) {
        this.useProxy = useProxy;
    }

//Fixme: Need refactoring to remove code duplication.

    protected HttpClient getHttpClient() {
        HttpClient client = new HttpClient();
        if (Jenkins.getInstance() != null) {
            ProxyConfiguration proxy = Jenkins.getInstance().proxy;
            if (useProxy && (proxy != null)){
                client.getHostConfiguration().setProxy(proxy.name, proxy.port);
                String username = proxy.getUserName();
                String password = proxy.getPassword();
                if (!StringUtils.isEmpty(username.trim()) && !StringUtils.isEmpty(password.trim())) {
                    logger.info("Using proxy authentication (user=" + username + ")");
                    client.getState().setProxyCredentials(AuthScope.ANY,
                            new UsernamePasswordCredentials(username.trim(), password.trim()));
                }
            }
        }
        return client;
    }

    public RestCallResponse makeRestCallGet(String url, String jenkinsUser, String token) {
        RestCallResponse response;
        HttpClient client = getHttpClient();
        GetMethod get = new GetMethod(url);
        try {
            get.getParams().setContentCharset("UTF-8");
            if (!StringUtils.isEmpty(jenkinsUser) && !StringUtils.isEmpty(token)) {
                get.setRequestHeader(HttpHeaders.AUTHORIZATION, getAuthHeader(jenkinsUser + ':' + token));
            }
            int responseCode = client.executeMethod(get);
            String responseString = getResponseString(get.getResponseBodyAsStream());
            response = new RestCallResponse(responseCode, responseString);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error connecting to endpoint " + url, e);
            response = new RestCallResponse(HttpStatus.SC_BAD_REQUEST, "");
        } finally {
            get.releaseConnection();
        }
        return response;
    }

    protected String getAuthHeader (final String userInfo) {
        byte[] encodedAuth = Base64.encodeBase64(
                userInfo.getBytes(StandardCharsets.US_ASCII));
        return "Basic " + new String(encodedAuth);
    }


    private boolean bypassProxy (String url, List<Pattern> bypassList)  {
        for (Pattern bp: bypassList) {
            WildCardURL wurl = new WildCardURL(bp.toString());
            if (wurl.matches(url)) return true;
        }
        return false;
    }

    public RestCallResponse makeRestCallPost(String url, String jsonString) {
        RestCallResponse response;
        HttpClient client = getHttpClient();
        PostMethod post = new PostMethod(url);
        try {
            StringRequestEntity requestEntity = new StringRequestEntity(
                    jsonString,
                    "application/json",
                    "UTF-8");
            post.setRequestEntity(requestEntity);
            post.addRequestHeader(CommonConstants.HEADER_API_USER,API_USER);
            int responseCode = client.executeMethod(post);
            String responseString = getResponseString(post.getResponseBodyAsStream());
            response = new RestCallResponse(responseCode, responseString);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Hygieia: Error posting to Hygieia", e);
            response = new RestCallResponse(HttpStatus.SC_BAD_REQUEST, "");
        } finally {
            post.releaseConnection();
        }
        return response;
    }

    public RestCallResponse makeRestCallPost(String url, String jsonString, String clientReference) {
        RestCallResponse response;
        HttpClient client = getHttpClient();
        PostMethod post = new PostMethod(url);
        try {
            StringRequestEntity requestEntity = new StringRequestEntity(
                    jsonString,
                    "application/json",
                    "UTF-8");
            post.setRequestEntity(requestEntity);
            post.addRequestHeader(CommonConstants.HEADER_API_USER,API_USER);
            post.addRequestHeader(CommonConstants.HEADER_CLIENT_CORRELATION_ID, clientReference);
            int responseCode = client.executeMethod(post);
            String responseString = getResponseString(post.getResponseBodyAsStream());
            response = new RestCallResponse(responseCode, responseString);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Hygieia: Error posting to Hygieia", e);
            response = new RestCallResponse(HttpStatus.SC_BAD_REQUEST, "");
        } finally {
            post.releaseConnection();
        }
        return response;
    }

    public RestCallResponse makeRestCallGet(String url) {
        RestCallResponse response;
        HttpClient client = getHttpClient();
        GetMethod get = new GetMethod(url);
        try {
            get.getParams().setContentCharset("UTF-8");
            get.addRequestHeader(CommonConstants.HEADER_API_USER,API_USER);
            int responseCode = client.executeMethod(get);
            String responseString = getResponseString(get.getResponseBodyAsStream());
            response = new RestCallResponse(responseCode, responseString);
        } catch (HttpException e) {
            logger.log(Level.WARNING, "Error connecting to Hygieia", e);
            response = new RestCallResponse(HttpStatus.SC_BAD_REQUEST, "");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error connecting to Hygieia", e);
            response = new RestCallResponse(HttpStatus.SC_BAD_REQUEST, "");
        } finally {
            get.releaseConnection();
        }
        return response;
    }

    private String getResponseString(InputStream in) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] byteArray = new byte[1024];
        int count;
        while ((count = in.read(byteArray, 0, byteArray.length)) > 0) {
            outputStream.write(byteArray, 0, count);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    public class RestCallResponse {
        private int responseCode;
        private String responseString;

        public RestCallResponse(int responseCode, String responseString) {
            this.responseCode = responseCode;
            this.responseString = responseString;
        }

        public int getResponseCode() {
            return responseCode;
        }

        public void setResponseCode(int responseCode) {
            this.responseCode = responseCode;
        }

        public String getResponseString() {
            return responseString;
        }

        public void setResponseString(String responseString) {
            this.responseString = responseString;
        }
    }

}