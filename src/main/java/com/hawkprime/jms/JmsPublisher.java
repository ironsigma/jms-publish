package com.hawkprime.jms;

import java.io.IOException;

import javax.jms.JMSException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
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
	@SuppressWarnings({ "static-access", "deprecation" })
	public static void main(final String[] args) throws JMSException {
		LOG.info("TibCo Message Publisher v1.0");
		Options options = new Options();

		options.addOption(OptionBuilder
			.withArgName("url")
			.withLongOpt("server")
			.hasArg()
			.isRequired()
			.withDescription("TibCo server URL: \"tcp://192.168.56.202:7222\"")
			.create("s"));

		options.addOption(OptionBuilder
			.withArgName("user")
			.withLongOpt("user")
			.hasArg()
			.isRequired()
			.withDescription("TibCo user")
			.create("u"));

		options.addOption(OptionBuilder
			.withArgName("password")
			.withLongOpt("pass")
			.hasArg()
			.isRequired()
			.withDescription("TibCo password")
			.create("p"));

		options.addOption(OptionBuilder
			.withArgName("name")
			.withLongOpt("queue")
			.hasArg()
			.isRequired()
			.withDescription("TibCo queue name")
			.create("q"));

		options.addOption(OptionBuilder
			.withArgName("directory")
			.withLongOpt("source")
			.hasArg()
			.isRequired()
			.withDescription("Source directory")
			.create("d"));

		options.addOption(OptionBuilder
			.withArgName("directory")
			.withLongOpt("target")
			.hasArg()
			.isRequired()
			.withDescription("Target directory")
			.create("t"));

		CommandLine cmd = null;
		try {

			CommandLineParser parser = new DefaultParser();
			cmd = parser.parse(options, args);

		} catch (ParseException e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(" \n\n", options);
			System.err.println("\nCommand line error: " + e.getMessage());
			return;
		}

		final TibcoQueue tibcoQueue = new TibcoQueue(
				cmd.getOptionValue("server"),
				cmd.getOptionValue("user"),
				cmd.getOptionValue("pass"),
				cmd.getOptionValue("queue"));

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

			DirectoryWatcher watcher = new DirectoryWatcher(cmd.getOptionValue("source"), ".xml");

			FileProcessor fileProcessor = new FileProcessor(tibcoQueue,
					watcher.getDirectory(), cmd.getOptionValue("target"));

			watcher.setInterval(5);
			watcher.addListener(fileProcessor);

			LOG.info("Watching directory \"{}\"", watcher.getDirectory());
			LOG.info("Moving proccessed files to \"{}\"", fileProcessor.getTarget());

			watcher.start();

		} catch (IOException ex) {
			LOG.error(ex.getMessage());
		}

	}
}
