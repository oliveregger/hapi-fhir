package ca.uhn.fhir.jpa.bulk;

import ca.uhn.fhir.jpa.api.config.DaoConfig;
import ca.uhn.fhir.jpa.api.model.BulkExportJobResults;
import ca.uhn.fhir.jpa.api.svc.IBatch2JobRunner;
import ca.uhn.fhir.jpa.batch.models.Batch2JobStartResponse;
import ca.uhn.fhir.jpa.provider.BaseResourceProviderR4Test;
import ca.uhn.fhir.jpa.util.BulkExportUtils;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.server.bulk.BulkDataExportOptions;
import ca.uhn.fhir.util.JsonUtil;
import com.google.common.collect.Sets;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Group;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A test to poke at our job framework and induce errors.
 */
public class BulkDataErrorAbuseTest extends BaseResourceProviderR4Test {
	private static final Logger ourLog = LoggerFactory.getLogger(BulkDataExportTest.class);

	@Autowired
	private DaoConfig myDaoConfig;

	@Autowired
	private IBatch2JobRunner myJobRunner;

	@AfterEach
	void afterEach() {
		myDaoConfig.setIndexMissingFields(DaoConfig.IndexEnabledEnum.DISABLED);
	}

	AtomicBoolean myRunningFlag = new AtomicBoolean(true);
	@Test
	public void testGroupBulkExportNotInGroup_DoesNotShowUp() throws InterruptedException {
		// Create some resources
		Patient patient = new Patient();
		patient.setId("PING1");
		patient.setGender(Enumerations.AdministrativeGender.FEMALE);
		patient.setActive(true);
		myClient.update().resource(patient).execute();

		patient = new Patient();
		patient.setId("PING2");
		patient.setGender(Enumerations.AdministrativeGender.MALE);
		patient.setActive(true);
		myClient.update().resource(patient).execute();

		patient = new Patient();
		patient.setId("PNING3");
		patient.setGender(Enumerations.AdministrativeGender.MALE);
		patient.setActive(true);
		myClient.update().resource(patient).execute();

		Group group = new Group();
		group.setId("Group/G2");
		group.setActive(true);
		group.addMember().getEntity().setReference("Patient/PING1");
		group.addMember().getEntity().setReference("Patient/PING2");
		myClient.update().resource(group).execute();

		// set the export options
		BulkDataExportOptions options = new BulkDataExportOptions();
		options.setResourceTypes(Sets.newHashSet("Patient"));
		options.setGroupId(new IdType("Group", "G2"));
		options.setFilters(new HashSet<>());
		options.setExportStyle(BulkDataExportOptions.ExportStyle.GROUP);
		options.setOutputFormat(Constants.CT_FHIR_NDJSON);

		BlockingQueue<Runnable> workQueue = new SynchronousQueue<>(); //new LinkedBlockingQueue<>(5);
		ExecutorService executorService = new ThreadPoolExecutor(10, 10,
			0L, TimeUnit.MILLISECONDS,
			workQueue);

		ourLog.info("starting loop");
		while(myRunningFlag.get()) {
			try {
				executorService.submit(()->{
					String jobId = null;
					try {
						jobId = startJob(options);

						// Run a scheduled pass to build the export
						myBatch2JobHelper.awaitJobCompletion(jobId, 60);


						verifyBulkExportResults(jobId, List.of("Patient/PING1", "Patient/PING2"), Collections.singletonList("Patient/PNING3"));
					} catch (Throwable theError) {
						ourLog.error("Winner winner! {}", jobId,  theError);
						if (myRunningFlag.get()) {
							// something bad happened
							// shutdown the pool
							// break;
							myRunningFlag.set(false);
							ourLog.info("break the loop");
							ourLog.info("shutdown started");
							executorService.shutdown();
						}
					}
				});
			} catch (RejectedExecutionException  e) {
				// we must be shutting down.
				//ourLog.info("Failed to submit, probably shutting down now {} {} ", executorService.isShutdown(), e.getMessage());
			}
		}
		ourLog.info("awaitTermination");
		executorService.awaitTermination(30, TimeUnit.SECONDS);
		ourLog.info("shutdown complete");
		ourLog.info("break from loop.");
		fail("Something broke, so we finished.");
	}


	private void verifyBulkExportResults(String theJobId, List<String> theContainedList, List<String> theExcludedList) {
		// Iterate over the files
		String report = myJobRunner.getJobInfo(theJobId).getReport();
		ourLog.debug("Export job {} report: {}", theJobId, report);
		if (!theContainedList.isEmpty()) {
			assertThat(report, not(emptyOrNullString()));
		}
		BulkExportJobResults results = JsonUtil.deserialize(report, BulkExportJobResults.class);

		Set<String> foundIds = new HashSet<>();
		for (Map.Entry<String, List<String>> file : results.getResourceTypeToBinaryIds().entrySet()) {
			String resourceType = file.getKey();
			List<String> binaryIds = file.getValue();
			for (var nextBinaryId : binaryIds) {

				Binary binary = myBinaryDao.read(new IdType(nextBinaryId), mySrd);
				assertEquals(Constants.CT_FHIR_NDJSON, binary.getContentType());

				String nextNdJsonFileContent = new String(binary.getContent(), Constants.CHARSET_UTF8);
				ourLog.trace("Export job {} file {} contents: {}", theJobId, nextBinaryId, nextNdJsonFileContent);

				List<String> lines = new BufferedReader(new StringReader(nextNdJsonFileContent))
					.lines().toList();
				ourLog.debug("Export job {} file {} line-count: {}", theJobId, nextBinaryId, lines.size());

				lines.stream()
					.map(line->myFhirContext.newJsonParser().parseResource(line))
					.map(r->r.getIdElement().toUnqualifiedVersionless())
					.forEach(nextId->{
						if (!resourceType.equals(nextId.getResourceType())) {
							fail("Found resource of type " + nextId.getResourceType() + " in file for type " + resourceType);
						} else {
							if (!foundIds.add(nextId.getValue())) {
								fail("Found duplicate ID: " + nextId.getValue());
							}
						}
					});
			}
		}

		ourLog.debug("Export job {} exported resources {}", theJobId, foundIds);

		for (String containedString : theContainedList) {
			assertThat(foundIds, hasItem(containedString));
		}
		for (String excludedString : theExcludedList) {
			assertThat(foundIds, not(hasItem(excludedString)));
		}
	}

	private String startJob(BulkDataExportOptions theOptions) {
		Batch2JobStartResponse startResponse = myJobRunner.startNewJob(BulkExportUtils.createBulkExportJobParametersFromExportOptions(theOptions));
		assertNotNull(startResponse);
		return startResponse.getJobId();
	}

}
