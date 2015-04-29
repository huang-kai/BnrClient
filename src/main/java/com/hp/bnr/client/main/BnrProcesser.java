package com.hp.bnr.client.main;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.bnr.client.pojo.Configer;
import com.hp.bnr.client.pojo.FileInfo;
import com.hp.bnr.client.pojo.Manifest;
import com.hp.bnr.client.util.HttpClientHelper;

public class BnrProcesser {
    private static final Logger logger = LoggerFactory.getLogger(BnrProcesser.class);

    protected Configer configer = null;
    
    private static String token = null;
    
    protected HttpClientHelper clientHelper;
    
    public BnrProcesser(Configer configer){
        this.configer=configer;
        clientHelper = HttpClientHelper.getInstance(configer.proxyHost,configer.proxyPort);
    }
    
    
    public String getTokenFromAccount() throws IOException, JSONException {
        if (token==null){
            logger.debug("Get token url is {}", configer.tokenGatewayUrl);
            StringBuffer payload = new StringBuffer();
            payload.append("grant_type=password&client_id=GoldFingerTest&");
            payload.append("client_secret=GoldFingerTest_SECRET&username=");
            payload.append(configer.email);
            payload.append("&password=");
            payload.append(configer.password);
            payload.append("&device_serial_id=");
            payload.append(configer.deviceId);
            payload.append("&device_model=HSTNH-I30C");
            payload.append("&device_os_version=null");
            payload.append("&device_platform=Nova");
            
            logger.debug("TokenGateway payload {}", payload.toString());
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Content-Type", "application/x-www-form-urlencoded");
            String response = clientHelper.doPost(configer.tokenGatewayUrl, payload.toString(), null , headers);
            logger.debug("Get token respones is {}", response);
            JSONObject result = new JSONObject(response);
            token= result.getString("access_token");
        }
        return token;
    }
    
    public String getStorageAuthToken(String token) throws IOException {
        logger.debug("storage auth url is {}", configer.storageAuthUrl);
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("email", configer.email);
        parameters.put("deviceId", configer.deviceId);
        parameters.put("token", token);
        String result = clientHelper.doPost(configer.storageAuthUrl, null, parameters, null);
        logger.debug("Get auth token respones is {}", result);
        return result;
    }
    
    public String getKeyescrow(String token) throws IOException{
        logger.debug("Get keyescrow url is {}", configer.keyescrowUrl);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json");
        
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("email", configer.email);
        parameters.put("deviceId", configer.deviceId);
        parameters.put("token", token);
        String result = clientHelper.doGet(configer.keyescrowUrl, parameters, headers);
        logger.debug("Get keyescrow respones is {}", result);
        return result;
    }
    
    public Manifest getLatestManifest(String url, String storageAuthToken) throws IOException, JSONException{
        String fullUrl = url +"manifest/";
        logger.debug("storage url is {}", fullUrl);
        String GUID = storageAuthToken.split(":")[1];
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-palm-host", GUID+".bnr.palmws.com");
        headers.put("Authorization", "GoldFingerDevice"+ " " + storageAuthToken);
        String response = clientHelper.doGetWithHeaders(fullUrl,headers);
        logger.debug("Get manifest list respones is {}", response);
        JSONArray results = new JSONArray(response);
        int latest = 0;
        String latestFileName = "";
        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.getJSONObject(i);
            String name = result.getString("Name");
            int fileNumber = Integer.parseInt(name);
            if (fileNumber>latest){
                latest = fileNumber;
                latestFileName = name;
            }
        }
        
        if (latestFileName.isEmpty()){
            return null;
        }
        
        fullUrl = fullUrl + latestFileName;
        String response1 = clientHelper.doGetWithHeaders(fullUrl, headers);
        logger.debug("Get manifest respones is {}", response1);
        JSONObject jsonManifest = new JSONObject(response1);
        Manifest manifest = new Manifest();
        manifest.setVersion(jsonManifest.getLong("version"));
        JSONArray files = jsonManifest.getJSONArray("files");
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            FileInfo fileInfo = new FileInfo();
            fileInfo.setChecksum(file.getString("checksum"));
            fileInfo.setPath(file.getString("path"));
            fileInfo.setSize(file.getLong("size"));
            fileInfo.setCreated(file.getString("created"));
            fileInfo.setLastModified(file.getLong("lastModified"));
            manifest.addFile(fileInfo);
        }
        return manifest;
    }
    
    public String getLatestManifestName (String url, String storageAuthToken) throws IOException, JSONException{
        String fullUrl = url +"manifest/";
        logger.debug("storage url is {}", fullUrl);
        String GUID = storageAuthToken.split(":")[1];
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-palm-host", GUID+".backup.gf.prod");
        headers.put("Authorization", "GoldFingerDevice"+ " " + storageAuthToken);
        headers.put("Content-Type", "application/json");
        String response = clientHelper.doGetWithHeaders(fullUrl,headers);
        logger.debug("Get manifest list respones is {}", response);
        JSONArray results = new JSONArray(response);
        int latest = 0;
        String latestFileName = "";
        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.getJSONObject(i);
            String name = result.getString("Name");
            int fileNumber = Integer.parseInt(name);
            if (fileNumber>latest){
                latest = fileNumber;
                latestFileName = name;
            }
        }
        return latestFileName;
    }
    
    public JSONObject logBackup(String token, JSONObject payload) throws JSONException, IOException{
        logger.debug("Log backup url is {}", configer.bnrintUrl);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json");
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("email", configer.email);
        parameters.put("deviceId", configer.deviceId);
        parameters.put("token", token);
        logger.debug("StartBackup payload: {}", payload.toString());
        String response = clientHelper.doPost(configer.bnrintUrl, payload.toString(), parameters , headers);
        logger.debug("Start backup respones is {}", response);
        JSONObject result = new JSONObject(response);
        return result.getJSONObject("result");
    }
    
    public Manifest downloadManifest(String url, String storageAuthToken, String name) throws IOException{
        String fullUrl = url + name;
        logger.debug("storage url is {}", fullUrl);
        String GUID = storageAuthToken.split(":")[1];
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-palm-host", GUID+".backup.gf.prod");
        headers.put("Authorization", "GoldFingerDevice"+ " " + storageAuthToken);
        headers.put("Content-Type","application/json");
        
        String response = clientHelper.doGetWithHeaders(fullUrl, headers);
        logger.debug("Upload manifest response is {}", response);
        ObjectMapper mapper = new ObjectMapper();
        Manifest result = mapper.readValue(response, Manifest.class);
        return result;
    }

}
