import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;

import javax.mail.internet.InternetAddress;

public class MailDB {

	private final static String TO = "To";
	private final static String FROM = "From";
	private final static String CC = "CC";
	private final static String REPLYTO = "Replyto";

	public static boolean debug = false;

	private PreparedStatement stmntInsertEnvelopePart;
	private PreparedStatement stmntInsertPart;
	private PreparedStatement stmntInsertEnvelope;
	private PreparedStatement stmntInsertSubscriber;
	private PreparedStatement stmntInsertEnvelopeSubscriber;

	private PreparedStatement stmntSelectEnvelope;
	private PreparedStatement stmntSelectSubscriber;

	private final static String[] TABLE_NAMES = { "Envelope", "Part",
			"Subscriber", "Envelope_Part", "Envelope_Subscriber" };

	private Properties dbProps;
	private Connection conn;

	private TreeSet<Integer> ignoredSQLErrors = null;
	private boolean autocommit = false;
	private boolean truncateOnConnect = false;
	private static int addresscnt = 0;

        public MailDB(Properties dbProps) {
		this.dbProps = dbProps;

		String strAutoCommit = getProperty("db.autocommit");
		this.autocommit = strAutoCommit != null ? strAutoCommit.trim()
				.equalsIgnoreCase("true") : null;

		String strTruncate = getProperty("db.truncateOnConnect");
		this.truncateOnConnect = strTruncate != null ? strTruncate.trim()
				.equalsIgnoreCase("true") : null;

		String strDebug = getProperty("debug");
		MailDB.debug = strDebug != null ? strDebug.trim()
				.equalsIgnoreCase("true") : null;

		String errorCodes = getProperty("db.ignoreErrorCode");
		if (errorCodes != null)
			parseErrorCodes(errorCodes);
	}

	private void truncateAll(Connection conn) throws SQLException {
		Statement stmnt;
		for (int i = 0; i < TABLE_NAMES.length; i++) {
			stmnt = conn.createStatement();
			stmnt.execute("truncate table " + TABLE_NAMES[i]);
		}
	}

	public void connect() throws IllegalAccessException,
			InstantiationException, SQLException, ClassNotFoundException {
		String userName = getProperty("db.user");
		String password = getProperty("db.pass");
		String dburl = getProperty("jdbc.url");
		String driver = getProperty("jdbc.driver");
		Class.forName(driver).newInstance();

		this.conn = DriverManager.getConnection(dburl, userName, password);
		this.conn.setAutoCommit(this.autocommit);

		this.stmntInsertEnvelopePart = conn
				.prepareStatement("INSERT INTO Envelope_Part (idEnvelope, idPart, idParent) "
						+ "values (?, ?, ?)");
		this.stmntInsertPart = conn
				.prepareStatement(
						"INSERT INTO Part (filename, content, contentType, contentLength, decodedContent, idReferencedEnvelope) "
								+ "values (?, ?, ?, ?, ?, ?)",
						Statement.RETURN_GENERATED_KEYS);
		this.stmntInsertEnvelope = conn.prepareStatement(
				"INSERT INTO Envelope (messageID, subject, sendDate, xmailer, useragent) "
						+ "values (?, ?, ?, ?, ?)",
				Statement.RETURN_GENERATED_KEYS);
		this.stmntInsertSubscriber = conn.prepareStatement(
				"INSERT INTO Subscriber (Address, Name) " + "values (?,?)",
				Statement.RETURN_GENERATED_KEYS);
		this.stmntInsertEnvelopeSubscriber = conn
				.prepareStatement("INSERT INTO Envelope_Subscriber(idEnvelope, idSubscriber, type) "
						+ "values (?,?,?)");
		this.stmntSelectEnvelope = conn
				.prepareStatement("select idEnvelope from Envelope where messageID = ? ");
		this.stmntSelectSubscriber = conn
				.prepareStatement("select idSubscriber from Subscriber where address  = ?  and name = ?");

		if (this.truncateOnConnect)
			truncateAll(conn);
	}

	private boolean throwSQLException(SQLException ex) throws SQLException {
		if(debug)
			ex.printStackTrace();
		
		if (!ignoredSQLErrors.contains(ex.getErrorCode()))
			throw ex;
		else 
			return true;
	}

	private void parseErrorCodes(String codeList) {
		ignoredSQLErrors = new TreeSet<Integer>();
		StringTokenizer errCodes = new StringTokenizer(codeList, ",");
		while (errCodes.hasMoreTokens())
			ignoredSQLErrors.add(Integer.parseInt(errCodes.nextToken().trim()));
	}

	private String getProperty(String key) {
		return strip(dbProps.getProperty(key));
	}

	private String strip(String prop) {
		if (prop != null) {
			prop = prop.trim();
			int lastChar = prop.length() - 1;
			if (prop.charAt(0) == '"' && prop.charAt(lastChar) == '"')
				return prop.substring(1, lastChar);
			else
				return prop;
		}
		return null;
	}

	public void close() throws SQLException {
		System.err.println("addrcnt: " + addresscnt);
		conn.close();
	}

	public boolean insertEnvelopePart(int keyEmail, int keyParent, int keyPart)
			throws SQLException {
		try {
			stmntInsertEnvelopePart.setInt(1, keyEmail);
			stmntInsertEnvelopePart.setInt(2, keyPart);
			stmntInsertEnvelopePart.setInt(3, keyParent);
			stmntInsertEnvelopePart.executeUpdate();
			return true;
		} catch (SQLException e) {
			throwSQLException(e);
		}
		return false;
	}

	public boolean insertPart(EmailPart ep) throws SQLException {
		try {
			ResultSet rs = null;
			Blob b = null;
			;

			stmntInsertPart.setString(1, ep.fileName);
			if (ep.content != null)
				stmntInsertPart.setBinaryStream(2, new ByteArrayInputStream(
						ep.content), ep.content.length);
			else
				stmntInsertPart.setBlob(2, (Blob)null);

			stmntInsertPart.setString(3, ep.contentType);
			stmntInsertPart.setInt(4, ep.content != null ? ep.content.length
					: 0);
			stmntInsertPart.setString(5, ep.decodedContent);
			stmntInsertPart.setInt(6, ep.referencedEmailKey);
			stmntInsertPart.executeUpdate();

			rs = stmntInsertPart.getGeneratedKeys();

			if (rs.next()) {
				int keyPart = rs.getInt(1);
				ep.key = keyPart;
				insertEnvelopePart(ep.emailKey, ep.parentKey, keyPart);

			} else {
				System.err.println("No key for child of: " + ep.parentKey);
			}

			if (rs != null)
				rs.close();
			return true;
		} catch (SQLException e) {
			throwSQLException(e);
		}
		return false;
	}

	public void insertChildParts(EmailPart parent) throws SQLException {
		Enumeration<EmailPart> enumPart = parent.children.elements();
		EmailPart child;
		while (enumPart.hasMoreElements()) {
			child = enumPart.nextElement();
			child.emailKey = parent.emailKey;
			child.parentKey = parent.key;

			if (insertPart(child))
				insertChildParts(child);
		}
	}

	public boolean insertEnvelope(EmailMessage email) throws SQLException {
		ResultSet rs = null;
		try {
			stmntInsertEnvelope.setString(1, email.messageid);
			stmntInsertEnvelope.setString(2, email.subject);
			stmntInsertEnvelope.setDate(3, email.senddate);
			stmntInsertEnvelope.setString(4, email.xmailer);
			stmntInsertEnvelope.setString(5, email.useragent);

			try {
				stmntInsertEnvelope.executeUpdate();

				rs = stmntInsertPart.getGeneratedKeys();

				if (rs.next())
					email.key = rs.getInt(1);
			} catch (SQLException ex) {
				if (throwSQLException(ex) && email.referenced) {
					stmntSelectEnvelope.setString(1, email.messageid);
					rs = stmntSelectEnvelope.executeQuery();

					if (rs.next())
						email.key = rs.getInt(1);
				}
			}

			if (rs != null)
				rs.close();

			if (email.from != null)
				insertEnvelopeSubscriber(email.key, email.from.elements(), FROM);
			if (email.to != null)
				insertEnvelopeSubscriber(email.key, email.to.elements(), TO);
			if (email.replyto != null)
				insertEnvelopeSubscriber(email.key, email.replyto.elements(),
						REPLYTO);
			if (email.cc != null)
				insertEnvelopeSubscriber(email.key, email.cc.elements(), CC);

			return true;
		} catch (SQLException e) {
			throwSQLException(e);
		}
		return false;
	}

	public Vector<Integer> insertSubscriber(InternetAddress[] a)
			throws SQLException {
		addresscnt += a.length;

		ResultSet rs = null;
		Vector<Integer> keys = new Vector<Integer>();

		for (int j = 0; j < a.length; j++) {
			try {
				stmntInsertSubscriber.setString(1, a[j].getAddress());
				stmntInsertSubscriber.setString(2, a[j].getPersonal());

				try {
					stmntInsertSubscriber.executeUpdate();
					rs = stmntInsertSubscriber.getGeneratedKeys();

					if (rs.next())
						keys.add(rs.getInt(1));

				} catch (SQLException ex) {
					if (throwSQLException(ex)) {
						stmntSelectSubscriber.setString(1, a[j].getAddress());
						stmntSelectSubscriber.setString(2, a[j].getPersonal());
						rs = stmntSelectSubscriber.executeQuery();

						if (rs.next())
							keys.add(rs.getInt(1));
					}
				}

				if (rs != null)
					rs.close();

			} catch (SQLException e) {
				throwSQLException(e);
			}
		}
		return keys;
	}

	public void commit() throws SQLException {
		if (!this.autocommit)
			this.conn.commit();
	}

	public boolean insertEnvelopeSubscriber(int keyEmail,
			Enumeration<Integer> keys, String type) throws SQLException {
		while (keys.hasMoreElements()) {
			try {
				stmntInsertEnvelopeSubscriber.setInt(1, keyEmail);
				stmntInsertEnvelopeSubscriber.setInt(2, keys.nextElement());
				stmntInsertEnvelopeSubscriber.setString(3, type);
				stmntInsertEnvelopeSubscriber.executeUpdate();
			} catch (SQLException e) {
				throwSQLException(e);
			}
		}
		return true;
	}
}
