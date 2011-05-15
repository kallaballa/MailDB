/*******************************************************************************
 * Copyright (C) 2009-2011 Amir Hassan <amir@viel-zu.org>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 ******************************************************************************/
import java.io.ByteArrayInputStream;
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

    String strAutoCommit = this.getProperty("db.autocommit");
    this.autocommit = strAutoCommit != null ? strAutoCommit.trim()
        .equalsIgnoreCase("true") : null;

    String strTruncate = this.getProperty("db.truncateOnConnect");
    this.truncateOnConnect = strTruncate != null ? strTruncate.trim()
        .equalsIgnoreCase("true") : null;

    String strDebug = this.getProperty("debug");
    MailDB.debug = strDebug != null ? strDebug.trim().equalsIgnoreCase("true")
        : null;

    String errorCodes = this.getProperty("db.ignoreErrorCode");
    if (errorCodes != null)
      this.parseErrorCodes(errorCodes);
  }

  public void close() throws SQLException {
    System.err.println("addrcnt: " + addresscnt);
    this.conn.close();
  }

  public void commit() throws SQLException {
    if (!this.autocommit)
      this.conn.commit();
  }

  public void connect() throws IllegalAccessException, InstantiationException,
      SQLException, ClassNotFoundException {
    String userName = this.getProperty("db.user");
    String password = this.getProperty("db.pass");
    String dburl = this.getProperty("jdbc.url");
    String driver = this.getProperty("jdbc.driver");
    Class.forName(driver).newInstance();

    this.conn = DriverManager.getConnection(dburl, userName, password);
    this.conn.setAutoCommit(this.autocommit);

    this.stmntInsertEnvelopePart = this.conn
        .prepareStatement("INSERT INTO Envelope_Part (idEnvelope, idPart, idParent) "
            + "values (?, ?, ?)");
    this.stmntInsertPart = this.conn
        .prepareStatement(
            "INSERT INTO Part (filename, content, contentType, contentLength, decodedContent, idReferencedEnvelope) "
                + "values (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
    this.stmntInsertEnvelope = this.conn.prepareStatement(
        "INSERT INTO Envelope (messageID, subject, sendDate, xmailer, useragent) "
            + "values (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
    this.stmntInsertSubscriber = this.conn.prepareStatement(
        "INSERT INTO Subscriber (Address, Name) " + "values (?,?)",
        Statement.RETURN_GENERATED_KEYS);
    this.stmntInsertEnvelopeSubscriber = this.conn
        .prepareStatement("INSERT INTO Envelope_Subscriber(idEnvelope, idSubscriber, type) "
            + "values (?,?,?)");
    this.stmntSelectEnvelope = this.conn
        .prepareStatement("select idEnvelope from Envelope where messageID = ? ");
    this.stmntSelectSubscriber = this.conn
        .prepareStatement("select idSubscriber from Subscriber where address  = ?  and name = ?");

    if (this.truncateOnConnect)
      this.truncateAll(this.conn);
  }

  private String getProperty(String key) {
    return this.strip(this.dbProps.getProperty(key));
  }

  public void insertChildParts(EmailPart parent) throws SQLException {
    Enumeration<EmailPart> enumPart = parent.children.elements();
    EmailPart child;
    while (enumPart.hasMoreElements()) {
      child = enumPart.nextElement();
      child.emailKey = parent.emailKey;
      child.parentKey = parent.key;

      if (this.insertPart(child))
        this.insertChildParts(child);
    }
  }

  public boolean insertEnvelope(EmailMessage email) throws SQLException {
    ResultSet rs = null;
    try {
      this.stmntInsertEnvelope.setString(1, email.messageid);
      this.stmntInsertEnvelope.setString(2, email.subject);
      this.stmntInsertEnvelope.setDate(3, email.senddate);
      this.stmntInsertEnvelope.setString(4, email.xmailer);
      this.stmntInsertEnvelope.setString(5, email.useragent);

      try {
        this.stmntInsertEnvelope.executeUpdate();

        rs = this.stmntInsertPart.getGeneratedKeys();

        if (rs.next())
          email.key = rs.getInt(1);
      } catch (SQLException ex) {
        if (this.throwSQLException(ex) && email.referenced) {
          this.stmntSelectEnvelope.setString(1, email.messageid);
          rs = this.stmntSelectEnvelope.executeQuery();

          if (rs.next())
            email.key = rs.getInt(1);
        }
      }

      if (rs != null)
        rs.close();

      if (email.from != null)
        this.insertEnvelopeSubscriber(email.key, email.from.elements(), FROM);
      if (email.to != null)
        this.insertEnvelopeSubscriber(email.key, email.to.elements(), TO);
      if (email.replyto != null)
        this.insertEnvelopeSubscriber(email.key, email.replyto.elements(),
            REPLYTO);
      if (email.cc != null)
        this.insertEnvelopeSubscriber(email.key, email.cc.elements(), CC);

      return true;
    } catch (SQLException e) {
      this.throwSQLException(e);
    }
    return false;
  }

  public boolean insertEnvelopePart(int keyEmail, int keyParent, int keyPart)
      throws SQLException {
    try {
      this.stmntInsertEnvelopePart.setInt(1, keyEmail);
      this.stmntInsertEnvelopePart.setInt(2, keyPart);
      this.stmntInsertEnvelopePart.setInt(3, keyParent);
      this.stmntInsertEnvelopePart.executeUpdate();
      return true;
    } catch (SQLException e) {
      this.throwSQLException(e);
    }
    return false;
  }

  public boolean insertEnvelopeSubscriber(int keyEmail,
      Enumeration<Integer> keys, String type) throws SQLException {
    while (keys.hasMoreElements()) {
      try {
        this.stmntInsertEnvelopeSubscriber.setInt(1, keyEmail);
        this.stmntInsertEnvelopeSubscriber.setInt(2, keys.nextElement());
        this.stmntInsertEnvelopeSubscriber.setString(3, type);
        this.stmntInsertEnvelopeSubscriber.executeUpdate();
      } catch (SQLException e) {
        this.throwSQLException(e);
      }
    }
    return true;
  }

  public boolean insertPart(EmailPart ep) throws SQLException {
    try {
      ResultSet rs = null;
      Blob b = null;
      ;

      this.stmntInsertPart.setString(1, ep.fileName);
      if (ep.content != null)
        this.stmntInsertPart.setBinaryStream(2, new ByteArrayInputStream(
            ep.content), ep.content.length);
      else
        this.stmntInsertPart.setBlob(2, (Blob) null);

      this.stmntInsertPart.setString(3, ep.contentType);
      this.stmntInsertPart
          .setInt(4, ep.content != null ? ep.content.length : 0);
      this.stmntInsertPart.setString(5, ep.decodedContent);
      this.stmntInsertPart.setInt(6, ep.referencedEmailKey);
      this.stmntInsertPart.executeUpdate();

      rs = this.stmntInsertPart.getGeneratedKeys();

      if (rs.next()) {
        int keyPart = rs.getInt(1);
        ep.key = keyPart;
        this.insertEnvelopePart(ep.emailKey, ep.parentKey, keyPart);

      } else {
        System.err.println("No key for child of: " + ep.parentKey);
      }

      if (rs != null)
        rs.close();
      return true;
    } catch (SQLException e) {
      this.throwSQLException(e);
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
        this.stmntInsertSubscriber.setString(1, a[j].getAddress());
        this.stmntInsertSubscriber.setString(2, a[j].getPersonal());

        try {
          this.stmntInsertSubscriber.executeUpdate();
          rs = this.stmntInsertSubscriber.getGeneratedKeys();

          if (rs.next())
            keys.add(rs.getInt(1));

        } catch (SQLException ex) {
          if (this.throwSQLException(ex)) {
            this.stmntSelectSubscriber.setString(1, a[j].getAddress());
            this.stmntSelectSubscriber.setString(2, a[j].getPersonal());
            rs = this.stmntSelectSubscriber.executeQuery();

            if (rs.next())
              keys.add(rs.getInt(1));
          }
        }

        if (rs != null)
          rs.close();

      } catch (SQLException e) {
        this.throwSQLException(e);
      }
    }
    return keys;
  }

  private void parseErrorCodes(String codeList) {
    this.ignoredSQLErrors = new TreeSet<Integer>();
    StringTokenizer errCodes = new StringTokenizer(codeList, ",");
    while (errCodes.hasMoreTokens())
      this.ignoredSQLErrors.add(Integer.parseInt(errCodes.nextToken().trim()));
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

  private boolean throwSQLException(SQLException ex) throws SQLException {
    if (debug)
      ex.printStackTrace();

    if (!this.ignoredSQLErrors.contains(ex.getErrorCode()))
      throw ex;
    else
      return true;
  }

  private void truncateAll(Connection conn) throws SQLException {
    Statement stmnt;
    for (String element : TABLE_NAMES) {
      stmnt = conn.createStatement();
      stmnt.execute("truncate table " + element);
    }
  }
}
