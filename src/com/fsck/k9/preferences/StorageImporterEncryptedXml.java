package com.fsck.k9.preferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.content.SharedPreferences;
import android.util.Log;

import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.preferences.StorageImporter.ImportElement;

public class StorageImporterEncryptedXml implements IStorageImporter {
    public int importPreferences(Preferences preferences, SharedPreferences.Editor editor, ImportElement dataset, String encryptionKey) throws StorageImportExportException {
        try {

            String data = dataset.data.toString();
            List<Integer> accountNumbers = Account.getExistingAccountNumbers(preferences);
            Log.i(K9.LOG_TAG, "Existing accountNumbers = " + accountNumbers);
            /**
             *  We translate UUIDs in the import file into new UUIDs in the local instance for the following reasons:
             *  1) Accidentally importing the same file twice cannot damage settings in an existing account.
             *     (Say, an account that was imported two months ago and has since had significant settings changes.)
             *  2) All UUIDs used in the local instance will be generated with the same generator.
             *  3) Importing a single file multiple times allows for creating multiple accounts from the same template.
             *  4) Exporting an account and importing back into the same instance is a poor-man's account copy (until a real
             *     copy function is created, if ever)
             */
            Map<String, String> uuidMapping = new HashMap<String, String>();
            String accountUuids = preferences.getPreferences().getString("accountUuids", null);

            StringReader sr = new StringReader(data);
            BufferedReader br = new BufferedReader(sr);
            String line = null;
            int settingsImported = 0;
            int numAccounts = 0;
            K9Krypto krypto = new K9Krypto(encryptionKey, K9Krypto.MODE.DECRYPT);
            do {
                line = br.readLine();
                if (line != null) {
                    //Log.i(K9.LOG_TAG, "Got line " + line);
                    String[] comps = line.split(":");
                    if (comps.length > 1) {
                        String keyEnc = comps[0];
                        String valueEnc = comps[1];
                        String key = krypto.decrypt(keyEnc);
                        String value = krypto.decrypt(valueEnc);
                        String[] keyParts = key.split("\\.");
                        if (keyParts.length > 1) {
                            String oldUuid = keyParts[0];
                            String newUuid = uuidMapping.get(oldUuid);
                            if (newUuid == null) {
                                newUuid = UUID.randomUUID().toString();
                                uuidMapping.put(oldUuid, newUuid);

                                Log.i(K9.LOG_TAG, "Mapping oldUuid " + oldUuid + " to newUuid " + newUuid);
                            }
                            keyParts[0] = newUuid;
                            if ("accountNumber".equals(keyParts[1])) {
                                int accountNumber = Account.findNewAccountNumber(accountNumbers);
                                accountNumbers.add(accountNumber);
                                value = Integer.toString(accountNumber);
                                accountUuids += (accountUuids.length() != 0 ? "," : "") + newUuid;
                                numAccounts++;
                            }
                            StringBuilder builder = new StringBuilder();
                            for (String part : keyParts) {
                                if (builder.length() > 0) {
                                    builder.append(".");
                                }
                                builder.append(part);
                            }
                            key = builder.toString();
                        }
                        //Log.i(K9.LOG_TAG, "Setting " + key + " = " + value);
                        settingsImported++;
                        editor.putString(key, value);
                    }
                }

            } while (line != null);

            editor.putString("accountUuids", accountUuids);
            Log.i(K9.LOG_TAG, "Imported " + settingsImported + " settings and " + numAccounts + " accounts");
            return numAccounts;
        } catch (IOException ie) {
            throw new StorageImportExportException("Unable to import settings", ie);
        } catch (Exception e) {
            throw new StorageImportExportException("Unable to decrypt settings", e);
        }
    }

    @Override
    public boolean needsKey() {
        return true;
    }
}
