package com.hawkprime.jms;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FileProcessor implements DirectoryListener {
	private static final Logger LOG = LoggerFactory.getLogger(FileProcessor.class);
	private File targetDirectory;
	private TibcoQueue tibcoQueue;
	private String sourceDirectory;
	private boolean hasHeaders;

	/**
	 * Constructor.
	 *
	 * @param tibcoQueue TibCo queue
	 * @param sourceDirectory source directory
	 * @param targetDirectoryPath target directory
	 * @param hasHeaders File contains headers.
	 * @throws IOException exception
	 */
	public FileProcessor(final TibcoQueue tibcoQueue, final String sourceDirectory,
			final String targetDirectoryPath, final boolean hasHeaders) throws IOException {

		this.tibcoQueue = tibcoQueue;
		this.sourceDirectory = sourceDirectory;
		this.targetDirectory = new File(targetDirectoryPath).getCanonicalFile();
		this.hasHeaders = hasHeaders;

		if (!targetDirectory.isDirectory()) {
			LOG.error("Path \"{}\" is not a directory.", targetDirectoryPath);
			throw new IOException("Target directory does not exist.");
		}

		if (hasHeaders) {
			LOG.info("File can contain headers");
		}
	}

	/**
	 * Get target directory.
	 *
	 * @return directory
	 */
	public String getTarget() {
		return targetDirectory.getAbsolutePath();
	}

	/**
	 * Process the file.
	 *
	 * @param file file
	 * @return true if successful, false otherwise.
	 */
	@SuppressWarnings("unchecked")
	private void processFile(final File file) {
		try {

			String relativePath = file.getAbsolutePath().replace(sourceDirectory, "");
			LOG.info("Processing file \"{}\"", relativePath);
			if (hasHeaders) {
				Map<String, Object> message = splitMessage(FileUtils.readFileToString(file));
				tibcoQueue.sendMessage((String) message.get("message"), (Map<String, String>) message.get("headers"));
			} else {
				tibcoQueue.sendMessage(FileUtils.readFileToString(file));
			}
			LOG.info("Message published to queue from file \"{}\"", relativePath);

		} catch (IOException e) {
			LOG.error("Unable to read file \"{}\", leaving at existing location.", file.getAbsolutePath());
			return;

		} catch (JMSException e) {
			LOG.error("Unable to send message to the queue: {}, leaving file at existnig location.", e.getMessage());
			return;
		}

		moveFileToTargetDirectory(file);
	}

	private Map<String, Object> splitMessage(String text) {
		Map<String, Object> message = new HashMap<String, Object>();
		StringBuilder messageText = new StringBuilder();
		Map<String, String> headers = new HashMap<String, String>();

		boolean inHeader = true;
		for (String line : text.split("\\r?\\n")) {
			if (inHeader) {
				// empty line, no more headers
				if (line.trim().length() == 0) {
					inHeader = false;
					continue;
				}

				// found separator and there's only one separator
				int index = line.indexOf(":");
				if (index != -1 && index == line.lastIndexOf(":")) {
					String[] components = line.split(":\\s*");
					headers.put(components[0], components[1]);
					continue;
				}

				inHeader = false;
			}

			messageText.append(line);
			messageText.append("\n");
		}

		message.put("headers", headers);
		message.put("message", messageText.toString());
		return message;
	}

	@Override
	public void fileAdded(final File file) {
		processFile(file);
	}

	@Override
	public void fileModified(final File file) {
		processFile(file);
	}

	@Override
	public void fileDeleted(final File file) {
		/* ignore */
	}

	private void moveFileToTargetDirectory(final File sourceFile) {
		String relativePath = sourceFile.getAbsolutePath().replace(sourceDirectory, "");
		LOG.trace("Reliave path \"{}\"", relativePath);

		File targetFile = new File(targetDirectory.getAbsolutePath() + relativePath);
		LOG.trace("Full path \"{}\"", targetFile.getAbsolutePath());

		if (targetFile.exists()) {
			LOG.warn("File \"{}\" already exists in target directory, overriding.", targetFile.getAbsolutePath());
			if (!targetFile.delete()) {
				LOG.error("Unable to delete exiting file \"{}\"", targetFile.getAbsolutePath());
				return;
			}

		} else {
			File fileParentDirectory = targetFile.getParentFile();
			if (!fileParentDirectory.exists()) {
				if (!fileParentDirectory.mkdirs()) {
					LOG.error("Unable to create target directory structure \"{}\"",
							fileParentDirectory.getAbsolutePath());
					return;
				}
			}
		}

		if (!sourceFile.renameTo(targetFile)) {
			LOG.error("Unable to move file from \"{}\" to \"{}\"",
					sourceFile.getAbsoluteFile(), targetFile.getAbsolutePath());
		}
	}
}
