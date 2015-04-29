package cn.kyne.bnr.client.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.kyne.bnr.client.pojo.Configer;

public class AccountHelper {

    private static final Logger logger = LoggerFactory.getLogger(AccountHelper.class);
    protected HttpClientHelper clientHelper;
    protected Configer configer = null;

    public AccountHelper() throws ConfigurationException {
        
        this.configer = initConfig("bnr_config.properties");
        clientHelper = HttpClientHelper.getInstance(configer.proxyHost, configer.proxyPort);
    }

    public void signinAccount() throws IOException, JSONException {
        if (isAccountExist()) {
            logger.debug("Account {} is exist",configer.email);
            authenticateFromDevice();
        } else {
            logger.debug("Creating account {} ",configer.email);
            createAccountWithDevice();
        }
    }

    private boolean isAccountExist() throws IOException, JSONException {
        String fullUrl = configer.accountUrl + "/deviceJ/isEmailAvailable";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        String body = "{\"email\":\"" + configer.email + "\"}";
        String response = clientHelper.doPost(fullUrl, body, null, headers);
        logger.debug("Is account exist response is {}", response);
        JSONObject result = new JSONObject(response);
        if ("0".equals(result.getString("isAvailable"))) {
            return true;
        } else {
            return false;
        }
    }

    private void createAccountWithDevice() throws JSONException, IOException {
        String fullUrl = configer.accountUrl + "/deviceJ/createDeviceAccount";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("Accept", "application/json;charset=UTF-8");
        String body = getCreateAccountBody();
        String response = clientHelper.doPost(fullUrl, body, null, headers);

    }

    private void authenticateFromDevice() throws JSONException, IOException {
        String fullUrl = configer.accountUrl + "/deviceJ/authenticateFromDevice";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json;charset=UTF-8");
        String body = getauthenticateFromDeviceBody();
        String response = clientHelper.doPost(fullUrl, body, null, headers);

    }

    private String getauthenticateFromDeviceBody() throws JSONException {
        JSONObject InAuthenticateFromDevice = new JSONObject();

        JSONObject device = new JSONObject();
        device.put("deviceID", configer.deviceId);
        device.put("nduID", getGenericNDUID());
        device.put("deviceModel", "HSTNH-I29C");
        device.put("firmwareVersion", "");
        device.put("serialNumber", "5ABCD10253");
        device.put("platform", "Nova");
        device.put("country", "us");
        device.put("macAddress", "08:00:27:8e:b9:71");

        JSONObject romToken = new JSONObject();
        romToken.put("buildVariant", "{\"sets\":\"1769\",\"1783\"}");
        romToken.put("serverAuthType", "");
        romToken.put("serverPwd", "0.5138823748910071");
        romToken.put("serverNonce", "MW1eRVhNO3pid1F5PVFQSQ==");
        romToken.put("clientCredential", "714576858");
        romToken.put("clientPwd", "0.5900887510674658");
        romToken.put("clientNonce", "azRzPnFpXFdXcDBjeDRzYQ==");
        romToken.put("softwareBuildBranch", "HP webOS 3.0.5");
        romToken.put("swUpdateTarget", "internal");
        romToken.put("softwareBuildNumber", "Nova-HP-Topaz-86");

        InAuthenticateFromDevice.put("application", "TokenGateway");
        InAuthenticateFromDevice.put("accountAlias", configer.email);
        InAuthenticateFromDevice.put("device", device);
        InAuthenticateFromDevice.put("romToken", romToken);
        InAuthenticateFromDevice.put("password", configer.password);
        JSONObject result = new JSONObject();
        result.put("InAuthenticateFromDevice", InAuthenticateFromDevice);
        return result.toString();
    }

    private String getCreateAccountBody() throws JSONException {
        JSONObject InCreateDeviceAccount = new JSONObject();
        JSONObject account = new JSONObject();
        account.put("email", configer.email);
        account.put("firstName", "backup");
        account.put("lastName", "test");
        account.put("language", "en");
        account.put("country", "us");

        JSONObject device = new JSONObject();
        device.put("deviceID", configer.deviceId);
        device.put("nduID", getGenericNDUID());
        device.put("deviceModel", "castle");
        device.put("firmwareVersion", "R2A07(R2A07)");
        device.put("network", "gsm");
        device.put("platform", "Nova");
        device.put("productSku", "FB354UA-EVT2");
        device.put("homeMcc", "310");
        device.put("homeMnc", "200");
        device.put("currentMcc", "310");
        device.put("currentMnc", "200");

        JSONObject romToken = new JSONObject();
        romToken.put("buildVariant", "{\"sets\":\"1769\",\"1783\"}");
        romToken.put("serverAuthType", "");
        romToken.put("serverPwd", "0.5138823748910071");
        romToken.put("serverNonce", "MW1eRVhNO3pid1F5PVFQSQ==");
        romToken.put("clientCredential", "714576858");
        romToken.put("clientPwd", "0.5900887510674658");
        romToken.put("clientNonce", "azRzPnFpXFdXcDBjeDRzYQ==");
        romToken.put("softwareBuildBranch", "HP webOS 3.0.5");
        romToken.put("swUpdateTarget", "internal");
        romToken.put("softwareBuildNumber", "Nova-HP-Topaz-86");

        JSONObject response = new JSONObject();
        response.put("questionID", 1001);
        response.put("response", "none");
        InCreateDeviceAccount.put("account", account);
        InCreateDeviceAccount.put("password", configer.password);
        InCreateDeviceAccount.put("device", device);
        InCreateDeviceAccount.put("romToken", romToken);
        InCreateDeviceAccount.put("response", response);
        JSONObject result = new JSONObject();
        result.put("InCreateDeviceAccount", InCreateDeviceAccount);
        return result.toString();
    }

    public static String getGenericNDUID() {
        StringBuffer nduid = new StringBuffer("FEED0BEEF");

        nduid.append(System.nanoTime());
        nduid.append(Long.toHexString(System.nanoTime()).toUpperCase());

        // reverse the last nanotime value so that the 'more changed' end
        // is also sucked into the value before the truncation happens. in
        // some cases we were running fast enough that the truncation could lead
        // to duplicates. side-effect is that the last digits in nduids will be
        // the same more often but they will be more unique inside. and it's
        // what
        // is on the inside that counts.
        String lastTime = String.valueOf(System.nanoTime());
        for (int i = lastTime.length() - 1; i > 0; i--)
            nduid.append(lastTime.charAt(i));

        // QALogger.logInfo(nduid.toString());

        return nduid.toString().substring(0, 40);
    }

    public static void main(String[] args) throws Exception {
        AccountHelper helper = new AccountHelper();
        helper.signinAccount();
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
}
