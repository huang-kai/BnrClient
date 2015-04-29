package com.hp.bnr.client.main;

import java.util.Observable;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.bnr.client.pojo.Configer;

public class RestoreThread extends Observable implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(RestoreThread.class);

    private Configer configer;

    private volatile String lastestManifestName = "0";

    public volatile boolean plzWait = false;

    public RestoreThread(Configer configer) {
        super();
        this.configer = configer;
    }

    public void run() {
        RestoreProcesser processer = new RestoreProcesser(configer);
        while (true) {
            try {
                if (!plzWait) {
                    String manifestName = processer.getLastestManifestNameFromServer();
                    if (!StringUtils.isBlank(manifestName)){
                        if (Integer.parseInt(manifestName) > Integer.parseInt(lastestManifestName)) {
                            setChanged();
                            notifyObservers("StartRestore");
                            try {
                                processer.doRestore(manifestName);
                                lastestManifestName = manifestName;
                            } finally {
                                Thread.sleep(3 * 1000);
                                setChanged();
                                notifyObservers("FinishRestore");
                            }
                        }
                    }else{
                        BackupProcesser backupProcesser = new BackupProcesser(configer);
                        lastestManifestName = backupProcesser.doFullBackup();
                    }
                    
                }
            } catch (Exception e) {
                logger.error("Do restore failed.", e);
            }
            
            try {
                Thread.sleep(configer.scanWaitTime * 60 * 1000);
            } catch (InterruptedException e) {
                ;
            }
        }

    }

    public void update(Observable o, Object arg) {
        lastestManifestName = (String) arg;
    }

    public String getLastestManifestName() {
        return lastestManifestName;
    }

    public void setLastestManifestName(String lastestManifestName) {
        this.lastestManifestName = lastestManifestName;
    }

}
