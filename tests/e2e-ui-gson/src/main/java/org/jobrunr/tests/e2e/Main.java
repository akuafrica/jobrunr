package org.jobrunr.tests.e2e;

import org.jobrunr.jobs.states.ScheduledState;
import org.jobrunr.storage.BackgroundJobServerStatus;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.h2.InMemoryStorageProvider;
import org.jobrunr.utils.mapper.gson.GsonJsonMapper;

import static java.time.Instant.now;
import static org.jobrunr.jobs.JobTestBuilder.aFailedJobThatEventuallySucceeded;
import static org.jobrunr.jobs.JobTestBuilder.aFailedJobWithRetries;
import static org.jobrunr.jobs.JobTestBuilder.aJob;
import static org.jobrunr.jobs.JobTestBuilder.aSucceededJob;
import static org.jobrunr.jobs.JobTestBuilder.anEnqueuedJob;
import static org.jobrunr.jobs.RecurringJobTestBuilder.aDefaultRecurringJob;

public class Main extends AbstractMain {

    public static void main(String[] args) throws Exception {
        new Main(args);
    }

    public Main(String[] args) throws Exception {
        super(args);
    }

    @Override
    protected StorageProvider initStorageProvider() {
        final InMemoryStorageProvider storageProvider = new InMemoryStorageProvider().withJsonMapper(new GsonJsonMapper());
        addDefaultData(storageProvider);
        return storageProvider;
    }

    // see https://github.com/eclipse/buildship/issues/991
    public void addDefaultData(StorageProvider storageProvider) {
        final BackgroundJobServerStatus backgroundJobServerStatus = new BackgroundJobServerStatus(10, 10);
        backgroundJobServerStatus.start();
        storageProvider.announceBackgroundJobServer(backgroundJobServerStatus);
        for (int i = 0; i < 33; i++) {
            storageProvider.save(anEnqueuedJob().build());
        }
        storageProvider.save(aJob().withState(new ScheduledState(now().plusSeconds(60L * 60 * 5))).build());
        storageProvider.save(aSucceededJob().build());
        storageProvider.save(aFailedJobWithRetries().build());
        storageProvider.save(aFailedJobThatEventuallySucceeded().build());
        storageProvider.saveRecurringJob(aDefaultRecurringJob().withId("import-sales-data").withName("Import all sales data at midnight").build());
        storageProvider.saveRecurringJob(aDefaultRecurringJob().withId("generate-sales-reports").withName("Generate sales report at 3am").withCronExpression("0 3 * * *").build());
    }
}
