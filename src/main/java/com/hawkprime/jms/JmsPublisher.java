package com.hawkprime.jms;

import java.io.IOException;

import javax.jms.JMSException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
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
	public static void main(final String[] args) throws JMSException {
		LOG.info("TibCo Message Publisher v1.0");
		Options options = new Options();

		options.addOption(Option.builder("s")
			.argName("url")
			.longOpt("server")
			.hasArg()
			.required()
			.desc("TibCo server URL: \"tcp://192.168.56.202:7222\"")
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
