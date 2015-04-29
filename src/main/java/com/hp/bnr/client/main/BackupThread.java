/**
 * 
 */
package com.hp.bnr.client.main;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.bnr.client.pojo.Configer;

/**
 * @author hukai
 * 
 */
public class BackupThread extends Observable implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(BackupThread.class);

    private Configer configer;

    public volatile boolean plzWait = false;

    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private boolean trace = false;

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    public BackupThread(Configer configer) throws IOException {
        super();
        this.configer = configer;
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey, Path>();
        this.trace = true;
    }

    public void run() {
        try {
            wathchDir(configer.backupPath, true);
        } catch (IOException e) {
            logger.error("Running wathch dir error.",e);
        }

    }

    private void wathchDir(String path, boolean recursive) throws IOException {
        Path watchDir = Paths.get(path);
        BackupProcesser processer = new BackupProcesser(configer);
        if (recursive) {
            logger.debug("Scanning {} ...", watchDir);
            registerAll(watchDir);
            logger.debug("Done.");
        } else {
            register(watchDir);
        }

        // enable trace after initial registration
        this.trace = true;

        for (;;) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                logger.error("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                // TBD - provide example of how OVERFLOW event is handled
                if (kind == OVERFLOW) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                // print out event
                logger.debug("{}: {}", event.kind().name(), child);

                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        } else if (isRegularFile(child)){
                            if (!plzWait) {
                                setChanged();
                                notifyObservers("StartBackup");
                                try {
                                    String lastestManifestName = processer.doBackup(child.toFile());
                                    setChanged();
                                    notifyObservers(lastestManifestName);
                                } catch (Exception e) {
                                    logger.error("Backup error.", e);
                                } finally {
                                    setChanged();
                                    notifyObservers("FinishBackup");
                                }
                            }
                        }
                    } catch (IOException x) {
                        // ignore to keep sample readbale
                    }
                }
                
                if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    if (isRegularFile(child)) {
                        if (!plzWait) {
                            setChanged();
                            notifyObservers("StartBackup");
                            try {
                                String lastestManifestName = processer.doBackup(child.toFile());
                                setChanged();
                                notifyObservers(lastestManifestName);
                            } catch (Exception e) {
                                logger.error("Backup error.", e);
                            } finally {
                                setChanged();
                                notifyObservers("FinishBackup");
                            }
                        }
                    }
                }
                if (kind == StandardWatchEventKinds.ENTRY_DELETE) {

                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                logger.debug("register: {}", dir);
            } else {
                if (!dir.equals(prev)) {
                    logger.debug("update: {} -> {}", prev, dir);
                }
            }
        }
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    private boolean isRegularFile(Path path){
        if (Files.isRegularFile(path, NOFOLLOW_LINKS)){
            if (!path.getFileName().toString().startsWith(".")&&!path.getFileName().toString().endsWith("~")){
                return true;
            }
        }
        return false;
    }
}
