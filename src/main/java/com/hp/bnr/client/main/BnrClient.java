package com.hp.bnr.client.main;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import com.hp.bnr.client.pojo.Configer;

public class BnrClient implements Observer{
    BackupThread backup;
    RestoreThread restore;

    
    public static void main(String[] args) throws Exception {
//        Configuration config = new PropertiesConfiguration("bnr_config.properties");
//        Configer configer = new Configer();
//        configer.email = config.getString("account.email");
//        configer.password = config.getString("account.password");
//        configer.deviceId = config.getString("account.device");
//        configer.tokenGatewayUrl = config.getString("account.tokengateway.url");
//        configer.bnrintUrl = config.getString("bnr.bnrint.url");
//        configer.storageAuthUrl = config.getString("bnr.storage.auth.url");
//        configer.storageUrl = config.getString("bnr.storage.url");
//        configer.backupPath = config.getString("bnr.backup.path");
//        configer.keyescrowUrl = config.getString("bnr.keyescrow.url");
//        
//        configer.proxyHost = config.getString("proxy.host");
//        configer.proxyPort = config.getInt("proxy.port");
//        
//        
//        // BackupProcesser processer = new BackupProcesser(configer);
//        // processer.doBackup();
//        // RestoreProcesser processer = new RestoreProcesser(configer);
//        // processer.doRestore();
//        BackupThread backup = new BackupThread(configer);
//        RestoreThread restore = new RestoreThread(configer);
//        backup.addObserver(restore);
//        
//        Thread backupThread = new Thread(backup);
//        Thread restoreThread = new Thread(restore);
//        // Start the thread
//        backupThread.start();
//        restoreThread.start();
        BnrClient client = new BnrClient();
        client.start();
    }
    
    private Configer initConfig(String configFileName) throws ConfigurationException{
        Configuration config = new PropertiesConfiguration(configFileName);
        Configer configer = new Configer();
        configer.email = config.getString("account.email");
        configer.password = config.getString("account.password");
        configer.deviceId = config.getString("account.device");
        configer.tokenGatewayUrl = config.getString("account.tokengateway.url");
        configer.bnrintUrl = config.getString("bnr.bnrint.url");
        configer.storageAuthUrl = config.getString("bnr.storage.auth.url");
        configer.storageUrl = config.getString("bnr.storage.url");
        configer.backupPath = config.getString("bnr.backup.path");
        configer.keyescrowUrl = config.getString("bnr.keyescrow.url");
        
        configer.proxyHost = config.getString("proxy.host");
        configer.proxyPort = config.getInt("proxy.port");
        configer.scanWaitTime = config.getLong("scan.wait.time");
        configer.accountUrl = config.getString("account.url");
        return configer;
    }
    
    public void start() throws IOException{
        try {
            Configer configer = initConfig("bnr_config.properties");
            backup = new BackupThread(configer);
            restore = new RestoreThread(configer);
            backup.addObserver(this);
            restore.addObserver(this);
            Thread backupThread = new Thread(backup);
            Thread restoreThread = new Thread(restore);
            // Start the thread
            backupThread.start();
            restoreThread.start();
            
        } catch (ConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }

    public void update(Observable o, Object arg) {
        String message = (String)arg;
        if ("StartBackup".equals(message)){
            restore.plzWait=true;
        }else if ("FinishBackup".equals(message)){
            restore.plzWait=false;
        }else if ("StartRestore".equals(message)){
            backup.plzWait=true;
        }else if ("FinishRestore".equals(message)){
            backup.plzWait=false;
        }else{
            restore.setLastestManifestName(message);
        }
        
    }

}
