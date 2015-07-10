package com.hawkprime.jms;

import java.io.File;

/**
 * The listener interface for receiving directory events. The class that is interested in processing
 * a directory event implements this interface, and the object created with that class is registered
 * with a component using the component's <code>addDirectoryListener</code> method. When
 * the directory event occurs, that object's appropriate method is invoked.
 *
 * @see DirectoryEvent
 */
public interface DirectoryListener {

	/**
	 * File added.
	 *
	 * @param file the file
	 */
	void fileAdded(File file);

	/**
	 * File modified.
	 *
	 * @param file the file
	 */
	void fileModified(File file);

	/**
	 * File deleted.
	 *
	 * @param file the file
	 */
	void fileDeleted(File file);
}