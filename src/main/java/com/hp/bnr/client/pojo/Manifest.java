package com.hp.bnr.client.pojo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include= JsonSerialize.Inclusion.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Manifest {
    
    private long version=0;
    
    private String deviceId="";
    
    private Set<FileInfo> files;

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Set<FileInfo> getFiles() {
        return files;
    }

    public void setFiles(Set<FileInfo> files) {
        this.files = files;
    }
    
    public void addFile(FileInfo file){
        if (files == null){
            files = new HashSet<FileInfo>();
        }
        files.add(file);
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

}
