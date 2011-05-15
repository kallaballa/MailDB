import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class MailImportException extends RuntimeException {
	private EmailMessage email;
	private final static String COLUMN_WIDTH_SPACES = "                    ";
	private final static int COLUMN_WIDTH = 20;

	public MailImportException(String errMessage, EmailMessage email, Throwable cause) {
		super(errMessage, cause);
		this.email = email;
	}

	public EmailMessage getEmail() {
		return email;
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
			printValue(msgStream, "Nr: ", email.nr);
			printValue(msgStream, "MessageID: ", email.messageid);
			printValue(msgStream, "DBKey: ", email.key);
			printValue(msgStream, "Referenced: ", email.referenced);
			printValue(msgStream, "Subject: ", email.subject);
			printValue(msgStream, "X-Mailer: ", email.xmailer);
		}
		msgStream.println("########################################");
		System.err.println(buffer.toString());
	}

	private void printValue(PrintStream stream, String key, Object value) {
		int fill = COLUMN_WIDTH - key.length();
		if(value == null)
			value = "<EMPTY>";
		
		if (fill < 0) {
			System.err.println("keys may not exceed " + COLUMN_WIDTH
					+ " characters");
			key = key.substring(0, COLUMN_WIDTH);
		}

		stream.println(key + COLUMN_WIDTH_SPACES.substring(0, fill) + value);
	}
}
