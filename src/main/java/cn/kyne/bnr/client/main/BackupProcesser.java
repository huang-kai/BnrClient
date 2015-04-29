package cn.kyne.bnr.client.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.kyne.bnr.client.pojo.Configer;
import cn.kyne.bnr.client.pojo.FileInfo;
import cn.kyne.bnr.client.pojo.Manifest;
import cn.kyne.bnr.client.util.FileUtil;


public class BackupProcesser extends BnrProcesser{
    
    private static final Logger logger = LoggerFactory.getLogger(BackupProcesser.class);

    
    public BackupProcesser(Configer configer){
        super(configer);
    }

    public String doFullBackup() throws IOException, JSONException, GeneralSecurityException{
        String token = getTokenFromAccount();
        long key = logStartBackup(token);
        String storageAuthToken = getStorageAuthToken(token).trim();
//        System.out.println(storageAuthToken);
//        String latestManifestName = getLatestManifestName(configer.storageUrl, storageAuthToken);
//        String storageAuthToken = "1:5d2ee9b62dab11e0a9e2003048d35f75:900000:4102473600000:h9n5z12577896331@palm.com:291459ba05366ead7dbda9582ee78df9:MCwCFAbmkT9+FgDi6dtsWH2XtVkjjZnqAhRcdSxEDY3cBeOy7PszYAgOxDiJvA==";
//        String storageAuthToken = "3:c08e412f95df11e2b3ef42b0d94d0c05:900000:1365565235672:qa1j7t4dfbedcea4@palm.com:c08dcbfe95df11e2b3ef42b0d94d0c05:MCwCFDVpyTq4RDOItahUmlTtddHxca0zAhQ+Nr+mFH5BY4WCMBZaxhFGYwBEfw==";
        String encodeKey = getKeyescrow(token);
        Manifest manifest = new Manifest();
        manifest.setVersion(0);
        manifest.setDeviceId(configer.deviceId);
        uploadFiles(configer.storageUrl,storageAuthToken, new File(configer.backupPath),encodeKey,manifest);
        String lastestManifestName = uploadManifest(configer.storageUrl,manifest,storageAuthToken);
        logFinishBackup(token, key);
        return lastestManifestName;
    }
    
    public String doBackup(File file)throws IOException, JSONException, GeneralSecurityException{
        String token = getTokenFromAccount();
        long key = logStartBackup(token);
        String storageAuthToken = getStorageAuthToken(token).trim();
        String encodeKey = getKeyescrow(token);
        String latestManifestName = getLatestManifestName(configer.storageUrl, storageAuthToken);
        Manifest manifest;
        if (!StringUtils.isBlank(latestManifestName)){
            manifest = downloadManifest(configer.storageUrl+"manifest/", storageAuthToken, latestManifestName);
            uploadFiles(configer.storageUrl,storageAuthToken, file,encodeKey,manifest);
            
        }else{
            manifest = new Manifest();
            manifest.setVersion(0);
            manifest.setDeviceId(configer.deviceId);
            uploadFiles(configer.storageUrl,storageAuthToken, new File(configer.backupPath),encodeKey,manifest);
        }
        
        String lastestManifestName = uploadManifest(configer.storageUrl,manifest,storageAuthToken);
        logFinishBackup(token, key);
        return lastestManifestName;
        
        
    }
    
    private JSONObject createStartBackupPayload() throws JSONException{
        JSONObject body = new JSONObject();
        JSONObject params = new JSONObject();
        JSONObject manifest = new JSONObject();
        manifest.put("dbVersion", 200);
        manifest.put("type", "incremental");
        manifest.put("deviceId", configer.deviceId);
        manifest.put("services", new JSONArray());
        params.put("manifest", manifest);
        params.put("startedBy", "desktop");
        body.put("params", params);
        body.put("method", "startBackup");
        return body;
    }
    
    private JSONObject createFinishBackupPayload(long key) throws JSONException{
        JSONObject body = new JSONObject();
        body.put("method", "finishBackup");
        JSONObject params = new JSONObject();
        JSONObject manifest = new JSONObject();
        manifest.put("dbVersion", 200);
        manifest.put("type", "incremental");
        manifest.put("deviceId", configer.deviceId);
        manifest.put("size", 74602);
        manifest.put("services", new JSONArray());
        params.put("manifest", manifest);
        params.put("key", key);
        params.put("manifestNumber", 14);
        params.put("startedBy", "desktop");
        body.put("params", params);
        return body;
    }
    
    protected long logStartBackup(String token) throws JSONException, IOException{
        JSONObject body = createStartBackupPayload();
        JSONObject result = logBackup(token, body);
        return result.getLong("key");
    }
    
    protected void logFinishBackup(String token, long key) throws JSONException, IOException{
        JSONObject body = createFinishBackupPayload(key);
        logBackup(token, body);
    }
    
    protected void uploadFiles(String rootUrl, String storageAuthToken, File path, String encodeKey, Manifest manifest) throws IOException, JSONException, GeneralSecurityException {
        if (!path.exists()){
            throw new IOException("Path or file doesn't exist");
        }
        
        if (path.isFile()){
            manifest.addFile(uploadFileWithEncryptZip(rootUrl, storageAuthToken, path, encodeKey));
        }else{
            for (File file:path.listFiles()){
                if (file.isDirectory()){
                    uploadFiles(rootUrl,storageAuthToken, file, encodeKey, manifest);
                }else {
                    try {
                        manifest.addFile(uploadFileWithEncryptZip(rootUrl, storageAuthToken, file, encodeKey));
                    }catch (Exception e){
                        logger.error("upload file {} error" , file, e);
                        // continue upload others
                    }
                }
            }
        }
    }
    
    private void uploadFile(String url, String storageAuthToken, File file) throws IOException, JSONException{
        logger.debug("storage url is {}", url);
        String GUID = storageAuthToken.split(":")[1];
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "GoldFingerDevice "+ storageAuthToken);
        headers.put("x-palm-host", GUID+".backup.gf.prod");
        headers.put("Content-Type", "application/json");
        String fullUrl = url;
        logger.debug("Uploading file: {}", file.getAbsolutePath());
        logger.debug("Put file: {} to {}", file.getAbsolutePath(), fullUrl);
        String response = clientHelper.doPut(fullUrl,new FileInputStream(file),headers);
        logger.debug("put file response {}", response);
    }
    
    private FileInfo uploadFileWithEncryptZip(String url, String storageAuthToken, File file, String encodeKey) throws IOException, GeneralSecurityException, JSONException{
        FileInfo fileInfo = new FileInfo();
        String filePath = file.getAbsolutePath().replace("\\", "/");
        String rootPath = (new File(configer.backupPath)).getAbsolutePath().replace("\\", "/");
        String urlPath = filePath.substring(rootPath.length(),filePath.length()-file.getName().length());
        fileInfo.setPath(filePath.substring(rootPath.length()));
        String checksum = FileUtil.getFileChecksum(file);
        
        
        File encryptZipFile = File.createTempFile("#temp", null);
        encryptZipFile.deleteOnExit();
        FileUtil.encryptZip(file, encryptZipFile, encodeKey);
        String finalPath = urlPath+checksum+".zip.enc";
        fileInfo.setChecksum(checksum);
        fileInfo.setLastModified(file.lastModified());
        fileInfo.setSize(file.length());
        fileInfo.setFinalFilePath(finalPath);
        try {
            uploadFile(url+"files"+finalPath, storageAuthToken, encryptZipFile);
            
        }finally{
            encryptZipFile.delete();
        }
        return fileInfo;
    }
    
   
    public String uploadManifest(String url, Manifest manifest, String storageAuthToken) throws IOException, JSONException{
        String fullUrl = url +"manifest/";
        logger.debug("storage url is {}", fullUrl);
        String GUID = storageAuthToken.split(":")[1];
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-palm-host", GUID+".backup.gf.prod");
        headers.put("Authorization", "GoldFingerDevice"+ " " + storageAuthToken);
        headers.put("Content-Type","application/octet-stream");
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("autonumber", "1");
        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(manifest);
        logger.debug("Manifest body is {}", body);
        String response = clientHelper.doPost(fullUrl, body, parameters, headers);
        logger.debug("Upload manifest response is {}", response);
        if (!StringUtils.isBlank(response)){
            JSONObject result = new JSONObject(response);
            return result.getString("Name");
        }else{
            throw new IOException("Upload manifest file failed: result is null");
        }
        
    }
}
