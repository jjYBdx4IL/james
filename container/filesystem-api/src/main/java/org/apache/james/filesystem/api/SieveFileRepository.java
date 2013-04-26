/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.filesystem.api;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.james.managesieve.api.ConfigurationError;
import org.apache.james.managesieve.api.DuplicateException;
import org.apache.james.managesieve.api.DuplicateUserException;
import org.apache.james.managesieve.api.IsActiveException;
import org.apache.james.managesieve.api.ManageSieveError;
import org.apache.james.managesieve.api.QuotaExceededException;
import org.apache.james.managesieve.api.QuotaNotFoundException;
import org.apache.james.managesieve.api.ScriptNotFoundException;
import org.apache.james.managesieve.api.ScriptSummary;
import org.apache.james.managesieve.api.SieveRepository;
import org.apache.james.managesieve.api.StorageException;
import org.apache.james.managesieve.api.UserNotFoundException;

import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * <code>SieveFileRepository</code> manages sieve scripts stored on the file system.
 * <p>The sieve root directory is a sub-directory of the application base directory named "sieve".
 * Scripts are stored in sub-directories of the sieve root directory, each with the name of the
 * associated user.
 */
public class SieveFileRepository implements SieveRepository {

    private static final String SIEVE_ROOT = FileSystem.FILE_PROTOCOL + "sieve/";
    private static final String UTF_8 = "UTF-8";
    private static final String FILE_NAME_QUOTA = ".quota";
    private static final String FILE_NAME_ACTIVE = ".active";
    private static final List<String> SYSTEM_FILES = Arrays.asList(FILE_NAME_QUOTA, FILE_NAME_ACTIVE);
    private static final int MAX_BUFF_SIZE = 32768;

    private FileSystem _fileSystem = null;
    private final Object lock = new Object();

    /**
     * Read a file with the specified encoding into a String
     *
     * @param file
     * @param encoding
     * @return
     * @throws FileNotFoundException
     */
    static protected String toString(File file, String encoding) throws FileNotFoundException {
        String script = null;
        Scanner scanner = null;
        try {
            scanner = new Scanner(file, encoding).useDelimiter("\\A");
            script = scanner.next();
        } finally {
            if (null != scanner) {
                scanner.close();
            }
        }
        return script;
    }

    static protected void toFile(File file, String content) throws StorageException {
        // Create a temporary file
        int bufferSize = content.length() > MAX_BUFF_SIZE ? MAX_BUFF_SIZE : content.length();
        File tmpFile = null;
        Writer out = null;
        try {
            tmpFile = File.createTempFile(file.getName(), ".tmp", file.getParentFile());
            try {
                out = new OutputStreamWriter(new BufferedOutputStream(
                        new FileOutputStream(tmpFile), bufferSize), UTF_8);
                out.write(content);
            } catch (UnsupportedEncodingException ex1) {
                // UTF-8 must always be supported
                throw new ManageSieveError("Runtime must always support UTF-8", ex1);
            } finally {
                IOUtils.closeQuietly(out);
            }
        } catch (IOException ex) {
            FileUtils.deleteQuietly(tmpFile);
            throw new StorageException(ex);
        }

        // Does the file exist?
        // If so, make a backup
        File backupFile = new File(file.getParentFile(), file.getName() + ".bak");
        if (file.exists()) {
            try {
                FileUtils.copyFile(file, backupFile);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }

        // Copy the temporary file to its final name
        try {
            FileUtils.copyFile(tmpFile, file);
        } catch (IOException ex) {
            throw new StorageException(ex);
        }
        // Tidy up
        if (tmpFile.exists()) {
            FileUtils.deleteQuietly(tmpFile);
        }
        if (backupFile.exists()) {
            FileUtils.deleteQuietly(backupFile);
        }
    }

    /**
     * Creates a new instance of SieveFileRepository.
     */
    public SieveFileRepository() {
        super();
    }

    /**
     * Creates a new instance of SieveFileRepository.
     *
     * @param fileSystem
     */
    public SieveFileRepository(FileSystem fileSystem) {
        this();
        setFileSystem(fileSystem);
    }

    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        _fileSystem = fileSystem;
    }

    @Override
    public void deleteScript(final String user, final String name) throws UserNotFoundException,
            ScriptNotFoundException, IsActiveException, StorageException {
        synchronized (lock) {
            File file = getScriptFile(user, name);
            if (isActiveFile(user, file)) {
                throw new IsActiveException("User: " + user + "Script: " + name);
            }
            try {
                FileUtils.forceDelete(file);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    @Override
    public String getScript(final String user, final String name) throws UserNotFoundException,
            ScriptNotFoundException {
        String script;
        try {
            script = toString(getScriptFile(user, name), UTF_8);
        } catch (FileNotFoundException ex) {
            throw new ScriptNotFoundException(ex);
        }
        return script;
    }

    /**
     * The default quota, if any, is stored in file '.quota' in the sieve root directory. Quotas for
     * specific users are stored in file '.quota' in the user's directory.
     * <p/>
     * <p>The '.quota' file contains a single positive integer value representing the quota in octets.
     *
     * @see org.apache.james.managesieve.api.SieveRepository#haveSpace(java.lang.String, java.lang.String, long)
     */
    @Override
    public void haveSpace(final String user, final String name, final long size) throws UserNotFoundException,
            QuotaExceededException {
        long usedSpace = 0;
        for (File file : getUserDirectory(user).listFiles()) {
            if (!(file.getName().equals(name) || SYSTEM_FILES.contains(file.getName()))) {
                usedSpace = usedSpace + file.length();
            }
        }

        long quota = Long.MAX_VALUE;
        File file = getQuotaFile(user);
        if (!file.exists()) {
            file = getQuotaFile();
        }
        if (file.exists()) {
            Scanner scanner = null;
            try {
                scanner = new Scanner(file, UTF_8);
                quota = scanner.nextLong();
            } catch (FileNotFoundException ex) {
                // no op
            } catch (NoSuchElementException ex) {
                // no op
            } finally {
                if (null != scanner) {
                    scanner.close();
                }
            }
        }
        if ((usedSpace + size) > quota) {
            throw new QuotaExceededException(" Quota: " + quota + " Used: " + usedSpace
                    + " Requested: " + size);
        }
    }

    @Override
    public List<ScriptSummary> listScripts(final String user) throws UserNotFoundException {
        File[] files = getUserDirectory(user).listFiles();
        List<ScriptSummary> summaries = new ArrayList<ScriptSummary>(files.length);
        File activeFile = null;
        try {
            activeFile = getActiveFile(user);
        } catch (ScriptNotFoundException ex) {
            // no op
        }
        final File activeFile1 = activeFile;
        for (final File file : files) {
            if (!SYSTEM_FILES.contains(file.getName())) {
                summaries.add(new ScriptSummary() {

                    public String getName() {
                        return file.getName();
                    }

                    public boolean isActive() {
                        boolean isActive = false;
                        if (null != activeFile1) {
                            isActive = 0 == activeFile1.compareTo(file);
                        }
                        return isActive;
                    }
                });
            }
        }
        return summaries;
    }

    @Override
    public void putScript(final String user, final String name, final String content)
            throws UserNotFoundException, StorageException, QuotaExceededException {
        synchronized (lock) {
            File file = new File(getUserDirectory(user), name);
            haveSpace(user, name, content.length());
            toFile(file, content);
        }
    }

    @Override
    public void renameScript(final String user, final String oldName, final String newName)
            throws UserNotFoundException, ScriptNotFoundException,
            DuplicateException, StorageException {
        synchronized (lock) {
            File oldFile = getScriptFile(user, oldName);
            File newFile = new File(getUserDirectory(user), newName);
            if (newFile.exists()) {
                throw new DuplicateException("User: " + user + "Script: " + newName);
            }
            boolean isActive = isActiveFile(user, oldFile);
            try {
                FileUtils.copyFile(oldFile, newFile);
                if (isActive) {
                    setActiveFile(newFile, true);
                }
                FileUtils.forceDelete(oldFile);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    @Override
    public String getActive(final String user) throws UserNotFoundException,
            ScriptNotFoundException {
        String script;
        try {
            script = toString(getActiveFile(user), UTF_8);
        } catch (FileNotFoundException ex) {
            throw new ScriptNotFoundException(ex);
        }
        return script;
    }

    @Override
    public void setActive(final String user, final String name) throws UserNotFoundException,
            ScriptNotFoundException, StorageException {
        synchronized (lock) {
            // Turn off currently active script, if any
            File oldActive = null;
            try {
                oldActive = getActiveFile(user);
                setActiveFile(oldActive, false);
            } catch (ScriptNotFoundException ex) {
                // This is permissible
            }
            // Turn on the new active script if not an empty name
            if ((null != name) && (!name.trim().isEmpty())) {
                try {
                    setActiveFile(getScriptFile(user, name), true);
                } catch (ScriptNotFoundException ex) {
                    if (null != oldActive) {
                        setActiveFile(oldActive, true);
                    }
                    throw ex;
                }
            }
        }
    }

    protected File getSieveRootDirectory() {
        try {
            return _fileSystem.getFile(SIEVE_ROOT);
        } catch (FileNotFoundException ex1) {
            throw new ConfigurationError(ex1);
        }
    }

    protected File getUserDirectory(String user) throws UserNotFoundException {
        File file = getUserDirectoryFile(user);
        if (!file.exists()) {
            throw new UserNotFoundException("User: " + user);
        }
        return file;
    }

    protected File getUserDirectoryFile(String user) {
        return new File(getSieveRootDirectory(), user + '/');
    }

    protected File getActiveFile(String user) throws UserNotFoundException,
            ScriptNotFoundException {
        File dir = getUserDirectory(user);
        String content;
        try {
            content = toString(new File(dir, FILE_NAME_ACTIVE), UTF_8);
        } catch (FileNotFoundException ex) {
            throw new ScriptNotFoundException("There is no active script.");
        }
        return new File(dir, content);
    }

    protected boolean isActiveFile(String user, File file) throws UserNotFoundException {
        boolean isActive = false;
        try {
            isActive = 0 == getActiveFile(user).compareTo(file);
        } catch (ScriptNotFoundException ex) {
            // no op;
        }
        return isActive;
    }

    protected void setActiveFile(File activeFile, boolean isActive) throws StorageException {
        File file = new File(activeFile.getParentFile(), FILE_NAME_ACTIVE);
        if (isActive) {
            String content = activeFile.getName();
            toFile(file, content);
        } else {
            try {
                FileUtils.forceDelete(file);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    protected File getScriptFile(String user, String name) throws UserNotFoundException,
            ScriptNotFoundException {
        File file = new File(getUserDirectory(user), name);
        if (!file.exists()) {
            throw new ScriptNotFoundException("User: " + user + "Script: " + name);
        }
        return file;
    }


    @Override
    public boolean hasUser(final String user) {
        boolean userExists = true;
        try {
            getUserDirectory(user);
        } catch (UserNotFoundException ex) {
            userExists = false;
        }
        return userExists;
    }

    @Override
    public void addUser(final String user) throws DuplicateUserException, StorageException {
        synchronized (lock) {
            boolean userExists = true;
            try {
                getUserDirectory(user);
            } catch (UserNotFoundException ex) {
                userExists = false;
            }
            if (userExists) {
                throw new DuplicateUserException("User: " + user);
            }
            File dir = getUserDirectoryFile(user);
            try {
                FileUtils.forceMkdir(dir);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    @Override
    public void removeUser(final String user) throws UserNotFoundException, StorageException {
        synchronized (lock) {
            File dir = getUserDirectory(user);
            try {
                FileUtils.forceDelete(dir);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    protected File getQuotaFile() {
        return new File(getSieveRootDirectory(), FILE_NAME_QUOTA);
    }

    @Override
    public boolean hasQuota() {
        return getQuotaFile().exists();
    }

    @Override
    public long getQuota() throws QuotaNotFoundException {
        Long quota = null;
        File file = getQuotaFile();
        if (file.exists()) {
            Scanner scanner = null;
            try {
                scanner = new Scanner(file, UTF_8);
                quota = scanner.nextLong();
            } catch (FileNotFoundException ex) {
                // no op
            } catch (NoSuchElementException ex) {
                // no op
            } finally {
                if (null != scanner) {
                    scanner.close();
                }
            }
        }
        if (null == quota) {
            throw new QuotaNotFoundException("No default quota");
        }
        return quota;
    }

    @Override
    public synchronized void removeQuota() throws QuotaNotFoundException, StorageException {
        File file = getQuotaFile();
        if (!file.exists()) {
            throw new QuotaNotFoundException("No default quota");
        }
        try {
            FileUtils.forceDelete(file);
        } catch (IOException ex) {
            throw new StorageException(ex);
        }
    }

    @Override
    public synchronized void setQuota(final long quota) throws StorageException {
        File file = getQuotaFile();
        String content = Long.toString(quota);
        toFile(file, content);
    }

    protected File getQuotaFile(String user) throws UserNotFoundException {
        return new File(getUserDirectory(user), FILE_NAME_QUOTA);
    }

    @Override
    public boolean hasQuota(final String user) throws UserNotFoundException {
        return getQuotaFile(user).exists();
    }

    @Override
    public long getQuota(final String user) throws UserNotFoundException, QuotaNotFoundException {
        Long quota = null;
        File file = getQuotaFile(user);
        if (file.exists()) {
            Scanner scanner = null;
            try {
                scanner = new Scanner(file, UTF_8);
                quota = scanner.nextLong();
            } catch (FileNotFoundException ex) {
                // no op
            } catch (NoSuchElementException ex) {
                // no op
            } finally {
                if (null != scanner) {
                    scanner.close();
                }
            }
        }
        if (null == quota) {
            throw new QuotaNotFoundException("No quota for user: " + user);
        }
        return quota;
    }

    @Override
    public void removeQuota(final String user) throws UserNotFoundException,
            QuotaNotFoundException, StorageException {
        synchronized (lock) {
            File file = getQuotaFile(user);
            if (!file.exists()) {
                throw new QuotaNotFoundException("No quota for user: " + user);
            }
            try {
                FileUtils.forceDelete(file);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    @Override
    public void setQuota(final String user, final long quota) throws UserNotFoundException,
            StorageException {
        synchronized (lock) {
            File file = getQuotaFile(user);
            String content = Long.toString(quota);
            toFile(file, content);
        }
    }

}