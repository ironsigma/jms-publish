package com.hawkprime.jms;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class DirectoryWatcher.
 */
public class DirectoryWatcher {
	private static final Logger LOG = LoggerFactory.getLogger(DirectoryWatcher.class);
	private static final int DEFAULT_INTERVAL = 2000;
	private static final int MILLIS_IN_SECOND = 1000;

	private enum FileEvent { ADDED, MODIFIED, DELETED }

	private List<DirectoryListener> listeners = new ArrayList<DirectoryListener>();
	private WatchThread watchThread = new WatchThread();
	private FileFilter extensionFileFilter;
	private File baseDirectory;
	private int interval = DEFAULT_INTERVAL;

	/**
	 * Constructor.
	 *
	 * @param directoryPath directory path
	 * @param extension filter extension
	 * @throws IOException exception
	 */
	public DirectoryWatcher(final String directoryPath, final String extension) throws IOException {
		baseDirectory = new File(directoryPath).getCanonicalFile();
		if (!baseDirectory.isDirectory()) {
			LOG.error("Path \"{}\" is not a directory.", directoryPath);
			throw new IOException("Source directory does not exist.");
		}

		extensionFileFilter = new ExtensionFileFilter(extension);
	}


	/**
	 * Set the watch interval.
	 * @param interval in seconds
	 */
	public void setInterval(final int interval) {
		this.interval = interval * MILLIS_IN_SECOND;

		if (this.interval < DEFAULT_INTERVAL) {
			this.interval = DEFAULT_INTERVAL;
		}
	}

	/**
	 * Get watch directory.
	 *
	 * @return directory
	 */
	public String getDirectory() {
		return baseDirectory.getAbsolutePath();
	}

	/**
	 * Adds the listener.
	 *
	 * @param listener the listener
	 */
	public void addListener(final DirectoryListener listener) {
		listeners.add(listener);
	}

	/**
	 * Start.
	 */
	public void start() {
		watchThread.start();
	}

	/**
	 * Stop.
	 */
	public void stop() {
		watchThread.halt();
	}

	/**
	 * The Class WatchThread.
	 */
	class WatchThread extends Thread {
		private boolean running = true;
		private Map<String, Long> fileModificationTimes = new HashMap<String, Long>();
		private List<String> deletedFiles = new ArrayList<String>();

		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			LOG.debug("Starting to watch directory \"{}\"", baseDirectory.getAbsolutePath());
			try {
				while (running) {

					scanDirectory(baseDirectory);

					LOG.trace("Sleeping for {} ms.", interval);
					Thread.sleep(interval);
				}
			} catch (InterruptedException e) {
				LOG.warn("Interrupted sleep.");
			}
			LOG.debug("Stopped watching.");
		}

		/**
		 * Scan directory for changes.
		 *
		 * @param directory directory
		 */
		public void scanDirectory(final File directory) {
			LOG.trace("Scanning directory \"{}\"", directory.getAbsolutePath());
			File[] fileArray = directory.listFiles(extensionFileFilter);

			// For each previously watched files
			for (String name : fileModificationTimes.keySet()) {
				File file = null;
				Long fileModificationTime = null;

				// Look for the physical file
				for (int i = 0; i < fileArray.length; i++) {
					file = fileArray[i];
					if (name.equals(file.getAbsolutePath())) {
						fileModificationTime = file.lastModified();
						break;
					}
				}

				// if found
				if (fileModificationTime != null) {
					Long lastModificationTime = fileModificationTimes.get(name);
					if (lastModificationTime.equals(fileModificationTime)) {
						LOG.debug("File \"{}\" has not changed.", name);

					} else {
						LOG.debug("File \"{}\" changed.", name);
						fileModificationTimes.put(name, fileModificationTime);
						notifyListeners(FileEvent.MODIFIED, file);
					}

				// can't delete while iterating, keep track instead
				} else {
					deletedFiles.add(name);
				}
			}

			// Do deletes
			for (String name : deletedFiles) {
				LOG.debug("File \"{}\" deleted.", name);
				fileModificationTimes.remove(name);
				notifyListeners(FileEvent.DELETED, new File(name));
			}
			deletedFiles.clear();

			// Do Additions
			for (File file : fileArray) {
				if (file.isDirectory()) {
					if (!file.getName().startsWith(".")) {
						scanDirectory(file);
					}
					continue;
				}

				String name = file.getAbsolutePath();
				if (!fileModificationTimes.containsKey(name)) {
					LOG.debug("File \"{}\" added.", name);
					notifyListeners(FileEvent.ADDED, file);
					fileModificationTimes.put(name, file.lastModified());
				}
			}
		}

		/**
		 * Notify listeners.
		 *
		 * @param event the event
		 * @param file the file
		 */
		public void notifyListeners(final FileEvent event, final File file) {
			for (DirectoryListener listener : listeners) {
				switch (event) {
					case ADDED:
						listener.fileAdded(file);
						break;

					case MODIFIED:
						listener.fileModified(file);
						break;

					case DELETED:
						listener.fileDeleted(file);
						break;

					default :
						break;
				}
			}
		}

		/**
		 * Stop watching.
		 */
		public void halt() {
			running = false;
		}
	}

	/**
	 * The Class ExtensionFileFilter.
	 */
	class ExtensionFileFilter implements FileFilter {
		private String extension;
		private Map<String, String> skipped = new HashMap<String, String>();

		/**
		 * Constructor.
		 *
		 * @param extension extension with dot prefix (.xml)
		 */
		public ExtensionFileFilter(final String extension) {
			this.extension = extension;
		}

		/* (non-Javadoc)
		 * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
		 */
		@Override
		public boolean accept(final File file) {
			if (file.isDirectory()
					|| file.getName().toLowerCase().endsWith(extension)) {

				return true;
			}

			String relativePath = file.getAbsolutePath().replace(baseDirectory.getAbsolutePath(), "");
			if (!skipped.containsKey(relativePath)) {
				LOG.warn("Found file \"{}\", it has no \"{}\" extension, skipping.", relativePath, extension);
				skipped.put(relativePath, null);
			}

			return false;
		}
	}
}