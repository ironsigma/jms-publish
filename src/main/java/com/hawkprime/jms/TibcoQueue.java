package com.hawkprime.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class JmsQueue.
 */
public class TibcoQueue {
	private static final Logger LOG = LoggerFactory.getLogger(TibcoQueue.class);

	private Connection connection;
	private Session session;
	private MessageProducer msgProducer;
	private Destination destination;

	private String serverUrl;
	private String userName;
	private String password;
	private String queueName;

	/**
	 * Instantiates a new TibCo queue.
	 *
	 * @param serverUrl the server URL
	 * @param userName the user name
	 * @param password the password
	 * @param queueName the queue name
	 */
	public TibcoQueue(final String serverUrl, final String userName,
			final String password, final String queueName) {

		this.serverUrl = serverUrl;
		this.userName = userName;
		this.password = password;
		this.queueName = queueName;
	}

	/**
	 *  Connect.
	 *
	 * @throws JMSException the JMS exception
	 */
	public void connect() throws JMSException {
		LOG.info("Connecting to \"{}/{}\" as \"{}\" ...", serverUrl, queueName, userName);
		ConnectionFactory factory = new com.tibco.tibjms.TibjmsConnectionFactory(serverUrl);
		connection = factory.createConnection(userName, password);
		session = connection.createSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
		destination = session.createQueue(queueName);
		msgProducer = session.createProducer(null);
	}

	/**
	 * Send queue message.
	 *
	 * @param message the message
	 * @throws JMSException the JMS exception
	 */
	public void sendMessage(final String message) throws JMSException {
		TextMessage msg = session.createTextMessage();
		msg.setText(message);
		msgProducer.send(destination, msg);
	}

	/**
	 * Close the connection.
	 */
	public void close() {
		if (connection != null) {
			try {
				LOG.info("Closing TibCo Connection");
				connection.close();
			} catch (JMSException e) {
				LOG.error("Unable to close TibCo Connection.");
			}
		}
	}
}
