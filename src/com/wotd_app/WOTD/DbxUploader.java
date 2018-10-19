package com.wotd_app.WOTD;
/**
 * Copyright (c) 2013, iquesters - All Rights Reserved.
 */
/*
Comment by the ENS491-2 Group:
This is a Dropbox Uploader class that we used for uploading the file
The document provides usage of this class
Pulled from: https://github.com/iquesters/software/blob/master/framework/src/org/iq/test/DbxUploader.java
*/

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Locale;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxAuthInfo;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWebAuthNoRedirect;
import com.dropbox.core.DbxWriteMode;
import com.dropbox.core.json.JsonReader;
import com.dropbox.core.json.JsonReader.FileLoadException;

/**
 * A simple utility class for dropbox uploading.
 * Use the {@link #DbxUploader(String, String) Auth file constructor} for the first time,
 * and the {@link #DbxUploader(String) Auth token file constructor} for any subsequent calls
 *
 */
public class DbxUploader {

    private DbxClient _dbxClient;
    //private String authTokenFilename;
    /**
     * Create a new DbxUploader
     * @param authFilename a path to your JSON file. It must of the form:
    {
    "key": "YOUR_KEY",
    "secret": "YOUR_SECRET"
    }
     * @throws FileLoadException
     * @throws IOException
     */
    public DbxUploader(String authFilename, String authTokenOutputFilename) throws FileLoadException, IOException {
        // Read app info file (contains app key and app secret)
        DbxAppInfo appInfo;
        try {
            appInfo = DbxAppInfo.Reader.readFromFile(authFilename);
        }
        catch (JsonReader.FileLoadException ex) {
            System.err.println("Error reading <app-info-file>: " + ex.getMessage());
            System.exit(1); return;
        }

        // Run through Dropbox API authorization process
        String userLocale = Locale.getDefault().toString();
        DbxRequestConfig requestConfig = new DbxRequestConfig("DbxUploader", userLocale);
        DbxWebAuthNoRedirect webAuth = new DbxWebAuthNoRedirect(requestConfig, appInfo);

        String authorizeUrl = webAuth.start();
        System.out.println("1. Go to " + authorizeUrl);
        System.out.println("2. Click \"Allow\" (you might have to log in first).");
        System.out.println("3. Copy the authorization code.");
        System.out.print("Enter the authorization code here: ");

        String code = new BufferedReader(new InputStreamReader(System.in)).readLine();
        if (code == null) {
            System.exit(1); return;
        }
        code = code.trim();

        DbxAuthFinish authFinish;
        try {
            authFinish = webAuth.finish(code);
        }
        catch (DbxException ex) {
            System.err.println("Error in DbxWebAuth.start: " + ex.getMessage());
            System.exit(1); return;
        }

        System.out.println("Authorization complete.");
        System.out.println("- User ID: " + authFinish.userId);
        System.out.println("- Access Token: " + authFinish.accessToken);

        _dbxClient = new DbxClient(requestConfig, authFinish.accessToken);

        // Save auth information to output file.
        DbxAuthInfo authInfo = new DbxAuthInfo(authFinish.accessToken, appInfo.host);

        try {
            DbxAuthInfo.Writer.writeToFile(authInfo, authTokenOutputFilename);
            System.out.println("Saved authorization information to \"" + authTokenOutputFilename + "\".");
        }
        catch (IOException ex) {
            System.err.println("Error saving to <auth-file-out>: " + ex.getMessage());
            System.err.println("Dumping to stderr instead:");
            DbxAuthInfo.Writer.writeToStream(authInfo, System.out);
            System.exit(1); return;
        }

    }


    /**
     *
     * @param authTokenFile Path to the auth token JSON file.
     * @throws FileLoadException
     * @throws IOException
     */
    public DbxUploader(String authTokenFile) throws FileLoadException, IOException {

        DbxAuthInfo authInfo;
        try {

            authInfo = DbxAuthInfo.Reader.readFromFile(authTokenFile);
        }
        catch (JsonReader.FileLoadException ex) {
            System.err.println("Error in DbxUploader constructor: problem loading <auth-file>: " + ex.getMessage());
            throw ex;
        }

        // Create a DbxClient, which is what you use to make API calls.
        String userLocale = Locale.getDefault().toString();
        DbxRequestConfig requestConfig = new DbxRequestConfig("DbxUploader", userLocale);
        _dbxClient = new DbxClient(requestConfig, authInfo.accessToken, authInfo.host);
    }

    /**
     * Upload a file to your Dropbox
     * @param srcFilename path to the source file to be uploaded e.g. /tmp/upload.txt
     * @param destFilename path to the destination. Must start with '/' and must NOT end with '/'
     *  e.g. /target_dir/upload.txt
     * @throws IOException
     */
    public void upload (String srcFilename, String destFilename) throws IOException {

        File uploadFile = new File (srcFilename);
        FileInputStream uploadFIS;
        try {
            uploadFIS = new FileInputStream(uploadFile);
        }
        catch (FileNotFoundException e1) {
            e1.printStackTrace();
            System.err.println("Error in upload(): problem opening " + srcFilename);
            return;
        }

        String targetPath = destFilename;
        try {
            _dbxClient.uploadFile(targetPath, DbxWriteMode.add(), uploadFile.length(), uploadFIS);
        }

        catch (DbxException e) {
            e.printStackTrace();
            uploadFIS.close();
            System.err.println("Error in upload(): " + e.getMessage());
            System.exit(1); return;
        }
    }

    public static void main (String[] args) {

        String srcFilename = args[0];
        String destFilename = args[1];
        String authFilename = null;
        String authTokenFilename = null;
        String authTokenOutputFilename = null;

        boolean isAuthTokenReady = false;

        if (args.length == 3) {
            authTokenFilename = args[2];
            authFilename = null;
            authTokenOutputFilename = null;
            isAuthTokenReady = true;
        }

        else if (args.length == 4) {
            authFilename  = args[2];
            authTokenOutputFilename = args[3];
            authTokenFilename = null;
            isAuthTokenReady = false;
        }

        else  {
            printUsage();
            System.exit(1);
        }

        DbxUploader dbxUp;
        try {
            if (isAuthTokenReady)
                dbxUp = new DbxUploader(authTokenFilename);
            else
                dbxUp = new DbxUploader(authFilename, authTokenOutputFilename);

            dbxUp.upload(srcFilename, destFilename);
        }

        catch (FileLoadException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }


    private static void printUsage() {
        PrintStream out = System.out;
        out.println("Usage: DbxUploader <upload-file-src> <upload-file-dest> <app-info-file> <auth-file-output>");
        out.println("Usage: DbxUploader <upload-file-src> <upload-file-dest> <auth-token-file> ");
        out.println("");
        out.println("<app-info-file>: a JSON file with information about your API app.  Example:");
        out.println("");
        out.println("  {");
        out.println("    \"key\": \"Your Dropbox API app key...\",");
        out.println("    \"secret\": \"Your Dropbox API app secret...\"");
        out.println("  }");
        out.println("");
        out.println("  Get an API app key by registering with Dropbox:");
        out.println("    https://dropbox.com/developers/apps");
        out.println("");
        out.println("<auth-file-output>: If authorization is successful, the resulting API");
        out.println("  access token will be saved to this file, which can then be used with");
        out.println("  other example programs.");
        out.println("<auth-token-file>: The file containing the authorization token.");

        out.println("");

    }

}