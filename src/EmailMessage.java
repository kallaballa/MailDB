import java.sql.Date;
import java.util.Vector;


public class EmailMessage {
	Vector<Integer> from = null;
	Vector<Integer> replyto = null;
	Vector<Integer> to = null;
	Vector<Integer> cc = null;
	int key;
	int nr;
	String messageid;
	String subject;
	Date senddate;
	String xmailer;
	String useragent;
	boolean referenced = false;
}
