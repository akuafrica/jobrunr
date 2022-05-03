package org.jobrunr.server.tasks;

import org.jobrunr.configuration.JobRunr;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.server.dashboard.DashboardNotificationManager;
import org.jobrunr.server.dashboard.NewJobRunrVersionNotification;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.utils.annotations.VisibleFor;
import org.jobrunr.utils.metadata.VersionRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jobrunr.utils.StringUtils.*;

public class CheckForNewJobRunrVersion implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckForNewJobRunrVersion.class);
    private static final Pattern versionPattern = Pattern.compile("\"latestVersion\"\\s*:\\s*\"([^,]*)\",");

    private final DashboardNotificationManager dashboardNotificationManager;
    private final StorageProvider storageProvider;
    private static boolean isFirstRun;
    private static boolean allowAnonymousDataUsage;

    public CheckForNewJobRunrVersion(BackgroundJobServer backgroundJobServer, boolean allowAnonymousDataUsage) {
        dashboardNotificationManager = backgroundJobServer.getDashboardNotificationManager();
        storageProvider = backgroundJobServer.getStorageProvider();

        CheckForNewJobRunrVersion.isFirstRun = true; // why: otherwise latest version API is spammed during testing
        CheckForNewJobRunrVersion.allowAnonymousDataUsage = allowAnonymousDataUsage;
    }

    @Override
    public void run() {
        if (isFirstRun) {
            final NewJobRunrVersionNotification newJobRunrVersionNotification = dashboardNotificationManager.getDashboardNotification(NewJobRunrVersionNotification.class);
            if (newJobRunrVersionNotification != null) {
                VersionNumber actualVersion = new VersionNumber(getActualVersion());
                VersionNumber latestVersion = new VersionNumber(newJobRunrVersionNotification.getLatestVersion());
                if (actualVersion.equals(latestVersion)) {
                    dashboardNotificationManager.deleteNotification(NewJobRunrVersionNotification.class);
                }
            }
        } else {
            try {
                VersionNumber latestVersion = new VersionNumber(getLatestVersion(storageProvider));
                VersionNumber actualVersion = new VersionNumber(getActualVersion());
                if (latestVersion.compareTo(actualVersion) > 0) {
                    dashboardNotificationManager.notify(new NewJobRunrVersionNotification(latestVersion.getCompleteVersion()));
                    LOGGER.info("JobRunr version {} is available.", latestVersion.completeVersion);
                } else {
                    dashboardNotificationManager.deleteNotification(NewJobRunrVersionNotification.class);
                }
            } catch (IOException e) {
                LOGGER.info("Unable to check for new JobRunr version:\n {}", e.getMessage());
            }
        }
        CheckForNewJobRunrVersion.isFirstRun = false;
    }

    @VisibleFor("testing")
    static String getLatestVersion(StorageProvider storageProvider) throws IOException {
        String url = "https://api.jobrunr.io/api/version/jobrunr/latest?currentVersion=" + getActualVersion();
        if (allowAnonymousDataUsage) {
            final String clusterId = storageProvider.getMetadata("id", "cluster").getValue();
            final long totalAmountOfSucceededJobs = storageProvider.getJobStats().getAllTimeSucceeded();
            url += "&clusterId=" + clusterId + "&succeededJobCount=" + totalAmountOfSucceededJobs;
        }
        URL apiUrl = new URL(url);
        HttpURLConnection con = (HttpURLConnection) apiUrl.openConnection();
        con.setRequestProperty("User-Agent", "JobRunr " + getActualVersion());
        con.setRequestMethod("GET");
        con.setConnectTimeout(2000);
        con.setReadTimeout(2000);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            Matcher matcher = versionPattern.matcher(content);
            final boolean versionIsFound = matcher.find();
            if (versionIsFound) {
                return matcher.group(1).replace("v", "");
            } else {
                throw new IOException("Github API has changed?");
            }
        } catch (UnknownHostException e) {
            throw e;
        } catch (IOException e) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                throw new IOException(content.toString());
            }
        }
    }

    @VisibleFor("testing")
    static String getActualVersion() {
        return VersionRetriever.getVersion(JobRunr.class);
    }

    static void resetCheckForNewVersion() {
        CheckForNewJobRunrVersion.isFirstRun = true;
    }

    protected static class VersionNumber implements Comparable<VersionNumber> {

        private final String completeVersion;
        private final String version;
        private final String qualifier;

        public VersionNumber(String completeVersion) {
            this.completeVersion = completeVersion;
            this.version = substringBefore(completeVersion, "-");
            this.qualifier = substringAfter(completeVersion, "-");
        }

        public String getCompleteVersion() {
            return completeVersion;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof VersionNumber) {
                return completeVersion.equals(((VersionNumber) obj).completeVersion);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(completeVersion);
        }

        @Override
        public int compareTo(VersionNumber o) {
            int versionsCompared = this.version.compareTo(o.version);
            if (versionsCompared == 0) {
                if (isNullOrEmpty(qualifier) && isNullOrEmpty(o.qualifier)) {
                    return 0;
                } else if (isNullOrEmpty(qualifier) && isNotNullOrEmpty(o.qualifier)) {
                    return 1;
                } else if (isNotNullOrEmpty(qualifier) && isNullOrEmpty(o.qualifier)) {
                    return -1;
                } else {
                    return qualifier.compareTo(o.qualifier);
                }
            } else {
                return versionsCompared;
            }
        }


    }
}
