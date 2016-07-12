package com.hawkprime.jms;

import java.io.IOException;

import javax.jms.JMSException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main.
 */
public final class JmsPublisher {
	private static final Logger LOG = LoggerFactory.getLogger(JmsPublisher.class);
	private static final String SERVER_OPT = "server";
	private static final String SSL_PASS_OPT = "ssl-pass";
	private static final String SSL_CLIENT_OPT = "ssl-client-key";
	private static final String SSL_CA_OPT = "ssl-ca";
	private static final String SSL_SERVER_OPT = "ssl-server-key";
	private static final String FILE_ARG = "file";

	private JmsPublisher() {
		/* empty */
	}

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 * @throws JMSException JMS exception
	 */
	// CHECKSTYLE IGNORE UncommentedMain
	public static void main(final String[] args) throws JMSException {
		LOG.info("TibCo Message Publisher v1.1-m075878");

		CommandLine cmd = parseCommandLine(args);

		final TibcoQueue tibcoQueue = new TibcoQueue(
					cmd.getOptionValue(SERVER_OPT),
					cmd.getOptionValue("user"),
					cmd.getOptionValue("pass"),
					cmd.getOptionValue("queue"));

		if (isSSLConnection(cmd.getOptionValue(SERVER_OPT))) {
			tibcoQueue.setSSLSettings(
					cmd.getOptionValue(SSL_PASS_OPT),
					cmd.getOptionValue(SSL_CLIENT_OPT),
					cmd.getOptionValue(SSL_CA_OPT),
					cmd.getOptionValue(SSL_SERVER_OPT));
		}

		try {
			tibcoQueue.connect();
		} catch (JMSException ex) {
			LOG.error("\nUnable to connect to TibCo server: \"{}\"", ex.getMessage());
			return;
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				tibcoQueue.close();
				LOG.info("Done.");
			}
		});

		try {

			final String extension =cmd.getOptionValue("extension", ".xml");

			DirectoryWatcher watcher = new DirectoryWatcher(cmd.getOptionValue("source"), extension);

			FileProcessor fileProcessor = new FileProcessor(tibcoQueue,
					watcher.getDirectory(), cmd.getOptionValue("target"), cmd.hasOption("headers"));

			watcher.setInterval(5);
			watcher.addListener(fileProcessor);

			LOG.info("Watching directory \"{}\" for {} files", watcher.getDirectory(), extension);
			LOG.info("Moving proccessed files to \"{}\"", fileProcessor.getTarget());

			watcher.start();

		} catch (IOException ex) {
			LOG.error(ex.getMessage());
		}

	}

	private static CommandLine parseCommandLine(final String[] args) {
		Options options = new Options();

		options.addOption(Option.builder("s")
			.argName("url")
			.longOpt(SERVER_OPT)
			.hasArg()
			.required()
			.desc("TibCo server URL: \"tcp://192.168.56.202:7222\"")
			.build());

		options.addOption(Option.builder("e")
				.argName("file extension")
				.longOpt("extension")
				.hasArg()
				.desc("Files to pickup (default: .xml)")
				.build());

		options.addOption(Option.builder("u")
				.argName("user name")
				.longOpt("user")
				.hasArg()
				.required()
				.desc("TibCo user")
				.build());

		options.addOption(Option.builder("p")
			.argName("password")
			.longOpt("pass")
			.hasArg()
			.required()
			.desc("TibCo password")
			.build());

		options.addOption(Option.builder("q")
			.argName("name")
			.longOpt("queue")
			.hasArg()
			.required()
			.desc("TibCo queue name")
			.build());

		options.addOption(Option.builder("d")
			.argName("directory")
			.longOpt("source")
			.hasArg()
			.required()
			.desc("Source directory")
			.build());

		options.addOption(Option.builder("t")
			.argName("directory")
			.longOpt("target")
			.hasArg()
			.required()
			.desc("Target directory")
			.build());

		options.addOption(Option.builder("h")
				.longOpt("headers")
				.desc("Top of file include headers that end at an empty line (Header Name: Header Value")
				.build());

		options.addOption(Option.builder()
			.argName(FILE_ARG)
			.longOpt(SSL_CA_OPT)
			.hasArg()
			.desc("SSL certificate authority file")
			.build());

		options.addOption(Option.builder()
			.argName(FILE_ARG)
			.longOpt(SSL_SERVER_OPT)
			.hasArg()
			.desc("SSL server key file")
			.build());

		options.addOption(Option.builder()
			.argName(FILE_ARG)
			.longOpt(SSL_CLIENT_OPT)
			.hasArg()
			.desc("SSL client key file")
			.build());

		options.addOption(Option.builder()
			.argName("password")
			.longOpt(SSL_PASS_OPT)
			.hasArg()
			.desc("SSL Password")
			.build());

		CommandLine cmd = null;
		try {

			CommandLineParser parser = new DefaultParser();
			cmd = parser.parse(options, args);

		} catch (ParseException e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(" \n\n", options);
			System.err.println("\nCommand line error: " + e.getMessage());
			System.exit(1);
		}

		boolean isSSLConnection = isSSLConnection(cmd.getOptionValue(SERVER_OPT));

		boolean hasAnySSLOption = cmd.hasOption(SSL_CA_OPT)
			|| cmd.hasOption(SSL_SERVER_OPT)
			|| cmd.hasOption(SSL_CLIENT_OPT)
			|| cmd.hasOption(SSL_PASS_OPT);

		boolean hasAllRequiredSSLOptions = cmd.hasOption(SSL_CA_OPT)
			&& cmd.hasOption(SSL_SERVER_OPT)
			&& cmd.hasOption(SSL_CLIENT_OPT)
			&& cmd.hasOption(SSL_PASS_OPT);

		if (!isSSLConnection && hasAnySSLOption) {
			LOG.warn("Ignoring SSL options, server url does not start with \"ssl://\"");
		}

		if ((isSSLConnection || hasAnySSLOption) && !hasAllRequiredSSLOptions) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(" \n\n", options);
			System.err.println("\nCommand line error: Missing SSL option");
			System.exit(1);
		}

		return cmd;
	}

	private static boolean isSSLConnection(final String url) {
		return url.startsWith("ssl:");
	}
}
