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
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class MailImportException extends RuntimeException {
  private EmailMessage email;
  private final static String COLUMN_WIDTH_SPACES = "                    ";
  private final static int COLUMN_WIDTH = 20;

  public MailImportException(String errMessage, EmailMessage email,
      Throwable cause) {
    super(errMessage, cause);
    this.email = email;
  }

  public EmailMessage getEmail() {
    return this.email;
  }

  public void handleException() {
    EmailMessage email = this.getEmail();

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream msgStream = new PrintStream(buffer);

    msgStream.println();
    msgStream.println("########################################");
    this.printStackTrace(msgStream);
    if (email != null) {
      msgStream.println("### Message Info #######################");
      this.printValue(msgStream, "Nr: ", email.nr);
      this.printValue(msgStream, "MessageID: ", email.messageid);
      this.printValue(msgStream, "DBKey: ", email.key);
      this.printValue(msgStream, "Referenced: ", email.referenced);
      this.printValue(msgStream, "Subject: ", email.subject);
      this.printValue(msgStream, "X-Mailer: ", email.xmailer);
    }
    msgStream.println("########################################");
    System.err.println(buffer.toString());
  }

  private void printValue(PrintStream stream, String key, Object value) {
    int fill = COLUMN_WIDTH - key.length();
    if (value == null)
      value = "<EMPTY>";

    if (fill < 0) {
      System.err.println("keys may not exceed " + COLUMN_WIDTH + " characters");
      key = key.substring(0, COLUMN_WIDTH);
    }

    stream.println(key + COLUMN_WIDTH_SPACES.substring(0, fill) + value);
  }
}
