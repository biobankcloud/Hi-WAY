/*******************************************************************************
 * In the Hi-WAY project we propose a novel approach of executing scientific
 * workflows processing Big Data, as found in NGS applications, on distributed
 * computational infrastructures. The Hi-WAY software stack comprises the func-
 * tional workflow language Cuneiform as well as the Hi-WAY ApplicationMaster
 * for Apache Hadoop 2.x (YARN).
 *
 * List of Contributors:
 *
 * Jörgen Brandt (HU Berlin)
 * Marc Bux (HU Berlin)
 * Ulf Leser (HU Berlin)
 *
 * Jörgen Brandt is funded by the European Commission through the BiobankCloud
 * project. Marc Bux is funded by the Deutsche Forschungsgemeinschaft through
 * research training group SOAMED (GRK 1651).
 *
 * Copyright 2014 Humboldt-Universität zu Berlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.huberlin.wbi.hiway.app;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationResponse;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.NodeState;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.QueueACL;
import org.apache.hadoop.yarn.api.records.QueueInfo;
import org.apache.hadoop.yarn.api.records.QueueUserACLInfo;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.api.records.YarnClusterMetrics;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.Records;

import de.huberlin.wbi.hiway.common.Constant;
import de.huberlin.wbi.hiway.common.Data;

/**
 * Hi-WAY Client for workflow submission to Hadoop YARN.
 */
public class Client {

	// a handle to the log, in which any events are recorded
	private static final Log log = LogFactory.getLog(Client.class);

	/**
	 * The main routine.
	 * 
	 * @param args
	 * Command line arguments passed to the Client.
	 */
	public static void main(String[] args) {
		boolean result = false;
		try {
			Client client = new Client();
			log.info("Initializing Client");
			try {
				boolean doRun = client.init(args);
				if (!doRun) {
					System.exit(0);
				}
			} catch (IllegalArgumentException e) {
				System.err.println(e.getLocalizedMessage());
				client.printUsage();
				System.exit(-1);
			}
			result = client.run();
		} catch (Throwable t) {
			log.fatal("Error running Client", t);
			System.exit(1);
		}
		if (result) {
			log.info("Application completed successfully");
			System.exit(0);
		}
		log.error("Application failed to complete successfully");
		System.exit(2);
	}

	// amount of memory resource to request for to run the App Master
	private int amMemory = 4096;

	// application master specific info to register a new Application with RM/ASM
	// the priority of the AM container
	private int amPriority = 0;
	// the queue to which this application is to be submitted in the RM
	private String amQueue = "";
	// ApplicationMaster jar file
//	private String appMasterJarPath = "";
	// main class to invoke application master
//	private final String appMasterMainClass;

	// start time for client
	private final long clientStartTime = System.currentTimeMillis();

	// timeout threshold for client. Kill app after time interval expires.
	private long clientTimeout;
	// the configuration of the Hadoop installation
	private Configuration conf;
//	private Configuration hiwayConf;

	private int containerCores = 1;
	// amount of memory and number of cores to request for containers in which workflow tasks will be executed
	private int containerMemory = 4096;

	// debug flag
	boolean debugFlag = false;

	// command line options
	private Options opts;
	// the type of workflow scheduler
	private Constant.SchedulingPolicy scheduler;

	// workflow task container priority
	private int shellCmdPriority = 0;
	// environment variables to be setup for the workflow tasks
	private Map<String, String> shellEnv = new HashMap<String, String>();

	// the workflow format and its path in the file system
	private String workflowPath;

	private Constant.WorkflowFormat workflowType;

	// a handle to the YARN ApplicationsManager (ASM)
	private YarnClient yarnClient;

	public Client() throws Exception {
		this(new YarnConfiguration());
		conf.addResource("core-site.xml");
	}

	public Client(Configuration conf) {
		this.conf = conf;
//		hiwayConf = new HiWayConfiguration();
		yarnClient = YarnClient.createYarnClient();
		yarnClient.init(conf);
		opts = new Options();
		opts.addOption("priority", true, "Application Priority. Default 0");
		opts.addOption("queue", true, "RM Queue in which this application is to be submitted");
		opts.addOption("timeout", true, "Application timeout in milliseconds. Default: 1 day.");
		opts.addOption("master_memory", true,
				"Amount of memory in MB to be requested to run the application master. Default 4096");
//		opts.addOption("jar", true, "Jar file containing the application master");
		opts.addOption("shell_env", true, "Environment for shell script. Specified as env_key=env_val pairs");
		opts.addOption("shell_cmd_priority", true, "Priority for the shell command containers");
		opts.addOption("container_memory", true,
				"Amount of memory in MB to be requested to run the shell command. Default 4096");
		opts.addOption("container_vcores", true,
				"Number of virtual vores to be requested to run the shell command. Default 1");
		opts.addOption("workflow", true, "The workflow file to be executed by the Application Master");
		String workflowFormats = "";
		for (Constant.WorkflowFormat format : Constant.WorkflowFormat.values()) {
			workflowFormats += ", " + format.toString();
		}
		opts.addOption("type", true, "The input file format. Valid arguments: " + workflowFormats.substring(2));
		String schedulingPolicies = "";
		for (Constant.SchedulingPolicy policy : Constant.SchedulingPolicy.values()) {
			schedulingPolicies += ", " + policy.toString();
		}
		opts.addOption("scheduler", true,
				"The workflow scheduling policy. Valid arguments: " + schedulingPolicies.substring(2));
		opts.addOption("debug", false, "Dump out debug information");
		opts.addOption("help", false, "Print usage");
	}

	/**
	 * Kill a submitted application by sending a call to the ASM.
	 * 
	 * @param appId
	 * Application Id to be killed.
	 * 
	 * @throws YarnException
	 * @throws IOException
	 */
	private void forceKillApplication(ApplicationId appId) throws YarnException, IOException {
		// Response can be ignored as it is non-null on success or throws an exception in case of failures
		yarnClient.killApplication(appId);
	}

	/**
	 * Parse command line options.
	 * 
	 * @param args
	 * Parsed command line options.
	 * @return Whether the init was successful to run the client.
	 * @throws ParseException
	 */
	public boolean init(String[] args) throws ParseException {

		CommandLine cliParser = new GnuParser().parse(opts, args);

		if (args.length == 0) {
			throw new IllegalArgumentException("No args specified for client to initialize");
		}

		if (cliParser.hasOption("help")) {
			printUsage();
			return false;
		}

		if (cliParser.hasOption("debug")) {
			debugFlag = true;
		}

		amPriority = Integer.parseInt(cliParser.getOptionValue("priority", "0"));
		amQueue = cliParser.getOptionValue("queue", "default");
		amMemory = Integer.parseInt(cliParser.getOptionValue("master_memory", "4096"));

		if (amMemory < 0) {
			throw new IllegalArgumentException("Invalid memory specified for application master, exiting."
					+ " Specified memory=" + amMemory);
		}

//		if (!cliParser.hasOption("jar")) {
//			throw new IllegalArgumentException("No jar file specified for application master");
//		}
//		appMasterJarPath = cliParser.getOptionValue("jar");
//		if (appMasterJarPath.startsWith("/")) {
//			throw new IllegalArgumentException("Only relative paths supported");
//		}
//		if (appMasterJarPath.contains("./")) {
//			throw new IllegalArgumentException("./ and ../ not allowed in path");
//		}

		if (!cliParser.hasOption("workflow")) {
			throw new IllegalArgumentException("No workflow file specified to be executed by application master");
		}

		workflowPath = cliParser.getOptionValue("workflow");
		if (workflowPath.startsWith("/")) {
			throw new IllegalArgumentException("Only relative paths supported");
		}
		if (workflowPath.contains("./")) {
			throw new IllegalArgumentException("./ and ../ not allowed in path");
		}
		workflowType = Constant.WorkflowFormat.valueOf(cliParser.getOptionValue("type",
				Constant.WorkflowFormat.cuneiform.toString()));
		scheduler = Constant.SchedulingPolicy.valueOf(cliParser.getOptionValue("scheduler",
				Constant.SchedulingPolicy.c3po.toString()));

		if (cliParser.hasOption("shell_env")) {
			String envs[] = cliParser.getOptionValues("shell_env");
			for (String env : envs) {
				env = env.trim();
				int index = env.indexOf('=');
				if (index == -1) {
					shellEnv.put(env, "");
					continue;
				}
				String key = env.substring(0, index);
				String val = "";
				if (index < (env.length() - 1)) {
					val = env.substring(index + 1);
				}
				shellEnv.put(key, val);
			}
		}
		shellCmdPriority = Integer.parseInt(cliParser.getOptionValue("shell_cmd_priority", "0"));

		containerMemory = Integer.parseInt(cliParser.getOptionValue("container_memory", "4096"));
		containerCores = Integer.parseInt(cliParser.getOptionValue("container_vcores", "1"));

		clientTimeout = Integer
				.parseInt(cliParser.getOptionValue("timeout", Integer.toString(1 * 24 * 60 * 60 * 1000)));

		return true;
	}

	/**
	 * Monitor the submitted application for completion. Kill application if time expires.
	 * 
	 * @param appId
	 * Application Id of application to be monitored
	 * @return true if application completed successfully
	 * @throws YarnException
	 * @throws IOException
	 */
	private boolean monitorApplication(ApplicationId appId) throws YarnException, IOException {
		while (true) {
			// Check app status every 1 second.
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				log.debug("Thread sleep in monitoring loop interrupted");
			}

			// Get application report for the appId we are interested in
			ApplicationReport report = yarnClient.getApplicationReport(appId);

			YarnApplicationState state = report.getYarnApplicationState();
			FinalApplicationStatus dsStatus = report.getFinalApplicationStatus();
			if (YarnApplicationState.FINISHED == state) {
				if (FinalApplicationStatus.SUCCEEDED == dsStatus) {
					log.info("Application has completed successfully. Breaking monitoring loop");
					log.info(report.getDiagnostics());
					return true;
				} else {
					log.info("Application did finish unsuccessfully." + " YarnState=" + state.toString()
							+ ", DSFinalStatus=" + dsStatus.toString() + ". Breaking monitoring loop");
					return false;
				}
			} else if (YarnApplicationState.KILLED == state || YarnApplicationState.FAILED == state) {
				log.info("Application did not finish." + " YarnState=" + state.toString() + ", DSFinalStatus="
						+ dsStatus.toString() + ". Breaking monitoring loop");
				return false;
			}

			if (System.currentTimeMillis() > (clientStartTime + clientTimeout)) {
				log.info("Reached client specified timeout for application. Killing application");
				forceKillApplication(appId);
				return false;
			}
		}

	}

	/**
	 * Helper function to print out usage.
	 */
	private void printUsage() {
		new HelpFormatter().printHelp("Client", opts);
	}

	/**
	 * Main run function for the client.
	 * 
	 * @return true if application completed successfully.
	 * @throws IOException
	 * @throws YarnException
	 */
	public boolean run() throws IOException, YarnException {
		log.info("Running Client");
		yarnClient.start();

		YarnClusterMetrics clusterMetrics = yarnClient.getYarnClusterMetrics();
		log.info("Got Cluster metric info from ASM" + ", numNodeManagers=" + clusterMetrics.getNumNodeManagers());

		List<NodeReport> clusterNodeReports = yarnClient.getNodeReports(NodeState.RUNNING);
		log.info("Got Cluster node info from ASM");
		for (NodeReport node : clusterNodeReports) {
			log.info("Got node report from ASM for" + ", nodeId=" + node.getNodeId() + ", nodeAddress"
					+ node.getHttpAddress() + ", nodeRackName" + node.getRackName() + ", nodeNumContainers"
					+ node.getNumContainers());
		}

		QueueInfo queueInfo = yarnClient.getQueueInfo(this.amQueue);
		log.info("Queue info" + ", queueName=" + queueInfo.getQueueName() + ", queueCurrentCapacity="
				+ queueInfo.getCurrentCapacity() + ", queueMaxCapacity=" + queueInfo.getMaximumCapacity()
				+ ", queueApplicationCount=" + queueInfo.getApplications().size() + ", queueChildQueueCount="
				+ queueInfo.getChildQueues().size());

		List<QueueUserACLInfo> listAclInfo = yarnClient.getQueueAclsInfo();
		for (QueueUserACLInfo aclInfo : listAclInfo) {
			for (QueueACL userAcl : aclInfo.getUserAcls()) {
				log.info("User ACL Info for Queue" + ", queueName=" + aclInfo.getQueueName() + ", userAcl="
						+ userAcl.name());
			}
		}

		// Get a new application id
		YarnClientApplication app = yarnClient.createApplication();
		GetNewApplicationResponse appResponse = app.getNewApplicationResponse();

		// Get min/max resource capabilities from RM and change memory ask if needed
		int maxMem = appResponse.getMaximumResourceCapability().getMemory();
		log.info("Max mem capabililty of resources in this cluster " + maxMem);

		// A resource ask cannot exceed the max.
		if (amMemory > maxMem) {
			log.info("AM memory specified above max threshold of cluster. Using max value." + ", specified=" + amMemory
					+ ", max=" + maxMem);
			amMemory = maxMem;
		}

		// set the application name
		ApplicationSubmissionContext appContext = app.getApplicationSubmissionContext();
		appContext.setApplicationType(Constant.APPLICATION_TYPE);
		appContext.setApplicationName("run " + workflowPath + " (type: " + workflowType.toString() + ", scheduler: "
				+ scheduler.toString() + ")");
		ApplicationId appId = appContext.getApplicationId();
		Data.setHdfsDirectoryPrefix(Constant.SANDBOX_DIRECTORY + "/" + appId);

		// Set up the container launch context for the application master
		ContainerLaunchContext amContainer = Records.newRecord(ContainerLaunchContext.class);

		// set local resources for the application master
		Map<String, LocalResource> localResources = new HashMap<String, LocalResource>();

		// Copy the application master jar to the filesystem
		log.info("Copy App Master jar from local filesystem and add to local environment");
		FileSystem fs = FileSystem.get(conf);

//		URI amJar;
//		try {
//			amJar = new URI(hiwayConf.get(HiWayConfiguration.HIWAY_AM_URI));
//		} catch (URISyntaxException e) {
//			throw new RuntimeException(e);
//		}
//		Path amJarPath = new Path(amJar);
//
//		LocalResource rsrc = Records.newRecord(LocalResource.class);
//		rsrc.setType(LocalResourceType.FILE);
//		rsrc.setVisibility(LocalResourceVisibility.APPLICATION);
//		rsrc.setResource(ConverterUtils.getYarnUrlFromPath(amJarPath));
//
//		FileStatus status = fs.getFileStatus(amJarPath);
//		rsrc.setTimestamp(status.getModificationTime());
//		rsrc.setSize(status.getLen());
//
//		localResources.put(amJarPath.getName(), rsrc);

		Data workflow = new Data(workflowPath);
		workflow.stageOut(fs, "");

		// set local resource info into app master container launch context
		amContainer.setLocalResources(localResources);

		// set the env variables to be setup in the env where the application master will be run
		log.info("Set the environment for the application master");
		Map<String, String> env = new HashMap<String, String>();

		StringBuilder classPathEnv = new StringBuilder(Environment.CLASSPATH.$()).append(File.pathSeparatorChar)
				.append("./*");
		for (String c : conf.getStrings(YarnConfiguration.YARN_APPLICATION_CLASSPATH,
				YarnConfiguration.DEFAULT_YARN_APPLICATION_CLASSPATH)) {
			classPathEnv.append(':');
			classPathEnv.append(File.pathSeparatorChar);
			classPathEnv.append(c.trim());
		}
		classPathEnv.append(File.pathSeparatorChar).append("./log4j.properties");

		if (conf.getBoolean(YarnConfiguration.IS_MINI_YARN_CLUSTER, false)) {
			classPathEnv.append(':');
			classPathEnv.append(System.getProperty("java.class.path"));
		}

		env.put("CLASSPATH", classPathEnv.toString());

		amContainer.setEnvironment(env);

		// Set the necessary command to execute the application master
		Vector<CharSequence> vargs = new Vector<CharSequence>(30);

		// Set java executable command
		log.info("Setting up app master command");
		vargs.add(Environment.JAVA_HOME.$() + "/bin/java");
		// Set Xmx based on am memory size
		vargs.add("-Xmx" + amMemory + "m");
		// Set class name
		if (workflowType.equals(Constant.WorkflowFormat.dax)) {
			vargs.add(HiWayConfiguration.HIWAY_DAX_AM_CLASS);
		} else {
			vargs.add(HiWayConfiguration.HIWAY_CF_AM_CLASS);
		}
		
		// Set params for Application Master
		vargs.add("--container_memory " + String.valueOf(containerMemory));
		vargs.add("--container_vcores " + String.valueOf(containerCores));
		vargs.add("--workflow " + workflowPath);
		vargs.add("--type " + workflowType.toString());
		vargs.add("--scheduler " + scheduler.toString());
		vargs.add("--appid " + appId.toString());
		vargs.add("--priority " + String.valueOf(shellCmdPriority));

		for (Map.Entry<String, String> entry : shellEnv.entrySet()) {
			vargs.add("--shell_env " + entry.getKey() + "=" + entry.getValue());
		}
		if (debugFlag) {
			vargs.add("--debug");
		}

		vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/AppMaster.stdout");
		vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/AppMaster.stderr");

		// Get final command
		StringBuilder command = new StringBuilder();
		for (CharSequence str : vargs) {
			command.append(str).append(" ");
		}

		log.info("Completed setting up app master command " + command.toString());
		List<String> commands = new ArrayList<String>();
		commands.add(command.toString());
		amContainer.setCommands(commands);

		// Set up resource type requirements
		Resource capability = Records.newRecord(Resource.class);
		capability.setMemory(amMemory);
		appContext.setResource(capability);

		// Setup security tokens
		if (UserGroupInformation.isSecurityEnabled()) {
			Credentials credentials = new Credentials();
			String tokenRenewer = conf.get(YarnConfiguration.RM_PRINCIPAL);
			if (tokenRenewer == null || tokenRenewer.length() == 0) {
				throw new IOException("Can't get Master Kerberos principal for the RM to use as renewer");
			}

			// For now, only getting tokens for the default file-system.
			final Token<?> tokens[] = fs.addDelegationTokens(tokenRenewer, credentials);
			if (tokens != null) {
				for (Token<?> token : tokens) {
					log.info("Got dt for " + fs.getUri() + "; " + token);
				}
			}
			DataOutputBuffer dob = new DataOutputBuffer();
			credentials.writeTokenStorageToStream(dob);
			ByteBuffer fsTokens = ByteBuffer.wrap(dob.getData(), 0, dob.getLength());
			amContainer.setTokens(fsTokens);
		}

		appContext.setAMContainerSpec(amContainer);

		// Set the priority for the application master
		Priority pri = Records.newRecord(Priority.class);
		pri.setPriority(amPriority);
		appContext.setPriority(pri);

		// Set the queue to which this application is to be submitted in the RM
		appContext.setQueue(amQueue);

		// Submit the application to the applications manager
		log.info("Submitting application to ASM");
		yarnClient.submitApplication(appContext);

		// Monitor the application
		boolean success = monitorApplication(appId);

		return success;

	}

}