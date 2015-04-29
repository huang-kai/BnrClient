package cn.kyne.bnr.client.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.kyne.bnr.client.pojo.Configer;
import cn.kyne.bnr.client.pojo.FileInfo;
import cn.kyne.bnr.client.pojo.Manifest;
import cn.kyne.bnr.client.util.FileUtil;

public class RestoreProcesser extends BnrProcesser{
    private static final Logger logger = LoggerFactory.getLogger(RestoreProcesser.class);
    
    public RestoreProcesser (Configer configer){
        super(configer);
    }
    
    public void doRestore(String latestManifestName){
        try {
            String token = getTokenFromAccount();
            String storageAuthToken = getStorageAuthToken(token).trim();
            if (StringUtils.isBlank(latestManifestName)){
                logger.debug("No backup found.");
                return;
            }else{
                try {
                    long minifestNum =  Long.parseLong(latestManifestName.substring(0, 6));
                    long key = logStartRestore(token,minifestNum);
                    Manifest manifest = downloadManifest(configer.storageUrl+"manifest/", storageAuthToken, latestManifestName);
                    if (manifest!=null){
                        String encodeKey = getKeyescrow(token);
                        File parent = new File(configer.backupPath);
                        if (!parent.exists()){
                            parent.mkdir();
                        }
                        for (FileInfo fileInfo: manifest.getFiles()){
                            String path = fileInfo.getPath();
                            File localFile = new File(parent,path);
                            if (localFile.exists()){
                                String localChecksum = FileUtil.getFileChecksum(localFile);
                                if (localChecksum.equals(fileInfo.getChecksum())){
                                    continue;
                                }
                            }
                            String [] split = fileInfo.getFinalFilePath().split("/");
                            String remoteFileName = split[split.length-1];
                            File remoteFile = downloadFile(configer.storageUrl+"files"+fileInfo.getFinalFilePath(), storageAuthToken, remoteFileName);
                            FileUtil.decryptUnzip(remoteFile, localFile, encodeKey);
                        }
                    }
                    logFinishRestore(token, key);
                    
                }catch(IndexOutOfBoundsException e){
                    logger.error("Manifest name isn't correct");
                }catch(NumberFormatException e){
                    logger.error("Manifest name isn't correct");
                } catch (GeneralSecurityException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            
            
            
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }

    private void logFinishRestore(String token, long key) throws JSONException, IOException {
        JSONObject body = createFinishRestorePayload(key);
        logBackup(token, body);
        
    }

    private JSONObject createFinishRestorePayload(long key) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("method", "finishRestore");
        JSONObject params = new JSONObject();
        JSONObject manifest = new JSONObject();
        manifest.put("dbVersion", 200);
        manifest.put("type", "full");
        manifest.put("deviceId", configer.deviceId);
        manifest.put("size", 74602);
        manifest.put("services", new JSONArray());
        params.put("manifest", manifest);
        params.put("key", key);
        params.put("startedBy", "desktop");
        body.put("params", params);
        return body;
    }

    private long logStartRestore(String token, long latestManifestName) throws JSONException, IOException {
        JSONObject body = createStartRestorePayload(latestManifestName);
        JSONObject result = logBackup(token, body);
        return result.getLong("key");
    }

    private JSONObject createStartRestorePayload(long manifestNum) throws JSONException {
        JSONObject body = new JSONObject();
        JSONObject params = new JSONObject();
        params.put("manifestNumber", manifestNum);
        params.put("startedBy", "desktop");
        body.put("params", params);
        body.put("method", "startRestore");
        return body;
    }
    
    
    public File downloadFile(String url,String storageAuthToken,String fileName) throws IOException{
        logger.debug("Download file url is {}", url);
        String GUID = storageAuthToken.split(":")[1];
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-palm-host", GUID+".backup.gf.prod");
        headers.put("Authorization", "GoldFingerDevice"+ " " + storageAuthToken);
        headers.put("Content-Type","application/octet-stream");
        byte [] response = clientHelper.doGetToByte(url, null, headers);
        ByteBuffer buf = ByteBuffer.wrap(response);
        File file = File.createTempFile("#temp",null);
        FileChannel wChannel = new FileOutputStream(file).getChannel();
        
        // Write the ByteBuffer contents; the bytes between the ByteBuffer's
        // position and the limit is written to the file
        wChannel.write(buf);
    
        // Close the file
        wChannel.close();

        return file;
    }
    
    public String getLastestManifestNameFromServer() throws IOException, JSONException{
        String token = getTokenFromAccount();
        String storageAuthToken = getStorageAuthToken(token).trim();
        String latestManifestName = getLatestManifestName(configer.storageUrl, storageAuthToken);
        return latestManifestName;
    }
}
