package com.hp.bnr.client.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;

public class FileUtil {
    private static final String CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    public static final String DIGEST_ALGORITHM = "MD5";

    private static void directoryZip(ZipOutputStream out, File f, String base) throws IOException {
        if (f.isDirectory()) {
            File[] fl = f.listFiles();
            out.putNextEntry(new ZipEntry(base + "/"));
            if (base.length() == 0) {
                base = "";
            } else {
                base = base + "/";
            }
            for (int i = 0; i < fl.length; i++) {
                directoryZip(out, fl[i], base + fl[i].getName());
            }
        } else {
            out.putNextEntry(new ZipEntry(base));
            FileInputStream in = null;
            try {
                in = new FileInputStream(f);
                byte[] bb = new byte[2048];
                int aa = 0;
                while ((aa = in.read(bb)) != -1) {
                    out.write(bb, 0, aa);
                }
            }finally{
                closeIgnoreError(in);
            }
        }
    }

    private static void fileZip(ZipOutputStream zos, File file) throws IOException  {
        if (file.isFile()) {
            zos.putNextEntry(new ZipEntry(file.getName()));
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                byte[] bb = new byte[2048];
                int aa = 0;
                while ((aa = fis.read(bb)) != -1) {
                    zos.write(bb, 0, aa);
                }
            }finally{
                closeIgnoreError(fis);
            }
            // System.out.println(file.getName());
        } else {
            directoryZip(zos, file, "");
        }
    }

    private static void fileUnZip(ZipInputStream zis, File file) throws IOException  {
        ZipEntry zip = zis.getNextEntry();
        if (zip == null)
            return;

        if(!file.getParentFile().exists()){
            file.getParentFile().mkdirs();
        }
            
        file.createNewFile();
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            byte b[] = new byte[2048];
            int aa = 0;
            while ((aa = zis.read(b)) != -1) {
                fos.write(b, 0, aa);
            }
        } finally {
            closeIgnoreError(fos);
        }
        
//        String name = zip.getName();
//        File f = new File(file.getAbsolutePath() + "/" + name);
//        if (zip.isDirectory()) {
//            f.mkdirs();
//            fileUnZip(zis, file);
//        } else {
//            f.createNewFile();
//            FileOutputStream fos = null;
//            try {
//                fos = new FileOutputStream(f);
//                byte b[] = new byte[2048];
//                int aa = 0;
//                while ((aa = zis.read(b)) != -1) {
//                    fos.write(b, 0, aa);
//                }
//            } finally {
//                closeIgnoreError(fos);
//            }
//            fileUnZip(zis, file);
//        }

    }

    private static void zip(File srcFile, File zipFile) throws IOException {
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new FileOutputStream(zipFile));
            fileZip(zos, srcFile);

        } finally {
            closeIgnoreError(zos);
        }
    }

    public static void unZip(File descFile, File zipFile) throws IOException  {
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new FileInputStream(zipFile));
            fileUnZip(zis, descFile);
        } finally {
            closeIgnoreError(zis);
        }
    }


    private static Key getKey(String key) {
        byte[] b = Base64.decodeBase64(key);
        SecretKeySpec dks = new SecretKeySpec(b, "AES");
        return dks;
    }

    public static void encrypt(File srcFile, File destFile, String encodePrivateKey) throws GeneralSecurityException, IOException {
        SecureRandom sr = new SecureRandom();
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        Key privateKey = getKey(encodePrivateKey);
        IvParameterSpec spec = new IvParameterSpec(privateKey.getEncoded());
        cipher.init(Cipher.ENCRYPT_MODE, privateKey, spec, sr);
        
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(srcFile);
            fos = new FileOutputStream(destFile);
            byte[] b = new byte[2048];
            while (fis.read(b) != -1) {
                fos.write(cipher.doFinal(b));
            }
        }finally{
            closeIgnoreError(fis,fos);
        }
        
    }

    public static void decrypt(File srcFile, File destFile, String encodePrivateKey) throws GeneralSecurityException, IOException {
        SecureRandom sr = new SecureRandom();
        Cipher ciphers = Cipher.getInstance(CIPHER_TRANSFORMATION);
        Key privateKey = getKey(encodePrivateKey);
        IvParameterSpec spec = new IvParameterSpec(privateKey.getEncoded());
        ciphers.init(Cipher.DECRYPT_MODE, privateKey, spec, sr);
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try{
            fis = new FileInputStream(srcFile);
            fos = new FileOutputStream(destFile);
            byte[] b = new byte[2064];
            while (fis.read(b) != -1) {
                fos.write(ciphers.doFinal(b));
            }
        }finally{
            closeIgnoreError(fis,fos);
        }
        
    }


    public static void encryptZip(File srcFile, File destfile, String encodeKey) throws IOException, GeneralSecurityException {
        File temp = File.createTempFile("#zip", ".zip");
        temp.deleteOnExit();

        zip(srcFile, temp);
        encrypt(temp, destfile, encodeKey);
        temp.delete();
    }

    public static void decryptUnzip(File srcfile, File destfile, String encodeKey) throws IOException, GeneralSecurityException  {
        File temp = File.createTempFile("#zip", ".zip");
        temp.deleteOnExit();
        decrypt(srcfile, temp, encodeKey);
        unZip(destfile, temp);
        temp.delete();
    }

    public static void main(String args[]) throws Exception {
        // long a = System.currentTimeMillis();
        File srcFile = new File("D:/test/11.txt");
        System.out.println(FileUtil.getFileChecksum(srcFile));
        
        File destFile = new File("D:/test/11.txt.zip.emc");
        FileUtil.encryptZip(srcFile,destFile,"IEL7LQr31vZu8y3dAz6FMQ==");
        File decryptFile = new File("D:/test1/22.txt");
        
        FileUtil.decryptUnzip(destFile,decryptFile,"IEL7LQr31vZu8y3dAz6FMQ==");
        System.out.println(FileUtil.getFileChecksum(decryptFile));

        // System.out.println(System.currentTimeMillis() - a);
    }

    /**
     * Closes the given Closeables, ignoring all errors. This is useful inside a
     * finally block, where we want to try to close a stream but not throw an
     * error if for some reason we can't do so.
     */
    public static void closeIgnoreError(Closeable... closeables) {
        for (Closeable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (IOException ex) {
                    // ignore error
                }
            }
        }
    }
    
    public static String getFileChecksum(File file) throws GeneralSecurityException, IOException {
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        byte[] digestBytes = null;
        InputStream in = new DigestInputStream(new FileInputStream(file), digest);
        IOUtils.copy(in, NullOutputStream.getInstance());
        in.close();
        digestBytes = digest.digest();
        return Hex.encodeHexString(digestBytes);
    }

}
